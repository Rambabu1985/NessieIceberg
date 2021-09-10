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
package org.projectnessie.versioned.persist.tests;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertAll;

import com.google.protobuf.ByteString;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Diff;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.ReferenceAlreadyExistsException;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.TagName;
import org.projectnessie.versioned.WithHash;
import org.projectnessie.versioned.persist.adapter.ContentsId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterConfig;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterFactory;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterFactory.Builder;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitAttempt;
import org.projectnessie.versioned.persist.adapter.KeyFilterPredicate;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;

/**
 * Tests that verify {@link DatabaseAdapter} implementations. A few tests have similar pendants via
 * the tests against the {@code VersionStore}.
 */
public abstract class AbstractTieredCommitsTest {
  protected static DatabaseAdapter databaseAdapter;

  protected static <C extends DatabaseAdapterConfig> void createAdapter(
      String adapterName, Function<C, DatabaseAdapterConfig> configurer) {
    createAdapter(
        DatabaseAdapterFactory.<C>loadFactoryByName(adapterName).newBuilder(), configurer);
  }

  protected static <C extends DatabaseAdapterConfig> void createAdapter(
      Builder<C> configBuilder, Function<C, DatabaseAdapterConfig> configurer) {
    databaseAdapter =
        configBuilder
            .configure(SystemPropertiesConfigurer::configureFromSystemProperties)
            // default to a quite small max-size for the CommitLogEntry.keyList + KeyListEntity
            // This is necessary for AbstractManyKeys to work properly!!
            .configure(c -> c.withMaxKeyListSize(2048))
            .configure(configurer)
            .build();
  }

  @BeforeEach
  void reinitializeRepo() {
    databaseAdapter.reinitializeRepo("main");
  }

  @AfterAll
  static void closeDatabaseAdapter() throws Exception {
    try {
      if (databaseAdapter != null) {
        databaseAdapter.close();
      }
    } finally {
      databaseAdapter = null;
    }
  }

  @Nested
  public class GlobalStates extends AbstractGlobalStates {
    GlobalStates() {
      super(databaseAdapter);
    }
  }

  @Nested
  public class CommitScenarios extends AbstractCommitScenarios {
    CommitScenarios() {
      super(databaseAdapter);
    }
  }

  @Nested
  public class ManyCommits extends AbstractManyCommits {
    ManyCommits() {
      super(databaseAdapter);
    }
  }

  @Nested
  public class ManyKeys extends AbstractManyKeys {
    ManyKeys() {
      super(databaseAdapter);
    }
  }

  @Nested
  public class Concurrency extends AbstractConcurrency {
    Concurrency() {
      super(databaseAdapter);
    }
  }

  @Test
  void createBranch() throws Exception {
    BranchName create = BranchName.of("createBranch");
    createNamedRef(create, TagName.of(create.getName()));
  }

  @Test
  void createTag() throws Exception {
    TagName create = TagName.of("createTag");
    createNamedRef(create, BranchName.of(create.getName()));
  }

  private void createNamedRef(NamedRef create, NamedRef opposite) throws Exception {
    BranchName branch = BranchName.of("main");

    try (Stream<WithHash<NamedRef>> refs = databaseAdapter.namedRefs()) {
      assertThat(refs.map(WithHash::getValue)).containsExactlyInAnyOrder(branch);
    }

    Hash mainHash = databaseAdapter.toHash(branch);

    assertThatThrownBy(() -> databaseAdapter.toHash(create))
        .isInstanceOf(ReferenceNotFoundException.class);

    Hash createHash = databaseAdapter.create(create, databaseAdapter.toHash(branch));
    assertThat(createHash).isEqualTo(mainHash);

    try (Stream<WithHash<NamedRef>> refs = databaseAdapter.namedRefs()) {
      assertThat(refs.map(WithHash::getValue)).containsExactlyInAnyOrder(branch, create);
    }

    assertThatThrownBy(() -> databaseAdapter.create(create, databaseAdapter.toHash(branch)))
        .isInstanceOf(ReferenceAlreadyExistsException.class);

    assertThat(databaseAdapter.toHash(create)).isEqualTo(createHash);
    assertThatThrownBy(() -> databaseAdapter.toHash(opposite))
        .isInstanceOf(ReferenceNotFoundException.class);

    assertThatThrownBy(
            () ->
                databaseAdapter.create(
                    BranchName.of(create.getName()), databaseAdapter.toHash(branch)))
        .isInstanceOf(ReferenceAlreadyExistsException.class);

    assertThatThrownBy(
            () -> databaseAdapter.delete(create, Optional.of(Hash.of("dead00004242fee18eef"))))
        .isInstanceOf(ReferenceConflictException.class);

    assertThatThrownBy(() -> databaseAdapter.delete(opposite, Optional.of(createHash)))
        .isInstanceOf(ReferenceNotFoundException.class);

    databaseAdapter.delete(create, Optional.of(createHash));

    assertThatThrownBy(() -> databaseAdapter.toHash(create))
        .isInstanceOf(ReferenceNotFoundException.class);

    try (Stream<WithHash<NamedRef>> refs = databaseAdapter.namedRefs()) {
      assertThat(refs.map(WithHash::getValue)).containsExactlyInAnyOrder(branch);
    }
  }

