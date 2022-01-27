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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.quarkus.test.security.TestSecurity;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.projectnessie.client.api.CommitMultipleOperationsBuilder;
import org.projectnessie.client.rest.NessieForbiddenException;
import org.projectnessie.error.BaseNessieClientServerException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse.Entry;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.LogResponse.LogEntry;
import org.projectnessie.model.Operation.Delete;
import org.projectnessie.model.Operation.Put;
import org.projectnessie.model.RefLogResponse;
import org.projectnessie.model.Reference;
import org.projectnessie.server.authz.NessieAuthorizationTestProfile;

@QuarkusTest
@TestProfile(value = NessieAuthorizationTestProfile.class)
class TestAuthorizationRules extends BaseClientAuthTest {

  @ParameterizedTest
  @ValueSource(booleans = {true, false})
  @TestSecurity(user = "test_user")
  void testAllOpsWithTestUser(boolean shouldFail) throws BaseNessieClientServerException {
    testAllOps("allowedBranchForTestUser", "test_user", shouldFail);
  }

  @Test
  @TestSecurity(user = "admin_user")
  void testAdminUserIsAllowedEverything() throws BaseNessieClientServerException {
    testAllOps("testAdminUserIsAllowedAllBranch", "admin_user", false);
  }

  private void testAllOps(String branchName, String role, boolean shouldFail)
      throws BaseNessieClientServerException {
    ContentKey key = ContentKey.of("allowed", "x");
    if (shouldFail) {
      branchName = "disallowedBranchForTestUser";
      key = ContentKey.of("disallowed", "x");
    }

    createBranch(Branch.of(branchName, null), role, shouldFail);

    Branch branchWithInvalidHash = Branch.of(branchName, "1234567890123456");
    Branch branch =
        shouldFail ? branchWithInvalidHash : retrieveBranch(branchName, role, shouldFail);

    listAllReferences(branchName, shouldFail);

    String cid = "cid-foo-" + UUID.randomUUID();
    addContent(branch, Put.of(key, IcebergTable.of("foo", 42, 42, 42, 42, cid)), role, shouldFail);

    if (!shouldFail) {
      // These requests cannot succeed, because "disallowedBranchForTestUser" could not be created
      getCommitLog(branchName, role, shouldFail);
      getEntriesFor(branchName, role, shouldFail);
      readContent(branchName, key, role, shouldFail);
    }

    branch = shouldFail ? branchWithInvalidHash : retrieveBranch(branchName, role, shouldFail);

    deleteContent(branch, Delete.of(key), role, shouldFail);

    branch = shouldFail ? branchWithInvalidHash : retrieveBranch(branchName, role, shouldFail);
    deleteBranch(branch, role, shouldFail);

    getRefLog(role, !role.equals("admin_user"));
  }

  @Test
  // test_user2 has all permissions on a Branch, but not permissions on a Key
  @TestSecurity(user = "test_user2")
  void testCanCommitButNotUpdateOrDeleteEntity() throws BaseNessieClientServerException {
    String role = "test_user2";
    ContentKey key = ContentKey.of("allowed", "some");
    String branchName = "allowedBranchForTestUser2";
    createBranch(Branch.of(branchName, null), role, false);

    listAllReferences(branchName, false);

    final Branch branch = retrieveBranch(branchName, role, false);

    assertThatThrownBy(
            () ->
                api()
                    .commitMultipleOperations()
                    .branch(branch)
                    .commitMeta(CommitMeta.fromMessage("add stuff"))
                    .operation(Put.of(key, IcebergTable.of("foo", 42, 42, 42, 42, "cid-foo")))
                    .commit())
        .isInstanceOf(NessieForbiddenException.class)
        .hasMessageContaining(
            String.format(
                "'UPDATE_ENTITY' is not allowed for role '%s' on content '%s'",
                role, key.toPathString()));

    readContent(branchName, key, role, true);

    final Branch b = retrieveBranch(branchName, role, false);

    assertThatThrownBy(
            () ->
                api()
                    .commitMultipleOperations()
                    .branch(b)
                    .commitMeta(CommitMeta.fromMessage("delete stuff"))
                    .operation(Delete.of(key))
                    .commit())
        .isInstanceOf(NessieForbiddenException.class)
        .hasMessageContaining(
            String.format(
                "'DELETE_ENTITY' is not allowed for role '%s' on content '%s'",
                role, key.toPathString()));

    deleteBranch(branch, role, false);
  }

  @Test
  @TestSecurity(user = "admin_user")
  void testCanReadTargetBranchDuringAssign() throws BaseNessieClientServerException {
    String role = "admin_user";
    String branchName = "adminCanReadWhenAssigning";
    String targetBranchName = "targetBranchForAssign";
    createBranch(Branch.of(branchName, null), role, false);
    Branch branch = retrieveBranch(branchName, role, false);

    createBranch(Branch.of(targetBranchName, null), role, false);
    Branch targetBranch = retrieveBranch(targetBranchName, role, false);

    addContent(
        targetBranch,
        Put.of(
            ContentKey.of("allowed", "x"),
            IcebergTable.of("foo", 42, 42, 42, 42, UUID.randomUUID().toString())),
        role,
        false);
    targetBranch = retrieveBranch(targetBranchName, role, false);

    api().assignBranch().branch(branch).assignTo(targetBranch).assign();
    branch = retrieveBranch(branchName, role, false);
    assertThat(branch.getHash()).isEqualTo(targetBranch.getHash());
  }

