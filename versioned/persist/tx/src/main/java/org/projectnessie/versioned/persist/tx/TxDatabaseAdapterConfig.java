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

import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterConfig;

/**
 * TX-database-adapter config interface.
 *
 * <p>See {@link DefaultTxDatabaseAdapterConfig} for the default, immutable-annotated type.
 */
public interface TxDatabaseAdapterConfig extends DatabaseAdapterConfig {

  /**
   * Provides JDBC connections to {@link TxDatabaseAdapter}, must be used in production scenarios.
   */
  @Nullable
  TxConnectionProvider getConnectionProvider();

  TxDatabaseAdapterConfig withConnectionProvider(TxConnectionProvider connectionProvider);

  /**
   * DML batch size, used when writing multiple commits to a branch during a transplant or merge
   * operation or when writing "overflow full key-lists".
   */
  @Value.Default
  default int getBatchSize() {
    return 20;
  }

  TxDatabaseAdapterConfig withBatchSize(int batchSize);

  @Nullable
  String getCatalog();

  TxDatabaseAdapterConfig withCatalog(String catalog);

  @Nullable
  String getSchema();

  TxDatabaseAdapterConfig withSchema(String schema);
}
