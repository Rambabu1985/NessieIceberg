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
package org.projectnessie.versioned.tests;

import static java.util.Arrays.asList;
import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonList;
import static org.projectnessie.model.CommitMeta.fromMessage;
import static org.projectnessie.versioned.tests.AbstractVersionStoreTestBase.METADATA_REWRITER;
import static org.projectnessie.versioned.testworker.OnRefOnly.newOnRef;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.Namespace;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Delete;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.MergeType;
import org.projectnessie.versioned.Put;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.VersionStore;

@ExtendWith(SoftAssertionsExtension.class)
public abstract class AbstractNamespaceValidation extends AbstractNestedVersionStore {
  @InjectSoftAssertions protected SoftAssertions soft;

  protected AbstractNamespaceValidation(VersionStore store) {
    super(store);
  }

  static Stream<ContentKey> contentKeys() {
    return Stream.of(
        ContentKey.of("ns", "table"),
        ContentKey.of("ns", "table"),
        ContentKey.of("ns2", "ns", "table"),
        ContentKey.of("ns2", "ns", "table"));
  }

  @ParameterizedTest
  @MethodSource("contentKeys")
  void commitWithNonExistingNamespace(ContentKey key) throws Exception {
    BranchName branch = BranchName.of("commitWithNonExistingNamespace");
    store().create(branch, Optional.empty());

    soft.assertThatThrownBy(
            () ->
                store()
                    .commit(
                        branch,
                        Optional.empty(),
                        fromMessage("non-existing-ns"),
                        singletonList(Put.of(key, newOnRef("value")))))
        .isInstanceOf(ReferenceConflictException.class)
        .hasMessage("Namespace '%s' must exist.", key.getNamespace());

    store()
        .commit(
            branch,
            Optional.empty(),
            fromMessage("initial commit"),
            singletonList(Put.of(ContentKey.of("unrelated-table"), newOnRef("value"))));

    soft.assertThatThrownBy(
            () ->
                store()
                    .commit(
                        branch,
                        Optional.empty(),
                        fromMessage("non-existing-ns"),
                        singletonList(Put.of(key, newOnRef("value")))))
        .isInstanceOf(ReferenceConflictException.class)
        .hasMessage("Namespace '%s' must exist.", key.getNamespace());
  }

  @ParameterizedTest
  @MethodSource("contentKeys")
  void commitWithNonNamespace(ContentKey key) throws Exception {
    BranchName branch = BranchName.of("commitWithNonNamespace");
    store().create(branch, Optional.empty());

    if (key.getElementCount() == 3) {
      // Give the non-namespace content commit a valid namespace.
      store()
          .commit(
              branch,
              Optional.empty(),
              fromMessage("initial commit"),
              singletonList(
                  Put.of(key.getParent().getParent(), Namespace.of(key.getParent().getParent()))));
    }

    // Add a non-namespace content using the parent key of the namespace to be checked below.
    store()
        .commit(
            branch,
            Optional.empty(),
            fromMessage("not a namespace"),
            singletonList(Put.of(key.getParent(), newOnRef("value"))));

    soft.assertThatThrownBy(
            () ->
                store()
                    .commit(
                        branch,
                        Optional.empty(),
                        fromMessage("non-existing-ns"),
                        singletonList(Put.of(key, newOnRef("value")))))
        .isInstanceOf(ReferenceConflictException.class)
        .hasMessage(
            "Expecting the key '%s' to be a namespace, but is not a namespace. "
                + "Using a content object that is not a namespace as a namespace is forbidden.",
            key.getNamespace());
  }

  @ParameterizedTest
  @CsvSource({"true", "false"})
  void preventNamespaceDeletionWithChildren(boolean childNamespace) throws Exception {
    BranchName branch = BranchName.of("branch");
    store().create(branch, Optional.empty());

    Namespace ns = Namespace.of("ns");
    ContentKey key = ContentKey.of(ns, "table");

    store()
        .commit(
            branch,
            Optional.empty(),
            fromMessage("initial"),
            asList(
                Put.of(ns.toContentKey(), ns),
                Put.of(key, childNamespace ? Namespace.of(key) : newOnRef("foo"))));

    soft.assertThatThrownBy(
            () ->
                store.commit(
                    branch,
                    Optional.empty(),
                    fromMessage("try delete ns"),
                    singletonList(Delete.of(ns.toContentKey()))))
        .isInstanceOf(ReferenceConflictException.class);
  }

  enum NamespaceValidationMergeTransplant {
    MERGE_SQUASH(true, false, false, false, false),
    MERGE_INDIVIDUAL(true, false, false, true, false),
    MERGE_CREATE_SQUASH(true, true, false, false, false),
    MERGE_CREATE_INDIVIDUAL(true, true, false, true, false),
    MERGE_DELETE_SQUASH(true, false, true, false, true),
    MERGE_DELETE_INDIVIDUAL(true, false, true, true, true),
    TRANSPLANT_SQUASH(false, false, false, false, false),
    TRANSPLANT_INDIVIDUAL(false, false, false, true, false),
    TRANSPLANT_CREATE_SQUASH(true, true, false, false, false),
    TRANSPLANT_CREATE_INDIVIDUAL(true, true, false, true, false),
    TRANSPLANT_DELETE_SQUASH(false, false, true, false, true),
    TRANSPLANT_DELETE_INDIVIDUAL(false, false, true, true, true),
    ;

    /** Whether to merge (or transplant, if false). */
    final boolean merge;
    /** Whether the namespace shall be created on the target branch. */
    final boolean createNamespaceOnTarget;
    /**
     * Whether the namespace shall be deleted on the target branch to trigger an error by the
     * namespace-exists check.
     */
    final boolean deleteNamespaceOnTarget;
    /** Whether merge/transplant shall keep individual commits or "squash" those. */
    final boolean individualCommits;

