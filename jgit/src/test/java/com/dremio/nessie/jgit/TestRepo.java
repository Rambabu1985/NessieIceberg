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

package com.dremio.nessie.jgit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertLinesMatch;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.Repository;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.dremio.nessie.backend.LogMessage;
import com.dremio.nessie.backend.simple.InMemory;
import com.dremio.nessie.error.NessieConflictException;
import com.dremio.nessie.model.Branch;
import com.dremio.nessie.model.CommitMeta;
import com.dremio.nessie.model.CommitMeta.Action;
import com.dremio.nessie.model.ImmutableCommitMeta;
import com.dremio.nessie.model.ImmutableTable;
import com.dremio.nessie.model.ImmutableTableMeta;
import com.dremio.nessie.model.Table;
import com.dremio.nessie.model.TableMeta;
import com.google.common.collect.Lists;

class TestRepo {

  private static InMemory backend;
  private static TableTableConverter tableConverter = new TableTableConverter();
  private Repository repository;

  @TempDir
  File jgitDir;

  enum RepoType {
    DYNAMO,
    INMEMORY,
    FILE
  }

  @BeforeAll
  public static void init() {
    backend = new InMemory();
  }

  private JgitBranchControllerLegacy controller(RepoType repoType) throws IOException {
    return controller(repoType, false);
  }

