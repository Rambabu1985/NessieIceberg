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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.gax.retrying.RetrySettings;
import com.google.auth.Credentials;
import com.google.auth.http.HttpTransportFactory;
import com.google.auth.oauth2.AccessToken;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.OAuth2Credentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import com.google.cloud.NoCredentials;
import com.google.cloud.http.HttpTransportOptions;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.URI;
import java.time.Duration;
import java.util.Date;
import java.util.function.Function;
import java.util.function.Supplier;
import org.projectnessie.catalog.secrets.TokenSecret;

public final class GcsClients {
  private GcsClients() {}

  public static Storage buildStorage(
      GcsOptions<?> gcsOptions,
      GcsBucketOptions bucketOptions,
      HttpTransportFactory transportFactory) {
    HttpTransportOptions.Builder transportOptions =
        HttpTransportOptions.newBuilder().setHttpTransportFactory(transportFactory);
    gcsOptions
        .connectTimeout()
        .ifPresent(d -> transportOptions.setConnectTimeout((int) d.toMillis()));
    gcsOptions.readTimeout().ifPresent(d -> transportOptions.setReadTimeout((int) d.toMillis()));

    StorageOptions.Builder builder =
        StorageOptions.http()
            .setCredentials(buildCredentials(bucketOptions, transportFactory))
            .setTransportOptions(transportOptions.build());
    bucketOptions.projectId().ifPresent(builder::setProjectId);
    bucketOptions.quotaProjectId().ifPresent(builder::setQuotaProjectId);
    bucketOptions.host().map(URI::toString).ifPresent(builder::setHost);
    bucketOptions.clientLibToken().ifPresent(builder::setClientLibToken);
    builder.setRetrySettings(buildRetrySettings(gcsOptions));
    // TODO ??
    // bucketOptions.buildStorageRetryStrategy().ifPresent(builder::setStorageRetryStrategy);

    return builder.build().getService();
  }

  static RetrySettings buildRetrySettings(GcsOptions<?> gcsOptions) {
    Function<Duration, org.threeten.bp.Duration> duration =
        d -> org.threeten.bp.Duration.ofMillis(d.toMillis());

    RetrySettings.Builder retry = RetrySettings.newBuilder();
    gcsOptions.maxAttempts().ifPresent(retry::setMaxAttempts);
    gcsOptions.logicalTimeout().map(duration).ifPresent(retry::setLogicalTimeout);
    gcsOptions.totalTimeout().map(duration).ifPresent(retry::setTotalTimeout);

    gcsOptions.initialRetryDelay().map(duration).ifPresent(retry::setInitialRetryDelay);
    gcsOptions.maxRetryDelay().map(duration).ifPresent(retry::setMaxRetryDelay);
    gcsOptions.retryDelayMultiplier().ifPresent(retry::setRetryDelayMultiplier);

    gcsOptions.initialRpcTimeout().map(duration).ifPresent(retry::setInitialRpcTimeout);
    gcsOptions.maxRpcTimeout().map(duration).ifPresent(retry::setMaxRpcTimeout);
    gcsOptions.rpcTimeoutMultiplier().ifPresent(retry::setRpcTimeoutMultiplier);

    return retry.build();
  }

  public static HttpTransportFactory buildSharedHttpTransportFactory() {
    // Uses the java.net.HttpURLConnection stuff...
    NetHttpTransport.Builder httpTransport = new NetHttpTransport.Builder();
    return new SharedHttpTransportFactory(httpTransport::build);
  }

  static final class SharedHttpTransportFactory implements HttpTransportFactory {
    private final Supplier<HttpTransport> delegate;
    private HttpTransport httpTransport;

    SharedHttpTransportFactory(Supplier<HttpTransport> delegate) {
      this.delegate = delegate;
    }

    @Override
    public HttpTransport create() {
      if (httpTransport == null) {
        synchronized (this) {
          if (httpTransport == null) {
            httpTransport = delegate.get();
          }
        }
      }
      return httpTransport;
    }
  }

  static Credentials buildCredentials(
      GcsBucketOptions bucketOptions, HttpTransportFactory transportFactory) {
    GcsBucketOptions.GcsAuthType authType =
        bucketOptions.authType().orElse(GcsBucketOptions.GcsAuthType.NONE);
    switch (authType) {
      case NONE:
        return NoCredentials.getInstance();
      case USER:
        try {
          return UserCredentials.fromStream(
              new ByteArrayInputStream(
                  bucketOptions
                      .authCredentialsJson()
                      .orElseThrow(() -> new IllegalStateException("auth-credentials-json missing"))
                      .key()
                      .getBytes(UTF_8)),
              transportFactory);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      case SERVICE_ACCOUNT:
        try {
          return ServiceAccountCredentials.fromStream(
              new ByteArrayInputStream(
                  bucketOptions
                      .authCredentialsJson()
                      .orElseThrow(() -> new IllegalStateException("auth-credentials-json missing"))
                      .key()
                      .getBytes(UTF_8)),
              transportFactory);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      case ACCESS_TOKEN:
        TokenSecret oauth2token =
            bucketOptions
                .oauth2Token()
                .orElseThrow(() -> new IllegalStateException("oauth2-token missing"));
        AccessToken accessToken =
            new AccessToken(
                oauth2token.token(),
                oauth2token.expiresAt().map(i -> new Date(i.toEpochMilli())).orElse(null));
        return OAuth2Credentials.create(accessToken);
      case APPLICATION_DEFAULT:
        try {
          return GoogleCredentials.getApplicationDefault();
        } catch (IOException e) {
          throw new IllegalArgumentException("Unable to load default credentials", e);
        }
      default:
        throw new IllegalArgumentException("Unsupported auth type " + authType);
    }
  }
}
