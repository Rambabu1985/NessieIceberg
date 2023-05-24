/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.client.http;

import com.fasterxml.jackson.databind.JsonNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class NessieApiCompatibility {

  private static final Logger LOGGER = LoggerFactory.getLogger(NessieApiCompatibility.class);
  private static final String MIN_API_VERSION = "minSupportedApiVersion";
  private static final String MAX_API_VERSION = "maxSupportedApiVersion";
  private static final String ACTUAL_API_VERSION = "actualApiVersion";

  /**
   * Checks if the API version of the client is compatible with the server's.
   *
   * @param clientApiVersion the API version of the client
   * @param httpClient the underlying HTTP client.
   * @throws NessieApiCompatibilityException if the API version is not compatible.
   */
  public static void check(int clientApiVersion, HttpClient httpClient)
      throws NessieApiCompatibilityException {
    JsonNode config;
    try {
      config = httpClient.newRequest().path("config").get().readEntity(JsonNode.class);
    } catch (Exception e) {
      LOGGER.warn(
          "API compatibility check: failed to contact config endpoint, proceeding without check",
          e);
      return;
    }
    int minServerApiVersion =
        config.hasNonNull(MIN_API_VERSION) ? config.get(MIN_API_VERSION).asInt() : 1;
    int maxServerApiVersion = config.get(MAX_API_VERSION).asInt();
    int actualServerApiVersion =
        config.hasNonNull(ACTUAL_API_VERSION) ? config.get(ACTUAL_API_VERSION).asInt() : 0;
    if (clientApiVersion < minServerApiVersion
        || clientApiVersion > maxServerApiVersion
        || (actualServerApiVersion > 0 && clientApiVersion != actualServerApiVersion)) {
      throw new NessieApiCompatibilityException(
          clientApiVersion, minServerApiVersion, maxServerApiVersion, actualServerApiVersion);
    }
  }
}
