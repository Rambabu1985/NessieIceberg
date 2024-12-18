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

import org.projectnessie.versioned.storage.common.objtypes.CommitObj;

/**
 * Interface to cut the Nessie commit graph at a certain point by remoting its parents. The cut
 * point is provided by {@link CutHistoryParams#cutPoint()}.
 *
 * <p>This operation is generally meaningful when followed with {@link PurgeObjects}, which will
 * removed pieces of the commit graph that are no longer reachable from reference HEADs.
 *
 * @see Cleanup#createCutHistory(CutHistoryParams)
 * @see Cleanup#createPurgeObjects(PurgeObjectsContext)
 */
public interface CutHistory {
  /**
   * Identifies commits, whose logical correctness would be affected by the {@link #cutHistory()}
   * operation. These commits have lists {@link CommitObj#tail() parent} that stretch beyond the cut
   * point.
   *
   * <p>Note: this operation may be time-consuming.
   */
  CutHistoryScanResult identifyAffectedCommits();

  /**
   * Rewrites commits identifies by the {@link #identifyAffectedCommits()} operation to shorten
   * their {@link CommitObj#tail() parent} lists so that they will not overlap the cut point.
   *
   * <p>Note: this operation is idempotent and does not affect the logical correctness of the Nessie
   * commit graph. However, commit traversal may be slower across the rewritten commits due to
   * shorted parent lists (i.e. more I/O round-trips).
   */
  UpdateParentsResult rewriteParents(CutHistoryScanResult scanResult);

  /**
   * Rewrites the commit marked as the history cut point to remove all its parents. The rewritten
   * commit will effectively become a "root" commit. This operation is idempotent.
   *
   * <p>Note: this operation should only be invoked after {@link
   * #rewriteParents(CutHistoryScanResult)} completed successfully to avoid corrupting the commit
   * graph.
   *
   * <p>Note: this operation will affect merges from base commits that used to be parents of the
   * history cut point.
   */
  UpdateParentsResult cutHistory();
}
