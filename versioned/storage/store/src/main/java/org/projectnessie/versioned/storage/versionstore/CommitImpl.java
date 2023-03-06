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
package org.projectnessie.versioned.storage.versionstore;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.Sets.newHashSetWithExpectedSize;
import static java.util.Objects.requireNonNull;
import static org.projectnessie.versioned.storage.common.logic.CreateCommit.Add.commitAdd;
import static org.projectnessie.versioned.storage.common.logic.CreateCommit.Remove.commitRemove;
import static org.projectnessie.versioned.storage.common.logic.CreateCommit.Unchanged.commitUnchanged;
import static org.projectnessie.versioned.storage.common.logic.CreateCommit.newCommitBuilder;
import static org.projectnessie.versioned.storage.common.logic.Logics.commitLogic;
import static org.projectnessie.versioned.storage.common.objtypes.CommitOp.contentIdMaybe;
import static org.projectnessie.versioned.storage.common.persist.ObjId.EMPTY_OBJ_ID;
import static org.projectnessie.versioned.storage.versionstore.RefMapping.referenceConflictException;
import static org.projectnessie.versioned.storage.versionstore.RefMapping.referenceNotFound;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.fromCommitMeta;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.keyToStoreKey;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.objIdToHash;
import static org.projectnessie.versioned.store.DefaultStoreWorker.payloadForContent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Content;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Delete;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.Operation;
import org.projectnessie.versioned.Put;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.Unchanged;
import org.projectnessie.versioned.storage.common.exceptions.CommitConflictException;
import org.projectnessie.versioned.storage.common.exceptions.ObjNotFoundException;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.indexes.StoreIndex;
import org.projectnessie.versioned.storage.common.indexes.StoreIndexElement;
import org.projectnessie.versioned.storage.common.indexes.StoreKey;
import org.projectnessie.versioned.storage.common.logic.CommitLogic;
import org.projectnessie.versioned.storage.common.logic.CommitRetry.RetryException;
import org.projectnessie.versioned.storage.common.logic.CreateCommit;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.objtypes.CommitOp;
import org.projectnessie.versioned.storage.common.objtypes.ContentValueObj;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;

class CommitImpl extends BaseCommitHelper {

  CommitImpl(
      @Nonnull @jakarta.annotation.Nonnull BranchName branch,
      @Nonnull @jakarta.annotation.Nonnull Optional<Hash> referenceHash,
      @Nonnull @jakarta.annotation.Nonnull Persist persist,
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nullable @jakarta.annotation.Nullable CommitObj head)
      throws ReferenceNotFoundException {
    super(branch, referenceHash, persist, reference, head);
  }

  /**
   * Keeps state between commit retries to avoid duplicate {@link
   * org.projectnessie.versioned.storage.common.objtypes.ContentValueObj value objects} for new
   * contents, which would otherwise get a new content-id during every commit retry, therefore
   * pollute the database. Also keeps track of which objects have already been successfully stored
   * in the database, which speeds up retries, which do not need to persist the same content values
   * again.
   */
  static class CommitRetryState {
    final Set<ObjId> storedContents = new HashSet<>();
    final Map<Key, String> generatedContentIds = new HashMap<>();
  }

  Hash commit(
      @Nonnull @jakarta.annotation.Nonnull Optional<?> retryState,
      @Nonnull @jakarta.annotation.Nonnull CommitMeta metadata,
      @Nonnull @jakarta.annotation.Nonnull List<Operation> operations,
      @Nonnull @jakarta.annotation.Nonnull Callable<Void> validator,
      @Nonnull @jakarta.annotation.Nonnull BiConsumer<Key, String> addedContents)
      throws ReferenceNotFoundException,
          ReferenceConflictException,
          RetryException,
          ObjTooLargeException {
    try {
      validator.call();
    } catch (RuntimeException e) {
      // just propagate the RuntimeException up
      throw e;
    } catch (Exception e) {
      throw new RuntimeException(e);
    }

    CreateCommit.Builder commit = newCommitBuilder().parentCommitId(headId());
    List<Obj> objectsToStore = new ArrayList<>(operations.size());

    CommitRetryState commitRetryState =
        retryState.map(x -> (CommitRetryState) x).orElseGet(CommitRetryState::new);

    Consumer<Obj> valueConsumer =
        obj -> {
          if (commitRetryState.storedContents.add(obj.id())) {
            objectsToStore.add(obj);
          }
        };

    try {
      commitAddOperations(operations, commit, valueConsumer, commitRetryState);
    } catch (ObjNotFoundException e) {
      throw new IllegalStateException("Content value objects not found", e);
    }

    fromCommitMeta(metadata, commit);

    ObjId newHead;
    try {
      CommitLogic commitLogic = commitLogic(persist);
      newHead = commitLogic.doCommit(commit.build(), objectsToStore);
    } catch (CommitConflictException e) {
      throw referenceConflictException(e);
    } catch (ObjNotFoundException e) {
      throw referenceNotFound(e);
    }

    checkState(
        newHead != null,
        "Hash collision detected, a commit with the same parent commit, commit message, "
            + "headers/commit-metadata and operations already exists");

    bumpReferencePointer(newHead, Optional.of(commitRetryState));

    commitRetryState.generatedContentIds.forEach(addedContents);

    return objIdToHash(newHead);
  }

