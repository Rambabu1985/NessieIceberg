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
package org.projectnessie.client.http.v2api;

import org.projectnessie.api.v2.params.DiffParams;
import org.projectnessie.client.builder.BaseGetDiffBuilder;
import org.projectnessie.client.http.HttpClient;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.DiffResponse;
import org.projectnessie.model.Reference;

final class HttpGetDiff extends BaseGetDiffBuilder<DiffParams> {
  private final HttpClient client;

  HttpGetDiff(HttpClient client) {
    super(DiffParams::forNextPage);
    this.client = client;
  }

  @Override
  protected DiffParams params() {
    return DiffParams.builder()
        .fromRef(Reference.toPathString(fromRefName, fromHashOnRef))
        .toRef(Reference.toPathString(toRefName, toHashOnRef))
        .maxRecords(maxRecords)
        .build();
  }

  @Override
  public DiffResponse get(DiffParams params) throws NessieNotFoundException {
    return client
        .newRequest()
        .path("trees/{from}/diff/{to}")
        .resolveTemplate("from", params.getFromRef())
        .resolveTemplate("to", params.getToRef())
        .queryParam("max-records", params.maxRecords())
        .queryParam("page-token", params.pageToken())
        .unwrap(NessieNotFoundException.class)
        .get()
        .readEntity(DiffResponse.class);
  }
}
