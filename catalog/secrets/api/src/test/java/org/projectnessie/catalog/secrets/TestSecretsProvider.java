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
package org.projectnessie.catalog.secrets;

import static org.projectnessie.catalog.secrets.BasicCredentials.JSON_NAME;
import static org.projectnessie.catalog.secrets.BasicCredentials.JSON_SECRET;
import static org.projectnessie.catalog.secrets.KeySecret.JSON_KEY;
import static org.projectnessie.catalog.secrets.TokenSecret.JSON_EXPIRES_AT;
import static org.projectnessie.catalog.secrets.TokenSecret.JSON_TOKEN;
import static org.projectnessie.catalog.secrets.UnsafePlainTextSecretsProvider.unsafePlainTextSecretsProvider;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

@ExtendWith(SoftAssertionsExtension.class)
public class TestSecretsProvider {
  @InjectSoftAssertions protected SoftAssertions soft;

  @Test
  public void test() {

    String key1Value = "key1-value";
    String nestedKey1Value = "nested-key1-value";
    Instant t2expires = Instant.now().atZone(ZoneOffset.UTC).toInstant();
    String t2expiresStr = t2expires.toString();
    String t1value = "t1-value";
    String t2value = "t2-value";
    String bc1name = "bc1-name";
    String bc2name = "bc2-name";
    String bc1secret = "bc1-secret";
    String bc2secret = "bc2-secret";

    SecretsProvider secretsProvider =
        unsafePlainTextSecretsProvider(
            Map.of(
                "key1", Map.of(JSON_KEY, key1Value),
                "nested.key1", Map.of(JSON_KEY, nestedKey1Value),
                "bc1", Map.of(JSON_NAME, bc1name, JSON_SECRET, bc1secret),
                "nested.bc2", Map.of(JSON_NAME, bc2name, JSON_SECRET, bc2secret),
                "t1", Map.of(JSON_TOKEN, t1value),
                "nested.t1", Map.of(JSON_TOKEN, t1value),
                "t2", Map.of(JSON_TOKEN, t2value, JSON_EXPIRES_AT, t2expiresStr),
                "nested.t2", Map.of(JSON_TOKEN, t2value, JSON_EXPIRES_AT, t2expiresStr)));

    soft.assertThat(secretsProvider.getSecret("key1", SecretType.KEY, KeySecret.class))
        .get()
        .extracting(KeySecret::key, KeySecret::asMap)
        .containsExactly(key1Value, Map.of(JSON_KEY, key1Value));

    soft.assertThat(secretsProvider.getSecret("nested.key1", SecretType.KEY, KeySecret.class))
        .get()
        .extracting(KeySecret::key, KeySecret::asMap)
        .containsExactly(nestedKey1Value, Map.of(JSON_KEY, nestedKey1Value));

    soft.assertThat(secretsProvider.getSecret("bc1", SecretType.BASIC, BasicCredentials.class))
        .get()
        .extracting(BasicCredentials::name, BasicCredentials::secret, BasicCredentials::asMap)
        .containsExactly(bc1name, bc1secret, Map.of(JSON_NAME, bc1name, JSON_SECRET, bc1secret));

    soft.assertThat(
            secretsProvider.getSecret("nested.bc2", SecretType.BASIC, BasicCredentials.class))
        .get()
        .extracting(BasicCredentials::name, BasicCredentials::secret, BasicCredentials::asMap)
        .containsExactly(bc2name, bc2secret, Map.of(JSON_NAME, bc2name, JSON_SECRET, bc2secret));

    soft.assertThat(secretsProvider.getSecret("t1", SecretType.EXPIRING_TOKEN, TokenSecret.class))
        .get()
        .extracting(TokenSecret::token, TokenSecret::expiresAt, TokenSecret::asMap)
        .containsExactly(t1value, Optional.empty(), Map.of(JSON_TOKEN, t1value));

    soft.assertThat(
            secretsProvider.getSecret("nested.t1", SecretType.EXPIRING_TOKEN, TokenSecret.class))
        .get()
        .extracting(TokenSecret::token, TokenSecret::expiresAt, TokenSecret::asMap)
        .containsExactly(t1value, Optional.empty(), Map.of(JSON_TOKEN, t1value));

    soft.assertThat(secretsProvider.getSecret("t2", SecretType.EXPIRING_TOKEN, TokenSecret.class))
        .get()
        .extracting(TokenSecret::token, TokenSecret::expiresAt, TokenSecret::asMap)
        .containsExactly(
            t2value,
            Optional.of(t2expires),
            Map.of(JSON_TOKEN, t2value, JSON_EXPIRES_AT, t2expiresStr));

    soft.assertThat(
            secretsProvider.getSecret("nested.t2", SecretType.EXPIRING_TOKEN, TokenSecret.class))
        .get()
        .extracting(TokenSecret::token, TokenSecret::expiresAt, TokenSecret::asMap)
        .containsExactly(
            t2value,
            Optional.of(t2expires),
            Map.of(JSON_TOKEN, t2value, JSON_EXPIRES_AT, t2expiresStr));
  }
}
