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
package com.dremio.nessie.versioned.impl;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Stream;

import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterEach;

import com.dremio.nessie.versioned.Serializer;
import com.dremio.nessie.versioned.StoreWorker;
import com.dremio.nessie.versioned.StringSerializer;
import com.dremio.nessie.versioned.VersionStore;
import com.dremio.nessie.versioned.tests.AbstractITVersionStore;

public abstract class AbstractITJGitVersionStore extends AbstractITVersionStore {
  protected Repository repository;
  protected VersionStore<String, String> store;

  protected static final StoreWorker<String, String> WORKER = new StoreWorker<String, String>() {

    @Override
    public Serializer<String> getValueSerializer() {
      return StringSerializer.getInstance();
    }

    @Override
    public Serializer<String> getMetadataSerializer() {
      return StringSerializer.getInstance();
    }

    @Override
    public Stream<AssetKey> getAssetKeys(String value) {
      return Stream.of();
    }

    @Override
    public CompletableFuture<Void> deleteAsset(AssetKey key) {
      throw new UnsupportedOperationException();
    }
  };

  abstract void setUp() throws IOException;

  @AfterEach
  void tearDown() {
    repository.close();
  }

  @Override protected VersionStore<String, String> store() {
    return store;
  }
}
