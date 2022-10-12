/*
 * Copyright (C) 2022 Dremio
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

import java.net.URI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.jaxrs.AbstractTestRest;

/**
 * Tests need to subclass this class and use @QuarkusIntegrationTest or @QuarkusTest, so that the
 * quarkus context is available to resolve the nessie URI.
 */
@ExtendWith(QuarkusNessieUriResolver.class)
public abstract class AbstractTestQuarkusRest extends AbstractTestRest {

  @BeforeEach
  public void setUp(URI quarkusNessieUri) {
    initApi(quarkusNessieUri);
  }
}
