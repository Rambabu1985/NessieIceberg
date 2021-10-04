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
package org.projectnessie.server.providers;

import static org.projectnessie.server.config.VersionStoreConfig.VersionStoreType.MONGO;

import javax.enterprise.context.Dependent;
import javax.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.mongodb.MongoDatabaseAdapterFactory;
import org.projectnessie.versioned.persist.mongodb.MongoDatabaseClient;
import org.projectnessie.versioned.persist.nontx.NonTransactionalDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.store.PersistVersionStore;

/** Version store factory for the MongoDB Database Adapter. */
@StoreType(MONGO)
@Dependent
public class MongoVersionStoreFactory implements VersionStoreFactory {

  @ConfigProperty(name = "quarkus.mongodb.database")
  String databaseName;

  @Inject MongoDatabaseClient client;
  @Inject NonTransactionalDatabaseAdapterConfig config;

  @Override
  public <VALUE, METADATA, VALUE_TYPE extends Enum<VALUE_TYPE>>
      VersionStore<VALUE, METADATA, VALUE_TYPE> newStore(
          StoreWorker<VALUE, METADATA, VALUE_TYPE> worker, ServerConfig serverConfig) {

    DatabaseAdapter adapter =
        new MongoDatabaseAdapterFactory()
            .newBuilder()
            .withConfig(config)
            .withConnector(client)
            .build();

    adapter.reinitializeRepo(serverConfig.getDefaultBranch());

    return new PersistVersionStore<>(adapter, worker);
  }
}
