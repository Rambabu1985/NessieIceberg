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
package org.projectnessie.catalog.secrets.spi;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.Map;
import java.util.stream.Collectors;
import org.projectnessie.catalog.secrets.BasicCredentials;
import org.projectnessie.catalog.secrets.KeySecret;
import org.projectnessie.catalog.secrets.TokenSecret;

/**
 * Abstraction for secrets managers that only return a single string for a single secret. Tries to
 * parse each secret from JSON as a map.
 */
public abstract class SingleValueSecretsSupplier implements SecretsSupplier {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  @Override
  public final Map<String, Map<String, String>> resolveSecrets(Collection<String> names) {
    Map<String, String> maps = resolveSingleValueSecrets(names);
    return maps.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, e -> parseOrSingle(e.getValue())));
  }

  @SuppressWarnings("unchecked")
  static Map<String, String> parseOrSingle(String s) {
    if (!s.trim().startsWith("{")) {
      return Map.of("value", s);
    }
    try {
      return MAPPER.readValue(s, Map.class);
    } catch (JsonProcessingException e) {
      return Map.of("value", s);
    }
  }

  /**
   * Resolve secrets.
   *
   * @param names names of the secrets to resolve
   * @return map of secret names to either the JSON representations of the secrets or, if not a JSON
   *     document, the single value for the secret. See {@link KeySecret#keySecret(Map)}, {@link
   *     BasicCredentials#basicCredentials(Map)}, {@link TokenSecret#tokenSecret(Map)}
   */
  protected abstract Map<String, String> resolveSingleValueSecrets(Collection<String> names);
}
