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
package org.projectnessie.versioned.persist.dynamodb;

import org.projectnessie.versioned.persist.adapter.DatabaseAdapterConfig;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

/** DynamoDB test connection-provider source using a local DynamoDB instance via testcontainers. */
public class LocalDynamoTestConnectionProviderSource extends DynamoTestConnectionProviderSource {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(LocalDynamoTestConnectionProviderSource.class);

  private GenericContainer<?> container;

  @Override
  public boolean isCompatibleWith(
      DatabaseAdapterConfig<?> adapterConfig, DatabaseAdapterFactory<?> databaseAdapterFactory) {
    return adapterConfig instanceof DynamoDatabaseAdapterConfig;
  }

  @Override
  public DynamoClientConfig createDefaultConnectionProviderConfig() {
    return ImmutableDynamoClientConfig.builder().build();
  }

  @Override
  public DynamoDatabaseClient createConnectionProvider() {
    return new DynamoDatabaseClient();
  }

  @Override
  public void start() throws Exception {
    if (container != null) {
      throw new IllegalStateException("Already started");
    }

    // dynalite is much faster than dynamodb-local
    // String version = System.getProperty("it.nessie.container.dynamodb.tag", "latest");
    // String imageName = "amazon/dynamodb-local:" + version;
    String version = System.getProperty("it.nessie.container.dynalite.tag", "latest");
    String imageName = "dimaqq/dynalite:" + version;

    if (System.getProperty("aws.accessKeyId") == null) {
      System.setProperty("aws.accessKeyId", "xxx");
    }
    if (System.getProperty("aws.secretAccessKey") == null) {
      System.setProperty("aws.secretAccessKey", "xxx");
    }

    container =
        new GenericContainer<>(imageName)
            .withLogConsumer(new Slf4jLogConsumer(LOGGER))
            .withExposedPorts(8000);
    container.start();

    Integer port = container.getFirstMappedPort();

    String endpointURI = String.format("http://localhost:%d", port);

    configureConnectionProviderConfigFromDefaults(
        c -> c.withEndpointURI(endpointURI).withRegion("US_WEST_2"));

    super.start();
  }

  @Override
  public void stop() throws Exception {
    try {
      super.stop();
    } finally {
      try {
        if (container != null) {
          container.stop();
        }
      } finally {
        container = null;
      }
    }
  }
}
