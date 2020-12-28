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
package com.dremio.nessie.iceberg.spark;

import com.dremio.nessie.iceberg.NessieCatalog;
import com.dremio.nessie.iceberg.ParsedTableIdentifier;
import java.util.Optional;
import org.apache.hadoop.conf.Configuration;
import org.apache.iceberg.Table;
import org.apache.iceberg.spark.source.IcebergSource;
import org.apache.spark.sql.sources.v2.DataSourceOptions;

/** Get a table from an Iceberg Nessie Catalog. */
public class NessieIcebergSource extends IcebergSource {

  @Override
  protected Table findTable(DataSourceOptions options, Configuration conf) {

    Optional<String> path = options.get("path");
    if (!path.isPresent()) {
      throw new IllegalArgumentException("Cannot open table: path is not set");
    }
    ParsedTableIdentifier identifier =
        ParsedTableIdentifier.getParsedTableIdentifier(path.get(), options.asMap());
    NessieCatalog catalog = new NessieCatalog(conf, identifier.getReference());
    return catalog.loadTable(identifier.getTableIdentifier());
  }
}
