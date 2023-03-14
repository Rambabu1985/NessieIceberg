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
import static java.lang.String.format;
import static java.util.Objects.requireNonNull;
import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.projectnessie.model.Content.Type.NAMESPACE;
import static org.projectnessie.versioned.MergeResult.KeyDetails.keyDetails;
import static org.projectnessie.versioned.storage.common.logic.CommitConflict.ConflictType.KEY_EXISTS;
import static org.projectnessie.versioned.storage.common.logic.CommitLogQuery.commitLogQuery;
import static org.projectnessie.versioned.storage.common.logic.CommitRetry.commitRetry;
import static org.projectnessie.versioned.storage.common.logic.Logics.commitLogic;
import static org.projectnessie.versioned.storage.common.logic.Logics.indexesLogic;
import static org.projectnessie.versioned.storage.common.persist.ObjId.EMPTY_OBJ_ID;
import static org.projectnessie.versioned.storage.versionstore.RefMapping.referenceConflictException;
import static org.projectnessie.versioned.storage.versionstore.RefMapping.referenceNotFound;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.hashToObjId;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.keyToStoreKey;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.objIdToHash;
import static org.projectnessie.versioned.storage.versionstore.TypeMapping.storeKeyToKey;
import static org.projectnessie.versioned.store.DefaultStoreWorker.contentTypeForPayload;
import static org.projectnessie.versioned.store.DefaultStoreWorker.payloadForContent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import org.agrona.collections.Object2IntHashMap;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.Namespace;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Commit;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ImmutableMergeResult;
import org.projectnessie.versioned.MergeResult;
import org.projectnessie.versioned.MergeResult.ConflictType;
import org.projectnessie.versioned.MergeResult.KeyDetails;
import org.projectnessie.versioned.MergeType;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.ReferenceRetryFailureException;
import org.projectnessie.versioned.storage.common.exceptions.CommitConflictException;
import org.projectnessie.versioned.storage.common.exceptions.CommitWrappedException;
import org.projectnessie.versioned.storage.common.exceptions.ObjNotFoundException;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.exceptions.RefConditionFailedException;
import org.projectnessie.versioned.storage.common.exceptions.RefNotFoundException;
import org.projectnessie.versioned.storage.common.exceptions.RetryTimeoutException;
import org.projectnessie.versioned.storage.common.indexes.StoreIndex;
import org.projectnessie.versioned.storage.common.indexes.StoreIndexElement;
import org.projectnessie.versioned.storage.common.indexes.StoreKey;
import org.projectnessie.versioned.storage.common.logic.CommitLogic;
import org.projectnessie.versioned.storage.common.logic.CommitRetry;
import org.projectnessie.versioned.storage.common.logic.CommitRetry.RetryException;
import org.projectnessie.versioned.storage.common.logic.ConflictHandler.ConflictResolution;
import org.projectnessie.versioned.storage.common.logic.CreateCommit;
import org.projectnessie.versioned.storage.common.logic.IndexesLogic;
import org.projectnessie.versioned.storage.common.logic.PagedResult;
import org.projectnessie.versioned.storage.common.objtypes.CommitObj;
import org.projectnessie.versioned.storage.common.objtypes.CommitOp;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.common.persist.Reference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class BaseCommitHelper {
  private static final Logger LOGGER = LoggerFactory.getLogger(BaseCommitHelper.class);

  final Persist persist;

  final BranchName branch;
  final Optional<Hash> referenceHash;
  final Reference reference;
  final CommitObj head;
  final CommitObj expected;

  BaseCommitHelper(
      @Nonnull @jakarta.annotation.Nonnull BranchName branch,
      @Nonnull @jakarta.annotation.Nonnull Optional<Hash> referenceHash,
      @Nonnull @jakarta.annotation.Nonnull Persist persist,
      @Nonnull @jakarta.annotation.Nonnull Reference reference,
      @Nullable @jakarta.annotation.Nullable CommitObj head)
      throws ReferenceNotFoundException {
    this.branch = branch;
    this.referenceHash = referenceHash;
    this.persist = persist;
    this.reference = reference;
    this.head = head;

    // Need to perform this "expected commit" load here as a validation that 'referenceHash' points
    // to an existing commit (and also to produce a "proper" exception message downstream)
    CommitObj e = head;
    if (referenceHash.isPresent()) {
      ObjId referenceObjId = hashToObjId(referenceHash.get());
      if (!referenceObjId.equals(headId())) {
        RefMapping refMapping = new RefMapping(persist);
        e = refMapping.commitInChain(branch, head, referenceHash);
      }
    }
    this.expected = e;
  }

  ObjId headId() {
    return head != null ? head.id() : EMPTY_OBJ_ID;
  }

  @FunctionalInterface
  interface CommitterSupplier<I> {
    I newCommitter(
        @Nonnull @jakarta.annotation.Nonnull BranchName branch,
        @Nonnull @jakarta.annotation.Nonnull Optional<Hash> referenceHash,
        @Nonnull @jakarta.annotation.Nonnull Persist persist,
        @Nonnull @jakarta.annotation.Nonnull Reference reference,
        @Nullable @jakarta.annotation.Nullable CommitObj head)
        throws ReferenceNotFoundException;
  }

  @FunctionalInterface
  interface CommittingFunction<R, I> {

    /**
     * Performs a committing operation attempt.
     *
     * @param impl implementation performing the committing operation
     * @param retryState The initial call to this function will receive an empty value, subsequent
     *     calls receive the parameter passed to the previous {@link RetryException}.
     */
    R perform(I impl, Optional<?> retryState)
        throws ReferenceNotFoundException,
            ReferenceConflictException,
            RetryException,
            ObjTooLargeException;
  }

  static <R, I> R committingOperation(
      @Nonnull @jakarta.annotation.Nonnull String operationName,
      @Nonnull @jakarta.annotation.Nonnull BranchName branch,
      @Nonnull @jakarta.annotation.Nonnull Optional<Hash> referenceHash,
      @Nonnull @jakarta.annotation.Nonnull Persist persist,
      @Nonnull @jakarta.annotation.Nonnull CommitterSupplier<I> committerSupplier,
      @Nonnull @jakarta.annotation.Nonnull CommittingFunction<R, I> committingFunction)
      throws ReferenceConflictException, ReferenceNotFoundException {
    try {
      return commitRetry(
          persist,
          (p, retryState) -> {
            RefMapping refMapping = new RefMapping(p);
            Reference reference;
            try {
              reference = refMapping.resolveNamedRef(branch);
            } catch (ReferenceNotFoundException e) {
              throw new CommitWrappedException(e);
            }

            try {
              CommitObj head = commitLogic(p).headCommit(reference);
              I committer =
                  committerSupplier.newCommitter(branch, referenceHash, p, reference, head);
              return committingFunction.perform(committer, retryState);
            } catch (ReferenceConflictException
                | ReferenceNotFoundException
                | ObjNotFoundException
                | ObjTooLargeException e) {
              throw new CommitWrappedException(e);
            }
          });
    } catch (CommitConflictException e) {
      throw referenceConflictException(e);
    } catch (CommitWrappedException e) {
      Throwable c = e.getCause();
      if (c instanceof ReferenceNotFoundException) {
        throw (ReferenceNotFoundException) c;
      }
      if (c instanceof ReferenceConflictException) {
        throw (ReferenceConflictException) c;
      }
      if (c instanceof RuntimeException) {
        throw (RuntimeException) c;
      }
      throw new RuntimeException(c);
    } catch (RetryTimeoutException e) {
      long millis = NANOSECONDS.toMillis(e.getTimeNanos());
      String msg =
          format(
              "The %s operation could not be performed after %d retries within the configured commit timeout after %d milliseconds",
              operationName, e.getRetry(), millis);
      LOGGER.warn("Operation timeout: {}", msg);
      throw new ReferenceRetryFailureException(msg, e.getRetry(), millis);
    }
  }

  void validateNamespacesToDeleteHaveNoChildren(
      Object2IntHashMap<ContentKey> allKeysToDelete, StoreIndex<CommitOp> headIndex)
      throws ReferenceConflictException {
    if (!persist.config().validateNamespaces()) {
      return;
    }

    int payloadNamespace = payloadForContent(NAMESPACE);

    try {
      allKeysToDelete.forEach(
          (namespaceKey, payload) -> {
            if (payloadNamespace != payload) {
              return;
            }

            StoreKey storeKey = keyToStoreKey(namespaceKey);

            for (Iterator<StoreIndexElement<CommitOp>> iter =
                    headIndex.iterator(storeKey, null, false);
                iter.hasNext(); ) {
              StoreIndexElement<CommitOp> el = iter.next();
              if (!el.content().action().exists()) {
                // element does not exist - ignore
                continue;
              }
              ContentKey elContentKey = storeKeyToKey(el.key());
              if (elContentKey == null) {
                // not a "content object" - ignore
                continue;
              }
              if (elContentKey.equals(namespaceKey)) {
                // this is our namespace - ignore
                continue;
              }
              if (allKeysToDelete.containsKey(elContentKey)) {
                // this key is being deleted as well - ignore
                continue;
              }

              int elLen = elContentKey.getElementCount();
              int nsLen = namespaceKey.getElementCount();

              // check if element is in the current namespace, fail it is true - this means,
              // there is a live content-key in the current namespace - must not delete the
              // namespace
              ContentKey truncatedElContentKey = elContentKey.truncateToLength(nsLen);
              int cmp = truncatedElContentKey.compareTo(namespaceKey);
              checkArgument(
                  elLen < nsLen || cmp != 0,
                  "The namespace '%s' would be deleted, but cannot, because it has children.",
                  namespaceKey);
              if (cmp > 0) {
                // iterated past the namespaceKey - break
                break;
              }
            }
          });
    } catch (IllegalArgumentException iae) {
      throw new ReferenceConflictException(iae.getMessage());
    }
  }

  void validateNamespacesExistForContentKeys(
      Map<ContentKey, Content> newContent, StoreIndex<CommitOp> headIndex)
      throws ReferenceConflictException {
    if (!persist.config().validateNamespaces()) {
      return;
    }
    if (newContent.isEmpty()) {
      return;
    }

    Set<ContentKey> namespaceKeys =
        newContent.keySet().stream()
            .filter(k -> k.getElementCount() > 1)
            .map(ContentKey::getParent)
            .collect(Collectors.toSet());

    for (ContentKey key : namespaceKeys) {
      Content namespaceAddedInThisCommit = newContent.get(key);
      if (namespaceAddedInThisCommit instanceof Namespace) {
        // Namespace for the current new-content-key has been added via the currently validated
        // commit. Nothing to do for `Namespace`s here.
        continue;
      }

      StoreIndexElement<CommitOp> ns = headIndex.get(keyToStoreKey(key));
      if (ns == null || !ns.content().action().exists()) {
        throw new ReferenceConflictException(format("Namespace '%s' must exist.", key));
      }
      CommitOp nsContent = ns.content();
      if (nsContent.payload() != payloadForContent(Content.Type.NAMESPACE)) {
        throw new ReferenceConflictException(
            format(
                "Expecting the key '%s' to be a namespace, but is not a namespace. "
                    + "Using a content object that is not a namespace as a namespace is forbidden.",
                key));
      }
    }
  }

  void verifyMergeTransplantCommitPolicies(
      StoreIndex<CommitOp> headIndex, CommitObj inspectedCommit) throws ReferenceConflictException {

    Map<ContentKey, Content> checkContents = new HashMap<>();
    Object2IntHashMap<ContentKey> deletedKeysAndPayload = new Object2IntHashMap<>(-1);

    IndexesLogic indexesLogic = indexesLogic(persist);
    for (StoreIndexElement<CommitOp> el : indexesLogic.commitOperations(inspectedCommit)) {
      StoreIndexElement<CommitOp> expected = headIndex.get(el.key());
      ObjId expectedId = null;
      if (expected != null) {
        CommitOp expectedContent = expected.content();
        if (expectedContent.action().exists()) {
          expectedId = expectedContent.value();
        }
      }

      CommitOp op = el.content();
      if (op.action().exists()) {
        ObjId value = requireNonNull(op.value());

        if (expectedId == null) {
          ContentKey contentKey = storeKeyToKey(el.key());
          // 'contentKey' will be 'null', if the store-key is not in the MAIN_UNIVERSE or the
          // variant is not CONTENT_DISCRIMINATOR.
          checkState(
              contentKey != null,
              "Merge/transplant with non-content-object store-keys is not implemented.");

          try {
            Content content = new ContentMapping(persist).fetchContent(value);
            checkContents.put(contentKey, content);
          } catch (ObjNotFoundException e) {
            throw new RuntimeException(e);
          }
        }
      } else {
        ContentKey contentKey = storeKeyToKey(el.key());
        // 'contentKey' will be 'null', if the store-key is not in the MAIN_UNIVERSE or the
        // variant is not CONTENT_DISCRIMINATOR.
        checkState(
            contentKey != null,
            "Merge/transplant with non-content-object store-keys is not implemented.");
        deletedKeysAndPayload.put(contentKey, op.payload());
      }
    }

    validateNamespacesExistForContentKeys(checkContents, headIndex);
    validateNamespacesToDeleteHaveNoChildren(deletedKeysAndPayload, headIndex);
  }

  /** Source commits for merge and transplant operations. */
  static final class SourceCommitsAndParent {

    /** Source commits in chronological order, most recent commit last. */
    final List<CommitObj> sourceCommits;
    /** Parent of the oldest commit. */
    final CommitObj sourceParent;

    SourceCommitsAndParent(List<CommitObj> sourceCommits, CommitObj sourceParent) {
      this.sourceCommits = sourceCommits;
      this.sourceParent = sourceParent;
    }

    CommitObj mostRecent() {
      return sourceCommits.get(sourceCommits.size() - 1);
    }
  }

  SourceCommitsAndParent loadSourceCommitsPlusParent(List<Hash> commitHashes)
      throws ReferenceNotFoundException {
    checkArgument(!commitHashes.isEmpty(), "List with source commits must not be empty");

    Obj[] objs;
    try {
      objs =
          persist.fetchObjs(
              commitHashes.stream().map(TypeMapping::hashToObjId).toArray(ObjId[]::new));
    } catch (ObjNotFoundException e) {
      throw referenceNotFound(e);
    }
    List<CommitObj> commits = new ArrayList<>(commitHashes.size());
    CommitObj parent = null;
    CommitLogic commitLogic = commitLogic(persist);
    for (int i = 0; i < objs.length; i++) {
      Obj o = objs[i];
      if (o == null) {
        throw RefMapping.hashNotFound(commitHashes.get(i));
      }
      CommitObj commit = (CommitObj) o;
      if (i > 0) {
        if (!commit.directParent().equals(commits.get(i - 1).id())) {
          throw new IllegalArgumentException("Sequence of hashes is not contiguous.");
        }
      } else {
        try {
          parent = commitLogic.fetchCommit(commit.directParent());
        } catch (ObjNotFoundException e) {
          throw referenceNotFound(e);
        }
      }
      commits.add(commit);
    }

    return new SourceCommitsAndParent(commits, parent);
  }

  SourceCommitsAndParent loadSourceCommitsPlusParent(
      @Nonnull @jakarta.annotation.Nonnull ObjId startCommitId,
      @Nonnull @jakarta.annotation.Nonnull ObjId endCommitId) {
    CommitLogic commitLogic = commitLogic(persist);
    List<CommitObj> commits = new ArrayList<>();
    for (PagedResult<CommitObj, ObjId> commitLog =
            commitLogic.commitLog(commitLogQuery(null, startCommitId, endCommitId));
        commitLog.hasNext(); ) {
      CommitObj commit = commitLog.next();
      if (commit.id().equals(endCommitId)) {
        Collections.reverse(commits);
        return new SourceCommitsAndParent(commits, commit);
      }
      commits.add(commit);
    }

    // Ends here, if 'endCommitId' is NO_ANCESTOR
    Collections.reverse(commits);
    return new SourceCommitsAndParent(commits, null);
  }

  ImmutableMergeResult<Commit> mergeSquashFastForward(
      boolean dryRun,
      ObjId fromId,
      CommitObj source,
      ImmutableMergeResult.Builder<Commit> result,
      Function<ContentKey, MergeType> mergeTypeForKey)
      throws RetryException {
    result.wasSuccessful(true);

    IndexesLogic indexesLogic = indexesLogic(persist);
    for (StoreIndexElement<CommitOp> el : indexesLogic.incrementalIndexFromCommit(source)) {
      if (el.content().action().currentCommit()) {
        StoreKey k = el.key();
        ContentKey key = storeKeyToKey(k);
        // Note: key==null, if not the "main universe" or not a "content" discriminator
        if (key != null) {
          result.putDetails(key, keyDetails(mergeTypeForKey.apply(key), ConflictType.NONE));
        }
      }
    }

    // Only need bump the reference pointer
    if (!dryRun) {
      bumpReferencePointer(fromId, Optional.empty());
      result.wasApplied(true).resultantTargetHash(objIdToHash(fromId));
    }

    return result.build();
  }

  ImmutableMergeResult.Builder<Commit> prepareMergeResult() {
    ImmutableMergeResult.Builder<Commit> mergeResult =
        MergeResult.<Commit>builder()
            .targetBranch(branch)
            .effectiveTargetHash(objIdToHash(headId()));

    referenceHash.ifPresent(mergeResult::expectedHash);
    return mergeResult;
  }

  ObjId identifyCommonAncestor(ObjId fromId) throws ReferenceNotFoundException {
    CommitLogic commitLogic = commitLogic(persist);
    ObjId commonAncestorId;
    try {
      commonAncestorId = commitLogic.findCommonAncestor(headId(), fromId);
    } catch (NoSuchElementException notFound) {
      throw new ReferenceNotFoundException(notFound.getMessage());
    }
    return commonAncestorId;
  }

  CommitObj createMergeTransplantCommit(
      Function<ContentKey, MergeType> mergeTypeForKey,
      Map<ContentKey, KeyDetails> keyDetailsMap,
      CreateCommit createCommit)
      throws ReferenceNotFoundException {
    try {
      CommitLogic commitLogic = commitLogic(persist);
      return commitLogic.buildCommitObj(
          createCommit,
          conflict -> {
            ContentKey key = storeKeyToKey(conflict.key());
            // Note: key==null, if not the "main universe" or not a "content"
            // discriminator
            if (key != null) {
              if (conflict.conflictType() == KEY_EXISTS) {
                // This is rather a hack to ignore conflicts when merging namespaces. If both the
                // source and target payload is NAMESPACE, let the target content "win" (aka no
                // change).
                CommitOp op = conflict.op();
                CommitOp ex = conflict.existing();
                if (op != null
                    && ex != null
                    && op.payload() == ex.payload()
                    && contentTypeForPayload((byte) op.payload()) == NAMESPACE) {
                  return ConflictResolution.IGNORE;
                }
              }
              MergeType mergeType = mergeTypeForKey.apply(key);
              switch (mergeType) {
                case NORMAL:
                  keyDetailsMap.put(key, keyDetails(mergeType, ConflictType.UNRESOLVABLE));
                  return ConflictResolution.IGNORE;
                case FORCE:
                  keyDetailsMap.put(key, keyDetails(mergeType, ConflictType.NONE));
                  return ConflictResolution.IGNORE;
                case DROP:
                  keyDetailsMap.put(key, keyDetails(mergeType, ConflictType.NONE));
                  return ConflictResolution.DROP;
                default:
                  throw new IllegalStateException(
                      "Unknown merge type " + mergeTypeForKey.apply(key));
              }
            }
            return ConflictResolution.IGNORE;
          },
          (k, v) -> {
            ContentKey key = storeKeyToKey(k);
            // Note: key==null, if not the "main universe" or not a "content"
            // discriminator
            if (key != null) {
              MergeType mergeType = mergeTypeForKey.apply(key);
              keyDetailsMap.putIfAbsent(key, keyDetails(mergeType, ConflictType.NONE));
            }
          });
    } catch (CommitConflictException conflict) {
      // Data conflicts are handled, if we get here, it's an internal error OR unimplemented
      // feature or data condition.
      throw new IllegalStateException("Unhandled conflict", conflict);
    } catch (ObjNotFoundException e) {
      throw referenceNotFound(e);
    }
  }

  void bumpReferencePointer(ObjId newHead, Optional<?> retryState) throws RetryException {
    try {
      persist.updateReferencePointer(reference, newHead);
    } catch (RefConditionFailedException e) {
      throw new CommitRetry.RetryException(retryState);
    } catch (RefNotFoundException e) {
      throw new RuntimeException("Internal reference not found", e);
    }
  }

  MergeResult<Commit> mergeTransplantSuccess(
      ImmutableMergeResult.Builder<Commit> mergeResult,
      ObjId newHead,
      boolean dryRun,
      Map<ContentKey, KeyDetails> keyDetailsMap)
      throws RetryException {
    boolean hasConflicts = false;
    for (Entry<ContentKey, KeyDetails> keyDetail : keyDetailsMap.entrySet()) {
      KeyDetails details = keyDetail.getValue();
      if (details.getConflictType() == ConflictType.UNRESOLVABLE) {
        hasConflicts = true;
      }
      mergeResult.putDetails(keyDetail.getKey(), details);
    }

    if (!hasConflicts) {
      mergeResult.wasSuccessful(true);
    }

    if (dryRun || hasConflicts) {
      return mergeResult.build();
    }

    bumpReferencePointer(newHead, Optional.empty());

    return mergeResult.resultantTargetHash(objIdToHash(newHead)).wasApplied(true).build();
  }
}
