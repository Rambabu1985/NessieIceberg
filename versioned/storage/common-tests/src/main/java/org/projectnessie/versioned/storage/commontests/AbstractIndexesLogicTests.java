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
package org.projectnessie.versioned.storage.commontests;

import static com.google.common.collect.Lists.newArrayList;
import static java.util.Collections.emptyList;
import static java.util.Objects.requireNonNull;
import static org.assertj.core.api.InstanceOfAssertFactories.list;
import static org.assertj.core.api.InstanceOfAssertFactories.type;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexElement.indexElement;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.emptyImmutableIndex;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.indexFromStripes;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.layeredIndex;
import static org.projectnessie.versioned.storage.common.indexes.StoreIndexes.newStoreIndex;
import static org.projectnessie.versioned.storage.common.indexes.StoreKey.key;
import static org.projectnessie.versioned.storage.common.logic.Logics.commitLogic;
import static org.projectnessie.versioned.storage.common.logic.Logics.indexesLogic;
import static org.projectnessie.versioned.storage.common.objtypes.CommitHeaders.EMPTY_COMMIT_HEADERS;
import static org.projectnessie.versioned.storage.common.objtypes.CommitObj.commitBuilder;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.ADD;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.INCREMENTAL_ADD;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.INCREMENTAL_REMOVE;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.Action.REMOVE;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.COMMIT_OP_SERIALIZER;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.commitOp;
import static org.projectnessie.versioned.storage.common.persist.ObjId.EMPTY_OBJ_ID;
import static org.projectnessie.versioned.storage.common.persist.ObjId.randomObjId;
import static org.projectnessie.versioned.storage.commontests.KeyIndexTestSet.basicIndexTestSet;

import java.util.Optional;
import java.util.function.Supplier;
import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.versioned.storage.common.indexes.StoreIndex;
import org.projectnessie.versioned.storage.common.indexes.StoreIndexElement;
import org.projectnessie.versioned.storage.common.indexes.StoreKey;
import org.projectnessie.versioned.storage.common.logic.CommitLogic;
import org.projectnessie.versioned.storage.common.logic.IndexesLogic;
import org.projectnessie.versioned.storage.common.logic.SuppliedCommitIndex;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.objtypes.CommitOp;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.testextension.NessiePersist;
import org.projectnessie.versioned.storage.testextension.PersistExtension;

/** {@link IndexesLogic} related tests to be run against every {@link Persist} implementation. */
@ExtendWith({PersistExtension.class, SoftAssertionsExtension.class})
public class AbstractIndexesLogicTests {

  @InjectSoftAssertions protected SoftAssertions soft;

  @NessiePersist protected Persist persist;

  @Test
  public void nonExistingCommit() {
    IndexesLogic indexesLogic = indexesLogic(persist);

    soft.assertThatCode(() -> indexesLogic.createIndexSupplier(() -> EMPTY_OBJ_ID).get())
        .doesNotThrowAnyException();
    soft.assertThat(indexesLogic.createIndexSupplier(() -> EMPTY_OBJ_ID))
        .extracting(Supplier::get)
        .extracting(SuppliedCommitIndex::index)
        .extracting(StoreIndex::elementCount)
        .isEqualTo(0);
  }

  @Test
  public void incompleteIndex() {
    IndexesLogic indexesLogic = indexesLogic(persist);

    CommitObj commit =
        commitBuilder()
            .id(randomObjId())
            .seq(42L)
            .created(42L)
            .headers(EMPTY_COMMIT_HEADERS)
            .message("msg")
            .incrementalIndex(newStoreIndex(COMMIT_OP_SERIALIZER).serialize())
            .incompleteIndex(true)
            .build();

    soft.assertThatIllegalArgumentException()
        .isThrownBy(() -> indexesLogic.buildCompleteIndex(commit, Optional.empty()))
        .withMessageEndingWith("has no complete key index");
    soft.assertThatIllegalArgumentException()
        .isThrownBy(() -> indexesLogic.incrementalIndexForUpdate(commit, Optional.empty()))
        .withMessageEndingWith("has no complete key index");
  }

