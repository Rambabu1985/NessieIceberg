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

import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import javax.validation.Valid;
import javax.validation.constraints.Pattern;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Contents;
import org.projectnessie.model.ContentsKey;
import org.projectnessie.model.Reference;
import org.projectnessie.model.Validation;

public interface GetContentsBuilder {
  GetContentsBuilder key(@Valid ContentsKey key);

  GetContentsBuilder keys(List<ContentsKey> keys);

  GetContentsBuilder refName(
      @Pattern(regexp = Validation.REF_NAME_REGEX, message = Validation.REF_NAME_MESSAGE)
          String refName);

  GetContentsBuilder hashOnRef(
      @Nullable @Pattern(regexp = Validation.HASH_REGEX, message = Validation.HASH_MESSAGE)
          String hashOnRef);

  /**
   * Convenience for {@link #refName(String) refName(reference.getName())}{@code .}{@link
   * #hashOnRef(String) hashOnRef(reference.getHash())}.
   */
  default GetContentsBuilder reference(Reference reference) {
    return refName(reference.getName()).hashOnRef(reference.getHash());
  }

  Map<ContentsKey, Contents> submit() throws NessieNotFoundException;
}
