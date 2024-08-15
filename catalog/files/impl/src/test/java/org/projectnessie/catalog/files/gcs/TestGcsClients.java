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
package org.projectnessie.catalog.files.gcs;

import static java.util.function.Function.identity;
import static org.projectnessie.catalog.files.gcs.GcsClients.buildSharedHttpTransportFactory;

import com.google.auth.http.HttpTransportFactory;
import java.util.Map;
import java.util.stream.Collectors;
import org.projectnessie.catalog.files.AbstractClients;
import org.projectnessie.catalog.files.api.BackendExceptionMapper;
import org.projectnessie.catalog.files.api.ObjectIO;
import org.projectnessie.catalog.secrets.SecretsProvider;
import org.projectnessie.objectstoragemock.ObjectStorageMock;
import org.projectnessie.storage.uri.StorageUri;

public class TestGcsClients extends AbstractClients {

  @Override
  protected StorageUri buildURI(String bucket, String key) {
    return StorageUri.of(String.format("gs://%s/%s", bucket, key));
  }

  @Override
  protected BackendExceptionMapper.Builder addExceptionHandlers(
      BackendExceptionMapper.Builder builder) {
    return builder.addAnalyzer(GcsExceptionMapper.INSTANCE);
  }

  @Override
  protected ObjectIO buildObjectIO(
      ObjectStorageMock.MockServer server1, ObjectStorageMock.MockServer server2) {

    HttpTransportFactory httpTransportFactory = buildSharedHttpTransportFactory();

    ImmutableGcsProgrammaticOptions.Builder gcsOptions =
        ImmutableGcsProgrammaticOptions.builder()
            .putBuckets(
                BUCKET_1,
                ImmutableGcsNamedBucketOptions.builder()
                    .host(server1.getGcsBaseUri())
                    .authType(GcsBucketOptions.GcsAuthType.NONE)
                    .build());
    if (server2 != null) {
      gcsOptions.putBuckets(
          BUCKET_2,
          ImmutableGcsNamedBucketOptions.builder()
              .host(server2.getGcsBaseUri())
              .authType(GcsBucketOptions.GcsAuthType.NONE)
              .build());
    }

    SecretsProvider secretsProvider =
        new SecretsProvider(
            names ->
                names.stream()
                    .collect(Collectors.toMap(identity(), k -> Map.of("secret", "secret"))));

    GcsStorageSupplier supplier =
        new GcsStorageSupplier(httpTransportFactory, gcsOptions.build(), secretsProvider);

    return new GcsObjectIO(supplier);
  }
}