    final boolean error;

    NamespaceValidationMergeTransplant(
        boolean merge,
        boolean createNamespaceOnTarget,
        boolean deleteNamespaceOnTarget,
        boolean individualCommits,
        boolean error) {
      this.merge = merge;
      this.createNamespaceOnTarget = createNamespaceOnTarget;
      this.deleteNamespaceOnTarget = deleteNamespaceOnTarget;
      this.individualCommits = individualCommits;
      this.error = error;
    }
  }

  /**
   * Validate various combinations of merge/transplant scenarios, validating that the
   * "namespace-exists checks for merged/transplanted keys" works properly.
   *
   * @see NamespaceValidationMergeTransplant
   */
  @ParameterizedTest
  @EnumSource(NamespaceValidationMergeTransplant.class)
  void mergeTransplantWithCommonButRemovedNamespace(NamespaceValidationMergeTransplant mode)
      throws Exception {
    BranchName root = BranchName.of("root");
    store().create(root, Optional.empty());

    Namespace ns = Namespace.of("ns");
    Namespace ns2 = Namespace.of("ns2");
    Hash rootHead =
        store()
            .commit(
                root,
                Optional.empty(),
                fromMessage("create namespace"),
                mode.createNamespaceOnTarget
                    ? singletonList(Put.of(ns2.toContentKey(), ns2))
                    : asList(Put.of(ns.toContentKey(), ns), Put.of(ns2.toContentKey(), ns2)));

    BranchName branch = BranchName.of("branch");
    store().create(branch, Optional.of(rootHead));

    if (mode.createNamespaceOnTarget) {
      store()
          .commit(
              branch,
              Optional.empty(),
              fromMessage("create namespace"),
              singletonList(Put.of(ns.toContentKey(), ns)));
    }

    ContentKey key = ContentKey.of(ns, "foo");
    Hash commit1 =
        store()
            .commit(
                branch,
                Optional.empty(),
                fromMessage("create table ns.foo"),
                singletonList(Put.of(key, newOnRef("foo"))));

    Hash commit2 =
        store()
            .commit(
                branch,
                Optional.empty(),
                fromMessage("create table ns2.bar"),
                singletonList(Put.of(ContentKey.of(ns2, "bar"), newOnRef("bar"))));

    store()
        .commit(
            root,
            Optional.empty(),
            fromMessage("unrelated"),
            singletonList(Put.of(ContentKey.of("unrelated-table"), newOnRef("bar"))));

    ThrowingCallable mergeTransplant =
        mode.merge
            ? () ->
                store()
                    .merge(
                        store().hashOnReference(branch, Optional.empty()),
                        root,
                        Optional.empty(),
                        METADATA_REWRITER,
                        mode.individualCommits,
                        emptyMap(),
                        MergeType.NORMAL,
                        false,
                        false)
            : () ->
                store()
                    .transplant(
                        root,
                        Optional.empty(),
                        asList(commit1, commit2),
                        METADATA_REWRITER,
                        mode.individualCommits,
                        emptyMap(),
                        MergeType.NORMAL,
                        false,
                        false);

    if (mode.deleteNamespaceOnTarget) {
      store()
          .commit(
              root,
              Optional.empty(),
              fromMessage("delete namespace"),
              singletonList(Delete.of(ns.toContentKey())));
    }

    if (mode.error) {
      soft.assertThatThrownBy(mergeTransplant)
          .isInstanceOf(ReferenceConflictException.class)
          .hasMessage("Namespace '%s' must exist.", key.getNamespace());
    } else {
      soft.assertThatCode(mergeTransplant).doesNotThrowAnyException();
    }
  }

  @Test
  void mustNotOverwriteNamespace() throws Exception {
    BranchName root = BranchName.of("root");
    store().create(root, Optional.empty());

    ContentKey key = ContentKey.of("key");

    store()
        .commit(
            root,
            Optional.empty(),
            fromMessage("create table ns.foo"),
            singletonList(Put.of(key, Namespace.of(key))));

    soft.assertThatThrownBy(
            () ->
                store()
                    .commit(
                        root,
                        Optional.empty(),
                        fromMessage("create table ns.foo"),
                        singletonList(Put.of(key, newOnRef("foo")))))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void deleteHierarchy() throws Exception {
    BranchName root = BranchName.of("root");
    store().create(root, Optional.empty());

    List<Namespace> namespaces =
        asList(
            Namespace.of("a"),
            Namespace.of("a", "b"),
            Namespace.of("a", "b", "c"),
            Namespace.of("x"),
            Namespace.of("x", "y"),
            Namespace.of("x", "y", "z"));
    List<ContentKey> tables =
        namespaces.stream()
            .flatMap(
                ns ->
                    Stream.of(
                        ContentKey.of(ns, "A"), ContentKey.of(ns, "B"), ContentKey.of(ns, "C")))
            .collect(Collectors.toList());

    store()
        .commit(
            root,
            Optional.empty(),
            fromMessage("unrelated"),
            Stream.concat(
                    tables.stream().map(t -> Put.of(t, newOnRef(t.toString()))),
                    namespaces.stream().map(ns -> Put.of(ns.toContentKey(), ns)))
                .collect(Collectors.toList()));

    soft.assertThatCode(
            () ->
                store()
                    .commit(
                        root,
                        Optional.empty(),
                        fromMessage("delete all the things"),
                        Stream.concat(
                                namespaces.stream().map(ns -> Delete.of(ns.toContentKey())),
                                tables.stream().map(Delete::of))
                            .collect(Collectors.toList())))
        .doesNotThrowAnyException();
  }
}
