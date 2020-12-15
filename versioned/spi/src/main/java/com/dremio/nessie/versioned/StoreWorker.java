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
package com.dremio.nessie.versioned;

/**
 * A set of helpers that users of a VersionStore must implement.
 *
 * @param <VALUE> The value type saved in the VersionStore.
 * @param <COMMIT_METADATA> The commit metadata type saved in the VersionStore.
 * @param <ASSET_KEY> The AssetKey class used retrievable from values.
 */
public interface StoreWorker<VALUE, COMMIT_METADATA, ASSET_KEY extends AssetKey<?>> {

  ValueWorker<VALUE, ASSET_KEY> getValueWorker();

  Serializer<COMMIT_METADATA> getMetadataSerializer();

  /**
   * Create StoreWorker for provided helpers.
   */
  static <VALUE, COMMIT_METADATA, ASSET_KEY extends AssetKey<?>> StoreWorker<VALUE, COMMIT_METADATA, ASSET_KEY>
      of(ValueWorker<VALUE, ASSET_KEY> valueWorker, Serializer<COMMIT_METADATA> commitSerializer) {
    return new StoreWorker<VALUE, COMMIT_METADATA, ASSET_KEY>() {

      @Override
      public ValueWorker<VALUE, ASSET_KEY> getValueWorker() {
        return valueWorker;
      }

      @Override
      public Serializer<COMMIT_METADATA> getMetadataSerializer() {
        return commitSerializer;
      }

    };
  }

}
