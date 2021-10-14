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

import java.util.Objects;
import javax.annotation.Nonnull;
import org.immutables.value.Value;

// TODO move this class to :nessie-model in a follow-up
@Value.Immutable
public abstract class ContentsId {
  public static ContentsId of(String id) {
    Objects.requireNonNull(id);
    return ImmutableContentsId.builder().id(id).build();
  }

  @Nonnull
  public abstract String getId();

  @Override
  public String toString() {
    return getId();
  }
}
