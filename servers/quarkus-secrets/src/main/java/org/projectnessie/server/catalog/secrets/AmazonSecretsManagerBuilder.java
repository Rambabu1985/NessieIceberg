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
package org.projectnessie.server.catalog.secrets;

import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;
import org.projectnessie.catalog.secrets.SecretsManager;
import org.projectnessie.catalog.secrets.aws.AwsSecretsManager;
import org.projectnessie.quarkus.config.QuarkusSecretsConfig;
import org.projectnessie.quarkus.config.QuarkusSecretsConfig.ExternalSecretsManagerType;
import software.amazon.awssdk.services.secretsmanager.SecretsManagerClient;

@Dependent
@SecretsManagerType(ExternalSecretsManagerType.AMAZON)
public class AmazonSecretsManagerBuilder implements SecretsManagerBuilder {
  @Inject SecretsManagerClient client;

  @Inject QuarkusSecretsConfig secretsConfig;

  @Override
  public SecretsManager buildManager() {
    String prefix =
        secretsConfig.path().map(String::trim).map(p -> p.endsWith(".") ? p : p + ".").orElse("");
    return new AwsSecretsManager(client, prefix);
  }
}
