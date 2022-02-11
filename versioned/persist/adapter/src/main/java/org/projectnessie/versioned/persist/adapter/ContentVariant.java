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
package org.projectnessie.versioned.persist.adapter;

/**
 * Determines "how" a content is maintained.
 *
 * <p>Whether a content variant is only stored in a Nessie commit or whether a content variant has
 * global state.
 */
public enum ContentVariant {
  /** Content is only stored in Nessie commits. */
  ON_REF,
  /** Content is stored in Nessie commits and requires global state. */
  WITH_GLOBAL
}
