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
package com.dremio.nessie.versioned.store.rocksdb;

import com.dremio.nessie.versioned.impl.condition.ExpressionPath;

/**
 * Provides evaluation of a {@link com.dremio.nessie.versioned.store.rocksdb.Condition} against the implementing class.
 */
interface Evaluator {
  /**
   * Checks that each Function in the Condition is met by the implementing class.
   * @param condition the condition to check
   * @return true if the condition is met
   */
  boolean evaluate(Condition condition);

  /**
   * Checks that the condition is met by the attribute (nameSegment) for the implementing class.
   * @param nameSegment path to the attribute in the implementing class
   * @param function the condition to check
   * @return true if the condition is met
   */
  boolean evaluateSegment(ExpressionPath.NameSegment nameSegment, Function function);
}
