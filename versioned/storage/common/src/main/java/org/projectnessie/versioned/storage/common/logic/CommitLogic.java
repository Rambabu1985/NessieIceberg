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
package org.projectnessie.versioned.storage.common.logic;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.projectnessie.versioned.storage.common.config.StoreConfig;
import org.projectnessie.versioned.storage.common.exceptions.CommitConflictException;
import org.projectnessie.versioned.storage.common.exceptions.ObjNotFoundException;
import org.projectnessie.versioned.storage.common.indexes.StoreIndex;
import org.projectnessie.versioned.storage.common.indexes.StoreKey;
import org.projectnessie.versioned.storage.common.logic.CreateCommit.Add;
import org.projectnessie.versioned.storage.common.logic.CreateCommit.Remove;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.objtypes.CommitObjReference;
import org.projectnessie.versioned.storage.common.objtypes.CommitOp;
import org.projectnessie.versioned.storage.common.objtypes.CommitType;
import org.projectnessie.versioned.storage.common.objtypes.ContentValueObj;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;

/** Logic to read commits and perform commits including conflict checks. */
public interface CommitLogic {

  @Nonnull
  @jakarta.annotation.Nonnull
  PagedResult<CommitObj, ObjId> commitLog(
      @Nonnull @jakarta.annotation.Nonnull CommitLogQuery commitLogQuery);

  @Nonnull
  @jakarta.annotation.Nonnull
  PagedResult<ObjId, ObjId> commitIdLog(
      @Nonnull @jakarta.annotation.Nonnull CommitLogQuery commitLogQuery);

  @Nonnull
  @jakarta.annotation.Nonnull
  PagedResult<DiffEntry, StoreKey> diff(@Nonnull @jakarta.annotation.Nonnull DiffQuery diffQuery);

  /**
   * Adds a new commit on top of its parent commit, performing checks of the existing vs expected
   * contents of the {@link CreateCommit#adds() adds} and {@link CreateCommit#removes() removes}.
   *
   * <h3>{@link CommitObj#tail Parent tail}</h3>
   *
   * The {@link CreateCommit#parentCommitId() direct parent commit ID} is added as the first element
   * in the persisted {@link CommitObj#tail()}, with up to {@link StoreConfig#parentsPerCommit()
   * parentsPerCommit - 1} entries from the parent commit tail.
   *
   * <h3>Checks on each {@link Add Add} in {@link CreateCommit#adds()}</h3>
   *
   * <ol>
   *   <li>If {@link Add#expectedValue() expected value} is {@code null}:
   *       <ol>
   *         <li>The {@link Add#key() key} to add must not exist.
   *       </ol>
   *   <li>If {@link Add#expectedValue() expected value} is not {@code null}:
   *       <ol>
   *         <li>The {@link Add#key() key} to add must exist.
   *         <li>The {@link Add#payload()} must match the {@link CommitOp#payload()} in the {@link
   *             StoreIndex store-index} of the {@link CreateCommit#parentCommitId() parent commit}.
   *         <li>The {@link Add#expectedValue() expected value} must match the {@link
   *             CommitOp#value() value} in the {@link StoreIndex store-index} of the {@link
   *             CreateCommit#parentCommitId() parent commit}.
   *       </ol>
   * </ol>
   *
   * <h3>Checks on each {@link Remove Remove} in {@link CreateCommit#removes()}</h3>
   *
   * <ol>
   *   <li>The {@link Remove#key() key} to remove must exist.
   *   <li>The {@link Remove#expectedValue() expected value} of the key to remove must match the
   *       {@link CommitOp#value() value} in the {@link StoreIndex store-index} of the {@link
   *       CreateCommit#parentCommitId() parent commit}.
   *   <li>The {@link Remove#payload() payload} in the {@link Remove Remove} must match the {@link
   *       CommitOp#payload() payload} in the {@link StoreIndex store-index} of the {@link
   *       CreateCommit#parentCommitId() parent commit}.
   * </ol>
   *
   * <h3>Initial commit (parent equals "no ancestor hash")</h3>
   *
   * All checks and operations described above apply.
   *
   * @param createCommit parameters for {@link #buildCommitObj(CreateCommit, ConflictHandler,
   *     CommitOpHandler)}
   * @param additionalObjects additional {@link Obj}s to store, for example {@link ContentValueObj}
   * @return the non-null object ID if the commit was stored as a new record or {@code null} if an
   *     object with the same ID already exists.
   * @see #buildCommitObj(CreateCommit, ConflictHandler, CommitOpHandler)
   * @see #storeCommit(CommitObj, List)
   */
  @Nullable
  @jakarta.annotation.Nullable
  ObjId doCommit(
      @Nonnull @jakarta.annotation.Nonnull CreateCommit createCommit,
      @Nonnull @jakarta.annotation.Nonnull List<Obj> additionalObjects)
      throws CommitConflictException, ObjNotFoundException;

