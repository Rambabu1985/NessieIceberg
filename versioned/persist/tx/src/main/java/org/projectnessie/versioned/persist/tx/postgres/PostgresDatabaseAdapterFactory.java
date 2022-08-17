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
package org.projectnessie.versioned.persist.tx.postgres;

import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;
import org.projectnessie.versioned.persist.tx.TxConnectionConfig;
import org.projectnessie.versioned.persist.tx.TxConnectionProvider;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapterFactory;

public class PostgresDatabaseAdapterFactory
    extends TxDatabaseAdapterFactory<
        PostgresDatabaseAdapter, TxConnectionProvider<TxConnectionConfig>> {

  public static final String NAME = "PostgreSQL";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected PostgresDatabaseAdapter create(
      TxDatabaseAdapterConfig config,
      TxConnectionProvider<TxConnectionConfig> connectionProvider,
      StoreWorker storeWorker,
      AdapterEventConsumer eventConsumer) {
    return new PostgresDatabaseAdapter(config, connectionProvider, storeWorker, eventConsumer);
  }
}
