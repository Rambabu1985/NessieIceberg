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

import java.util.stream.Stream;

final class Tables {

  static final String TABLE_REPO_DESC = "repo_desc";
  static final String TABLE_GLOBAL_POINTER = "global_pointer";
  static final String TABLE_GLOBAL_LOG = "global_log";
  static final String TABLE_COMMIT_LOG = "commit_log";
  static final String TABLE_KEY_LISTS = "key_lists";
  static final String TABLE_REF_LOG = "ref_log";
  static final String TABLE_REF_HEADS = "ref_heads";
  static final String TABLE_REF_NAMES = "ref_names";
  static final String TABLE_REF_LOG_HEADS = "ref_log_heads";

  static final String KEY_NAME = "key";
  static final String VALUE_NAME = "val";

  private Tables() {}

  static Stream<String> all() {
    return Stream.concat(Stream.of(TABLE_GLOBAL_POINTER), allExceptGlobalPointer());
  }

  static Stream<String> allExceptGlobalPointer() {
    return Stream.of(
        TABLE_REPO_DESC,
        TABLE_GLOBAL_LOG,
        TABLE_COMMIT_LOG,
        TABLE_KEY_LISTS,
        TABLE_REF_LOG,
        TABLE_REF_HEADS,
        TABLE_REF_NAMES,
        TABLE_REF_LOG_HEADS);
  }
}