  @Test
  void verifyNotFoundAndConflictExceptionsForUnreachableCommit() throws Exception {
    BranchName main = BranchName.of("main");
    BranchName unreachable = BranchName.of("unreachable");
    BranchName helper = BranchName.of("helper");

    databaseAdapter.create(unreachable, databaseAdapter.toHash(main));
    Hash helperHead = databaseAdapter.create(helper, databaseAdapter.toHash(main));

    Hash unreachableHead =
        databaseAdapter.commit(
            ImmutableCommitAttempt.builder()
                .commitToBranch(unreachable)
                .commitMetaSerialized(ByteString.copyFromUtf8("commit meta"))
                .addPuts(
                    KeyWithBytes.of(
                        Key.of("foo"),
                        ContentsId.of("contentsId"),
                        (byte) 0,
                        ByteString.copyFromUtf8("hello")))
                .build());

    assertAll(
        () ->
            assertThatThrownBy(
                    () -> databaseAdapter.hashOnReference(main, Optional.of(unreachableHead)))
                .isInstanceOf(ReferenceNotFoundException.class)
                .hasMessage(
                    String.format(
                        "Could not find commit '%s' in reference '%s'.",
                        unreachableHead.asString(), main.getName())),
        () ->
            assertThatThrownBy(
                    () ->
                        databaseAdapter.commit(
                            ImmutableCommitAttempt.builder()
                                .commitToBranch(helper)
                                .expectedHead(Optional.of(unreachableHead))
                                .commitMetaSerialized(ByteString.copyFromUtf8("commit meta"))
                                .addPuts(
                                    KeyWithBytes.of(
                                        Key.of("bar"),
                                        ContentsId.of("contentsId-no-no"),
                                        (byte) 0,
                                        ByteString.copyFromUtf8("hello")))
                                .build()))
                .isInstanceOf(ReferenceNotFoundException.class)
                .hasMessage(
                    String.format(
                        "Could not find commit '%s' in reference '%s'.",
                        unreachableHead.asString(), helper.getName())),
        () ->
            assertThatThrownBy(
                    () ->
                        databaseAdapter.assign(
                            helper, Optional.of(unreachableHead), databaseAdapter.toHash(main)))
                .isInstanceOf(ReferenceConflictException.class)
                .hasMessage(
                    String.format(
                        "Named-reference '%s' is not at expected hash '%s', but at '%s'.",
                        helper.getName(), unreachableHead.asString(), helperHead.asString())));
  }

  @Test
  void assign() throws Exception {
    BranchName main = BranchName.of("main");
    TagName tag = TagName.of("tag");
    TagName branch = TagName.of("branch");

    databaseAdapter.create(branch, databaseAdapter.toHash(main));
    databaseAdapter.create(tag, databaseAdapter.toHash(main));

    Hash beginning = databaseAdapter.toHash(main);

    Hash[] commits = new Hash[3];
    for (int i = 0; i < commits.length; i++) {
      commits[i] =
          databaseAdapter.commit(
              ImmutableCommitAttempt.builder()
                  .commitToBranch(main)
                  .commitMetaSerialized(ByteString.copyFromUtf8("commit meta " + i))
                  .addPuts(
                      KeyWithBytes.of(
                          Key.of("bar", Integer.toString(i)),
                          ContentsId.of("contentsId-" + i),
                          (byte) 0,
                          ByteString.copyFromUtf8("hello " + i)))
                  .build());
    }

    Hash expect = beginning;
    for (Hash commit : commits) {
      assertThat(Arrays.asList(databaseAdapter.toHash(branch), databaseAdapter.toHash(tag)))
          .containsExactly(expect, expect);

      databaseAdapter.assign(tag, Optional.of(expect), commit);

      databaseAdapter.assign(branch, Optional.of(expect), commit);

      expect = commit;
    }

    assertThat(Arrays.asList(databaseAdapter.toHash(branch), databaseAdapter.toHash(tag)))
        .containsExactly(commits[commits.length - 1], commits[commits.length - 1]);
  }

