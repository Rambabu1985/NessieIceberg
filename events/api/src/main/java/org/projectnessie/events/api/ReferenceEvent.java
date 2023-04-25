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
package org.projectnessie.events.api;

import com.fasterxml.jackson.annotation.JsonIgnore;
import org.immutables.value.Value;

/**
 * Event that is emitted when a reference is created, updated or deleted.
 *
 * <p>This type has 3 child interfaces:
 *
 * <ul>
 *   <li>{@link ReferenceCreatedEvent}: for reference creations;
 *   <li>{@link ReferenceUpdatedEvent}: for reference updates (reassignments);
 *   <li>{@link ReferenceDeletedEvent}: for reference deletions.
 * </ul>
 */
public interface ReferenceEvent extends Event {

  /** The reference type name for branches. */
  String BRANCH = "BRANCH";

  /** The reference type name for tags. */
  String TAG = "TAG";

  /** The name of the reference, e.g. "branch1". */
  String getReferenceName();

  /** The full name of the reference, e.g. "refs/heads/branch1". */
  String getFullReferenceName();

  /**
   * The type of the reference. This is usually either {@value #BRANCH} or {@value #TAG}, but more
   * types may be added in the future.
   */
  String getReferenceType();

  /** Returns {@code true} if the reference is a branch, {@code false} otherwise. */
  @Value.Derived
  @JsonIgnore
  default boolean isBranch() {
    return getReferenceType().equalsIgnoreCase(BRANCH);
  }

  /** Returns {@code true} if the reference is a tag, {@code false} otherwise. */
  @Value.Derived
  @JsonIgnore
  default boolean isTag() {
    return getReferenceType().equalsIgnoreCase(TAG);
  }
}
