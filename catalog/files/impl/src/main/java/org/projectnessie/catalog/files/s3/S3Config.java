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
package org.projectnessie.catalog.files.s3;

import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Optional;
import java.util.OptionalInt;
import org.immutables.value.Value;
import org.projectnessie.catalog.secrets.KeySecret;
import org.projectnessie.nessie.docgen.annotations.ConfigDocs.ConfigItem;

@Value.Immutable
public interface S3Config {
  /** Override the default maximum number of pooled connections. */
  @ConfigItem(section = "transport")
  OptionalInt maxHttpConnections();

  /** Override the default connection read timeout. */
  @ConfigItem(section = "transport")
  Optional<Duration> readTimeout();

  /** Override the default TCP connect timeout. */
  @ConfigItem(section = "transport")
  Optional<Duration> connectTimeout();

  /**
   * Override default connection acquisition timeout. This is the time a request will wait for a
   * connection from the pool.
   */
  @ConfigItem(section = "transport")
  Optional<Duration> connectionAcquisitionTimeout();

  /** Override default max idle time of a pooled connection. */
  @ConfigItem(section = "transport")
  Optional<Duration> connectionMaxIdleTime();

  /** Override default time-time of a pooled connection. */
  @ConfigItem(section = "transport")
  Optional<Duration> connectionTimeToLive();

  /** Override default behavior whether to expect an HTTP/100-Continue. */
  @ConfigItem(section = "transport")
  Optional<Boolean> expectContinueEnabled();

  /**
   * Instruct the S3 HTTP client to accept all SSL certificates, if set to {@code true}. Enabling
   * this option is dangerous, it is strongly recommended to leave this option unset or {@code
   * false}.
   */
  @ConfigItem(section = "transport")
  Optional<Boolean> trustAllCertificates();

  /**
   * Override to set the file path to a custom SSL trust store. {@code
   * nessie.catalog.service.s3.trust-store.type} and {@code
   * nessie.catalog.service.s3.trust-store.password} must be supplied as well when providing a
   * custom trust store.
   *
   * <p>When running in k8s or Docker, the path is local within the pod/container and must be
   * explicitly mounted.
   */
  @ConfigItem(section = "transport")
  Optional<Path> trustStorePath();

  /**
   * Override to set the type of the custom SSL trust store specified in {@code
   * nessie.catalog.service.s3.trust-store.path}.
   *
   * <p>Supported types include {@code JKS}, {@code PKCS12}, and all key store types supported by
   * Java 17.
   */
  @ConfigItem(section = "transport")
  Optional<String> trustStoreType();

  /**
   * Override to set the password for the custom SSL trust store specified in {@code
   * nessie.catalog.service.s3.trust-store.path}.
   */
  @ConfigItem(section = "transport")
  Optional<KeySecret> trustStorePassword();

  /**
   * Override to set the file path to a custom SSL key store. {@code
   * nessie.catalog.service.s3.key-store.type} and {@code
   * nessie.catalog.service.s3.key-store.password} must be supplied as well when providing a custom
   * key store.
   *
   * <p>When running in k8s or Docker, the path is local within the pod/container and must be
   * explicitly mounted.
   */
  @ConfigItem(section = "transport")
  Optional<Path> keyStorePath();

  /**
   * Override to set the type of the custom SSL key store specified in {@code
   * nessie.catalog.service.s3.key-store.path}.
   *
   * <p>Supported types include {@code JKS}, {@code PKCS12}, and all key store types supported by
   * Java 17.
   */
  @ConfigItem(section = "transport")
  Optional<String> keyStoreType();

  /**
   * Override to set the password for the custom SSL key store specified in {@code
   * nessie.catalog.service.s3.key-store.path}.
   */
  @ConfigItem(section = "transport")
  Optional<KeySecret> keyStorePassword();

  /**
   * Interval after which a request is retried when S3 response with some "retry later" response.
   */
  Optional<Duration> retryAfter();

  static Builder builder() {
    return ImmutableS3Config.builder();
  }

  @SuppressWarnings("unused")
  interface Builder {
    @CanIgnoreReturnValue
    Builder from(S3Config instance);

    @CanIgnoreReturnValue
    Builder maxHttpConnections(int maxHttpConnections);

    @CanIgnoreReturnValue
    Builder readTimeout(Duration readTimeout);

    @CanIgnoreReturnValue
    Builder connectTimeout(Duration connectTimeout);

    @CanIgnoreReturnValue
    Builder connectionAcquisitionTimeout(Duration connectionAcquisitionTimeout);

    @CanIgnoreReturnValue
    Builder connectionMaxIdleTime(Duration connectionMaxIdleTime);

    @CanIgnoreReturnValue
    Builder connectionTimeToLive(Duration connectionTimeToLive);

    @CanIgnoreReturnValue
    Builder expectContinueEnabled(boolean expectContinueEnabled);

    @CanIgnoreReturnValue
    Builder trustStorePath(Path trustStorePath);

    @CanIgnoreReturnValue
    Builder trustStoreType(String trustStoreType);

    @CanIgnoreReturnValue
    Builder trustStorePassword(KeySecret trustStorePassword);

    @CanIgnoreReturnValue
    Builder keyStorePath(Path keyStorePath);

    @CanIgnoreReturnValue
    Builder keyStoreType(String keyStoreType);

    @CanIgnoreReturnValue
    Builder keyStorePassword(KeySecret keyStorePassword);

    @CanIgnoreReturnValue
    Builder retryAfter(Duration retryAfter);

    S3Config build();
  }
}
