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

import java.net.URI;
import java.util.Optional;
import org.immutables.value.Value;
import org.projectnessie.catalog.secrets.BasicCredentials;

public interface S3BucketOptions {

  /** Default value for {@link #authType()}, being {@link S3AuthType#STATIC}. */
  S3AuthType DEFAULT_SERVER_AUTH_MODE = S3AuthType.STATIC;

  /**
   * Endpoint URI, required for private (non-AWS) clouds, specified either per bucket or in the
   * top-level S3 settings.
   *
   * <p>If the endpoint URIs for the Nessie server and clients differ, this one defines the endpoint
   * used for the Nessie server.
   */
  Optional<URI> endpoint();

  /**
   * When using a specific endpoint ({@code endpoint}) and the endpoint URIs for the Nessie server
   * differ, you can specify the URI passed down to clients using this setting. Otherwise, clients
   * will receive the value from the {@code endpoint} setting.
   */
  Optional<URI> externalEndpoint();

  /**
   * Whether to use path-style access. If true, path-style access will be used, as in: {@code
   * https://<domain>/<bucket>}. If false, a virtual-hosted style will be used instead, as in:
   * {@code https://<bucket>.<domain>}. If unspecified, the default will depend on the cloud
   * provider.
   */
  Optional<Boolean> pathStyleAccess();

  /**
   * AWS Access point for this bucket. Access points can be used to perform S3 operations by
   * specifying a mapping of bucket to access points. This is useful for multi-region access,
   * cross-region access, disaster recovery, etc.
   *
   * @see <a
   *     href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/using-access-points.html">Access
   *     Points</a>
   */
  Optional<String> accessPoint();

  /**
   * Authorize cross-region calls when contacting an {@code access-point}.
   *
   * <p>By default, attempting to use an access point in a different region will throw an exception.
   * When enabled, this property allows using access points in other regions.
   */
  Optional<Boolean> allowCrossRegionAccessPoint();

  /**
   * DNS name of the region, required for AWS. The region must be specified for AWS, either per
   * bucket or in the top-level S3 settings.
   */
  Optional<String> region();

  /**
   * The authentication mode to use by the Catalog server. If not set, the default is {@code
   * STATIC}. Depending on the authentication mode, other properties may be required.
   *
   * <p>Valid values are:
   *
   * <ul>
   *   <li>{@code APPLICATION_GLOBAL}: Use the AWSSDK <a
   *       href="https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/credentials-chain.html">default
   *       credentials provider</a>.
   *   <li>{@code STATIC}: Static credentials provided through the {@code access-key} option.
   * </ul>
   */
  Optional<S3AuthType> authType();

  default S3AuthType effectiveAuthMode() {
    return authType().orElse(DEFAULT_SERVER_AUTH_MODE);
  }

  /**
   * An access-key-id and secret-access-key must be configured using the {@code name} and {@code
   * secret} fields, either per bucket or in the top-level S3 settings.
   *
   * <p>Required when {@code auth-type} is {@code STATIC}.
   *
   * <p>For STS, this defines the Access Key ID and Secret Key ID to be used as a basic credential
   * for obtaining temporary session credentials.
   */
  Optional<BasicCredentials> accessKey();

  /** Optional parameter to disable S3 request signing. Default is to enable S3 request signing. */
  Optional<Boolean> requestSigningEnabled();

  /**
   * The <a href="https://docs.aws.amazon.com/STS/latest/APIReference/welcome.html">Security Token
   * Service</a> endpoint.
   *
   * <p>This parameter must be set when running in a private (non-AWS) cloud and the catalog is
   * configured to use S3 sessions (e.g. to use the "assume role" functionality).
   */
  Optional<URI> stsEndpoint();

  /** Configure assume-role functionality for Nessie server. */
  Optional<S3ServerIam> serverIam();

  /** Configure assume-role/scoped-down credentials for clients. */
  Optional<S3ClientIam> clientIam();

  @Value.NonAttribute
  default Optional<S3ServerIam> getEnabledServerIam() {
    return serverIam().filter(iam -> iam.enabled().orElse(false));
  }

  @Value.NonAttribute
  default Optional<S3ClientIam> getEnabledClientIam() {
    return clientIam().filter(iam -> iam.enabled().orElse(false));
  }

  @Value.NonAttribute
  default boolean effectiveRequestSigningEnabled() {
    return requestSigningEnabled().orElse(true);
  }

  @Value.NonAttribute
  default boolean effectiveClientAssumeRoleEnabled() {
    if (clientIam().isEmpty() || region().isEmpty()) {
      return false;
    }
    return clientIam().get().enabled().orElse(false);
  }
}
