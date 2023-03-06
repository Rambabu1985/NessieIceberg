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

import static org.assertj.core.util.Streams.stream;
import static org.projectnessie.versioned.GetNamedRefsParams.RetrieveOptions.BASE_REFERENCE_RELATED_AND_COMMIT_META;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.GetNamedRefsParams;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ReferenceAlreadyExistsException;
import org.projectnessie.versioned.ReferenceInfo;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.TagName;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.VersionStoreException;
import org.projectnessie.versioned.paging.PaginationIterator;

@ExtendWith(SoftAssertionsExtension.class)
public abstract class AbstractReferences extends AbstractNestedVersionStore {
  @InjectSoftAssertions protected SoftAssertions soft;

  protected AbstractReferences(VersionStore store) {
    super(store);
  }

  /*
   * Test:
   * - Create a branch with no hash assigned to it
   * - check that a hash is returned by toHash
   * - check the branch is returned by getNamedRefs
   * - check that no commits are returned using getCommits
   * - check the branch cannot be created
   * - check the branch can be deleted
   */
  @Test
  public void createAndDeleteBranch() throws Exception {
    final BranchName branch = BranchName.of("foo");
    store().create(branch, Optional.empty());
    final Hash hash = store().hashOnReference(branch, Optional.empty());
    soft.assertThat(hash).isNotNull();

    final BranchName anotherBranch = BranchName.of("bar");
    final Hash createHash = store().create(anotherBranch, Optional.of(hash));
    final Hash commitHash = commit("Some Commit").toBranch(anotherBranch);
    soft.assertThat(commitHash).isNotEqualTo(createHash);

    final BranchName anotherAnotherBranch = BranchName.of("baz");
    final Hash otherCreateHash = store().create(anotherAnotherBranch, Optional.of(commitHash));
    soft.assertThat(otherCreateHash).isEqualTo(commitHash);

    List<ReferenceInfo<CommitMeta>> namedRefs;
    try (PaginationIterator<ReferenceInfo<CommitMeta>> str =
        store().getNamedRefs(GetNamedRefsParams.DEFAULT, null)) {
      namedRefs = stream(str).filter(this::filterMainBranch).collect(Collectors.toList());
    }
    soft.assertThat(namedRefs)
        .containsExactlyInAnyOrder(
            ReferenceInfo.of(hash, branch),
            ReferenceInfo.of(commitHash, anotherBranch),
            ReferenceInfo.of(commitHash, anotherAnotherBranch));

    soft.assertThat(commitsList(branch, false)).isEmpty();
    soft.assertThat(commitsList(anotherBranch, false)).hasSize(1);
    soft.assertThat(commitsList(anotherAnotherBranch, false)).hasSize(1);
    soft.assertThat(commitsList(hash, false)).isEmpty(); // empty commit should not be listed
    soft.assertThat(commitsList(commitHash, false)).hasSize(1); // empty commit should not be listed

    soft.assertThatThrownBy(() -> store().create(branch, Optional.empty()))
        .isInstanceOf(ReferenceAlreadyExistsException.class);
    soft.assertThatThrownBy(() -> store().create(branch, Optional.of(hash)))
        .isInstanceOf(ReferenceAlreadyExistsException.class);

    store().delete(branch, Optional.of(hash));
    soft.assertThatThrownBy(() -> store().hashOnReference(branch, Optional.empty()))
        .isInstanceOf(ReferenceNotFoundException.class);
    try (PaginationIterator<ReferenceInfo<CommitMeta>> str =
        store().getNamedRefs(GetNamedRefsParams.DEFAULT, null)) {
      soft.assertThat(stream(str).filter(this::filterMainBranch)).hasSize(2); // bar + baz
    }
    soft.assertThatThrownBy(() -> store().delete(branch, Optional.of(hash)))
        .isInstanceOf(ReferenceNotFoundException.class);
  }

