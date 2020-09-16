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

package com.dremio.nessie.client;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import com.dremio.nessie.client.rest.NessieBadRequestException;
import com.dremio.nessie.client.rest.NessieConflictException;
import com.dremio.nessie.client.rest.NessieForbiddenException;
import com.dremio.nessie.client.rest.NessieInternalServerException;
import com.dremio.nessie.client.rest.NessieNotAuthorizedException;
import com.dremio.nessie.client.rest.NessieNotFoundException;
import com.dremio.nessie.client.rest.NessiePreconditionFailedException;
import com.dremio.nessie.error.ImmutableNessieError;
import com.dremio.nessie.error.NessieError;
import com.dremio.nessie.json.ObjectMapperBuilder;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * common REST utils.
 */
public final class RestUtils {

  private static final ObjectMapper OBJECT_MAPPER = ObjectMapperBuilder.createObjectMapper();

  private RestUtils() {

  }

  /**
   * check that response had a valid return code. Throw exception if not.
   */
  public static void checkResponse(Response response) {
    Status status = Status.fromStatusCode(response.getStatus());
    if (status == Status.OK || status == Status.CREATED) {
      return;
    }
    NessieError error = readException(status, response);
    switch (status) {
      case BAD_REQUEST:
        throw new NessieBadRequestException(response, error);
      case UNAUTHORIZED:
        throw new NessieNotAuthorizedException(response, error);
      case FORBIDDEN:
        throw new NessieForbiddenException(response, error);
      case NOT_FOUND:
        throw new NessieNotFoundException(response, error);
      case CONFLICT:
        throw new NessieConflictException(response, error);
      case PRECONDITION_FAILED:
        throw new NessiePreconditionFailedException(response, error);
      case INTERNAL_SERVER_ERROR:
        throw new NessieInternalServerException(response, error);
      default:
        try {
          String msg = OBJECT_MAPPER.writeValueAsString(error);
          throw new RuntimeException(
            "Unknown exception " + response.getStatus() + " with message " + msg);
        } catch (JsonProcessingException e) {
          throw new RuntimeException("Unknown exception " + response.getStatus(), e);
        }
    }
  }

  private static NessieError readException(Status status, Response response) {
    String msg = response.readEntity(String.class);
    NessieError error;
    try {
      error = OBJECT_MAPPER.readValue(msg, NessieError.class);
    } catch (Exception ex) {
      error = ImmutableNessieError.builder()
                                  .errorCode(status.getStatusCode())
                                  .errorMessage(msg)
                                  .statusMessage(status.getReasonPhrase())
                                  .build();
    }
    return error;
  }

}
