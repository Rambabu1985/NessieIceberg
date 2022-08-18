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
package org.projectnessie.versioned.persist.dynamodb;

import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;
import org.projectnessie.versioned.persist.nontx.NonTransactionalDatabaseAdapterConfig;
import org.projectnessie.versioned.persist.nontx.NonTransactionalDatabaseAdapterFactory;

public class DynamoDatabaseAdapterFactory
    extends NonTransactionalDatabaseAdapterFactory<DynamoDatabaseAdapter, DynamoDatabaseClient> {

  public static final String NAME = "DynamoDB";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  protected DynamoDatabaseAdapter create(
      NonTransactionalDatabaseAdapterConfig config,
      DynamoDatabaseClient dynamoDatabaseClient,
      AdapterEventConsumer eventConsumer) {
    return new DynamoDatabaseAdapter(config, dynamoDatabaseClient, eventConsumer);
  }
}