  @Test
  @TestSecurity(user = "admin_user")
  void testCanReadTargetBranchDuringMerge() throws BaseNessieClientServerException {
    String role = "admin_user";
    String branchName = "adminCanReadWhenMerging";
    String targetBranchName = "targetBranchForMerge";
    createBranch(Branch.of(branchName, null), role, false);
    Branch branch = retrieveBranch(branchName, role, false);

    createBranch(Branch.of(targetBranchName, null), role, false);
    Branch targetBranch = retrieveBranch(targetBranchName, role, false);

    addContent(
        branch,
        Put.of(
            ContentKey.of("allowed", "x"),
            IcebergTable.of("foo", 42, 42, 42, 42, UUID.randomUUID().toString())),
        role,
        false);
    branch = retrieveBranch(branchName, role, false);

    api().mergeRefIntoBranch().fromRef(branch).branch(targetBranch).merge();

    targetBranch = retrieveBranch(targetBranch.getName(), role, false);

    assertThat(api().getCommitLog().reference(targetBranch).get().getLogEntries()).isNotEmpty();
  }

  @Test
  @TestSecurity(user = "admin_user")
  void testCanReadTargetBranchDuringTransplant() throws BaseNessieClientServerException {
    String role = "admin_user";
    String branchName = "adminCanReadWhenTransplanting";
    String targetBranchName = "targetBranchForTransplant";
    createBranch(Branch.of(branchName, null), role, false);
    Branch branch = retrieveBranch(branchName, role, false);

    createBranch(Branch.of(targetBranchName, null), role, false);
    Branch targetBranch = retrieveBranch(targetBranchName, role, false);

    addContent(
        branch,
        Put.of(
            ContentKey.of("allowed", "x"),
            IcebergTable.of("foo", 42, 42, 42, 42, UUID.randomUUID().toString())),
        role,
        false);
    branch = retrieveBranch(branchName, role, false);

    api()
        .transplantCommitsIntoBranch()
        .fromRefName(branch.getName())
        .hashesToTransplant(
            api().getCommitLog().reference(branch).get().getLogEntries().stream()
                .map(e -> e.getCommitMeta().getHash())
                .collect(Collectors.toList()))
        .branch(targetBranch)
        .transplant();

    targetBranch = retrieveBranch(targetBranch.getName(), role, false);

    assertThat(api().getCommitLog().reference(targetBranch).get().getLogEntries()).isNotEmpty();
  }

  @Test
  @TestSecurity(user = "user1")
  void testCannotReadTargetBranch() throws BaseNessieClientServerException {
    String role = "user1";
    String branchName = "allowedBranchForUser1";
    createBranch(Branch.of(branchName, null), role, false);
    String disallowedBranch = "disallowedBranchForUser1";
    createBranch(Branch.of(disallowedBranch, null), role, false);

    final Branch branch = retrieveBranch(branchName, role, false);

    String errorMessage =
        String.format(
            "'VIEW_REFERENCE' is not allowed for role '%s' on reference '%s'",
            role, disallowedBranch);
    assertThatThrownBy(
            () ->
                api()
                    .assignBranch()
                    .branch(branch)
                    .assignTo(Branch.of(disallowedBranch, branch.getHash()))
                    .assign())
        .isInstanceOf(NessieForbiddenException.class)
        .hasMessageContaining(errorMessage);

    assertThatThrownBy(
            () ->
                api()
                    .mergeRefIntoBranch()
                    .fromRef(branch)
                    .branch((Branch.of(disallowedBranch, branch.getHash())))
                    .merge())
        .isInstanceOf(NessieForbiddenException.class)
        .hasMessageContaining(errorMessage);

    assertThatThrownBy(
            () ->
                api()
                    .transplantCommitsIntoBranch()
                    .hashesToTransplant(Arrays.asList(branch.getHash()))
                    .fromRefName(branch.getName())
                    .branch((Branch.of(disallowedBranch, branch.getHash())))
                    .transplant())
        .isInstanceOf(NessieForbiddenException.class)
        .hasMessageContaining(errorMessage);

    deleteBranch(branch, role, false);
  }

  private void listAllReferences(String branchName, boolean filteredOut) {
    if (filteredOut) {
      assertThat(api().getAllReferences().get().getReferences())
          .extracting(Reference::getName)
          .doesNotContain(branchName);
    } else {
      assertThat(api().getAllReferences().get().getReferences())
          .extracting(Reference::getName)
          .contains(branchName);
    }
  }

