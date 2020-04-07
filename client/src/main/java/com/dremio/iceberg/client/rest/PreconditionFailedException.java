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

package com.dremio.iceberg.client.rest;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

public class PreconditionFailedException extends ExtendedClientErrorException {

  public PreconditionFailedException(Response response) {
    super(validate(response, Status.PRECONDITION_FAILED));
  }
}
