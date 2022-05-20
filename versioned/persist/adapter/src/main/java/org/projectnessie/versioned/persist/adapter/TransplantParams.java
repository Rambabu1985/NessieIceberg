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

import java.util.List;
import org.immutables.value.Value;
import org.projectnessie.versioned.Hash;

/**
 * API helper method to encapsulate parameters for {@link
 * DatabaseAdapter#transplant(TransplantParams)}.
 */
@Value.Immutable
public interface TransplantParams extends MetadataRewriteParams {

  /** Commits to cherry-pick onto {@link #getToBranch()}. */
  List<Hash> getSequenceToTransplant();

  @SuppressWarnings("override")
  interface Builder extends MetadataRewriteParams.Builder<Builder> {
    Builder sequenceToTransplant(Iterable<? extends Hash> elements);

    Builder addSequenceToTransplant(Hash... elements);

    TransplantParams build();
  }

  static Builder builder() {
    return ImmutableTransplantParams.builder();
  }
}