  private Branch retrieveBranch(String branchName, String role, boolean shouldFail)
      throws NessieNotFoundException {
    if (shouldFail) {
      assertThatThrownBy(() -> api().getReference().refName(branchName).get())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'VIEW_REFERENCE' is not allowed for role '%s' on reference '%s'",
                  role, branchName));
      return null;
    } else {
      return (Branch) api().getReference().refName(branchName).get();
    }
  }

  private void createBranch(Branch branch, String role, boolean shouldFail)
      throws BaseNessieClientServerException {
    if (shouldFail) {
      assertThatThrownBy(
              () -> api().createReference().sourceRefName("main").reference(branch).create())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'CREATE_REFERENCE' is not allowed for role '%s' on reference '%s'",
                  role, branch.getName()));
    } else {
      api().createReference().sourceRefName("main").reference(branch).create();
    }
  }

  private void deleteBranch(Branch branch, String role, boolean shouldFail)
      throws BaseNessieClientServerException {
    if (shouldFail) {
      assertThatThrownBy(() -> api().deleteBranch().branch(branch).delete())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'DELETE_REFERENCE' is not allowed for role '%s' on reference '%s'",
                  role, branch.getName()));
    } else {
      api().deleteBranch().branch(branch).delete();
    }
  }

  private void readContent(String branchName, ContentKey key, String role, boolean shouldFail)
      throws NessieNotFoundException {
    if (shouldFail) {
      assertThatThrownBy(
              () ->
                  api()
                      .getContent()
                      .refName(branchName)
                      .key(key)
                      .get()
                      .get(key)
                      .unwrap(IcebergTable.class)
                      .get())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'READ_ENTITY_VALUE' is not allowed for role '%s' on content '%s'",
                  role, key.toPathString()));
    } else {
      assertThat(
              api()
                  .getContent()
                  .refName(branchName)
                  .key(key)
                  .get()
                  .get(key)
                  .unwrap(IcebergTable.class)
                  .get())
          .isNotNull()
          .isInstanceOf(IcebergTable.class);
    }
  }

  private void getEntriesFor(String branchName, String role, boolean shouldFail)
      throws NessieNotFoundException {
    if (shouldFail) {
      assertThatThrownBy(() -> api().getEntries().refName(branchName).get().getEntries())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format("'READ_ENTRIES' is not allowed for role '%s' on reference", role));
    } else {
      List<Entry> tables = api().getEntries().refName(branchName).get().getEntries();
      assertThat(tables).isNotEmpty();
    }
  }

  private void getCommitLog(String branchName, String role, boolean shouldFail)
      throws NessieNotFoundException {
    if (shouldFail) {
      assertThatThrownBy(() -> api().getCommitLog().refName(branchName).get().getLogEntries())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format("'LIST_COMMIT_LOG' is not allowed for role '%s' on reference", role));
    } else {
      List<LogEntry> commits = api().getCommitLog().refName(branchName).get().getLogEntries();
      assertThat(commits).isNotEmpty();
    }
  }

  private void getRefLog(String role, boolean shouldFail) throws NessieNotFoundException {
    if (shouldFail) {
      assertThatThrownBy(() -> api().getRefLog().get().getLogEntries())
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(String.format("'VIEW_REFLOG' is not allowed for role '%s'", role));
    } else {
      List<RefLogResponse.RefLogResponseEntry> entries = api().getRefLog().get().getLogEntries();
      assertThat(entries).isNotEmpty();
    }
  }

  private void addContent(Branch branch, Put put, String role, boolean shouldFail)
      throws BaseNessieClientServerException {

    CommitMultipleOperationsBuilder commitOp =
        api()
            .commitMultipleOperations()
            .branch(branch)
            .operation(put)
            .commitMeta(CommitMeta.fromMessage("add stuff"));

    if (shouldFail) {
      // adding content requires COMMIT_CHANGE_AGAINST_REFERENCE & UPDATE_ENTITY, but this is
      // difficult to test here, so we're testing this in a separate method
      assertThatThrownBy(commitOp::commit)
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'COMMIT_CHANGE_AGAINST_REFERENCE' is not allowed for role '%s' on reference '%s'",
                  role, branch.getName()));
    } else {
      commitOp.commit();
    }
  }

  private void deleteContent(Branch branch, Delete delete, String role, boolean shouldFail)
      throws BaseNessieClientServerException {

    CommitMultipleOperationsBuilder commitOp =
        api()
            .commitMultipleOperations()
            .branch(branch)
            .operation(delete)
            .commitMeta(CommitMeta.fromMessage("delete stuff"));

    if (shouldFail) {
      // deleting content requires COMMIT_CHANGE_AGAINST_REFERENCE & DELETE_ENTITY, but this is
      // difficult to test here, so we're testing this in a separate method
      assertThatThrownBy(commitOp::commit)
          .isInstanceOf(NessieForbiddenException.class)
          .hasMessageContaining(
              String.format(
                  "'COMMIT_CHANGE_AGAINST_REFERENCE' is not allowed for role '%s' on reference '%s'",
                  role, branch.getName()));
    } else {
      commitOp.commit();
    }
  }
}
