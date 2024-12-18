/*
 * Copyright (C) 2024 Dremio
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
package org.projectnessie.versioned.storage.cleanup;

import java.util.Map;
import java.util.Set;
import org.projectnessie.nessie.immutables.NessieImmutable;
import org.projectnessie.versioned.storage.common.persist.ObjId;

/** Represents results of updating the commit history. */
@NessieImmutable
public interface CutHistoryResult {

  /** Input parameters that drove the cut operation. */
  CutHistoryScanResult input();

  /** IDs of commits that were actually rewritten by this operation. */
  Set<ObjId> rewrittenCommitIds();

  /**
   * Indicates whether the {@link CutHistoryScanResult#cutPoint()} commit was actually rewritten.
   */
  boolean wasHistoryCut();

  /**
   * Update failures by commit ID.
   *
   * <p>Note: failures do not break the commit graph's logical consistency.
   */
  Map<ObjId, Throwable> failures();

  static ImmutableCutHistoryResult.Builder builder() {
    return ImmutableCutHistoryResult.builder();
  }
}
