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
package com.dremio.nessie.versioned.store.jdbc;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.function.BiConsumer;

import com.dremio.nessie.versioned.impl.condition.UpdateExpression;
import com.dremio.nessie.versioned.store.Id;
import com.dremio.nessie.versioned.store.ValueType;

/**
 * Wraps conditions passed to e.g. {@link com.dremio.nessie.versioned.store.Store#update(ValueType, Id,
 * UpdateExpression, Optional, Optional)}.
 */
final class Conditions {
  private Map<String, ValueApplicator> applicators = new LinkedHashMap<>();
  private boolean ifNotExists;

  boolean isIfNotExists() {
    return ifNotExists;
  }

  void setIfNotExists() {
    this.ifNotExists = true;
  }

  void forEach(BiConsumer<String, ValueApplicator> action) {
    applicators.forEach(action);
  }

  int size() {
    return applicators.size();
  }

  void add(String column, ValueApplicator valueApplicator) {
    applicators.put(column, valueApplicator);
  }

  Conditions addIdCondition(Id id, DatabaseAdapter databaseAdapter) {
    applicators.put(
        JdbcEntity.ID,
        (pstmt, index) -> {
          databaseAdapter.setId(pstmt, index, id);
          return 1;
        }
    );
    return this;
  }
}