  @Test
  public void buildIndex() {
    IndexesLogic indexesLogic = indexesLogic(persist);

    StoreIndex<CommitOp> index = newStoreIndex(COMMIT_OP_SERIALIZER);

    StoreIndexElement<CommitOp> add =
        indexElement(key("foo", "bar"), commitOp(ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> remove =
        indexElement(key("baz"), commitOp(REMOVE, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrementalRemove =
        indexElement(key("meep"), commitOp(INCREMENTAL_REMOVE, 1, randomObjId()));

    StoreIndexElement<CommitOp> incrementalAdd =
        indexElement(key("foo", "bar"), commitOp(INCREMENTAL_ADD, 1, add.content().value()));

    index.add(add);
    index.add(remove);
    index.add(incrementalRemove);

    CommitObj commit =
        commitBuilder()
            .id(randomObjId())
            .seq(1L)
            .created(42L)
            .message("msg")
            .headers(EMPTY_COMMIT_HEADERS)
            .addTail(EMPTY_OBJ_ID)
            .incrementalIndex(index.serialize())
            .build();

    soft.assertThat(indexesLogic.buildCompleteIndex(commit, Optional.empty()))
        .isEqualTo(index)
        .containsExactly(remove, add, incrementalRemove);
    soft.assertThat(indexesLogic.incrementalIndexForUpdate(commit, Optional.empty()))
        .containsExactly(incrementalAdd);
  }

  @Test
  public void indexSupplier() throws Exception {
    IndexesLogic indexesLogic = indexesLogic(persist);

    StoreIndex<CommitOp> index = newStoreIndex(COMMIT_OP_SERIALIZER);

    StoreIndexElement<CommitOp> add =
        indexElement(key("foo", "bar"), commitOp(ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> remove =
        indexElement(key("baz"), commitOp(REMOVE, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrementalRemove =
        indexElement(key("meep"), commitOp(INCREMENTAL_REMOVE, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrementalAdd =
        indexElement(key("dog", "bark"), commitOp(INCREMENTAL_ADD, 1, add.content().value()));

    index.add(add);
    index.add(remove);
    index.add(incrementalRemove);
    index.add(incrementalAdd);

    CommitObj commit =
        commitBuilder()
            .id(randomObjId())
            .seq(1L)
            .created(42L)
            .message("msg")
            .headers(EMPTY_COMMIT_HEADERS)
            .addTail(EMPTY_OBJ_ID)
            .incrementalIndex(index.serialize())
            .build();

    CommitLogic commitLogic = commitLogic(persist);
    commitLogic.storeCommit(commit, emptyList());

    soft.assertThat(persist.fetchObj(commit.id())).isEqualTo(commit);

    soft.assertThat(indexesLogic.createIndexSupplier(commit::id).get())
        .extracting(SuppliedCommitIndex::index)
        .isEqualTo(index);
  }

  @Test
  public void referenceIndex() throws Exception {
    IndexesLogic indexesLogic = indexesLogic(persist);

    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();
    StoreIndex<CommitOp> striped = indexFromStripes(indexTestSet.keyIndex().divide(5));

    ObjId referenceIndexId = indexesLogic.persistStripedIndex(striped);

    StoreIndex<CommitOp> loadedIndex =
        indexesLogic.buildReferenceIndexOnly(referenceIndexId, EMPTY_OBJ_ID);
    soft.assertThat(loadedIndex)
        .asInstanceOf(type(StoreIndex.class))
        .extracting(
            StoreIndex::elementCount, i -> i.stripes().size(), StoreIndex::first, StoreIndex::last)
        .containsExactly(
            striped.elementCount(), striped.stripes().size(), striped.first(), striped.last());
    for (int i = 0; i < loadedIndex.stripes().size(); i++) {
      soft.assertThat(newArrayList(loadedIndex.stripes().get(i)))
          .describedAs("Segment #%d", i)
          .containsExactlyElementsOf(newArrayList(striped.stripes().get(i)));
    }
    soft.assertThat(loadedIndex)
        .asInstanceOf(type(StoreIndex.class))
        .extracting(StoreIndex::asKeyList, list(StoreKey.class))
        .containsExactlyElementsOf(striped.asKeyList());
  }

  @Test
  public void buildIndexFromCommitWithReferenceIndex() throws Exception {
    IndexesLogic indexesLogic = indexesLogic(persist);

    KeyIndexTestSet<CommitOp> indexTestSet = basicIndexTestSet();
    StoreIndex<CommitOp> striped = indexFromStripes(indexTestSet.keyIndex().divide(5));

    ObjId referenceIndexId = indexesLogic.persistStripedIndex(striped);

    StoreIndex<CommitOp> incremental = newStoreIndex(COMMIT_OP_SERIALIZER);
    StoreIndexElement<CommitOp> directAdd =
        indexElement(key("d_add"), commitOp(ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> directRemove =
        indexElement(key("d_remove"), commitOp(REMOVE, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrAdd =
        indexElement(key("i_add"), commitOp(INCREMENTAL_ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrRemove =
        indexElement(key("i_remove"), commitOp(INCREMENTAL_REMOVE, 1, randomObjId()));
    incremental.add(directAdd);
    incremental.add(directRemove);
    incremental.add(incrAdd);
    incremental.add(incrRemove);

    ObjId commitId = randomObjId();
    CommitObj commit =
        commitBuilder()
            .created(42L)
            .seq(1L)
            .id(commitId)
            .addTail(EMPTY_OBJ_ID)
            .message("msg")
            .headers(EMPTY_COMMIT_HEADERS)
            .incrementalIndex(incremental.serialize())
            .referenceIndex(referenceIndexId)
            .build();

    StoreIndex<CommitOp> layered = layeredIndex(striped, incremental);

    CommitLogic commitLogic = commitLogic(persist);
    commitLogic.storeCommit(commit, emptyList());

    CommitObj loaded = requireNonNull(commitLogic.fetchCommit(commitId));
    soft.assertThat(loaded).extracting(CommitObj::referenceIndex).isEqualTo(referenceIndexId);
    StoreIndex<CommitOp> loadedIndex = indexesLogic.buildCompleteIndex(loaded, Optional.empty());

    soft.assertThat(loadedIndex)
        .asInstanceOf(type(StoreIndex.class))
        .extracting(StoreIndex::elementCount, StoreIndex::first, StoreIndex::last)
        .containsExactly(layered.elementCount(), layered.first(), layered.last());
    soft.assertThat(loadedIndex)
        .asInstanceOf(type(StoreIndex.class))
        .extracting(StoreIndex::asKeyList, list(StoreKey.class))
        .containsExactlyElementsOf(layered.asKeyList());

    StoreIndex<CommitOp> index = indexesLogic.incrementalIndexFromCommit(loaded);
    soft.assertThat(index).containsExactly(directAdd, directRemove, incrAdd, incrRemove);
    soft.assertThat(indexesLogic.commitOperations(index)).containsExactly(directAdd, directRemove);

    StoreIndex<CommitOp> incrForUpdate =
        indexesLogic.incrementalIndexForUpdate(loaded, Optional.empty());
    soft.assertThat(incrForUpdate)
        .containsExactly(
            indexElement(
                directAdd.key(), commitOp(INCREMENTAL_ADD, 1, directAdd.content().value())),
            indexElement(
                directRemove.key(),
                commitOp(INCREMENTAL_REMOVE, 1, directRemove.content().value())),
            incrAdd,
            incrRemove);
  }

  @Test
  public void perCommitOpsEmpty() {
    IndexesLogic indexesLogic = indexesLogic(persist);

    CommitObj commit =
        CommitObj.commitBuilder()
            .id(randomObjId())
            .seq(1L)
            .created(42L)
            .addTail(EMPTY_OBJ_ID)
            .message("msg")
            .headers(EMPTY_COMMIT_HEADERS)
            .incrementalIndex(emptyImmutableIndex(COMMIT_OP_SERIALIZER).serialize())
            .build();

    soft.assertThat(indexesLogic.commitOperations(commit)).isEmpty();
  }

  @Test
  public void perCommitOps() {
    IndexesLogic indexesLogic = indexesLogic(persist);

    StoreIndex<CommitOp> index = newStoreIndex(COMMIT_OP_SERIALIZER);

    StoreIndexElement<CommitOp> add1 = indexElement(key("add1"), commitOp(ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> add2 = indexElement(key("add2"), commitOp(ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> remove1 =
        indexElement(key("remove1"), commitOp(REMOVE, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrementalAdd1 =
        indexElement(key("incrementalAdd1"), commitOp(INCREMENTAL_ADD, 1, randomObjId()));
    StoreIndexElement<CommitOp> incrementalRemove2 =
        indexElement(key("incrementalRemove2"), commitOp(INCREMENTAL_REMOVE, 1, randomObjId()));

    index.add(add1);
    index.add(add2);
    index.add(remove1);
    index.add(incrementalAdd1);
    index.add(incrementalRemove2);

    CommitObj commit =
        CommitObj.commitBuilder()
            .id(randomObjId())
            .seq(1L)
            .created(42L)
            .addTail(EMPTY_OBJ_ID)
            .message("msg")
            .headers(EMPTY_COMMIT_HEADERS)
            .incrementalIndex(index.serialize())
            .build();

    soft.assertThat(index)
        .hasSize(5)
        .containsExactly(add1, add2, incrementalAdd1, incrementalRemove2, remove1);

    soft.assertThat(indexesLogic.commitOperations(commit)).containsExactly(add1, add2, remove1);
  }
}
