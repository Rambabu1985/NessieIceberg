/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.client.auth.oauth2;

import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_WEB_PORT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_AUTH_ENDPOINT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_BACKGROUND_THREAD_IDLE_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_CLIENT_ID;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_CLIENT_SCOPES;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_CLIENT_SECRET;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_DEFAULT_ACCESS_TOKEN_LIFESPAN;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_DEFAULT_REFRESH_TOKEN_LIFESPAN;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_GRANT_TYPE;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_ISSUER_URL;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_PASSWORD;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_REFRESH_SAFETY_WINDOW;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_TOKEN_ENDPOINT;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_TOKEN_EXCHANGE_ENABLED;
import static org.projectnessie.client.NessieConfigConstants.CONF_NESSIE_OAUTH2_USERNAME;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_AUTHORIZATION_CODE_FLOW_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_BACKGROUND_THREAD_IDLE_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_DEFAULT_ACCESS_TOKEN_LIFESPAN;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_DEFAULT_REFRESH_TOKEN_LIFESPAN;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT;
import static org.projectnessie.client.NessieConfigConstants.DEFAULT_REFRESH_SAFETY_WINDOW;
import static org.projectnessie.client.auth.oauth2.OAuth2ClientConfig.applyConfigOption;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.concurrent.ScheduledExecutorService;
import java.util.function.Function;
import javax.net.ssl.SSLContext;
import org.immutables.value.Value;
import org.projectnessie.client.NessieConfigConstants;

/** Configuration options for {@link OAuth2Authenticator}. */
@Value.Immutable(lazyhash = true)
public interface OAuth2AuthenticatorConfig {

