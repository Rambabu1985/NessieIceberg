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
package org.projectnessie.server.store;

import java.util.function.Supplier;
import org.projectnessie.model.Content;
import org.projectnessie.model.DeltaLakeTable;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.server.store.proto.ObjectTypes;

public final class DeltaLakeTableSerializer extends BaseSerializer<DeltaLakeTable> {

  @Override
  public Content.Type contentType() {
    return Content.Type.DELTA_LAKE_TABLE;
  }

  @Override
  public int payload() {
    return 2;
  }

  @Override
  protected void toStoreOnRefState(DeltaLakeTable content, ObjectTypes.Content.Builder builder) {
    ObjectTypes.DeltaLakeTable.Builder table =
        ObjectTypes.DeltaLakeTable.newBuilder()
            .addAllMetadataLocationHistory(content.getMetadataLocationHistory())
            .addAllCheckpointLocationHistory(content.getCheckpointLocationHistory());
    String lastCheckpoint = content.getLastCheckpoint();
    if (lastCheckpoint != null) {
      table.setLastCheckpoint(lastCheckpoint);
    }
    builder.setDeltaLakeTable(table);
  }

  @Override
  protected DeltaLakeTable valueFromStore(
      ObjectTypes.Content content, Supplier<ByteString> globalState) {
    return valueFromStoreDeltaLakeTable(content);
  }
}
