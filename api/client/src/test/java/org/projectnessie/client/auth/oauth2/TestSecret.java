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
package org.projectnessie.client.auth.oauth2;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TestSecret {

  @Test
  void length() {
    Secret secret = new Secret("secret");
    assertThat(secret.length()).isEqualTo(6);
  }

  @Test
  void getStringAndClear() {
    Secret secret = new Secret("secret");
    String string = secret.getString();
    secret.clear();
    assertThat(string).isEqualTo("secret");
    assertThat(secret.value).containsOnly('\0');
  }
}
