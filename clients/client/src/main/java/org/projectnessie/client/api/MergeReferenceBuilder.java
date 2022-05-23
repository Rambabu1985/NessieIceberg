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
package org.projectnessie.client.api;

import javax.validation.constraints.NotBlank;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.MergeResponse;
import org.projectnessie.model.Reference;

/**
 * Request builder for "merge reference".
 *
 * @since {@link NessieApiV1}
 */
public interface MergeReferenceBuilder extends MergeTransplantBuilder<MergeReferenceBuilder> {
  MergeReferenceBuilder fromHash(@NotBlank String fromHash);

  /**
   * Convenience for {@link #fromRefName(String) fromRefName(fromRef.getName())}{@code .}{@link
   * #fromHash(String) fromHash(fromRef.getHash())}.
   */
  default MergeReferenceBuilder fromRef(Reference fromRef) {
    return fromRefName(fromRef.getName()).fromHash(fromRef.getHash());
  }

  MergeResponse merge() throws NessieNotFoundException, NessieConflictException;
}
