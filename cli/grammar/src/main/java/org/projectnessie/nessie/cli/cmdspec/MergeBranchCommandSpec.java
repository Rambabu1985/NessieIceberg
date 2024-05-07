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
package org.projectnessie.nessie.cli.cmdspec;

import jakarta.annotation.Nullable;
import java.util.Map;
import org.immutables.value.Value;
import org.projectnessie.nessie.cli.grammar.Node;

@Value.Immutable
public interface MergeBranchCommandSpec extends RefWithTypeCommandSpec, RefWithHashCommandSpec {
  default CommandType commandType() {
    return CommandType.MERGE_BRANCH;
  }

  @Nullable
  @Override
  @Value.Default
  default Node sourceNode() {
    return RefWithHashCommandSpec.super.sourceNode();
  }

  boolean isDryRun();

  @Override
  @Nullable
  String getRefType();

  @Override
  String getRef();

  @Nullable
  @Override
  String getRefTimestampOrHash();

  @Nullable
  String getInto();

  @Nullable
  String getDefaultMergeBehavior();

  Map<String, String> getKeyMergeBehaviors();
}
