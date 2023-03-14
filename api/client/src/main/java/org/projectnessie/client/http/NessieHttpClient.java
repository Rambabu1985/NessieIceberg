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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import org.projectnessie.api.v1.http.HttpConfigApi;
import org.projectnessie.api.v1.http.HttpContentApi;
import org.projectnessie.api.v1.http.HttpDiffApi;
import org.projectnessie.api.v1.http.HttpNamespaceApi;
import org.projectnessie.api.v1.http.HttpRefLogApi;
import org.projectnessie.api.v1.http.HttpTreeApi;
import org.projectnessie.client.rest.NessieHttpResponseFilter;
import org.projectnessie.error.BaseNessieClientServerException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class NessieHttpClient extends NessieApiClient {

  private static final Logger LOGGER = LoggerFactory.getLogger(NessieHttpClient.class);

  private static final ObjectMapper MAPPER =
      new ObjectMapper()
          .enable(SerializationFeature.INDENT_OUTPUT)
          .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

  /**
   * Create new HTTP Nessie client. All REST api endpoints are mapped here. This client should
   * support any JAX-RS implementation
   *
   * @param authentication authenticator to use
   * @param enableTracing whether to enable tracing
   * @param clientBuilder the client-builder to use
   */
  NessieHttpClient(
      HttpAuthentication authentication, boolean enableTracing, HttpClient.Builder clientBuilder) {
    this(buildClient(authentication, enableTracing, clientBuilder));
  }

  static HttpClient buildClient(
      HttpAuthentication authentication, boolean enableTracing, HttpClient.Builder clientBuilder) {
    clientBuilder.setObjectMapper(MAPPER);
    if (enableTracing) {
      addTracing(clientBuilder);
    }
    if (authentication != null) {
      authentication.applyToHttpClient(clientBuilder);
    }
    clientBuilder.addResponseFilter(new NessieHttpResponseFilter(MAPPER));
    return clientBuilder.build();
  }

  private NessieHttpClient(HttpClient client) {
    super(
        wrap(HttpConfigApi.class, new HttpConfigClient(client)),
        wrap(HttpTreeApi.class, new HttpTreeClient(client)),
        wrap(HttpContentApi.class, new HttpContentClient(client)),
        wrap(HttpDiffApi.class, new HttpDiffClient(client)),
        wrap(HttpRefLogApi.class, new HttpRefLogClient(client)),
        wrap(HttpNamespaceApi.class, new HttpNamespaceClient(client)));
  }

  private static void addTracing(HttpClient.Builder httpClient) {
    try {
      OpentelemetryTracing.addTracing(httpClient);
    } catch (NoClassDefFoundError e) {
      LOGGER.warn(
          "Failed to initialize tracing, the opentracing libraries are probably missing.", e);
    }
  }

  @SuppressWarnings("unchecked")
  private static <T> T wrap(Class<T> iface, T delegate) {
    return (T)
        Proxy.newProxyInstance(
            delegate.getClass().getClassLoader(),
            new Class[] {iface},
            new ExceptionRewriter(delegate));
  }

  /**
   * This will rewrite exceptions, so they are correctly thrown by the api classes. (since the
   * filter will cause them to be wrapped in {@link javax.ws.rs.client.ResponseProcessingException})
   */
  private static class ExceptionRewriter implements InvocationHandler {

    private final Object delegate;

    public ExceptionRewriter(Object delegate) {
      this.delegate = delegate;
    }

    @Override
    public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
      try {
        return method.invoke(delegate, args);
      } catch (InvocationTargetException ex) {
        Throwable targetException = ex.getTargetException();
        if (targetException instanceof HttpClientException) {
          Throwable cause = targetException.getCause();
          if (cause instanceof BaseNessieClientServerException) {
            throw cause;
          }
        }

        if (targetException instanceof RuntimeException) {
          throw targetException;
        }

        throw ex;
      }
    }
  }
}
