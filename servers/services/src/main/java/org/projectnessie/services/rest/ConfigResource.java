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
package org.projectnessie.services.rest;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;
import org.projectnessie.api.ConfigApi;
import org.projectnessie.model.ImmutableNessieConfiguration;
import org.projectnessie.model.NessieConfiguration;
import org.projectnessie.services.config.ServerConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** REST endpoint to retrieve server settings. */
@RequestScoped
public class ConfigResource implements ConfigApi {

  private static final Logger logger = LoggerFactory.getLogger(ConfigResource.class);
  private final ServerConfig config;

  @Inject
  public ConfigResource(ServerConfig config) {
    this.config = config;
  }

  @Override
  public NessieConfiguration getConfig() {
    return ImmutableNessieConfiguration.builder()
        .defaultBranch(this.config.getDefaultBranch())
        .build();
  }
}