  private JgitBranchControllerLegacy controller(RepoType repoType, boolean reuse) throws IOException {
    final JgitBranchControllerLegacy controller;
    switch (repoType) {
      case DYNAMO:
        controller = new JgitBranchControllerLegacy(backend);
        break;
      case INMEMORY:
        try {
          repository = reuse ? repository :
            new InMemoryRepository.Builder().setRepositoryDescription(new DfsRepositoryDescription()).build();
          controller = new JgitBranchControllerLegacy(repository);
          break;
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      case FILE:
        try {
          repository = Git.init().setDirectory(jgitDir).call().getRepository();
          controller = new JgitBranchControllerLegacy(repository);
          break;
        } catch (GitAPIException e) {
          throw new RuntimeException(e);
        }
      default:
        throw new RuntimeException("Can't reach here");
    }
    try {
      controller.create("master", null, commitMeta("master", "", Action.CREATE_BRANCH, 1), tableConverter);
    } catch (NessieConflictException e) {
      //pass already has a master
    }
    return controller;
  }


  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void test(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.getBranch("master");
    assertEquals("master", branch.getName());
    List<Branch> branches = controller.getBranches();
    assertEquals(1, branches.size());
    assertEquals(branch.getId(), branches.get(0).getId());
    assertEquals(branch.getName(), branches.get(0).getName());
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("master",
                                      commitMeta("master", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    assertNotEquals(branch.getId(), commit);
    branch = controller.getBranch("master");
    assertEquals(branch.getId(), commit);
    tables = controller.getTables("master", null, tableConverter);
    assertEquals(1, tables.size());
    tables = controller.getTables("master", "db", tableConverter);
    assertEquals(1, tables.size());
    Table newTable = controller.getTable("master", "db.table", false);
    assertEquals(tables.get(0), newTable.getId());
    assertEquals(table.getId(), newTable.getId());
    assertEquals(table.getNamespace(), newTable.getNamespace());
    assertEquals(table.getName(), newTable.getName());
    Table table1 = ImmutableTable.builder()
                                 .id("dbx.table")
                                 .namespace("dbx")
                                 .name("table")
                                 .metadataLocation("")
                                 .build();
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 1),
                      branch.getId(),
                      tableConverter,
                      table1);
    tables = controller.getTables("master", null, tableConverter);
    assertEquals(2, tables.size());
    tables = controller.getTables("master", "db", tableConverter);
    assertEquals(1, tables.size());
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 2),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table1).withIsDeleted(true),
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
  }

  @SuppressWarnings("VariableDeclarationUsageDistance")
  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testMerge(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.create("test",
                                      "master",
                                      commitMeta("test", "", Action.CREATE_BRANCH, 1), tableConverter);
    List<Branch> branches = controller.getBranches();
    assertEquals(2, branches.size());
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("test",
                                      commitMeta("test", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    assertTrue(controller.getTables("master", null, tableConverter).isEmpty());
    assertEquals(1, controller.getTables("test", null, tableConverter).size());
    Table newTable = controller.getTable("test", "db.table", false);
    assertEquals(table.getId(), newTable.getId());
    assertEquals(table.getNamespace(), newTable.getNamespace());
    assertEquals(table.getName(), newTable.getName());
    String finalCommit = commit;
    assertThrows(NessieConflictException.class,
        () -> controller.promote("master",
                                 "test",
                                 finalCommit,
                                 commitMeta("master", "", Action.MERGE, 1),
                                 false,
                                 false,
                                 null, tableConverter));
    commit = controller.getBranch("master").getId();
    commit = controller.promote("master",
                                "test",
                                commit,
                                commitMeta("master", "", Action.MERGE, 1),
                                false,
                                false,
                                null, tableConverter);
    assertEquals(1, controller.getTables("master", null, tableConverter).size());
    newTable = controller.getTable("master", "db.table", false);
    assertEquals(table.getId(), newTable.getId());
    assertEquals(table.getNamespace(), newTable.getNamespace());
    assertEquals(table.getName(), newTable.getName());
    controller.deleteBranch("test", commit, commitMetaDelete("test"));
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 2),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    branches = controller.getBranches();
    assertEquals(1, branches.size());
  }

  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testForceMerge(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.create("test",
                                      "master",
                                      commitMeta("test", "", Action.CREATE_BRANCH, 1), tableConverter);
    List<Branch> branches = controller.getBranches();
    assertEquals(2, branches.size());
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("test",
                                      commitMeta("test", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    Table table1 = ImmutableTable.builder()
                                 .id("dbx.table")
                                 .namespace("dbx")
                                 .name("table")
                                 .metadataLocation("")
                                 .build();
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 1),
                      branch.getId(),
                      tableConverter,
                      table1);
    assertEquals(1, controller.getTables("master", null, tableConverter).size());
    assertEquals(1, controller.getTables("test", null, tableConverter).size());
    String finalCommit = commit;
    assertThrows(NessieConflictException.class, () -> controller.promote("master",
                                                                         "test",
                                                                         finalCommit,
                                                                         commitMeta("master",
                                                                                    "",
                                                                                    Action.MERGE,
                                                                                    1),
                                                                         false,
                                                                         false,
                                                                         null, tableConverter));
    commit = controller.getBranch("master").getId();
    commit = controller.promote("master",
                                "test",
                                commit,
                                commitMeta("master", "", Action.MERGE, 1),
                                true,
                                false,
                                null, tableConverter);
    assertEquals(controller.getTables("master", null, tableConverter),
                 controller.getTables("test", null, tableConverter));
    controller.deleteBranch("test", commit, commitMetaDelete("test"));
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 2),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    branches = controller.getBranches();
    assertEquals(1, branches.size());
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
  }

  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testCherryPick(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.create("test",
                                      "master",
                                      commitMeta("test", "", Action.CREATE_BRANCH, 1), tableConverter);
    List<Branch> branches = controller.getBranches();
    assertEquals(2, branches.size());
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("test",
                                      commitMeta("test", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    Table table1 = ImmutableTable.builder()
                                 .id("dbx.table")
                                 .namespace("dbx")
                                 .name("table")
                                 .metadataLocation("")
                                 .build();
    commit = controller.commit("master",
                               commitMeta("master", "", Action.COMMIT, 1),
                               branch.getId(),
                               tableConverter,
                               table1);
    assertEquals(1, controller.getTables("master", null, tableConverter).size());
    assertEquals(1, controller.getTables("test", null, tableConverter).size());
    commit = controller.promote("master",
                                "test",
                                commit,
                                commitMeta("master", "", Action.MERGE, 1),
                                false,
                                true,
                                null, tableConverter);
    assertEquals(2, controller.getTables("master", null, tableConverter).size());
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 2),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table1).withIsDeleted(true),
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
    commit = controller.getBranch("test").getId();
    controller.deleteBranch("test", commit, commitMetaDelete("test"));
    branches = controller.getBranches();
    assertEquals(1, branches.size());
  }

  @SuppressWarnings("VariableDeclarationUsageDistance")
  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testMetadata(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.getBranch("master");
    TableMeta tableMeta = ImmutableTableMeta.builder()
                                            .schema("x")
                                            .sourceId("y")
                                            .build();
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .metadata(tableMeta)
                                .build();
    String commit = controller.commit("master",
                                      commitMeta("master", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    assertEquals(1, controller.getTables("master", null, tableConverter).size());
    table = controller.getTable("master", "db.table", true);
    assertNotNull(table.getMetadata());
    assertEquals(tableMeta, table.getMetadata());
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 1),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
  }

  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testConflict(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.getBranch("master");
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("master",
                                      commitMeta("master", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    assertThrows(NessieConflictException.class,
        () -> controller.commit("master",
                                commitMeta("master", "", Action.COMMIT, 1),
                                branch.getId(),
                                tableConverter,
                                ImmutableTable.copyOf(table).withMetadataLocation("x")));
    Table table1 = ImmutableTable.builder()
                                 .id("dbx.table")
                                 .namespace("dbx")
                                 .name("table")
                                 .metadataLocation("")
                                 .build();
    commit = controller.commit("master",
                               commitMeta("master", "", Action.COMMIT, 1),
                               branch.getId(),
                               tableConverter,
                               table1
    );
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 1),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table).withMetadataLocation("x")
    );
    assertEquals(2, controller.getTables("master", null, tableConverter).size());
    controller.commit("master",
                      commitMeta("master", "", Action.COMMIT, 2),
                      commit,
                      tableConverter,
                      ImmutableTable.copyOf(table1).withIsDeleted(true),
                      ImmutableTable.copyOf(table).withIsDeleted(true));
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
  }

  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testLog(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    Branch branch = controller.getBranch("master");
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit1 = controller.commit("master",
                                      commitMeta("master", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      ImmutableTable.copyOf(table).withMetadataLocation("x"));
    String commit2 = controller.commit("master",
                                       commitMeta("master", "", Action.COMMIT, 1),
                                       commit1,
                                       tableConverter,
                                       ImmutableTable.copyOf(table).withMetadataLocation("y"));
    String commit3 = controller.commit("master",
                                       commitMeta("master", "", Action.COMMIT, 1),
                                       commit2,
                                       tableConverter,
                                       ImmutableTable.copyOf(table).withMetadataLocation("z"));
    String commit4 = controller.commit("master",
                                       commitMeta("master", "", Action.COMMIT, 1),
                                       commit3,
                                       tableConverter,
                                       ImmutableTable.copyOf(table).withMetadataLocation("a"));
    String commit5 = controller.commit("master",
                                       commitMeta("master", "", Action.COMMIT, 1),
                                       commit4,
                                       tableConverter,
                                       ImmutableTable.copyOf(table).withMetadataLocation("b"));
    List<String> commits = Lists.newArrayList(commit5, commit4, commit3, commit2, commit1, branch.getId());
    List<String> messageStream = controller.log("master").map(LogMessage::commitId).collect(Collectors.toList());
    assertLinesMatch(commits, messageStream);

  }

  @ParameterizedTest
  @EnumSource(RepoType.class)
  public void testMultipleControllers(RepoType repoType) throws IOException {
    JgitBranchControllerLegacy controller = controller(repoType);
    JgitBranchControllerLegacy controller2 = controller(repoType, true);
    Branch branch = controller.getBranch("master");
    Branch branch2 = controller2.getBranch("master");
    Table table = ImmutableTable.builder()
                                .id("db.table")
                                .namespace("db")
                                .name("table")
                                .metadataLocation("")
                                .build();
    String commit = controller.commit("master",
                                      commitMeta("master", "", Action.COMMIT, 1),
                                      branch.getId(),
                                      tableConverter,
                                      table);
    assertThrows(NessieConflictException.class,
        () -> controller2.commit("master",
                                 commitMeta("master", "", Action.COMMIT, 1),
                                 branch2.getId(),
                                 tableConverter,
                                 ImmutableTable.copyOf(table).withMetadataLocation("xx")));
    commit = controller2.getBranch("master").getId();
    commit = controller2.commit("master",
                                commitMeta("master", "", Action.COMMIT, 1),
                                commit,
                                tableConverter,
                                ImmutableTable.copyOf(table).withMetadataLocation("xx"));
    controller2.commit("master",
                       commitMeta("master", "", Action.COMMIT, 2),
                       commit,
                       tableConverter,
                       ImmutableTable.copyOf(table).withIsDeleted(true));
    controller.getBranch("master");
    List<String> tables = controller.getTables("master", null, tableConverter);
    assertTrue(tables.isEmpty());
  }

  @SuppressWarnings("MissingJavadocMethod")
  @AfterEach
  public void empty() throws IOException {
    backend.close();
  }

  @AfterAll
  public static void close() {
    backend.close();
  }

  private static CommitMeta commitMetaDelete(String branch) {
    return commitMeta(branch, "", Action.DELETE_BRANCH, 1);
  }

  private static CommitMeta commitMeta(String branch, String comment, Action action, int changes) {
    return ImmutableCommitMeta.builder()
                              .action(action)
                              .ref(branch)
                              .changes(changes)
                              .comment(comment)
                              .commiter("test")
                              .email("test@test.org")
                              .build();
  }
}
