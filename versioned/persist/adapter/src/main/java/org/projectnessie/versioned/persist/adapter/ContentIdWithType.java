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
package org.projectnessie.versioned.persist.adapter;

import org.immutables.value.Value;

/**
 * Used when dealing with global states in operations for Nessie-GC, like enumerating all globally
 * managed content. Composite of content-id and content-type.
 */
@Value.Immutable
public interface ContentIdWithType {
  ContentId getContentId();

  byte getType();

  static ContentIdWithType of(ContentId contentId, byte type) {
    return ImmutableContentIdWithType.builder().type(type).contentId(contentId).build();
  }
}
