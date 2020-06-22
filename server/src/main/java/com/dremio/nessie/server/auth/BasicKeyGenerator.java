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

package com.dremio.nessie.server.auth;

import com.dremio.nessie.jwt.KeyGenerator;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import java.security.Key;

/**
 * key generator for testing purposes only.
 */
public class BasicKeyGenerator implements KeyGenerator {
  private static final Key KEY = Keys.secretKeyFor(SignatureAlgorithm.HS512);

  @Override
  public Key generateKey() {
    return KEY;
  }
}