  /**
   * Creates a new {@link OAuth2AuthenticatorConfig} from the given configuration supplier.
   *
   * @param config the configuration supplier
   * @return a new {@link OAuth2AuthenticatorConfig}
   * @throws NullPointerException if {@code config} is {@code null}, or a required configuration
   *     option is missing
   * @throws IllegalArgumentException if the configuration is otherwise invalid
   * @see NessieConfigConstants
   */
  static OAuth2AuthenticatorConfig fromConfigSupplier(Function<String, String> config) {
    Objects.requireNonNull(config, "config must not be null");
    OAuth2ClientConfig.Builder builder =
        OAuth2ClientConfig.builder()
            .clientId(
                Objects.requireNonNull(
                    config.apply(CONF_NESSIE_OAUTH2_CLIENT_ID), "client ID must not be null"))
            .clientSecret(
                Objects.requireNonNull(
                    config.apply(CONF_NESSIE_OAUTH2_CLIENT_SECRET),
                    "client secret must not be null"));
    applyConfigOption(config, CONF_NESSIE_OAUTH2_ISSUER_URL, builder::issuerUrl, URI::create);
    applyConfigOption(
        config, CONF_NESSIE_OAUTH2_TOKEN_ENDPOINT, builder::tokenEndpoint, URI::create);
    applyConfigOption(config, CONF_NESSIE_OAUTH2_AUTH_ENDPOINT, builder::authEndpoint, URI::create);
    applyConfigOption(
        config, CONF_NESSIE_OAUTH2_GRANT_TYPE, builder::grantType, GrantType::fromCanonicalName);
    applyConfigOption(config, CONF_NESSIE_OAUTH2_USERNAME, builder::username);
    applyConfigOption(config, CONF_NESSIE_OAUTH2_PASSWORD, builder::password);
    applyConfigOption(config, CONF_NESSIE_OAUTH2_CLIENT_SCOPES, builder::scope);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_TOKEN_EXCHANGE_ENABLED,
        builder::tokenExchangeEnabled,
        Boolean::parseBoolean);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_DEFAULT_ACCESS_TOKEN_LIFESPAN,
        builder::defaultAccessTokenLifespan,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_DEFAULT_REFRESH_TOKEN_LIFESPAN,
        builder::defaultRefreshTokenLifespan,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_REFRESH_SAFETY_WINDOW,
        builder::refreshSafetyWindow,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT,
        builder::preemptiveTokenRefreshIdleTimeout,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_BACKGROUND_THREAD_IDLE_TIMEOUT,
        builder::backgroundThreadIdleTimeout,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_TIMEOUT,
        builder::authorizationCodeFlowTimeout,
        Duration::parse);
    applyConfigOption(
        config,
        CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_WEB_PORT,
        builder::authorizationCodeFlowWebServerPort,
        Integer::parseInt);
    return builder.build();
  }

  /**
   * The root URL of the OpenID Connect identity issuer provider, which will be used for discovering
   * supported endpoints and their locations.
   *
   * <p>Endpoint discovery is performed using the OpenID Connect Discovery metadata published by the
   * issuer. See <a href="https://openid.net/specs/openid-connect-discovery-1_0.html">OpenID Connect
   * Discovery 1.0</a> for more information.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_ISSUER_URL
   */
  Optional<URI> getIssuerUrl();

  /**
   * The OAuth2 token endpoint. Either this or {@link #getIssuerUrl()} must be set.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_TOKEN_ENDPOINT
   */
  Optional<URI> getTokenEndpoint();

  /**
   * The OAuth2 authorization endpoint. Either this or {@link #getIssuerUrl()} must be set, if the
   * grant type is {@link GrantType#AUTHORIZATION_CODE}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_AUTH_ENDPOINT
   */
  Optional<URI> getAuthEndpoint();

  /**
   * The OAuth2 grant type. Defaults to {@link GrantType#CLIENT_CREDENTIALS}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_GRANT_TYPE
   */
  @Value.Default
  default GrantType getGrantType() {
    return GrantType.CLIENT_CREDENTIALS;
  }

  /**
   * The OAuth2 client ID. Must be set.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_CLIENT_ID
   */
  String getClientId();

  /**
   * The OAuth2 client secret. Must be set.
   *
   * <p>Once read by the Nessie client, the secret contents will be cleared from memory.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_CLIENT_SECRET
   */
  ByteBuffer getClientSecret();

  /**
   * The OAuth2 username. Only relevant for {@link GrantType#PASSWORD} grant type.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_USERNAME
   */
  Optional<String> getUsername();

  /**
   * The OAuth2 password. Only relevant for {@link GrantType#PASSWORD} grant type.
   *
   * <p>Once read by the Nessie client, the password contents will be cleared from memory.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_PASSWORD
   */
  Optional<ByteBuffer> getPassword();

  /**
   * The OAuth2 scope. Optional.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_CLIENT_SCOPES
   */
  Optional<String> getScope();

  /**
   * Whether token exchange is enabled. Defaults to {@code true}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_TOKEN_EXCHANGE_ENABLED
   */
  @Value.Default
  default boolean getTokenExchangeEnabled() {
    return true;
  }

  /**
   * The default access token lifespan. Optional, defaults to {@link
   * NessieConfigConstants#DEFAULT_DEFAULT_ACCESS_TOKEN_LIFESPAN}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_DEFAULT_ACCESS_TOKEN_LIFESPAN
   */
  @Value.Default
  default Duration getDefaultAccessTokenLifespan() {
    return Duration.parse(DEFAULT_DEFAULT_ACCESS_TOKEN_LIFESPAN);
  }

  /**
   * The default refresh token lifespan. Optional, defaults to {@link
   * NessieConfigConstants#DEFAULT_DEFAULT_REFRESH_TOKEN_LIFESPAN}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_DEFAULT_REFRESH_TOKEN_LIFESPAN
   */
  @Value.Default
  default Duration getDefaultRefreshTokenLifespan() {
    return Duration.parse(DEFAULT_DEFAULT_REFRESH_TOKEN_LIFESPAN);
  }

  /**
   * The refresh safety window. A new token will be fetched when the current token's remaining
   * lifespan is less than this value. Optional, defaults to {@link
   * NessieConfigConstants#DEFAULT_REFRESH_SAFETY_WINDOW}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_REFRESH_SAFETY_WINDOW
   */
  @Value.Default
  default Duration getRefreshSafetyWindow() {
    return Duration.parse(DEFAULT_REFRESH_SAFETY_WINDOW);
  }

  /**
   * For how long the OAuth2 client should keep the tokens fresh, if the client is not being
   * actively used. Defaults to {@link
   * NessieConfigConstants#DEFAULT_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT
   */
  @Value.Default
  default Duration getPreemptiveTokenRefreshIdleTimeout() {
    return Duration.parse(DEFAULT_PREEMPTIVE_TOKEN_REFRESH_IDLE_TIMEOUT);
  }

  /**
   * The maximum time a background thread can be idle before it is closed. Only relevant when using
   * the default {@link #getExecutor() executor}. Defaults to {@link
   * NessieConfigConstants#DEFAULT_BACKGROUND_THREAD_IDLE_TIMEOUT}.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_BACKGROUND_THREAD_IDLE_TIMEOUT
   */
  @Value.Default
  default Duration getBackgroundThreadIdleTimeout() {
    return Duration.parse(DEFAULT_BACKGROUND_THREAD_IDLE_TIMEOUT);
  }

  /**
   * How long to wait for an authorization code. Defaults to {@link
   * NessieConfigConstants#DEFAULT_AUTHORIZATION_CODE_FLOW_TIMEOUT}. Only relevant when using the
   * {@link GrantType#AUTHORIZATION_CODE} grant type.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_TIMEOUT
   */
  @Value.Default
  default Duration getAuthorizationCodeFlowTimeout() {
    return Duration.parse(DEFAULT_AUTHORIZATION_CODE_FLOW_TIMEOUT);
  }

  /**
   * The port to use for the local web server that listens for the authorization code. Optional. If
   * not set or set to zero, a random port from the dynamic client port range will be used. Only
   * relevant when using the {@link GrantType#AUTHORIZATION_CODE} grant type.
   *
   * @see NessieConfigConstants#CONF_NESSIE_OAUTH2_AUTHORIZATION_CODE_FLOW_WEB_PORT
   */
  OptionalInt getAuthorizationCodeFlowWebServerPort();

  /**
   * The SSL context to use for HTTPS connections to the authentication provider, if the server uses
   * a self-signed certificate or a certificate signed by a CA that is not in the default trust
   * store of the JVM. Optional; if not set, the default SSL context is used.
   */
  Optional<SSLContext> getSslContext();

  /**
   * The {@link ObjectMapper} to use for JSON serialization and deserialization. Defaults to a
   * vanilla instance.
   */
  @Value.Default
  default ObjectMapper getObjectMapper() {
    return OAuth2ClientConfig.OBJECT_MAPPER;
  }

  /**
   * The executor to use for background tasks such as refreshing tokens. Defaults to a thread pool
   * with daemon threads, and a single thread initially. The pool will grow as needed and can also
   * shrink to zero threads if no activity is detected for {@link
   * #getBackgroundThreadIdleTimeout()}.
   */
  @Value.Default
  default ScheduledExecutorService getExecutor() {
    return new OAuth2TokenRefreshExecutor(getBackgroundThreadIdleTimeout());
  }

  static OAuth2AuthenticatorConfig.Builder builder() {
    return ImmutableOAuth2AuthenticatorConfig.builder();
  }

  interface Builder {

    @CanIgnoreReturnValue
    Builder from(OAuth2AuthenticatorConfig config);

    @CanIgnoreReturnValue
    Builder issuerUrl(URI issuerUrl);

    @CanIgnoreReturnValue
    Builder tokenEndpoint(URI tokenEndpoint);

    @CanIgnoreReturnValue
    Builder authEndpoint(URI authEndpoint);

    @CanIgnoreReturnValue
    Builder grantType(GrantType grantType);

    @CanIgnoreReturnValue
    Builder clientId(String clientId);

    @CanIgnoreReturnValue
    Builder clientSecret(ByteBuffer clientSecret);

    @CanIgnoreReturnValue
    default Builder clientSecret(String clientSecret) {
      return clientSecret(ByteBuffer.wrap(clientSecret.getBytes(StandardCharsets.UTF_8)));
    }

    @CanIgnoreReturnValue
    Builder username(String username);

    @CanIgnoreReturnValue
    Builder password(ByteBuffer password);

    @CanIgnoreReturnValue
    default Builder password(String password) {
      return password(ByteBuffer.wrap(password.getBytes(StandardCharsets.UTF_8)));
    }

    @CanIgnoreReturnValue
    Builder scope(String scope);

    @CanIgnoreReturnValue
    Builder tokenExchangeEnabled(boolean tokenExchangeEnabled);

    @CanIgnoreReturnValue
    Builder defaultAccessTokenLifespan(Duration defaultAccessTokenLifespan);

    @CanIgnoreReturnValue
    Builder defaultRefreshTokenLifespan(Duration defaultRefreshTokenLifespan);

    @CanIgnoreReturnValue
    Builder refreshSafetyWindow(Duration refreshSafetyWindow);

    @CanIgnoreReturnValue
    Builder preemptiveTokenRefreshIdleTimeout(Duration preemptiveTokenRefreshIdleTimeout);

    @CanIgnoreReturnValue
    Builder backgroundThreadIdleTimeout(Duration backgroundThreadIdleTimeout);

    @CanIgnoreReturnValue
    Builder authorizationCodeFlowTimeout(Duration authorizationCodeFlowTimeout);

    @CanIgnoreReturnValue
    Builder authorizationCodeFlowWebServerPort(int authorizationCodeFlowWebServerPort);

    @CanIgnoreReturnValue
    Builder sslContext(SSLContext sslContext);

    @CanIgnoreReturnValue
    Builder objectMapper(ObjectMapper objectMapper);

    @CanIgnoreReturnValue
    Builder executor(ScheduledExecutorService executor);

    OAuth2AuthenticatorConfig build();
  }
}
