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
package org.projectnessie.client.rest;

import org.projectnessie.client.http.HttpClientException;
import org.projectnessie.client.http.ResponseContext;
import org.projectnessie.client.http.ResponseFilter;

public class NessieHttpResponseFilter implements ResponseFilter {

  public NessieHttpResponseFilter() {}

  @Override
  public void filter(ResponseContext con) {
    try {
      ResponseCheckFilter.checkResponse(con);
    } catch (RuntimeException e) {
      throw e; // re-throw RuntimeExceptions unchanged
    } catch (Exception e) {
      throw new HttpClientException(e); // pass up invalid response exception as untyped exception
    }
  }
}
