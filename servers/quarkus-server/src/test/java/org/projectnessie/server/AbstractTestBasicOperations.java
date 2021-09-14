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
package org.projectnessie.server;

import static org.junit.jupiter.api.Assertions.assertThrows;

import io.quarkus.test.security.TestSecurity;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import org.projectnessie.api.ContentsApi;
import org.projectnessie.api.TreeApi;
import org.projectnessie.api.params.EntriesParams;
import org.projectnessie.client.NessieClient;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentsKey;
import org.projectnessie.model.EntriesResponse.Entry;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.ImmutableBranch;
import org.projectnessie.model.ImmutableDelete;
import org.projectnessie.model.ImmutableOperations;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.Reference;

class AbstractTestBasicOperations {

  private NessieClient client;
  private TreeApi tree;
  private ContentsApi contents;

  @AfterEach
  void closeClient() {
    if (client != null) {
      client.close();
      client = null;
    }
  }

  void getCatalog(String branch) throws NessieNotFoundException, NessieConflictException {
    client = NessieClient.builder().withUri("http://localhost:19121/api/v1").build();
    tree = client.getTreeApi();
    contents = client.getContentsApi();
    if (branch != null) {
      tree.createReference("main", Branch.of(branch, null));
    }
  }

  void tryEndpointPass(Executable runnable) {
    Assertions.assertDoesNotThrow(runnable);
  }

  @Test
  @TestSecurity(
      user = "admin_user",
      roles = {"admin", "user"})
  void testAdmin() throws NessieNotFoundException, NessieConflictException {
    getCatalog("testx");
    Branch branch = (Branch) tree.getReferenceByName("testx");
    List<Entry> tables = tree.getEntries("testx", EntriesParams.empty()).getEntries();
    Assertions.assertTrue(tables.isEmpty());
    ContentsKey key = ContentsKey.of("x", "x");
    tryEndpointPass(
        () ->
            tree.commitMultipleOperations(
                branch.getName(),
                branch.getHash(),
                ImmutableOperations.builder()
                    .addOperations(Put.of(key, IcebergTable.of("foo", -1L)))
                    .commitMeta(CommitMeta.fromMessage("empty message"))
                    .build()));

    Assertions.assertTrue(
        contents.getContents(key, "testx", null).unwrap(IcebergTable.class).isPresent());

    Branch master = (Branch) tree.getReferenceByName("testx");
    Branch test = ImmutableBranch.builder().hash(master.getHash()).name("testy").build();
    tryEndpointPass(
        () -> tree.createReference(master.getName(), Branch.of(test.getName(), test.getHash())));
    Branch test2 = (Branch) tree.getReferenceByName("testy");
    tryEndpointPass(() -> tree.deleteBranch(test2.getName(), test2.getHash()));
    tryEndpointPass(
        () ->
            tree.commitMultipleOperations(
                master.getName(),
                master.getHash(),
                ImmutableOperations.builder()
                    .addOperations(ImmutableDelete.builder().key(key).build())
                    .commitMeta(CommitMeta.fromMessage(""))
                    .build()));
    assertThrows(NessieNotFoundException.class, () -> contents.getContents(key, "testx", null));
    tryEndpointPass(
        () -> {
          Reference b = tree.getReferenceByName(branch.getName());
          // Note: the initial version-store implementations just committed this operation, but it
          // should actually fail, because the operations of the 1st commit above and this commit
          // have conflicts.
          tree.commitMultipleOperations(
              b.getName(),
              b.getHash(),
              ImmutableOperations.builder()
                  .addOperations(Put.of(key, IcebergTable.of("bar", -1L)))
                  .commitMeta(CommitMeta.fromMessage(""))
                  .build());
        });
  }

  @Test
  @TestSecurity(authorizationEnabled = false)
  void testUserCleanup() throws NessieNotFoundException, NessieConflictException {
    getCatalog(null);
    Reference r = client.getTreeApi().getReferenceByName("testx");
    client.getTreeApi().deleteBranch(r.getName(), r.getHash());
  }
}
