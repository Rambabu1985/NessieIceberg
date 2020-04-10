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
package com.dremio.iceberg.server;

import java.io.IOException;
import java.util.List;

import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import com.dremio.iceberg.client.AlleyCatalog;
import com.dremio.iceberg.client.AlleyClient;
import com.dremio.iceberg.model.Table;

public class CatalogTest {

  private static TestAlleyServer server;
  private AlleyCatalog catalog;
  private AlleyClient client;

  @BeforeClass
  public static void create() throws Exception {
    server = new TestAlleyServer();
    server.start(9992);
  }

  @Before
  public void getCatalog() {
    Configuration hadoopConfig = new Configuration();
    hadoopConfig.set("iceberg.alley.host", "localhost");
    hadoopConfig.set("iceberg.alley.port", "9992");
    hadoopConfig.set("iceberg.alley.ssl", "false");
    hadoopConfig.set("iceberg.alley.base", "api/v1");
    hadoopConfig.set("iceberg.alley.username", "admin_user");
    hadoopConfig.set("iceberg.alley.password", "test123");
    catalog = new AlleyCatalog(new com.dremio.iceberg.model.Configuration(hadoopConfig));
    client = new AlleyClient(hadoopConfig);
  }

  @Test
  public void test() {
    createTable(TableIdentifier.of("foo", "bar"));
    List<TableIdentifier> tables = catalog.listTables(Namespace.of("foo"));
    Assert.assertEquals(1, tables.size());
    Assert.assertEquals(TableIdentifier.of("foo", "bar"), tables.get(0));
    catalog.renameTable(TableIdentifier.of("foo", "bar"), TableIdentifier.of("foo", "baz"));
    tables = catalog.listTables(null);
    Assert.assertEquals(1, tables.size());
    Assert.assertEquals(TableIdentifier.of("foo", "baz"), tables.get(0));
    catalog.dropTable(TableIdentifier.of("foo", "baz"));
    tables = catalog.listTables(Namespace.empty());
    Assert.assertTrue(tables.isEmpty());
  }

  @After
  public void closeCatalog() throws IOException {
    catalog.close();
    client.close();
    catalog = null;
    client = null;
  }

  @AfterClass
  public static void destroy() throws IOException {
    server = null;
  }

  private void createTable(TableIdentifier tableIdentifier) {
    Table table = new Table(tableIdentifier.name(), tableIdentifier.namespace().toString(), null);
    client.createTable(table);
  }

}
