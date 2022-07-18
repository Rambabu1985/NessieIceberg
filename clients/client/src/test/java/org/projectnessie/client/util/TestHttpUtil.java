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
package org.projectnessie.client.util;

import com.sun.net.httpserver.HttpExchange;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class TestHttpUtil {

  private TestHttpUtil() {}

  public static void writeResponseBody(HttpExchange http, String response) throws IOException {
    writeResponseBody(http, response, "application/json");
  }

  public static void writeResponseBody(HttpExchange http, String response, String contentType)
      throws IOException {
    http.getResponseHeaders().add("Content-Type", contentType);
    byte[] body = response.getBytes(StandardCharsets.UTF_8);
    http.sendResponseHeaders(200, body.length);
    try (OutputStream os = http.getResponseBody()) {
      os.write(body);
    }
  }
}
