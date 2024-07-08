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
import java.time.Duration;
import java.util.Optional;
import org.projectnessie.catalog.secrets.BasicCredentials;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;

public interface S3BucketOptions {
  /**
   * Default value for {@link #roleSessionName()} that identifies the session simply as a "Nessie"
   * session.
   */
  String DEFAULT_SESSION_NAME = "nessie";

  /** Default value for {@link #minSessionCredentialValidityPeriod()}. */
  Duration DEFAULT_SESSION_DURATION =
      Duration.ofHours(1); // 1 hour lifetime is common for session credentials in S3

  /**
   * Default value for {@link #clientAuthenticationMode()}, being {@link
   * S3ClientAuthenticationMode#REQUEST_SIGNING}.
   */
  S3ClientAuthenticationMode DEFAULT_AUTHENTICATION_MODE =
      S3ClientAuthenticationMode.REQUEST_SIGNING;

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
   * An access-key-id and secret-access-key must be configured using the {@code name} and {@code
   * secret} fields, either per bucket or in the top-level S3 settings. For STS, this defines the
   * Access Key ID and Secret Key ID to be used as a basic credential for obtaining temporary
   * session credentials.
   */
  Optional<BasicCredentials> accessKey();

  /**
   * The <a href="https://docs.aws.amazon.com/STS/latest/APIReference/welcome.html">Security Token
   * Service</a> endpoint.
   *
   * <p>This parameter must be set when running in a private (non-AWS) cloud and the catalog is
   * configured to use S3 sessions (e.g. to use the "assume role" functionality).
   */
  Optional<URI> stsEndpoint();

  /**
   * The <a href="https://docs.aws.amazon.com/IAM/latest/UserGuide/reference-arns.html">ARN</a> of
   * the role to assume for accessing S3 data. This parameter is required for Amazon S3, but may not
   * be required for other storage providers (e.g. Minio does not use it at all).
   */
  Optional<String> assumeRole();

  /**
   * IAM policy in JSON format to be used as an inline <a
   * href="https://docs.aws.amazon.com/IAM/latest/UserGuide/access_policies.html#policies_session">session
   * policy</a> (optional).
   *
   * @see AssumeRoleRequest#policy()
   */
  Optional<String> sessionIamPolicy();

  /**
   * An identifier for the assumed role session. This parameter is most important in cases when the
   * same role is assumed by different principals in different use cases.
   *
   * @see AssumeRoleRequest#roleSessionName()
   */
  Optional<String> roleSessionName();

  /**
   * An identifier for the party assuming the role. This parameter must match the external ID
   * configured in IAM rules that <a
   * href="https://docs.aws.amazon.com/IAM/latest/UserGuide/id_roles_create_for-user_externalid.html">govern</a>
   * the assume role process for the specified {@code role-arn}.
   *
   * <p>This parameter is essential in preventing the <a
   * href="https://docs.aws.amazon.com/IAM/latest/UserGuide/confused-deputy.html">Confused
   * Deputy</a> problem.
   *
   * @see AssumeRoleRequest#externalId()
   */
  Optional<String> externalId();

  /** Controls the authentication mode for Catalog clients accessing this bucket. */
  Optional<S3ClientAuthenticationMode> clientAuthenticationMode();

  default S3ClientAuthenticationMode effectiveClientAuthenticationMode() {
    return clientAuthenticationMode().orElse(DEFAULT_AUTHENTICATION_MODE);
  }

  /**
   * A higher bound estimate of the expected duration of client "sessions" working with data in this
   * bucket. A session, for example, is the lifetime of an Iceberg REST catalog object on the client
   * side. This value is used for validating expiration times of credentials associated with the
   * warehouse.
   *
   * <p>This parameter is relevant only when {@code client-authentication-mode} is {@code
   * ASSUME_ROLE}.
   */
  Optional<Duration> clientSessionDuration();

  /**
   * The minimum required validity period for session credentials. The value of {@code
   * client-session-duration} is used if set, otherwise the default ({@code
   * DEFAULT_SESSION_DURATION}) session duration is assumed.
   */
  default Duration minSessionCredentialValidityPeriod() {
    return clientSessionDuration().orElse(DEFAULT_SESSION_DURATION);
  }
}