  @Test
  void diff() throws Exception {
    BranchName main = BranchName.of("main");
    BranchName branch = BranchName.of("branch");

    Hash initialHash = databaseAdapter.create(branch, databaseAdapter.toHash(main));

    Hash[] commits = new Hash[3];
    for (int i = 0; i < commits.length; i++) {
      ImmutableCommitAttempt.Builder commit =
          ImmutableCommitAttempt.builder()
              .commitToBranch(branch)
              .commitMetaSerialized(ByteString.copyFromUtf8("commit " + i));
      for (int k = 0; k < 3; k++) {
        commit.addPuts(
            KeyWithBytes.of(
                Key.of("key", Integer.toString(k)),
                ContentsId.of("C" + k),
                (byte) 0,
                ByteString.copyFromUtf8("value " + i + " for " + k)));
      }
      commits[i] = databaseAdapter.commit(commit.build());
    }

    try (Stream<Diff<ByteString>> diff =
        databaseAdapter.diff(
            databaseAdapter.toHash(main),
            databaseAdapter.hashOnReference(branch, Optional.of(initialHash)),
            KeyFilterPredicate.ALLOW_ALL)) {
      assertThat(diff).isEmpty();
    }

    for (int i = 0; i < commits.length; i++) {
      try (Stream<Diff<ByteString>> diff =
          databaseAdapter.diff(
              databaseAdapter.toHash(main),
              databaseAdapter.hashOnReference(branch, Optional.of(commits[i])),
              KeyFilterPredicate.ALLOW_ALL)) {
        int c = i;
        assertThat(diff)
            .containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 3)
                    .mapToObj(
                        k ->
                            Diff.of(
                                Key.of("key", Integer.toString(k)),
                                Optional.empty(),
                                Optional.of(ByteString.copyFromUtf8("value " + c + " for " + k))))
                    .collect(Collectors.toList()));
      }
    }

    for (int i = 0; i < commits.length; i++) {
      try (Stream<Diff<ByteString>> diff =
          databaseAdapter.diff(
              databaseAdapter.hashOnReference(branch, Optional.of(commits[i])),
              databaseAdapter.toHash(main),
              KeyFilterPredicate.ALLOW_ALL)) {
        int c = i;
        assertThat(diff)
            .containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 3)
                    .mapToObj(
                        k ->
                            Diff.of(
                                Key.of("key", Integer.toString(k)),
                                Optional.of(ByteString.copyFromUtf8("value " + c + " for " + k)),
                                Optional.empty()))
                    .collect(Collectors.toList()));
      }
    }

    for (int i = 1; i < commits.length; i++) {
      try (Stream<Diff<ByteString>> diff =
          databaseAdapter.diff(
              databaseAdapter.hashOnReference(branch, Optional.of(commits[i - 1])),
              databaseAdapter.hashOnReference(branch, Optional.of(commits[i])),
              KeyFilterPredicate.ALLOW_ALL)) {
        int c = i;
        assertThat(diff)
            .containsExactlyInAnyOrderElementsOf(
                IntStream.range(0, 3)
                    .mapToObj(
                        k ->
                            Diff.of(
                                Key.of("key", Integer.toString(k)),
                                Optional.of(
                                    ByteString.copyFromUtf8("value " + (c - 1) + " for " + k)),
                                Optional.of(ByteString.copyFromUtf8("value " + c + " for " + k))))
                    .collect(Collectors.toList()));
      }
    }
  }

  @Test
  void recreateDefaultBranch() throws Exception {
    BranchName main = BranchName.of("main");
    Hash mainHead = databaseAdapter.toHash(main);
    databaseAdapter.delete(main, Optional.of(mainHead));

    assertThatThrownBy(() -> databaseAdapter.toHash(main))
        .isInstanceOf(ReferenceNotFoundException.class);

    databaseAdapter.create(main, null);
    databaseAdapter.toHash(main);
  }

  @Nested
  public class MergeTransplant extends AbstractMergeTransplant {
    MergeTransplant() {
      super(databaseAdapter);
    }
  }
}