  /**
   * Stores a new commit and handles storing the (external) {@link CommitObj#referenceIndex()
   * reference index}, when the {@link CommitObj#incrementalIndex() incremental index} becomes too
   * big.
   *
   * @param commit commit to store
   * @param additionalObjects additional {@link Obj}s to store, for example {@link ContentValueObj}
   * @return commit ID, if the commit is new, or {@code null} - see {@link Persist#storeObj(Obj)}
   * @see #doCommit(CreateCommit, List)
   * @see #buildCommitObj(CreateCommit, ConflictHandler, CommitOpHandler)
   * @see #updateCommit(CommitObj)
   */
  boolean storeCommit(
      @Nonnull @jakarta.annotation.Nonnull CommitObj commit,
      @Nonnull @jakarta.annotation.Nonnull List<Obj> additionalObjects);

  /**
   * Updates an <em>existing</em> commit and handles storing the (external) {@link
   * CommitObj#referenceIndex() reference index}, when the {@link CommitObj#incrementalIndex()
   * incremental index} becomes too big.
   *
   * @param commit the commit to update
   */
  void updateCommit(@Nonnull @jakarta.annotation.Nonnull CommitObj commit)
      throws ObjNotFoundException;

  /**
   * Similar to {@link #doCommit(CreateCommit, List)}, but does not persist the {@link CommitObj}
   * and allows conflict handling.
   *
   * @see #doCommit(CreateCommit, List)
   * @see #storeCommit(CommitObj, List)
   */
  @Nonnull
  @jakarta.annotation.Nonnull
  CommitObj buildCommitObj(
      @Nonnull @jakarta.annotation.Nonnull CreateCommit createCommit,
      @Nonnull @jakarta.annotation.Nonnull ConflictHandler conflictHandler,
      CommitOpHandler commitOpHandler)
      throws CommitConflictException, ObjNotFoundException;

  @Nonnull
  @jakarta.annotation.Nonnull
  ObjId findCommonAncestor(
      @Nonnull @jakarta.annotation.Nonnull ObjId headId,
      @Nonnull @jakarta.annotation.Nonnull ObjId otherId)
      throws NoSuchElementException;

  /**
   * Retrieves the {@link CommitOp commit object} referenced by {@code commitId}. Resolves a {@link
   * CommitObjReference}, if necessary.
   */
  @Nullable
  @jakarta.annotation.Nullable
  CommitObj fetchCommit(@Nonnull @jakarta.annotation.Nonnull ObjId commitId)
      throws ObjNotFoundException;

  /**
   * Applies the changes between {@code base} and {@code mostRecent} to the commit builder.
   *
   * <p>Used to squash multiple commits and optionally, when using a different {@link
   * CreateCommit.Builder#parentCommitId(ObjId) parent commit}, provide the operations for a merge
   * commit.
   *
   * @return value of {@code createCommit}
   */
  @Nonnull
  @jakarta.annotation.Nonnull
  CreateCommit.Builder diffToCreateCommit(
      @Nonnull @jakarta.annotation.Nonnull PagedResult<DiffEntry, StoreKey> diff,
      @Nonnull @jakarta.annotation.Nonnull CreateCommit.Builder createCommit);

  @Nullable
  @jakarta.annotation.Nullable
  CommitObj headCommit(@Nonnull @jakarta.annotation.Nonnull Reference reference)
      throws ObjNotFoundException;

  /**
   * Identifies all heads and fork-points.
   *
   * <ul>
   *   <li>"Heads" are commits that are not referenced by other commits.
   *   <li>"Fork points" are commits that are the parent of more than one other commit. Knowing
   *       these commits can help to optimize the traversal of commit logs of multiple heads.
   * </ul>
   *
   * <p>{@link CommitType#INTERNAL internal commits} are excluded from this calculation.
   *
   * <p>It is possible that databases have to scan all rows/items in the tables/collections, which
   * can lead to a <em>very</em> long runtime of this method.
   *
   * @param expectedCommitCount it is recommended to tell the implementation the total number of
   *     commits in the Nessie repository
   * @param commitHandler called for every commit while scanning all commits
   */
  HeadsAndForkPoints identifyAllHeadsAndForkPoints(
      int expectedCommitCount, Consumer<CommitObj> commitHandler);
}
