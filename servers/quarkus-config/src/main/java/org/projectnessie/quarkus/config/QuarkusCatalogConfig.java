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
package org.projectnessie.quarkus.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import io.smallrye.config.WithName;

@ConfigMapping(prefix = "nessie.catalog")
public interface QuarkusCatalogConfig {

  /**
   * Nessie tries to verify the connectivity to the object stores configured for each warehouse and
   * exposes this information as a readiness check. It is recommended to leave this setting enabled.
   */
  @WithName("object-stores.health-check.enabled")
  @WithDefault("true")
  boolean objectStoresHealthCheck();
}
