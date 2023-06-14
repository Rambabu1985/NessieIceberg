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
package org.projectnessie.versioned.storage.bigtable;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.versioned.storage.commontests.AbstractBackendRepositoryTests;
import org.projectnessie.versioned.storage.commontests.AbstractPersistTests;
import org.projectnessie.versioned.storage.testextension.NessieBackend;
import org.projectnessie.versioned.storage.testextension.PersistExtension;

@NessieBackend(BigTableBackendContainerTestFactory.class)
@ExtendWith(PersistExtension.class)
public class ITBigTablePersist extends AbstractPersistTests {

  @Nested
  public class NoAdminClientBackendRepositoryTests extends AbstractBackendRepositoryTests {
    @BeforeEach
    void noAdminClient() {
      BigTableBackend b = (BigTableBackend) backend;
      backend = new BigTableBackend(b.client(), null, false);
    }
  }
}
