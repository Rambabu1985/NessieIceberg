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
package org.projectnessie.server;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.util.function.Consumer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.client.api.NessieApiV1;
import org.projectnessie.client.http.HttpClientBuilder;

/** Base class for client-base authentication and authorization tests. */
@ExtendWith(QuarkusNessieUriResolver.class)
public abstract class BaseClientAuthTest {

  private URI quarkusNessieUri;

  private NessieApiV1 api;
  private Consumer<HttpClientBuilder> customizer;

  @BeforeEach
  void setUp(URI quarkusNessieUri) {
    this.quarkusNessieUri = quarkusNessieUri;
  }

  @AfterEach
  void closeClient() {
    if (api != null) {
      api.close();
      api = null;
    }
  }

  protected void withClientCustomizer(Consumer<HttpClientBuilder> customizer) {
    Preconditions.checkState(api == null, "withClientCustomizer but api has already been created!");
    this.customizer = customizer;
  }

  protected NessieApiV1 api() {
    if (api != null) {
      return api;
    }

    HttpClientBuilder builder = HttpClientBuilder.builder().withUri(quarkusNessieUri);

    if (customizer != null) {
      customizer.accept(builder);
    }

    api = builder.build(NessieApiV1.class);

    return api;
  }
}
