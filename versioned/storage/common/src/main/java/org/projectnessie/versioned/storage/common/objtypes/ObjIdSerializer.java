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
package org.projectnessie.versioned.storage.common.objtypes;

import static org.projectnessie.versioned.storage.common.persist.ObjId.deserializeObjId;

import java.nio.ByteBuffer;
import org.projectnessie.versioned.storage.common.indexes.ElementSerializer;
import org.projectnessie.versioned.storage.common.persist.ObjId;

public final class ObjIdSerializer implements ElementSerializer<ObjId> {
  public static final ElementSerializer<ObjId> OBJ_ID_SERIALIZER = new ObjIdSerializer();

  private ObjIdSerializer() {}

  @Override
  public int serializedSize(ObjId value) {
    return value.serializedSize();
  }

  @Override
  public ByteBuffer serialize(ObjId value, ByteBuffer target) {
    return value.serializeTo(target);
  }

  @Override
  public ObjId deserialize(ByteBuffer buffer) {
    return deserializeObjId(buffer);
  }
}
