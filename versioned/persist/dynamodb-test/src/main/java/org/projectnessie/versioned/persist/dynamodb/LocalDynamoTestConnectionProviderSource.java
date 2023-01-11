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

import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;

/** DynamoDB test connection-provider source using a local DynamoDB instance via testcontainers. */
public class LocalDynamoTestConnectionProviderSource extends DynamoTestConnectionProviderSource {

  private static final Logger LOGGER =
      LoggerFactory.getLogger(LocalDynamoTestConnectionProviderSource.class);
  public static final int DYNAMODB_PORT = 8000;

  private GenericContainer<?> container;
  private String endpointURI;

  @Override
  public DynamoClientConfig createDefaultConnectionProviderConfig() {
    return ImmutableDefaultDynamoClientConfig.builder().build();
  }

  @Override
  public DynamoDatabaseClient createConnectionProvider() {
    return new DynamoDatabaseClient();
  }

  @Override
  public void start() throws Exception {
    startDynamo();

    configureConnectionProviderConfigFromDefaults(
        c ->
            ImmutableDefaultDynamoClientConfig.builder()
                .endpointURI(endpointURI)
                .region("US_WEST_2")
                .credentialsProvider(
                    StaticCredentialsProvider.create(AwsBasicCredentials.create("xxx", "xxx")))
                .build());

    super.start();
  }

  public String getEndpointURI() {
    return endpointURI;
  }

  public void startDynamo() {
    startDynamo(Optional.empty(), false);
  }

  /**
   * Starts the DynamoDB mock with an optional Docker network ID and a flag to turn off all output
   * to stdout and stderr.
   */
  public void startDynamo(Optional<String> containerNetworkId, boolean quiet) {
    if (container != null) {
      throw new IllegalStateException("Already started");
    }

    String version = System.getProperty("it.nessie.container.dynamodb-local.tag", "latest");
    String imageName = "amazon/dynamodb-local:" + version;

    if (!quiet) {
      LOGGER.info("Starting Dynamo test container (network-id: {})", containerNetworkId);
    }

    for (int retry = 0; ; retry++) {
      container =
          new GenericContainer<>(imageName)
              .withLogConsumer(quiet ? outputFrame -> {} : new Slf4jLogConsumer(LOGGER))
              .withExposedPorts(DYNAMODB_PORT)
              .withCommand("-jar", "DynamoDBLocal.jar", "-inMemory", "-sharedDb")
              .withStartupAttempts(5);
      containerNetworkId.ifPresent(container::withNetworkMode);
      try {
        container.start();
        break;
      } catch (ContainerLaunchException e) {
        container.close();
        if (e.getCause() != null && e.getCause() instanceof ContainerFetchException && retry < 3) {
          LOGGER.warn(
              "Launch of container {} failed, will retry...", container.getContainerId(), e);
          continue;
        }
        LOGGER.error("Launch of container {} failed", container.getContainerId(), e);
        throw new RuntimeException(e);
      }
    }

    Integer port = containerNetworkId.isPresent() ? DYNAMODB_PORT : container.getFirstMappedPort();
    String host =
        containerNetworkId.isPresent()
            ? container.getCurrentContainerInfo().getConfig().getHostName()
            : container.getHost();

    endpointURI = String.format("http://%s:%d", host, port);

    if (!quiet) {
      LOGGER.info(
          "Dynamo test container endpoint is {} (network-id: {})", endpointURI, containerNetworkId);
    }
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
