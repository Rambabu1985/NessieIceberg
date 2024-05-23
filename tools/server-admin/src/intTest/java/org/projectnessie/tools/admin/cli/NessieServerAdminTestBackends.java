/*
 * Copyright (C) 2024 Dremio
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
package org.projectnessie.tools.admin.cli;

import java.util.Map;
import org.projectnessie.quarkus.config.VersionStoreConfig.VersionStoreType;
import org.projectnessie.versioned.storage.bigtabletests.BigTableBackendContainerTestFactory;
import org.projectnessie.versioned.storage.cassandratests.CassandraBackendTestFactory;
import org.projectnessie.versioned.storage.dynamodbtests.DynamoDBBackendTestFactory;
import org.projectnessie.versioned.storage.jdbctests.MariaDBBackendTestFactory;
import org.projectnessie.versioned.storage.jdbctests.MySQLBackendTestFactory;
import org.projectnessie.versioned.storage.jdbctests.PostgreSQLBackendTestFactory;
import org.projectnessie.versioned.storage.mongodbtests.MongoDBBackendTestFactory;
import org.projectnessie.versioned.storage.testextension.BackendTestFactory;

public enum NessieServerAdminTestBackends {
  mongo {
    @Override
    BackendTestFactory backendFactory() {
      return new MongoDBBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of("nessie.version.store.type", VersionStoreType.MONGODB.name());
    }
  },

  bigtable {
    @Override
    BackendTestFactory backendFactory() {
      return new BigTableBackendContainerTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of("nessie.version.store.type", VersionStoreType.BIGTABLE.name());
    }
  },

  dynamo {
    @Override
    BackendTestFactory backendFactory() {
      return new DynamoDBBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of("nessie.version.store.type", VersionStoreType.DYNAMODB.name());
    }
  },

  cassandra {
    @Override
    BackendTestFactory backendFactory() {
      return new CassandraBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of("nessie.version.store.type", VersionStoreType.CASSANDRA.name());
    }
  },

  postgres {
    @Override
    BackendTestFactory backendFactory() {
      return new PostgreSQLBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of(
          "nessie.version.store.type",
          VersionStoreType.JDBC.name(),
          "nessie.version.store.persist.jdbc.datasource",
          "postgresql");
    }
  },

  mariadb {
    @Override
    BackendTestFactory backendFactory() {
      return new MariaDBBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of(
          "nessie.version.store.type",
          VersionStoreType.JDBC.name(),
          "nessie.version.store.persist.jdbc.datasource",
          "mariadb");
    }
  },

  mysql {
    @Override
    BackendTestFactory backendFactory() {
      return new MySQLBackendTestFactory();
    }

    @Override
    Map<String, String> quarkusConfig() {
      return Map.of(
          "nessie.version.store.type",
          VersionStoreType.JDBC.name(),
          "nessie.version.store.persist.jdbc.datasource",
          "mariadb");
    }
  },
  ;

  abstract BackendTestFactory backendFactory();

  abstract Map<String, String> quarkusConfig();
}
