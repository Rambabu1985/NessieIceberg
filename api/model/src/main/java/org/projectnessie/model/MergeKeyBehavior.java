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
package org.projectnessie.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.annotation.Nullable;
import org.immutables.value.Value;
import org.projectnessie.model.ser.Views;

@Value.Immutable
@JsonSerialize(as = ImmutableMergeKeyBehavior.class)
@JsonDeserialize(as = ImmutableMergeKeyBehavior.class)
public interface MergeKeyBehavior {

  ContentKey getKey();

  MergeBehavior getMergeBehavior();

  /**
   * If present, the current content on the target branch will be compared against this value.
   *
   * <p>This parameter is not supported when multiple commits will be generated, which means only
   * merge operations.
   *
   * <p>Supplying a {@link #getResolvedContent() resolved content} requires setting this attribute.
   * The merge operation will result in a "conflict", if current value on the target branch is
   * different from this value.
   */
  @JsonInclude(Include.NON_NULL)
  @JsonView(Views.V2.class)
  @Nullable
  @jakarta.annotation.Nullable
  Content getExpectedTargetContent();

  /**
   * Clients can provide a "resolved" content object, which will then automatically be persisted via
   * the merge operation instead of detecting and potentially raising a merge-conflict, assuming the
   * content-type is the same.
   *
   * <p>This functionality is not implemented for the "legacy" storage model, using this option with
   * the "legacy" storage model will result in an error.
   *
   * <p>This parameter is not supported when multiple commits will be generated, which means only
   * merge operations.
   *
   * <p>It is mandatory to supply the {@link #getExpectedTargetContent() expected content value},
   */
  @JsonInclude(Include.NON_NULL)
  @JsonView(Views.V2.class)
  @Nullable
  @jakarta.annotation.Nullable
  Content getResolvedContent();

  // TODO add metadata list from https://github.com/projectnessie/nessie/pull/6616

  static ImmutableMergeKeyBehavior.Builder builder() {
    return ImmutableMergeKeyBehavior.builder();
  }

  static MergeKeyBehavior of(ContentKey key, MergeBehavior mergeBehavior) {
    return builder().key(key).mergeBehavior(mergeBehavior).build();
  }

  static MergeKeyBehavior of(
      ContentKey key,
      MergeBehavior mergeBehavior,
      Content expectedTargetContent,
      Content resolvedContent) {
    return builder()
        .key(key)
        .mergeBehavior(mergeBehavior)
        .expectedTargetContent(expectedTargetContent)
        .resolvedContent(resolvedContent)
        .build();
  }
}
