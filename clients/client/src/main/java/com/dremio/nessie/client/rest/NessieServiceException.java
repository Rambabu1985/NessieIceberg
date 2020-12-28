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
package com.dremio.nessie.client.rest;

import com.dremio.nessie.error.NessieError;
import javax.ws.rs.client.ResponseProcessingException;
import javax.ws.rs.core.Response;

/** A Nessie REST API runtime exception. */
public class NessieServiceException extends ResponseProcessingException {

  private final NessieError error;

  public NessieServiceException(NessieError error) {
    super(Response.status(error.getStatus()).build(), error.getFullMessage());
    this.error = error;
  }

  public NessieError getError() {
    return error;
  }
}
