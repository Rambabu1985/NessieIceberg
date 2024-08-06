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

import static com.google.common.base.Preconditions.checkArgument;
import static org.projectnessie.catalog.files.s3.S3IamPolicies.locationDependentPolicy;

import java.util.Optional;
import software.amazon.awssdk.services.sts.StsClient;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;

class StsCredentialsFetcherImpl implements StsCredentialsFetcher {

  private final StsClientsPool clientsPool;

  StsCredentialsFetcherImpl(StsClientsPool clientsPool) {
    this.clientsPool = clientsPool;
  }

  @Override
  public Credentials fetchCredentialsForClient(
      S3BucketOptions bucketOptions, S3ClientIam iam, Optional<StorageLocations> locations) {
    AssumeRoleRequest.Builder request = AssumeRoleRequest.builder();
    if (locations.isPresent()) {
      iam.policy()
          .ifPresentOrElse(
              request::policy, () -> request.policy(locationDependentPolicy(iam, locations.get())));
    } else {
      iam.policy().ifPresent(request::policy);
    }
    return doFetchCredentials(bucketOptions, request, iam);
  }

  @Override
  public Credentials fetchCredentialsForServer(S3BucketOptions bucketOptions, S3ServerIam iam) {
    return doFetchCredentials(bucketOptions, AssumeRoleRequest.builder(), iam);
  }

  private Credentials doFetchCredentials(
      S3BucketOptions bucketOptions, AssumeRoleRequest.Builder request, S3Iam iam) {
    request.roleSessionName(iam.roleSessionName().orElse(S3Iam.DEFAULT_SESSION_NAME));
    iam.assumeRole().ifPresent(request::roleArn);
    iam.externalId().ifPresent(request::externalId);
    iam.sessionDuration()
        .ifPresent(
            duration -> {
              long seconds = duration.toSeconds();
              checkArgument(
                  seconds < Integer.MAX_VALUE,
                  "Requested session duration is too long: " + duration);
              request.durationSeconds((int) seconds);
            });
    request.overrideConfiguration(
        builder -> {
          S3AuthType authType = bucketOptions.effectiveAuthType();
          builder.credentialsProvider(authType.newCredentialsProvider(bucketOptions));
        });

    AssumeRoleRequest req = request.build();

    @SuppressWarnings("resource")
    StsClient client = clientsPool.stsClientForBucket(bucketOptions);

    AssumeRoleResponse response = client.assumeRole(req);
    return response.credentials();
  }
}
