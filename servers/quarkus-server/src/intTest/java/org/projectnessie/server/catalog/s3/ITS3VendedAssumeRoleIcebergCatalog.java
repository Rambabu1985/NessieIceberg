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
package org.projectnessie.server.catalog.s3;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusIntegrationTest;
import io.quarkus.test.junit.QuarkusTestProfile;
import io.quarkus.test.junit.TestProfile;
import java.util.Map;
import org.apache.iceberg.CatalogProperties;
import org.apache.iceberg.DataFile;
import org.apache.iceberg.DataFiles;
import org.apache.iceberg.PartitionSpec;
import org.apache.iceberg.Schema;
import org.apache.iceberg.Table;
import org.apache.iceberg.aws.AwsClientProperties;
import org.apache.iceberg.catalog.Namespace;
import org.apache.iceberg.catalog.TableIdentifier;
import org.apache.iceberg.rest.RESTCatalog;
import org.apache.iceberg.types.Types;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.projectnessie.catalog.files.s3.S3ClientAuthenticationMode;
import org.projectnessie.minio.MinioContainer;
import org.projectnessie.server.catalog.Catalogs;
import org.projectnessie.server.catalog.MinioTestResourceLifecycleManager;
import org.testcontainers.shaded.com.google.common.collect.ImmutableMap;

@QuarkusTestResource(
    restrictToAnnotatedClass = true,
    value = MinioTestResourceLifecycleManager.class)
@QuarkusIntegrationTest
@TestProfile(ITS3VendedAssumeRoleIcebergCatalog.Profile.class)
public class ITS3VendedAssumeRoleIcebergCatalog {

  private static final String IAM_POLICY =
      """
      { "Version":"2012-10-17",
        "Statement": [
          {"Sid":"A1", "Effect":"Allow", "Action":"s3:*", "Resource":"arn:aws:s3:::*"},
          {"Sid":"D1", "Effect":"Deny", "Action":"s3:*", "Resource":"arn:aws:s3:::*/blockedNamespace/*/*/snap*"}
         ]
      }
      """;

  private static final Schema SCHEMA =
      new Schema(Types.NestedField.required(1, "id", Types.LongType.get()));

  private static final PartitionSpec PARTITION_SPEC =
      PartitionSpec.builderFor(SCHEMA).bucket("id", 16).build();

  private static final DataFile FILE_A =
      DataFiles.builder(PARTITION_SPEC)
          .withPath("/path/to/data-a.parquet")
          .withFileSizeInBytes(10)
          .withPartitionPath("id_bucket=0")
          .withRecordCount(1)
          .build();

  @SuppressWarnings("unused")
  // Injected by MinioTestResourceLifecycleManager
  private MinioContainer minio;

  private static final Catalogs CATALOGS = new Catalogs();

  RESTCatalog catalog() {
    return CATALOGS.getCatalog(
        Map.of(
            AwsClientProperties.CLIENT_REGION,
            MinioTestResourceLifecycleManager.TEST_REGION,
            CatalogProperties.WAREHOUSE_LOCATION,
            minio.s3BucketUri("").toString()));
  }

  @AfterAll
  static void closeRestCatalog() throws Exception {
    CATALOGS.close();
  }

  @Test
  void testCatalogProperties() {
    @SuppressWarnings("resource")
    RESTCatalog catalog = catalog();

    assertThat(catalog.properties())
        .containsKeys("s3.access-key-id", "s3.secret-access-key", "s3.session-token");
  }

  @Test
  void testCreateTable() {
    @SuppressWarnings("resource")
    RESTCatalog catalog = catalog();

    Namespace ns = Namespace.of("allowedNamespace");
    catalog.createNamespace(ns);
    Table table = catalog.createTable(TableIdentifier.of(ns, "table1"), SCHEMA);
    // this operation will use credentials sent from the Catalog Server.
    table.newAppend().appendFile(FILE_A).commit();
  }

  @Test
  void testCreateTableForbidden() {
    @SuppressWarnings("resource")
    RESTCatalog catalog = catalog();

    Namespace ns = Namespace.of("blockedNamespace");
    catalog.createNamespace(ns);
    // Note: access to metadata files under `blockedNamespace` is allowed
    Table table = catalog.createTable(TableIdentifier.of(ns, "table2"), SCHEMA);
    // Attempts to create snapshot files are blocked by the session IAM policy
    assertThatThrownBy(() -> table.newAppend().appendFile(FILE_A).commit())
        .hasMessageContaining("Access Denied")
        // make sure the error happens on the client side
        .hasStackTraceContaining("software.amazon.awssdk.services.s3.DefaultS3Client")
        .hasStackTraceContaining("org.apache.iceberg.SnapshotProducer");
  }

  public static class Profile implements QuarkusTestProfile {
    @Override
    public Map<String, String> getConfigOverrides() {
      return ImmutableMap.<String, String>builder()
          .put(
              "nessie.catalog.service.s3.default-options.auth-mode",
              S3ClientAuthenticationMode.ASSUME_ROLE.name())
          .put("nessie.catalog.service.s3.default-options.session-iam-policy", IAM_POLICY)
          .put(
              "nessie.catalog.service.s3.default-options.assume-role",
              "test-role") // Note: unused by Minio
          .put(
              "nessie.catalog.service.s3.default-options.external-id",
              "test-external-id") // Note: unused by Minio
          .build();
    }
  }
}
