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
package com.dremio.nessie.versioned.impl;

import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import com.dremio.nessie.backend.dynamodb.LocalDynamoDB;
import com.dremio.nessie.versioned.VersionStore;
import com.dremio.nessie.versioned.VersionStoreException;
import com.dremio.nessie.versioned.tests.AbstractITVersionStore;

@ExtendWith(LocalDynamoDB.class)
public class ITDynamoDBVersionStore extends AbstractITVersionStore {

  private DynamoStoreFixture fixture;

  @BeforeEach
  void setup() {
    fixture = new DynamoStoreFixture();
  }

  @AfterEach
  void deleteResources() {
    fixture.close();
  }

  @Override
  protected VersionStore<String, String> store() {
    return fixture;
  }

  @Test
  protected void transplant() throws VersionStoreException {
    // TODO: implement transplant.
    assertThrows(UnsupportedOperationException.class, () -> super.transplant());
  }
}