  /*
   * Test:
   * - Create a branch with no hash assigned to it
   * - add a commit to the branch
   * - create a tag for the initial hash
   * - create another tag for the hash after the commit
   * - check that cannot create existing tags, or tag with no assigned hash
   * - check that a hash is returned by toHash
   * - check the tags are returned by getNamedRefs
   * - check that expected commits are returned by getCommits
   * - check the branch can be deleted
   */
  @Test
  public void createAndDeleteTag() throws Exception {
    final BranchName branch = BranchName.of("foo");
    store().create(branch, Optional.empty());

    final Hash initialHash = store().hashOnReference(branch, Optional.empty());
    final Hash commitHash = commit("Some commit").toBranch(branch);

    final TagName tag = TagName.of("tag");
    store().create(tag, Optional.of(initialHash));

    final TagName anotherTag = TagName.of("another-tag");
    store().create(anotherTag, Optional.of(commitHash));

    soft.assertThatThrownBy(() -> store().create(tag, Optional.of(initialHash)))
        .isInstanceOf(ReferenceAlreadyExistsException.class);

    soft.assertThat(store().hashOnReference(tag, Optional.empty())).isEqualTo(initialHash);
    soft.assertThat(store().hashOnReference(anotherTag, Optional.empty())).isEqualTo(commitHash);

    List<ReferenceInfo<CommitMeta>> namedRefs;
    try (PaginationIterator<ReferenceInfo<CommitMeta>> str =
        store().getNamedRefs(GetNamedRefsParams.DEFAULT, null)) {
      namedRefs = stream(str).filter(this::filterMainBranch).collect(Collectors.toList());
    }
    soft.assertThat(namedRefs)
        .containsExactlyInAnyOrder(
            ReferenceInfo.of(commitHash, branch),
            ReferenceInfo.of(initialHash, tag),
            ReferenceInfo.of(commitHash, anotherTag));

    soft.assertThat(commitsList(tag, false)).isEmpty();
    soft.assertThat(commitsList(initialHash, false)).isEmpty(); // empty commit should not be listed

    soft.assertThat(commitsList(anotherTag, false)).hasSize(1);
    soft.assertThat(commitsList(commitHash, false)).hasSize(1); // empty commit should not be listed

    store().delete(tag, Optional.of(initialHash));
    soft.assertThatThrownBy(() -> store().hashOnReference(tag, Optional.empty()))
        .isInstanceOf(ReferenceNotFoundException.class);
    try (PaginationIterator<ReferenceInfo<CommitMeta>> str =
        store().getNamedRefs(GetNamedRefsParams.DEFAULT, null)) {
      soft.assertThat(stream(str).filter(this::filterMainBranch)).hasSize(2); // foo + another-tag
    }
    soft.assertThatThrownBy(() -> store().delete(tag, Optional.of(initialHash)))
        .isInstanceOf(ReferenceNotFoundException.class);
  }

  /**
   * Rudimentary test for {@link VersionStore#getNamedRef(String, GetNamedRefsParams)}. Better tests
   * in {@code AbstractGetNamedReferences} in {@code :nessie-versioned-persist-tests}.
   */
  @Test
  void getNamedRef() throws VersionStoreException {
    final BranchName branch = BranchName.of("getNamedRef");
    Hash hashFromCreate = store().create(branch, Optional.empty());
    soft.assertThat(store().hashOnReference(branch, Optional.empty())).isEqualTo(hashFromCreate);

    final Hash firstCommitHash = commit("First Commit").toBranch(branch);

    soft.assertThat(store().getNamedRef(branch.getName(), GetNamedRefsParams.DEFAULT))
        .extracting(ReferenceInfo::getHash, ReferenceInfo::getNamedRef)
        .containsExactly(firstCommitHash, branch);

    final Hash secondCommitHash = commit("Second Commit").toBranch(branch);
    final Hash thirdCommitHash = commit("Third Commit").toBranch(branch);

    BranchName branchName = BranchName.of("getNamedRef_branch_" + secondCommitHash.asString());
    TagName tagName = TagName.of("getNamedRef_tag_" + thirdCommitHash.asString());

    store().create(branchName, Optional.of(secondCommitHash));
    store().create(tagName, Optional.of(thirdCommitHash));

    // Verifies that the result of "getNamedRef" for the branch created at "firstCommitHash" is
    // correct
    soft.assertThat(store().getNamedRef(branchName.getName(), GetNamedRefsParams.DEFAULT))
        .extracting(ReferenceInfo::getHash, ReferenceInfo::getNamedRef)
        .containsExactly(secondCommitHash, branchName);

    // Verifies that the result of "getNamedRef" for the tag created at "firstCommitHash" is correct
    soft.assertThat(store().getNamedRef(tagName.getName(), GetNamedRefsParams.DEFAULT))
        .extracting(ReferenceInfo::getHash, ReferenceInfo::getNamedRef)
        .containsExactly(thirdCommitHash, tagName);

    // Verifies that the result of "getNamedRef" for the branch created at "firstCommitHash" is
    // correct
    soft.assertThat(store().getNamedRef(branchName.getName(), GetNamedRefsParams.DEFAULT))
        .extracting(ReferenceInfo::getHash, ReferenceInfo::getNamedRef)
        .containsExactly(secondCommitHash, branchName);

    soft.assertThatThrownBy(() -> store().getNamedRef("unknown-ref", GetNamedRefsParams.DEFAULT))
        .isInstanceOf(ReferenceNotFoundException.class);
    soft.assertThatThrownBy(
            () -> store().getNamedRef("1234567890abcdef", GetNamedRefsParams.DEFAULT))
        .isInstanceOf(ReferenceNotFoundException.class);

    soft.assertThatThrownBy(
            () ->
                store()
                    .getNamedRef(
                        branchName.getName(),
                        GetNamedRefsParams.builder()
                            .baseReference(BranchName.of("does-not-exist"))
                            .branchRetrieveOptions(BASE_REFERENCE_RELATED_AND_COMMIT_META)
                            .build()))
        .isInstanceOf(ReferenceNotFoundException.class)
        .hasMessageContaining("'does-not-exist");
  }
}
