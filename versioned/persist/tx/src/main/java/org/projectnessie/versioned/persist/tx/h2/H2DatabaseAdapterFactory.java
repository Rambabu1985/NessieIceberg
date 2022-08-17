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
package org.projectnessie.versioned.persist.tx.h2;

import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;
import org.projectnessie.versioned.persist.tx.TxConnectionConfig;
import org.projectnessie.versioned.persist.tx.TxConnectionProvider;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapterFactory;

public class H2DatabaseAdapterFactory
    extends TxDatabaseAdapterFactory<H2DatabaseAdapter, TxConnectionProvider<TxConnectionConfig>> {

  public static final String NAME = "H2";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected H2DatabaseAdapter create(
      TxDatabaseAdapterConfig config,
      TxConnectionProvider<TxConnectionConfig> connectionProvider,
      StoreWorker storeWorker,
      AdapterEventConsumer eventConsumer) {
    return new H2DatabaseAdapter(config, connectionProvider, storeWorker, eventConsumer);
  }
}
