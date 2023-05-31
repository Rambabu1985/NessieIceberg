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
package org.projectnessie.versioned;

import java.util.List;
import org.projectnessie.model.CommitMeta;

public interface MetadataRewriter<T> {
  T rewriteSingle(T metadata);

  T squash(List<T> metadata);

  MetadataRewriter<CommitMeta> NOOP_REWRITER =
      new MetadataRewriter<CommitMeta>() {
        @Override
        public CommitMeta rewriteSingle(CommitMeta metadata) {
          return metadata;
        }

        @Override
        public CommitMeta squash(List<CommitMeta> metadata) {
          return metadata.get(0);
        }
      };
}
