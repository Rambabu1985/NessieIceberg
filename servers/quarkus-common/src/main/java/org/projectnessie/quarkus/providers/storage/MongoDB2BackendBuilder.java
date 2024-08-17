/*
 * Copyright (C) 2022 Dremio
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
package org.projectnessie.quarkus.providers.storage;

import static org.projectnessie.quarkus.config.VersionStoreConfig.VersionStoreType.MONGODB2;

import com.mongodb.client.MongoClient;
import io.quarkus.arc.Arc;
import io.quarkus.mongodb.runtime.MongoClientBeanUtil;
import io.quarkus.mongodb.runtime.MongoClients;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.projectnessie.quarkus.providers.versionstore.StoreType;
import org.projectnessie.versioned.storage.common.persist.Backend;
import org.projectnessie.versioned.storage.mongodb2.MongoDB2BackendConfig;
import org.projectnessie.versioned.storage.mongodb2.MongoDB2BackendFactory;

@StoreType(MONGODB2)
@Dependent
public class MongoDB2BackendBuilder implements BackendBuilder {

  @Inject
  @ConfigProperty(name = "quarkus.mongodb.database")
  String databaseName;

  @Override
  public Backend buildBackend() {
    MongoClients mongoClients = Arc.container().instance(MongoClients.class).get();
    MongoClient client =
        mongoClients.createMongoClient(MongoClientBeanUtil.DEFAULT_MONGOCLIENT_NAME);

    MongoDB2BackendFactory factory = new MongoDB2BackendFactory();
    MongoDB2BackendConfig c =
        MongoDB2BackendConfig.builder().databaseName(databaseName).client(client).build();
    return factory.buildBackend(c);
  }
}
