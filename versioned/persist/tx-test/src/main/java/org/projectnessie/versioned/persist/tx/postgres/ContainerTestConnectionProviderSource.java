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

import org.projectnessie.versioned.persist.tx.local.GenericJdbcTestConnectionProviderSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.ContainerFetchException;
import org.testcontainers.containers.ContainerLaunchException;
import org.testcontainers.containers.JdbcDatabaseContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;

abstract class ContainerTestConnectionProviderSource
    extends GenericJdbcTestConnectionProviderSource {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(ContainerTestConnectionProviderSource.class);

  private JdbcDatabaseContainer<?> container;

  @Override
  public void start() throws Exception {
    if (container != null) {
      throw new IllegalStateException("Already started");
    }

    for (int retry = 0; ; retry++) {
      container =
          createContainer().withLogConsumer(new Slf4jLogConsumer(LOGGER)).withStartupAttempts(5);
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

    configureConnectionProviderConfigFromDefaults(
        c ->
            c.withJdbcUrl(container.getJdbcUrl())
                .withJdbcUser(container.getUsername())
                .withJdbcPass(container.getPassword()));
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

  protected abstract JdbcDatabaseContainer<?> createContainer();
}
