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
package org.projectnessie.client.http.v1api;

import org.projectnessie.client.builder.BaseAssignBranchBuilder;
import org.projectnessie.client.http.NessieApiClient;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.Reference;

final class HttpAssignBranch extends BaseAssignBranchBuilder {

  private final NessieApiClient client;

  HttpAssignBranch(NessieApiClient client) {
    this.client = client;
  }

  @Override
  public void assign() throws NessieNotFoundException, NessieConflictException {
    client.getTreeApi().assignReference(Reference.ReferenceType.BRANCH, branchName, hash, assignTo);
  }

  @Override
  public Branch assignAndGet() {
    throw new UnsupportedOperationException(
        "The assignAndGet operation is not supported for branches in API v1");
  }
}