  void commitAddOperations(
      List<Operation> operations,
      CreateCommit.Builder commit,
      Consumer<Obj> contentToStore,
      CommitRetryState commitRetryState)
      throws ObjNotFoundException {
    Set<Key> allKeys = new HashSet<>();

    Set<StoreKey> storeKeysForHead =
        expectedIndex() != headIndex() ? newHashSetWithExpectedSize(operations.size()) : null;

    List<StoreKey> storeKeys = new ArrayList<>();
    for (Operation operation : operations) {
      Key key = operation.getKey();
      checkArgument(allKeys.add(key), "Duplicate key in commit operations: %s", key);
      StoreKey storeKey = keyToStoreKey(key);
      storeKeys.add(storeKey);
      if (storeKeysForHead != null && operation instanceof Unchanged) {
        storeKeysForHead.add(storeKey);
      }
    }

    expectedIndex().loadIfNecessary(new HashSet<>(storeKeys));
    if (storeKeysForHead != null) {
      headIndex().loadIfNecessary(storeKeysForHead);
    }

    Map<UUID, StoreKey> deleted = new HashMap<>();
    for (int i = 0; i < operations.size(); i++) {
      Operation operation = operations.get(i);
      StoreKey storeKey = storeKeys.get(i);

      if (operation instanceof Put) {
        commitAddPut(
            expectedIndex(),
            commit,
            (Put) operation,
            storeKey,
            contentToStore,
            commitRetryState,
            deleted);
      } else if (operation instanceof Delete) {
        commitAddDelete(expectedIndex(), commit, storeKey, deleted);
      } else if (operation instanceof Unchanged) {
        commitAddUnchanged(headIndex(), expectedIndex(), commit, storeKey);
      }
    }
  }

  private static void commitAddUnchanged(
      StoreIndex<CommitOp> headIndex,
      StoreIndex<CommitOp> expectedIndex,
      CreateCommit.Builder commit,
      StoreKey storeKey) {
    // nothing to do, if head == expected
    if (headIndex != expectedIndex) {
      StoreIndexElement<CommitOp> expectedElement = expectedIndex.get(storeKey);

      int payload = 0;
      ObjId expectedValue = null;
      UUID expectedContentID = null;

      // TODO add much stricter handling of Delete against existing content, but that requires
      //  changes to the model
      // TODO require expectedContent for existing content
      // TODO validate content-ID in store-index against content-ID in operation

      if (expectedElement != null) {
        CommitOp content = expectedElement.content();
        if (content.action().exists()) {
          payload = content.payload();
          expectedValue = content.value();
          expectedContentID = content.contentId();
        }
      }

      if (expectedValue == null) {
        expectedValue = EMPTY_OBJ_ID;
      }

      commit.addUnchanged(commitUnchanged(storeKey, payload, expectedValue, expectedContentID));
    }
  }

