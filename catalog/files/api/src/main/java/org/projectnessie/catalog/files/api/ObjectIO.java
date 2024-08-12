/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.catalog.files.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import org.projectnessie.storage.uri.StorageUri;

public interface ObjectIO {
  void ping(StorageUri uri) throws IOException;

  InputStream readObject(StorageUri uri) throws IOException;

  OutputStream writeObject(StorageUri uri) throws IOException;

  void deleteObjects(List<StorageUri> uris) throws IOException;

  void icebergWarehouseConfig(
      StorageUri warehouse,
      BiConsumer<String, String> defaultConfig,
      BiConsumer<String, String> configOverride);

  void icebergTableConfig(
      StorageLocations storageLocations,
      BiConsumer<String, String> config,
      Predicate<StorageLocations> signingPredicate,
      boolean canDoCredentialsVending);

  void trinoSampleConfig(
      StorageUri warehouse,
      Map<String, String> icebergConfig,
      BiConsumer<String, String> properties);

  String FILE_IO_IMPL = "io-impl";
}
