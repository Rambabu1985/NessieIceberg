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

import java.util.Optional;
import org.immutables.value.Value;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Hash;

public interface ToBranchParams {

  /**
   * Branch to commit to. If {@link #getExpectedHead()} is present, the referenced branch's HEAD
   * must be equal to this hash.
   */
  BranchName getToBranch();

  /** Expected HEAD of {@link #getToBranch()}. */
  @Value.Default
  @SuppressWarnings("immutables:untype")
  default Optional<Hash> getExpectedHead() {
    return Optional.empty();
  }

  @SuppressWarnings({"override", "UnusedReturnValue"})
  interface Builder<B> {
    B toBranch(BranchName toBranch);

    B expectedHead(Optional<Hash> expectedHead);
  }
}
