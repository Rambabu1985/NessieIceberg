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
package org.projectnessie.versioned;

/** Extension of Serializable that includes a single byte payload. */
public interface SerializerWithPayload<V, T extends Enum<T>> extends Serializer<V> {

  Byte getPayload(V value);

  default T getType(V value) {
    return getType(getPayload(value));
  }

  T getType(Byte payload);
}
