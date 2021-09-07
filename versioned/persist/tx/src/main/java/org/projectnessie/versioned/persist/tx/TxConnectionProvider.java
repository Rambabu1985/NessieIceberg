/*
 * Copyright (C) 2020 Dremio
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.projectnessie.versioned.persist.tx;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.text.MessageFormat;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class TxConnectionProvider implements AutoCloseable {

  private static final Logger LOGGER = LoggerFactory.getLogger(TxConnectionProvider.class);

  private boolean setupDone;

  public void configure(TxDatabaseAdapterConfig config) {}

  public synchronized void setupDatabase(
      TxDatabaseAdapter adapter, TxDatabaseAdapterConfig config) {
    if (setupDone) {
      return;
    }

    try (Connection c = this.borrowConnection()) {
      try (Statement st = c.createStatement()) {
        boolean metadataUpperCase = adapter.metadataUpperCase();
        String catalog = config.getCatalog();
        String schema = config.getSchema();
        Object[] formatParamsArray = adapter.databaseSqlFormatParameters().toArray();

        Stream<String> ddls =
            adapter.allCreateTableDDL().entrySet().stream()
                .filter(e -> !tableExists(c, metadataUpperCase, catalog, schema, e.getKey()))
                .flatMap(e -> e.getValue().stream())
                .map(s -> MessageFormat.format(s, formatParamsArray));

        executeDDLs(adapter.batchDDL(), ddls, st);
      }

      setupDone = true;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  private void executeDDLs(boolean batchDDL, Stream<String> ddls, Statement st)
      throws SQLException {
    if (batchDDL) {
      String ddl = ddls.map(s -> s + ";").collect(Collectors.joining("\n\n"));
      if (!ddl.isEmpty()) {
        ddl = "BEGIN;\n" + ddl + "\nEND TRANSACTION;\n";
        LOGGER.debug("Executing DDL batch\n{}", ddl);
        st.execute(ddl);
      }
    } else {
      List<String> ddlStatements = ddls.collect(Collectors.toList());
      for (String ddl : ddlStatements) {
        LOGGER.debug("Executing DDL: {}", ddl);
        st.execute(ddl);
      }
    }
  }

  private static boolean tableExists(
      Connection conn, boolean metadataUpperCase, String catalog, String schema, String table) {
    if (metadataUpperCase) {
      catalog = catalog != null ? catalog.toUpperCase(Locale.ROOT) : null;
      schema = schema != null ? schema.toUpperCase(Locale.ROOT) : null;
      table = table != null ? table.toUpperCase(Locale.ROOT) : null;
    }

    try (ResultSet tables = conn.getMetaData().getTables(catalog, schema, table, null)) {
      return tables.next();
    } catch (SQLException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  public void close() throws Exception {}

  /**
   * Borrow a connection from the {@link TxConnectionProvider} implementation.
   *
   * @return borrowed {@link Connection}
   */
  public abstract Connection borrowConnection() throws SQLException;
}
