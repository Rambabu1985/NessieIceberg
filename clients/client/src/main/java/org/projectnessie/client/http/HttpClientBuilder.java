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
package org.projectnessie.client.http;

import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_AUTH_TYPE;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OWNER;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_PASSWORD;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_REPOSITORY;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_TRACING;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_URI;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_URL;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_USERNAME;

import java.net.URI;
import java.util.function.Function;
import org.projectnessie.client.NessieClient;
import org.projectnessie.client.NessieClient.AuthType;
import org.projectnessie.client.NessieClientBuilder;
import org.projectnessie.client.NessieConfigConstants;
import org.projectnessie.model.Validation;

/**
 * A builder class that creates a {@link NessieHttpClient} via {@link HttpClientBuilder#builder()}.
 */
public class HttpClientBuilder implements NessieClientBuilder<HttpClientBuilder> {

  private AuthType authType = AuthType.NONE;
  private URI uri;
  private String username;
  private String password;
  private String owner;
  private String repo;
  private boolean tracing;
  private int readTimeoutMillis =
      Integer.parseInt(System.getProperty("sun.net.client.defaultReadTimeout", "25000"));
  private int connectionTimeoutMillis =
      Integer.parseInt(System.getProperty("sun.net.client.defaultConnectionTimeout", "5000"));

  protected HttpClientBuilder() {}

  public static HttpClientBuilder builder() {
    return new HttpClientBuilder();
  }

  /**
   * Same semantics as {@link #fromConfig(Function)}, uses the system properties.
   *
   * @return {@code this}
   * @see #fromConfig(Function)
   */
  @Override
  public HttpClientBuilder fromSystemProperties() {
    return fromConfig(System::getProperty);
  }

  /**
   * Configure this HttpClientBuilder instance using a configuration object and standard Nessie
   * configuration keys defined by the constants defined in {@link NessieConfigConstants}.
   * Non-{@code null} values returned by the {@code configuration}-function will override previously
   * configured values.
   *
   * @param configuration The function that returns a configuration value for a configuration key.
   * @return {@code this}
   * @see #fromSystemProperties()
   */
  @Override
  public HttpClientBuilder fromConfig(Function<String, String> configuration) {
    String uri = configuration.apply(CONF_NESSIE_URI);
    if (uri == null) {
      uri = configuration.apply(CONF_NESSIE_URL);
    }
    if (uri != null) {
      this.uri = URI.create(uri);
    }
    String username = configuration.apply(CONF_NESSIE_USERNAME);
    if (username != null) {
      this.username = username;
    }
    String password = configuration.apply(CONF_NESSIE_PASSWORD);
    if (password != null) {
      this.password = password;
    }
    String authType = configuration.apply(CONF_NESSIE_AUTH_TYPE);
    if (authType != null) {
      this.authType = AuthType.valueOf(authType);
    }
    String tracing = configuration.apply(CONF_NESSIE_TRACING);
    if (tracing != null) {
      this.tracing = Boolean.parseBoolean(tracing);
    }
    String owner = configuration.apply(CONF_NESSIE_OWNER);
    if (owner != null) {
      Validation.validateOwner(owner);
      this.owner = owner;
    }
    String repo = configuration.apply(CONF_NESSIE_REPOSITORY);
    if (repo != null) {
      Validation.validateRepo(repo);
      this.repo = repo;
    }
    return this;
  }

  /**
   * Set the authentication type. Default is {@link AuthType#NONE}.
   *
   * @param authType new auth-type
   * @return {@code this}
   */
  @Override
  public HttpClientBuilder withAuthType(AuthType authType) {
    this.authType = authType;
    return this;
  }

  /**
   * Set the Nessie server URI. A server URI must be configured.
   *
   * @param uri server URI
   * @return {@code this}
   */
  @Override
  public HttpClientBuilder withUri(URI uri) {
    this.uri = uri;
    return this;
  }

  /**
   * Convenience method for {@link #withUri(URI)} taking a string.
   *
   * @param uri server URI
   * @return {@code this}
   */
  @Override
  public HttpClientBuilder withUri(String uri) {
    return withUri(URI.create(uri));
  }

  /**
   * Set the repository owner + ID within the Nessie instance.
   *
   * <p>{@link NessieClient#getContentsApi()} and {@link NessieClient#getTreeApi()} are scoped to
   * the repository specified by this option.
   *
   * @param owner repository owner
   * @param repo repository ID
   * @return {@code this}
   */
  public HttpClientBuilder withRepoOwner(String owner, String repo) {
    Validation.validateOwner(owner);
    this.owner = owner;
    this.repo = repo;
    return this;
  }

  /**
   * Set the username for {@link AuthType#BASIC} authentication.
   *
   * @param username username
   * @return {@code this}
   */
  @Override
  public HttpClientBuilder withUsername(String username) {
    this.username = username;
    return this;
  }

  /**
   * Set the password for {@link AuthType#BASIC} authentication.
   *
   * @param password password
   * @return {@code this}
   */
  @Override
  public HttpClientBuilder withPassword(String password) {
    this.password = password;
    return this;
  }

  /**
   * Whether to enable adding the HTTP headers of an active OpenTracing span to all Nessie requests.
   * If enabled, the OpenTracing dependencies must be present at runtime.
   *
   * @param tracing {@code true} to enable passing HTTP headers for active tracing spans.
   * @return {@code this}
   */
  public HttpClientBuilder withTracing(boolean tracing) {
    this.tracing = tracing;
    return this;
  }

  /**
   * Set the read timeout in milliseconds for this client. Timeout will throw {@link
   * HttpClientReadTimeoutException}.
   *
   * @param readTimeoutMillis number of seconds to wait for a response from server.
   * @return {@code this}
   */
  public HttpClientBuilder withReadTimeout(int readTimeoutMillis) {
    this.readTimeoutMillis = readTimeoutMillis;
    return this;
  }

  /**
   * Set the connection timeout in milliseconds for this client. Timeout will throw {@link
   * HttpClientException}.
   *
   * @param connectionTimeoutMillis number of seconds to wait to connect to the server.
   * @return {@code this}
   */
  public HttpClientBuilder withConnectionTimeout(int connectionTimeoutMillis) {
    this.connectionTimeoutMillis = connectionTimeoutMillis;
    return this;
  }

  /**
   * Builds a new {@link NessieHttpClient}.
   *
   * @return A new {@link NessieHttpClient}.
   */
  @Override
  public NessieClient build() {
    return new NessieHttpClient(
        owner,
        repo,
        authType,
        uri,
        username,
        password,
        tracing,
        readTimeoutMillis,
        connectionTimeoutMillis);
  }
}
