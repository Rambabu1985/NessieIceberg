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
package org.projectnessie.minio;

import com.google.common.base.Preconditions;
import java.net.URI;
import java.time.Duration;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.junit.jupiter.api.extension.ExtensionContext.Store.CloseableResource;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.HttpWaitStrategy;
import org.testcontainers.utility.Base58;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.CreateBucketRequest;

final class MinioContainer extends GenericContainer<MinioContainer>
    implements MinioAccess, CloseableResource {

  private static final int DEFAULT_PORT = 9000;
  private static final String DEFAULT_IMAGE = "quay.io/minio/minio";
  private static final String DEFAULT_TAG = "latest";

  private static final String MINIO_ACCESS_KEY = "MINIO_ROOT_USER";
  private static final String MINIO_SECRET_KEY = "MINIO_ROOT_PASSWORD";

  private static final String DEFAULT_STORAGE_DIRECTORY = "/data";
  private static final String HEALTH_ENDPOINT = "/minio/health/ready";

  private final String accessKey;
  private final String secretKey;
  private final String bucket;

  private String hostPort;
  private String s3endpoint;
  private S3Client s3;
  private URI bucketBaseUri;

  public MinioContainer() {
    this(null, null, null, null);
  }

  @SuppressWarnings("resource")
  public MinioContainer(String image, String accessKey, String secretKey, String bucket) {
    super(image == null ? DEFAULT_IMAGE + ":" + DEFAULT_TAG : image);
    withNetworkAliases(randomString("minio"));
    withLogConsumer(new Slf4jLogConsumer(LoggerFactory.getLogger(MinioContainer.class)));
    addExposedPort(DEFAULT_PORT);
    this.accessKey = accessKey != null ? accessKey : randomString("access");
    this.secretKey = secretKey != null ? secretKey : randomString("secret");
    this.bucket = bucket != null ? bucket : randomString("bucket");
    withEnv(MINIO_ACCESS_KEY, this.accessKey);
    withEnv(MINIO_SECRET_KEY, this.secretKey);
    withCommand("server", DEFAULT_STORAGE_DIRECTORY);
    setWaitStrategy(
        new HttpWaitStrategy()
            .forPort(DEFAULT_PORT)
            .forPath(HEALTH_ENDPOINT)
            .withStartupTimeout(Duration.ofMinutes(2)));
  }

  private static String randomString(String prefix) {
    return prefix + "-" + Base58.randomString(6).toLowerCase(Locale.ROOT);
  }

  @Override
  public String hostPort() {
    Preconditions.checkState(hostPort != null, "Container not yet started");
    return hostPort;
  }

  @Override
  public String accessKey() {
    return accessKey;
  }

  @Override
  public String secretKey() {
    return secretKey;
  }

  @Override
  public String bucket() {
    return bucket;
  }

  @Override
  public String s3endpoint() {
    Preconditions.checkState(s3endpoint != null, "Container not yet started");
    return s3endpoint;
  }

  @Override
  public S3Client s3Client() {
    Preconditions.checkState(s3 != null, "Container not yet started");
    return s3;
  }

  @Override
  public Map<String, String> icebergProperties() {
    Map<String, String> props = new HashMap<>();
    props.put("s3.access-key-id", accessKey());
    props.put("s3.secret-access-key", secretKey());
    props.put("s3.endpoint", s3endpoint());
    props.put("http-client.type", "urlconnection");
    return props;
  }

  @Override
  public Configuration hadoopConfiguration() {
    Configuration conf = new Configuration();
    conf.set("fs.s3a.access.key", accessKey());
    conf.set("fs.s3a.secret.key", secretKey());
    conf.set("fs.s3a.endpoint", s3endpoint());
    return conf;
  }

  @Override
  public URI s3BucketUri(String path) {
    Preconditions.checkState(bucketBaseUri != null, "Container not yet started");
    return bucketBaseUri.resolve(path);
  }

  @Override
  public void start() {
    super.start();

    this.hostPort = getHost() + ":" + getMappedPort(DEFAULT_PORT);
    this.s3endpoint = String.format("http://%s/", hostPort);
    this.bucketBaseUri = URI.create(String.format("s3://%s/", bucket()));

    this.s3 = createS3Client();
    this.s3.createBucket(CreateBucketRequest.builder().bucket(bucket()).build());
  }

  @Override
  public void close() {
    stop();
  }

  @Override
  public void stop() {
    try {
      if (s3 != null) {
        s3.close();
      }
    } finally {
      s3 = null;
      super.stop();
    }
  }

  private S3Client createS3Client() {
    return S3Client.builder()
        .httpClientBuilder(UrlConnectionHttpClient.builder())
        .applyMutation(builder -> builder.endpointOverride(URI.create(s3endpoint())))
        // .serviceConfiguration(s3Configuration(s3PathStyleAccess, s3UseArnRegionEnabled))
        // credentialsProvider(s3AccessKeyId, s3SecretAccessKey, s3SessionToken)
        .credentialsProvider(
            StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey(), secretKey())))
        .build();
  }
}
