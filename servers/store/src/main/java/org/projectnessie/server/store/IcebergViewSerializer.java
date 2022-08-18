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

import com.google.protobuf.ByteString;
import java.util.function.Supplier;
import org.projectnessie.model.Content;
import org.projectnessie.model.IcebergView;
import org.projectnessie.server.store.proto.ObjectTypes;

public final class IcebergViewSerializer extends BaseSerializer<IcebergView> {

  @Override
  public Content.Type contentType() {
    return Content.Type.ICEBERG_VIEW;
  }

  @Override
  protected void toStoreOnRefState(IcebergView view, ObjectTypes.Content.Builder builder) {
    ObjectTypes.IcebergViewState.Builder stateBuilder =
        ObjectTypes.IcebergViewState.newBuilder()
            .setVersionId(view.getVersionId())
            .setSchemaId(view.getSchemaId())
            .setDialect(view.getDialect())
            .setSqlText(view.getSqlText())
            .setMetadataLocation(view.getMetadataLocation());

    builder.setIcebergViewState(stateBuilder);
  }

  @Override
  public IcebergView applyId(IcebergView content, String id) {
    return IcebergView.builder().from(content).id(id).build();
  }

  @Override
  public boolean requiresGlobalState(byte payload, ByteString content) {
    ObjectTypes.Content parsed = parse(content);
    return !parsed.getIcebergViewState().hasMetadataLocation();
  }

  @Override
  protected IcebergView valueFromStore(
      ObjectTypes.Content content, Supplier<ByteString> globalState) {
    return valueFromStoreIcebergView(content, new IcebergMetadataPointerSupplier(globalState));
  }
}
