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

package com.dremio.iceberg.backend;


import com.dremio.iceberg.model.VersionedWrapper;
import java.util.List;


/**
 * backend for a single type of API object (eg Tag/Table/User etc).
 *
 * <p>Note it is the backends responsibility to update the version
 *
 * @param <T> The entity type this backend handles
 */
public interface EntityBackend<T> extends AutoCloseable {

  VersionedWrapper<T> get(String name);

  default List<VersionedWrapper<T>> getAll(boolean includeDeleted) {
    return getAll(null, null, includeDeleted);
  }

  default List<VersionedWrapper<T>> getAll(String namespace, boolean includeDeleted) {
    return getAll(null, namespace, includeDeleted);
  }

  List<VersionedWrapper<T>> getAll(String name, String namespace, boolean includeDeleted);

  default VersionedWrapper<T> increment(VersionedWrapper<T> obj) {
    return obj.increment();
  }

  void create(String name, VersionedWrapper<T> table);


  void update(String name, VersionedWrapper<T> table);


  void remove(String name);

}
