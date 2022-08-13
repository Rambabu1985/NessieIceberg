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

import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterFactory;
import org.projectnessie.versioned.persist.adapter.DatabaseConnectionProvider;
import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;

public abstract class TxDatabaseAdapterFactory<
        ADAPTER extends TxDatabaseAdapter, CONNECTOR extends DatabaseConnectionProvider<?>>
    implements DatabaseAdapterFactory<
        ADAPTER, TxDatabaseAdapterConfig, AdjustableTxDatabaseAdapterConfig, CONNECTOR> {

  protected abstract ADAPTER create(
      TxDatabaseAdapterConfig config,
      CONNECTOR connector,
      StoreWorker<?, ?, ?> storeWorker,
      AdapterEventConsumer eventConsumer);

  @Override
  public Builder<ADAPTER, TxDatabaseAdapterConfig, AdjustableTxDatabaseAdapterConfig, CONNECTOR>
      newBuilder() {
    return new TxBuilder();
  }

  private class TxBuilder
      extends Builder<
          ADAPTER, TxDatabaseAdapterConfig, AdjustableTxDatabaseAdapterConfig, CONNECTOR> {
    @Override
    protected TxDatabaseAdapterConfig getDefaultConfig() {
      return ImmutableAdjustableTxDatabaseAdapterConfig.builder().build();
    }

    @Override
    protected AdjustableTxDatabaseAdapterConfig adjustableConfig(TxDatabaseAdapterConfig config) {
      return ImmutableAdjustableTxDatabaseAdapterConfig.builder().from(config).build();
    }

    @Override
    public ADAPTER build(StoreWorker<?, ?, ?> storeWorker) {
      return create(getConfig(), getConnector(), storeWorker, getEventConsumer());
    }
  }
}