  private void commitAddDelete(
      StoreIndex<CommitOp> expectedIndex,
      CreateCommit.Builder commit,
      StoreKey storeKey,
      Map<UUID, StoreKey> deleted) {
    StoreIndexElement<CommitOp> existingElement = expectedIndex.get(storeKey);

    int payload = 0;
    ObjId existingValue = null;
    UUID existingContentID = null;

    // TODO add much stricter handling of Delete against existing content, but that requires changes
    //  to the model
    // TODO require expectedContent for existing content
    // TODO validate content-ID in store-index against content-ID in operation

    if (existingElement != null) {
      CommitOp content = existingElement.content();
      if (content.action().exists()) {
        payload = content.payload();
        existingValue = content.value();
        existingContentID = content.contentId();
        deleted.put(existingContentID, storeKey);
      }
    }

    if (existingValue == null) {
      existingValue = EMPTY_OBJ_ID;
    }

    // TODO remove other existing variants beside TypeMapping.CONTENT_DISCRIMINATOR.
    //  Plan: shorten the storeKey (remove CONTENT_DISCRIMINATOR), iterate over the headIndex()
    //  and add removes for all the found keys.

    commit.addRemoves(commitRemove(storeKey, payload, existingValue, existingContentID));
  }

  private void commitAddPut(
      StoreIndex<CommitOp> expectedIndex,
      CreateCommit.Builder commit,
      Put put,
      StoreKey storeKey,
      Consumer<Obj> contentToStore,
      CommitRetryState commitRetryState,
      Map<UUID, StoreKey> deleted)
      throws ObjNotFoundException {
    Content putValue = put.getValue();
    Key putKey = put.getKey();
    String putValueId = putValue.getId();

    int payload = payloadForContent(putValue);
    ObjId existingValue = null;
    UUID existingContentID;
    String expectedContentIDString;

    Content expectedContent = put.getExpectedValue();

    ContentMapping contentMapping = new ContentMapping(persist);

    StoreIndexElement<CommitOp> existing = expectedIndex.get(storeKey);
    String expectedUUID = expectedContent != null ? expectedContent.getId() : null;
    if (existing == null && expectedUUID != null) {
      // Check for a Delete-op in the same commit, representing a rename operation.
      UUID expectedContentID = UUID.fromString(expectedUUID);
      StoreKey deletedKey = deleted.remove(expectedContentID);
      if (deletedKey != null) {
        existing = expectedIndex.get(deletedKey);
      }
    }

    boolean exists = false;
    if (existing != null) {
      CommitOp content = existing.content();
      if (content.action().exists()) {
        payload = content.payload();
        existingValue = requireNonNull(content.value());
        existingContentID = content.contentId();
        expectedContentIDString =
            existingContentID != null
                ? existingContentID.toString()
                : contentIdFromContent(existingValue);

        checkArgument(
            putValueId != null, "New value to update existing key '%s' has no content iD", putKey);

        checkArgument(
            expectedContentIDString.equals(putValueId),
            "Key '%s' already exists with content ID %s, which is different from "
                + "the content ID %s in the operation",
            putKey,
            expectedContentIDString,
            putValueId);

        checkArgument(
            expectedContent != null,
            "Key '%s' already exists, but Put-operation has no expectedValue",
            putKey);

        checkArgument(
            expectedUUID != null, "Expected value to update key '%s' has no content iD", putKey);

        exists = true;
      }
    }
    if (!exists) {
      checkArgument(
          expectedContent == null,
          "Key '%s' does not exist, but Put-operation has expectedValue",
          putKey);

      checkArgument(putValueId == null, "New value for new must not have a content iD");

      putValueId =
          commitRetryState.generatedContentIds.computeIfAbsent(
              putKey, x -> UUID.randomUUID().toString());
      putValue = contentMapping.assignContentId(putValue, putValueId);
    }

    checkState(
        putValueId != null, "INTERNAL: Must only persist a Content object with a content ID");

    ContentValueObj value = contentMapping.buildContent(putValue, payload);
    contentToStore.accept(value);
    ObjId valueId = requireNonNull(value.id());

    // Note: the content-ID from legacy, imported Nessie repositories could theoretically been
    // any string value. If it's a UUID, use it, otherwise ignore it down the road.
    UUID contentId = contentIdMaybe(putValueId);
    commit.addAdds(commitAdd(storeKey, payload, valueId, existingValue, contentId));
  }

  private String contentIdFromContent(@Nonnull @jakarta.annotation.Nonnull ObjId contentValueId)
      throws ObjNotFoundException {
    return new ContentMapping(persist).fetchContent(contentValueId).getId();
  }
}
