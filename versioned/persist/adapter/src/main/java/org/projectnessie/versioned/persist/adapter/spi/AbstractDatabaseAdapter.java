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
package org.projectnessie.versioned.persist.adapter.spi;

import static java.util.concurrent.TimeUnit.NANOSECONDS;
import static org.projectnessie.versioned.persist.adapter.KeyFilterPredicate.ALLOW_ALL;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterMetrics.tryLoopFinished;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.hashKey;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.hashNotFound;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.newHasher;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.randomHash;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.referenceNotFound;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.takeUntilExcludeLast;
import static org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterUtil.takeUntilIncludeLast;
import static org.projectnessie.versioned.persist.adapter.spi.Traced.trace;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;
import com.google.common.hash.Hasher;
import com.google.protobuf.ByteString;
import com.google.protobuf.UnsafeByteOperations;
import io.opentracing.Span;
import io.opentracing.Tracer;
import io.opentracing.log.Fields;
import io.opentracing.tag.Tags;
import io.opentracing.util.GlobalTracer;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.Spliterators.AbstractSpliterator;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;
import javax.annotation.Nonnull;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Diff;
import org.projectnessie.versioned.GetNamedRefsParams;
import org.projectnessie.versioned.GetNamedRefsParams.RetrieveOptions;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ImmutableKeyDetails;
import org.projectnessie.versioned.ImmutableMergeResult;
import org.projectnessie.versioned.ImmutableReferenceInfo;
import org.projectnessie.versioned.Key;
import org.projectnessie.versioned.MergeConflictException;
import org.projectnessie.versioned.MergeResult;
import org.projectnessie.versioned.MergeResult.ConflictType;
import org.projectnessie.versioned.MergeResult.KeyDetails;
import org.projectnessie.versioned.MergeType;
import org.projectnessie.versioned.MetadataRewriter;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.RefLogNotFoundException;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceInfo;
import org.projectnessie.versioned.ReferenceInfo.CommitsAheadBehind;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.StoreWorker;
import org.projectnessie.versioned.TagName;
import org.projectnessie.versioned.persist.adapter.CommitLogEntry;
import org.projectnessie.versioned.persist.adapter.CommitParams;
import org.projectnessie.versioned.persist.adapter.ContentAndState;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterConfig;
import org.projectnessie.versioned.persist.adapter.Difference;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitLogEntry;
import org.projectnessie.versioned.persist.adapter.ImmutableKeyList;
import org.projectnessie.versioned.persist.adapter.KeyFilterPredicate;
import org.projectnessie.versioned.persist.adapter.KeyList;
import org.projectnessie.versioned.persist.adapter.KeyListEntity;
import org.projectnessie.versioned.persist.adapter.KeyListEntry;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;
import org.projectnessie.versioned.persist.adapter.MergeParams;
import org.projectnessie.versioned.persist.adapter.MetadataRewriteParams;
import org.projectnessie.versioned.persist.adapter.RefLog;
import org.projectnessie.versioned.persist.adapter.TransplantParams;
import org.projectnessie.versioned.persist.adapter.events.AdapterEvent;
import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Contains all the database-independent logic for a Database-adapter.
 *
 * <p>This class does not implement everything from {@link
 * org.projectnessie.versioned.persist.adapter.DatabaseAdapter}.
 *
 * <p>Implementations must consider that production environments may use instances of this class in
 * JAX-RS {@code RequestScope}, which means that it must be very cheap to create new instances of
 * the implementations.
 *
 * <p>Managed resources like a connection-pool must be managed outside of {@link
 * AbstractDatabaseAdapter} implementations. The recommended way to "inject" such managed resources
 * into short-lived {@link AbstractDatabaseAdapter} implementations is via a special configuration
 * attribute.
 *
 * @param <OP_CONTEXT> context for each operation, so for each operation in {@link
 *     org.projectnessie.versioned.persist.adapter.DatabaseAdapter} that requires database access.
 *     For example, used to have one "borrowed" database connection per database-adapter operation.
 * @param <CONFIG> configuration interface type for the concrete implementation
 */
public abstract class AbstractDatabaseAdapter<
        OP_CONTEXT extends AutoCloseable, CONFIG extends DatabaseAdapterConfig>
    implements DatabaseAdapter {

  private static final Logger LOGGER = LoggerFactory.getLogger(AbstractDatabaseAdapter.class);

  protected static final String TAG_HASH = "hash";
  protected static final String TAG_COUNT = "count";
  protected final CONFIG config;
  protected final StoreWorker<?, ?, ?> storeWorker;
  private final AdapterEventConsumer eventConsumer;

  @SuppressWarnings("UnstableApiUsage")
  public static final Hash NO_ANCESTOR =
      Hash.of(
          UnsafeByteOperations.unsafeWrap(
              newHasher().putString("empty", StandardCharsets.UTF_8).hash().asBytes()));

  protected static long COMMIT_LOG_HASH_SEED = 946928273206945677L;

  protected AbstractDatabaseAdapter(
      CONFIG config, StoreWorker<?, ?, ?> storeWorker, AdapterEventConsumer eventConsumer) {
    Objects.requireNonNull(config, "config parameter must not be null");
    this.config = config;
    this.storeWorker = storeWorker;
    this.eventConsumer = eventConsumer;
  }

  @VisibleForTesting
  public CONFIG getConfig() {
    return config;
  }

  @VisibleForTesting
  public AdapterEventConsumer getEventConsumer() {
    return eventConsumer;
  }

  @VisibleForTesting
  public abstract OP_CONTEXT borrowConnection();

  @Override
  public Hash noAncestorHash() {
    return NO_ANCESTOR;
  }

  // /////////////////////////////////////////////////////////////////////////////////////////////
  // DatabaseAdapter subclass API (protected)
  // /////////////////////////////////////////////////////////////////////////////////////////////

  /** Returns the current time in microseconds since epoch. */
  protected long commitTimeInMicros() {
    Instant instant = config.getClock().instant();
    long time = instant.getEpochSecond();
    long nano = instant.getNano();
    return TimeUnit.SECONDS.toMicros(time) + NANOSECONDS.toMicros(nano);
  }

  /**
   * Logic implementation of a commit-attempt.
   *
   * @param ctx technical operation-context
   * @param commitParams commit parameters
   * @param branchHead current HEAD of {@code branch}
   * @param newKeyLists consumer for optimistically written {@link KeyListEntity}s
   * @return optimistically written commit-log-entry
   */
  protected CommitLogEntry commitAttempt(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash branchHead,
      CommitParams commitParams,
      Consumer<Hash> newKeyLists)
      throws ReferenceNotFoundException, ReferenceConflictException {
    List<String> mismatches = new ArrayList<>();

    Callable<Void> validator = commitParams.getValidator();
    if (validator != null) {
      try {
        validator.call();
      } catch (RuntimeException e) {
        // just propagate the RuntimeException up
        throw e;
      } catch (Exception e) {
        throw new RuntimeException(e);
      }
    }

    checkContentKeysUnique(commitParams);

    // verify expected global-states
    checkExpectedGlobalStates(ctx, commitParams, mismatches::add);

    CommitLogEntry currentBranchEntry =
        checkForModifiedKeysBetweenExpectedAndCurrentCommit(
            ctx, commitParams, branchHead, mismatches);

    if (!mismatches.isEmpty()) {
      throw new ReferenceConflictException(String.join("\n", mismatches));
    }

    int parentsPerCommit = config.getParentsPerCommit();
    List<Hash> newParents = new ArrayList<>(parentsPerCommit);
    newParents.add(branchHead);
    long commitSeq;
    if (currentBranchEntry != null) {
      List<Hash> p = currentBranchEntry.getParents();
      newParents.addAll(p.subList(0, Math.min(p.size(), parentsPerCommit - 1)));
      commitSeq = currentBranchEntry.getCommitSeq() + 1;
    } else {
      commitSeq = 1;
    }

    // Helps when building a new key-list to not fetch the current commit again.
    Function<Hash, CommitLogEntry> currentCommit =
        h -> h.equals(branchHead) ? currentBranchEntry : null;

    CommitLogEntry newBranchCommit =
        buildIndividualCommit(
            ctx,
            timeInMicros,
            newParents,
            commitSeq,
            commitParams.getCommitMetaSerialized(),
            commitParams.getPuts(),
            commitParams.getDeletes(),
            currentBranchEntry != null ? currentBranchEntry.getKeyListDistance() : 0,
            newKeyLists,
            currentCommit);
    writeIndividualCommit(ctx, newBranchCommit);
    return newBranchCommit;
  }

  private static void checkContentKeysUnique(CommitParams commitParams) {
    Set<Key> keys = new HashSet<>();
    Set<Key> duplicates = new HashSet<>();
    Stream.concat(
            Stream.concat(
                commitParams.getDeletes().stream(),
                commitParams.getPuts().stream().map(KeyWithBytes::getKey)),
            commitParams.getUnchanged().stream())
        .forEach(
            key -> {
              if (!keys.add(key)) {
                duplicates.add(key);
              }
            });
    if (!duplicates.isEmpty()) {
      throw new IllegalArgumentException(
          String.format(
              "Duplicate keys are not allowed in a commit: %s",
              duplicates.stream().map(Key::toString).collect(Collectors.joining(", "))));
    }
  }

  /**
   * Logic implementation of a merge-attempt.
   *
   * @param ctx technical operation context
   * @param toHead current HEAD of {@code toBranch}
   * @param branchCommits consumer for the individual commits to merge
   * @param newKeyLists consumer for optimistically written {@link KeyListEntity}s
   * @return hash of the last commit-log-entry written to {@code toBranch}
   */
  protected Hash mergeAttempt(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash toHead,
      Consumer<Hash> branchCommits,
      Consumer<Hash> newKeyLists,
      Consumer<CommitLogEntry> writtenCommits,
      MergeParams mergeParams,
      ImmutableMergeResult.Builder<CommitLogEntry> mergeResult)
      throws ReferenceNotFoundException, ReferenceConflictException {

    validateHashExists(ctx, mergeParams.getMergeFromHash());

    // 1. ensure 'expectedHash' is a parent of HEAD-of-'toBranch'
    hashOnRef(ctx, mergeParams.getToBranch(), mergeParams.getExpectedHead(), toHead);

    mergeParams.getExpectedHead().ifPresent(mergeResult::expectedHash);
    mergeResult.targetBranch(mergeParams.getToBranch()).effectiveTargetHash(toHead);

    // 2. find nearest common-ancestor between 'from' + 'fromHash'
    Hash commonAncestor =
        findCommonAncestor(ctx, mergeParams.getMergeFromHash(), mergeParams.getToBranch(), toHead);

    mergeResult.commonAncestor(commonAncestor);

    // 3. Collect commit-log-entries
    List<CommitLogEntry> toEntriesReverseChronological =
        takeUntilExcludeLast(
                readCommitLogStream(ctx, toHead), e -> e.getHash().equals(commonAncestor))
            .collect(Collectors.toList());

    toEntriesReverseChronological.forEach(mergeResult::addTargetCommits);

    List<CommitLogEntry> commitsToMergeChronological =
        takeUntilExcludeLast(
                readCommitLogStream(ctx, mergeParams.getMergeFromHash()),
                e -> e.getHash().equals(commonAncestor))
            .collect(Collectors.toList());

    if (commitsToMergeChronological.isEmpty()) {
      // Nothing to merge, shortcut
      throw new IllegalArgumentException(
          String.format(
              "No hashes to merge from '%s' onto '%s' @ '%s'.",
              mergeParams.getMergeFromHash().asString(),
              mergeParams.getToBranch().getName(),
              toHead));
    }

    commitsToMergeChronological.forEach(mergeResult::addSourceCommits);

    return mergeTransplantCommon(
        ctx,
        timeInMicros,
        toHead,
        branchCommits,
        newKeyLists,
        commitsToMergeChronological,
        toEntriesReverseChronological,
        mergeParams,
        mergeResult,
        writtenCommits);
  }

  /**
   * Logic implementation of a transplant-attempt.
   *
   * @param ctx technical operation context
   * @param targetHead current HEAD of {@code targetBranch}
   * @param branchCommits consumer for the individual commits to merge
   * @param newKeyLists consumer for optimistically written {@link KeyListEntity}s
   * @return hash of the last commit-log-entry written to {@code targetBranch}
   */
  protected Hash transplantAttempt(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash targetHead,
      Consumer<Hash> branchCommits,
      Consumer<Hash> newKeyLists,
      Consumer<CommitLogEntry> writtenCommits,
      TransplantParams transplantParams,
      ImmutableMergeResult.Builder<CommitLogEntry> mergeResult)
      throws ReferenceNotFoundException, ReferenceConflictException {
    if (transplantParams.getSequenceToTransplant().isEmpty()) {
      throw new IllegalArgumentException("No hashes to transplant given.");
    }

    transplantParams.getExpectedHead().ifPresent(mergeResult::expectedHash);
    mergeResult.targetBranch(transplantParams.getToBranch()).effectiveTargetHash(targetHead);

    // 1. ensure 'expectedHash' is a parent of HEAD-of-'targetBranch' & collect keys
    List<CommitLogEntry> targetEntriesReverseChronological = new ArrayList<>();
    hashOnRef(
        ctx,
        targetHead,
        transplantParams.getToBranch(),
        transplantParams.getExpectedHead(),
        targetEntriesReverseChronological::add);

    targetEntriesReverseChronological.forEach(mergeResult::addTargetCommits);

    // Exclude the expected-hash on the target-branch from key-collisions check
    if (!targetEntriesReverseChronological.isEmpty()
        && transplantParams.getExpectedHead().isPresent()
        && targetEntriesReverseChronological
            .get(0)
            .getHash()
            .equals(transplantParams.getExpectedHead().get())) {
      targetEntriesReverseChronological.remove(0);
    }

    // 4. ensure 'sequenceToTransplant' is sequential
    int[] index = {transplantParams.getSequenceToTransplant().size() - 1};
    Hash lastHash =
        transplantParams
            .getSequenceToTransplant()
            .get(transplantParams.getSequenceToTransplant().size() - 1);
    List<CommitLogEntry> commitsToTransplantChronological =
        takeUntilExcludeLast(
                readCommitLogStream(ctx, lastHash),
                e -> {
                  int i = index[0]--;
                  if (i == -1) {
                    return true;
                  }
                  if (!e.getHash().equals(transplantParams.getSequenceToTransplant().get(i))) {
                    throw new IllegalArgumentException("Sequence of hashes is not contiguous.");
                  }
                  return false;
                })
            .collect(Collectors.toList());

    commitsToTransplantChronological.forEach(mergeResult::addSourceCommits);

    // 5. check for key-collisions
    return mergeTransplantCommon(
        ctx,
        timeInMicros,
        targetHead,
        branchCommits,
        newKeyLists,
        commitsToTransplantChronological,
        targetEntriesReverseChronological,
        transplantParams,
        mergeResult,
        writtenCommits);
  }

  protected Hash mergeTransplantCommon(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash toHead,
      Consumer<Hash> branchCommits,
      Consumer<Hash> newKeyLists,
      List<CommitLogEntry> commitsToMergeChronological,
      List<CommitLogEntry> toEntriesReverseChronological,
      MetadataRewriteParams params,
      ImmutableMergeResult.Builder<CommitLogEntry> mergeResult,
      Consumer<CommitLogEntry> writtenCommits)
      throws ReferenceConflictException, ReferenceNotFoundException {

    // Collect modified keys.
    Collections.reverse(toEntriesReverseChronological);

    Map<Key, ImmutableKeyDetails.Builder> keyDetailsMap = new HashMap<>();

    Function<Key, MergeType> mergeType =
        key -> params.getMergeTypes().getOrDefault(key, params.getDefaultMergeType());

    Function<Key, ImmutableKeyDetails.Builder> keyDetails =
        key ->
            keyDetailsMap.computeIfAbsent(
                key, x -> KeyDetails.builder().mergeType(mergeType.apply(key)));

    BiConsumer<Stream<CommitLogEntry>, BiConsumer<ImmutableKeyDetails.Builder, Iterable<Hash>>>
        keysFromCommitsToKeyDetails =
            (commits, receiver) -> {
              Map<Key, Set<Hash>> keyHashesMap = new HashMap<>();
              Function<Key, Set<Hash>> keyHashes =
                  key -> keyHashesMap.computeIfAbsent(key, x -> new LinkedHashSet<>());
              commits.forEach(
                  commit -> {
                    commit
                        .getDeletes()
                        .forEach(delete -> keyHashes.apply(delete).add(commit.getHash()));
                    commit
                        .getPuts()
                        .forEach(put -> keyHashes.apply(put.getKey()).add(commit.getHash()));
                  });
              keyHashesMap.forEach((key, hashes) -> receiver.accept(keyDetails.apply(key), hashes));
            };

    Set<Key> keysTouchedOnTarget = new HashSet<>();
    keysFromCommitsToKeyDetails.accept(
        commitsToMergeChronological.stream(), ImmutableKeyDetails.Builder::addAllSourceCommits);
    keysFromCommitsToKeyDetails.accept(
        toEntriesReverseChronological.stream()
            .peek(
                e -> {
                  e.getPuts().stream().map(KeyWithBytes::getKey).forEach(keysTouchedOnTarget::add);
                  e.getDeletes().forEach(keysTouchedOnTarget::remove);
                }),
        ImmutableKeyDetails.Builder::addAllTargetCommits);

    Predicate<Key> skipCheckPredicate = k -> mergeType.apply(k).isSkipCheck();
    Predicate<Key> mergePredicate = k -> mergeType.apply(k).isMerge();

    // Ignore keys in collision-check that will not be merged or collision-checked
    keysTouchedOnTarget.removeIf(skipCheckPredicate);

    boolean hasCollisions =
        hasKeyCollisions(ctx, toHead, keysTouchedOnTarget, commitsToMergeChronological, keyDetails);
    keyDetailsMap.forEach((key, details) -> mergeResult.putDetails(key, details.build()));

    mergeResult.wasSuccessful(!hasCollisions);

    if (hasCollisions && !params.isDryRun()) {
      MergeResult<CommitLogEntry> result = mergeResult.resultantTargetHash(toHead).build();
      throw new MergeConflictException(
          String.format(
              "The following keys have been changed in conflict: %s",
              result.getDetails().entrySet().stream()
                  .filter(e -> e.getValue().getConflictType() != ConflictType.NONE)
                  .map(e -> String.format("'%s'", e.getKey()))
                  .collect(Collectors.joining(", "))),
          result);
    }

    if (params.isDryRun() || hasCollisions) {
      return toHead;
    }

    if (params.keepIndividualCommits() || commitsToMergeChronological.size() == 1) {
      // re-apply commits in 'sequenceToTransplant' onto 'targetBranch'
      toHead =
          copyCommits(
              ctx,
              timeInMicros,
              toHead,
              commitsToMergeChronological,
              newKeyLists,
              params.getUpdateCommitMetadata(),
              mergePredicate);

      // Write commits

      writeMultipleCommits(ctx, commitsToMergeChronological);
      commitsToMergeChronological.stream()
          .peek(writtenCommits)
          .map(CommitLogEntry::getHash)
          .forEach(branchCommits);
    } else {
      CommitLogEntry squashed =
          squashCommits(
              ctx,
              timeInMicros,
              toHead,
              commitsToMergeChronological,
              newKeyLists,
              params.getUpdateCommitMetadata(),
              mergePredicate);

      if (squashed != null) {
        writtenCommits.accept(squashed);
        toHead = squashed.getHash();
      }
    }
    return toHead;
  }

  /**
   * Compute the diff between two references.
   *
   * @param ctx technical operation context
   * @param from "from" reference to compute the difference from, appears on the "from" side in
   *     {@link Diff} with hash in {@code from} to compute the diff for, must exist in {@code from}
   * @param to "to" reference to compute the difference from, appears on the "to" side in {@link
   *     Diff} with hash in {@code to} to compute the diff for, must exist in {@code to}
   * @param keyFilter optional filter on key + content-id + content-type
   * @return computed difference
   */
  protected Stream<Difference> buildDiff(
      OP_CONTEXT ctx, Hash from, Hash to, KeyFilterPredicate keyFilter)
      throws ReferenceNotFoundException {
    // TODO this implementation works, but is definitely not the most efficient one.

    Set<Key> allKeys = new HashSet<>();
    try (Stream<Key> s = keysForCommitEntry(ctx, from, keyFilter).map(KeyListEntry::getKey)) {
      s.forEach(allKeys::add);
    }
    try (Stream<Key> s = keysForCommitEntry(ctx, to, keyFilter).map(KeyListEntry::getKey)) {
      s.forEach(allKeys::add);
    }

    if (allKeys.isEmpty()) {
      // no keys, shortcut
      return Stream.empty();
    }

    List<Key> allKeysList = new ArrayList<>(allKeys);
    Map<Key, ContentAndState<ByteString>> fromValues =
        fetchValues(ctx, from, allKeysList, keyFilter);
    Map<Key, ContentAndState<ByteString>> toValues = fetchValues(ctx, to, allKeysList, keyFilter);

    Function<ContentAndState<ByteString>, Optional<ByteString>> valToContent =
        cs -> cs != null ? Optional.of(cs.getRefState()) : Optional.empty();

    return IntStream.range(0, allKeys.size())
        .mapToObj(allKeysList::get)
        .map(
            k -> {
              ContentAndState<ByteString> fromVal = fromValues.get(k);
              ContentAndState<ByteString> toVal = toValues.get(k);
              Optional<ByteString> f = valToContent.apply(fromVal);
              Optional<ByteString> t = valToContent.apply(toVal);
              if (f.equals(t)) {
                return null;
              }
              Optional<ByteString> g =
                  Optional.ofNullable(
                      fromVal != null
                          ? fromVal.getGlobalState()
                          : (toVal != null ? toVal.getGlobalState() : null));
              return Difference.of(k, g, f, t);
            })
        .filter(Objects::nonNull);
  }

  /**
   * Common functionality to filter and enhance based on the given {@link GetNamedRefsParams}.
   *
   * @param ctx database-adapter context
   * @param params defines which kind of references and which additional information shall be
   *     retrieved
   * @param defaultBranchHead prerequisite, the hash of the default branch's HEAD commit (depending
   *     on the database-adapter implementation). If {@code null}, {@link
   *     #namedRefsWithDefaultBranchRelatedInfo(AutoCloseable, GetNamedRefsParams, Stream, Hash)}
   *     will not add additional default-branch related information (common ancestor and commits
   *     behind/ahead).
   * @param refs current {@link Stream} of {@link ReferenceInfo} to be enhanced.
   * @return filtered/enhanced stream based on {@code refs}.
   */
  protected Stream<ReferenceInfo<ByteString>> namedRefsFilterAndEnhance(
      OP_CONTEXT ctx,
      GetNamedRefsParams params,
      Hash defaultBranchHead,
      Stream<ReferenceInfo<ByteString>> refs) {
    refs = namedRefsMaybeFilter(params, refs);

    refs = namedRefsWithDefaultBranchRelatedInfo(ctx, params, refs, defaultBranchHead);

    refs = namedReferenceWithCommitMeta(ctx, params, refs);

    return refs;
  }

  /** Applies the reference type filter (tags or branches) to the Java stream. */
  protected static Stream<ReferenceInfo<ByteString>> namedRefsMaybeFilter(
      GetNamedRefsParams params, Stream<ReferenceInfo<ByteString>> refs) {
    if (params.getBranchRetrieveOptions().isRetrieve()
        && params.getTagRetrieveOptions().isRetrieve()) {
      // No filtering necessary, if all named-reference types (tags and branches) are being fetched.
      return refs;
    }
    return refs.filter(ref -> namedRefsRetrieveOptionsForReference(params, ref).isRetrieve());
  }

  protected static boolean namedRefsRequiresBaseReference(GetNamedRefsParams params) {
    return namedRefsRequiresBaseReference(params.getBranchRetrieveOptions())
        || namedRefsRequiresBaseReference(params.getTagRetrieveOptions());
  }

  protected static boolean namedRefsRequiresBaseReference(
      GetNamedRefsParams.RetrieveOptions retrieveOptions) {
    return retrieveOptions.isComputeAheadBehind() || retrieveOptions.isComputeCommonAncestor();
  }

  protected static boolean namedRefsAnyRetrieves(GetNamedRefsParams params) {
    return params.getBranchRetrieveOptions().isRetrieve()
        || params.getTagRetrieveOptions().isRetrieve();
  }

  protected static GetNamedRefsParams.RetrieveOptions namedRefsRetrieveOptionsForReference(
      GetNamedRefsParams params, ReferenceInfo<ByteString> ref) {
    return namedRefsRetrieveOptionsForReference(params, ref.getNamedRef());
  }

  protected static GetNamedRefsParams.RetrieveOptions namedRefsRetrieveOptionsForReference(
      GetNamedRefsParams params, NamedRef ref) {
    if (ref instanceof BranchName) {
      return params.getBranchRetrieveOptions();
    }
    if (ref instanceof TagName) {
      return params.getTagRetrieveOptions();
    }
    throw new IllegalArgumentException("ref must be either BranchName or TabName, but is " + ref);
  }

  /**
   * Returns an updated {@link ReferenceInfo} with the commit-meta of the reference's HEAD commit.
   */
  protected Stream<ReferenceInfo<ByteString>> namedReferenceWithCommitMeta(
      OP_CONTEXT ctx, GetNamedRefsParams params, Stream<ReferenceInfo<ByteString>> refs) {
    return refs.map(
        ref -> {
          if (!namedRefsRetrieveOptionsForReference(params, ref).isRetrieveCommitMetaForHead()) {
            return ref;
          }
          CommitLogEntry logEntry = fetchFromCommitLog(ctx, ref.getHash());
          if (logEntry == null) {
            return ref;
          }
          return ImmutableReferenceInfo.<ByteString>builder()
              .from(ref)
              .headCommitMeta(logEntry.getMetadata())
              .commitSeq(logEntry.getCommitSeq())
              .build();
        });
  }

  /**
   * If necessary based on the given {@link GetNamedRefsParams}, updates the returned {@link
   * ReferenceInfo}s with the common-ancestor of the named reference and the default branch and the
   * number of commits behind/ahead compared to the default branch.
   *
   * <p>The common ancestor and/or information of commits behind/ahead is meaningless ({@code null})
   * for the default branch. Both fields are also {@code null} if the named reference points to the
   * {@link #noAncestorHash()} (beginning of time).
   */
  protected Stream<ReferenceInfo<ByteString>> namedRefsWithDefaultBranchRelatedInfo(
      OP_CONTEXT ctx,
      GetNamedRefsParams params,
      Stream<ReferenceInfo<ByteString>> refs,
      Hash defaultBranchHead) {
    if (defaultBranchHead == null) {
      // No enhancement of common ancestor and/or commits behind/ahead.
      return refs;
    }

    CommonAncestorState commonAncestorState =
        new CommonAncestorState(
            ctx,
            defaultBranchHead,
            params.getBranchRetrieveOptions().isComputeAheadBehind()
                || params.getTagRetrieveOptions().isComputeAheadBehind());

    return refs.map(
        ref -> {
          if (ref.getNamedRef().equals(params.getBaseReference())) {
            return ref;
          }

          RetrieveOptions retrieveOptions = namedRefsRetrieveOptionsForReference(params, ref);

          ReferenceInfo<ByteString> updated =
              namedRefsRequiresBaseReference(retrieveOptions)
                  ? findCommonAncestor(
                      ctx,
                      ref.getHash(),
                      commonAncestorState,
                      (diffOnFrom, hash) -> {
                        ReferenceInfo<ByteString> newRef = ref;
                        if (retrieveOptions.isComputeCommonAncestor()) {
                          newRef = newRef.withCommonAncestor(hash);
                        }
                        if (retrieveOptions.isComputeAheadBehind()) {
                          int behind = commonAncestorState.indexOf(hash);
                          CommitsAheadBehind aheadBehind =
                              CommitsAheadBehind.of(diffOnFrom, behind);
                          newRef = newRef.withAheadBehind(aheadBehind);
                        }
                        return newRef;
                      })
                  : null;

          return updated != null ? updated : ref;
        });
  }

  /**
   * Convenience for {@link #hashOnRef(AutoCloseable, Hash, NamedRef, Optional, Consumer)
   * hashOnRef(ctx, knownHead, ref.getReference(), ref.getHashOnReference(), null)}.
   */
  protected Hash hashOnRef(
      OP_CONTEXT ctx, NamedRef reference, Optional<Hash> hashOnRef, Hash knownHead)
      throws ReferenceNotFoundException {
    return hashOnRef(ctx, knownHead, reference, hashOnRef, null);
  }

  /**
   * Ensures that {@code ref} exists and that the hash in {@code hashOnRef} exists in that
   * reference.
   *
   * @param ctx technical operation context
   * @param ref reference that must contain {@code hashOnRef}
   * @param knownHead current HEAD of {@code ref}
   * @param hashOnRef hash to verify whether it exists in {@code ref}
   * @param commitLogVisitor optional consumer that will receive all visited {@link
   *     CommitLogEntry}s, can be {@code null}.
   * @return value of {@code hashOnRef} or, if {@code hashOnRef} is empty, {@code knownHead}
   * @throws ReferenceNotFoundException if either {@code ref} does not exist or {@code hashOnRef}
   *     does not exist on {@code ref}
   */
  protected Hash hashOnRef(
      OP_CONTEXT ctx,
      Hash knownHead,
      NamedRef ref,
      Optional<Hash> hashOnRef,
      Consumer<CommitLogEntry> commitLogVisitor)
      throws ReferenceNotFoundException {
    if (hashOnRef.isPresent()) {
      Hash suspect = hashOnRef.get();

      // If the client requests 'NO_ANCESTOR' (== beginning of time), skip the existence-check.
      if (suspect.equals(NO_ANCESTOR)) {
        if (commitLogVisitor != null) {
          readCommitLogStream(ctx, knownHead).forEach(commitLogVisitor);
        }
        return suspect;
      }

      Stream<Hash> hashes;
      if (commitLogVisitor != null) {
        hashes =
            readCommitLogStream(ctx, knownHead).peek(commitLogVisitor).map(CommitLogEntry::getHash);
      } else {
        hashes = readCommitLogHashesStream(ctx, knownHead);
      }
      if (hashes.noneMatch(suspect::equals)) {
        throw hashNotFound(ref, suspect);
      }
      return suspect;
    } else {
      return knownHead;
    }
  }

  protected void validateHashExists(OP_CONTEXT ctx, Hash hash) throws ReferenceNotFoundException {
    if (!NO_ANCESTOR.equals(hash) && fetchFromCommitLog(ctx, hash) == null) {
      throw referenceNotFound(hash);
    }
  }

  /** Load the commit-log entry for the given hash, return {@code null}, if not found. */
  @VisibleForTesting
  public final CommitLogEntry fetchFromCommitLog(OP_CONTEXT ctx, Hash hash) {
    if (hash.equals(NO_ANCESTOR)) {
      // Do not try to fetch NO_ANCESTOR - it won't exist.
      return null;
    }
    try (Traced ignore = trace("fetchFromCommitLog").tag(TAG_HASH, hash.asString())) {
      return doFetchFromCommitLog(ctx, hash);
    }
  }

  protected abstract CommitLogEntry doFetchFromCommitLog(OP_CONTEXT ctx, Hash hash);

  /**
   * Fetch multiple {@link CommitLogEntry commit-log-entries} from the commit-log. The returned list
   * must have exactly as many elements as in the parameter {@code hashes}. Non-existing hashes are
   * returned as {@code null}.
   */
  private List<CommitLogEntry> fetchMultipleFromCommitLog(
      OP_CONTEXT ctx, List<Hash> hashes, @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits) {
    List<CommitLogEntry> result = new ArrayList<>(hashes.size());
    BitSet remainingHashes = null;

    // Prefetch commits already available in memory. Record indexes for the missing commits to
    // enable placing them in the correct positions later, when they are fetched from storage.
    for (int i = 0; i < hashes.size(); i++) {
      Hash hash = hashes.get(i);
      if (NO_ANCESTOR.equals(hash)) {
        result.add(null);
        continue;
      }

      CommitLogEntry found = inMemoryCommits.apply(hash);
      if (found != null) {
        result.add(found);
      } else {
        if (remainingHashes == null) {
          remainingHashes = new BitSet();
        }
        result.add(null); // to be replaced with storage result below
        remainingHashes.set(i);
      }
    }

    if (remainingHashes != null) {
      List<CommitLogEntry> fromStorage;

      try (Traced ignore =
          trace("fetchPageFromCommitLog")
              .tag(TAG_HASH, hashes.get(0).asString())
              .tag(TAG_COUNT, hashes.size())) {
        fromStorage =
            doFetchMultipleFromCommitLog(
                ctx, remainingHashes.stream().mapToObj(hashes::get).collect(Collectors.toList()));
      }

      // Fill the gaps in the final result list. Note that fetchPageFromCommitLog must return the
      // list of the same size as its `remainingHashes` parameter.
      Iterator<CommitLogEntry> iter = fromStorage.iterator();
      remainingHashes.stream()
          .forEach(
              i -> {
                result.set(i, iter.next());
              });
    }

    return result;
  }

  protected abstract List<CommitLogEntry> doFetchMultipleFromCommitLog(
      OP_CONTEXT ctx, List<Hash> hashes);

  /** Reads from the commit-log starting at the given commit-log-hash. */
  protected Stream<CommitLogEntry> readCommitLogStream(OP_CONTEXT ctx, Hash initialHash)
      throws ReferenceNotFoundException {
    Spliterator<CommitLogEntry> split = readCommitLog(ctx, initialHash, h -> null);
    return StreamSupport.stream(split, false);
  }

  protected Stream<CommitLogEntry> readCommitLogStream(
      OP_CONTEXT ctx, Hash initialHash, @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits)
      throws ReferenceNotFoundException {
    Spliterator<CommitLogEntry> split = readCommitLog(ctx, initialHash, inMemoryCommits);
    return StreamSupport.stream(split, false);
  }

  protected Spliterator<CommitLogEntry> readCommitLog(
      OP_CONTEXT ctx, Hash initialHash, @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits)
      throws ReferenceNotFoundException {
    Preconditions.checkNotNull(inMemoryCommits, "in-memory commits cannot be null");

    if (NO_ANCESTOR.equals(initialHash)) {
      return Spliterators.emptySpliterator();
    }

    CommitLogEntry initial = inMemoryCommits.apply(initialHash);
    if (initial == null) {
      initial = fetchFromCommitLog(ctx, initialHash);
    }

    if (initial == null) {
      throw referenceNotFound(initialHash);
    }

    BiFunction<OP_CONTEXT, List<Hash>, List<CommitLogEntry>> fetcher =
        (c, hashes) -> fetchMultipleFromCommitLog(c, hashes, inMemoryCommits);

    return logFetcher(ctx, initial, fetcher, CommitLogEntry::getParents);
  }

  /**
   * Like {@link #readCommitLogStream(AutoCloseable, Hash)}, but only returns the {@link Hash
   * commit-log-entry hashes}, which can be taken from {@link CommitLogEntry#getParents()}, thus no
   * need to perform a read-operation against every hash.
   */
  protected Stream<Hash> readCommitLogHashesStream(OP_CONTEXT ctx, Hash initialHash) {
    Spliterator<Hash> split = readCommitLogHashes(ctx, initialHash);
    return StreamSupport.stream(split, false);
  }

  protected Spliterator<Hash> readCommitLogHashes(OP_CONTEXT ctx, Hash initialHash) {
    return logFetcher(
        ctx,
        initialHash,
        (c, hashes) -> hashes,
        hash -> {
          CommitLogEntry entry = fetchFromCommitLog(ctx, hash);
          if (entry == null) {
            return Collections.emptyList();
          }
          return entry.getParents();
        });
  }

  /**
   * Constructs a {@link Stream} of entries for either the global-state-log or a commit-log or a
   * reflog entry. Use {@link #readCommitLogStream(AutoCloseable, Hash)} or the similar
   * implementation for the global-log or reflog entry for non-transactional adapters.
   */
  protected <T> Spliterator<T> logFetcher(
      OP_CONTEXT ctx,
      T initial,
      BiFunction<OP_CONTEXT, List<Hash>, List<T>> fetcher,
      Function<T, List<Hash>> nextPage) {
    return logFetcherCommon(ctx, Collections.singletonList(initial), fetcher, nextPage);
  }

  protected <T> Spliterator<T> logFetcherWithPage(
      OP_CONTEXT ctx,
      List<Hash> initialPage,
      BiFunction<OP_CONTEXT, List<Hash>, List<T>> fetcher,
      Function<T, List<Hash>> nextPage) {
    return logFetcherCommon(ctx, fetcher.apply(ctx, initialPage), fetcher, nextPage);
  }

  private <T> Spliterator<T> logFetcherCommon(
      OP_CONTEXT ctx,
      List<T> initial,
      BiFunction<OP_CONTEXT, List<Hash>, List<T>> fetcher,
      Function<T, List<Hash>> nextPage) {
    return new AbstractSpliterator<T>(Long.MAX_VALUE, 0) {
      private Iterator<T> currentBatch;
      private boolean eof;
      private T previous;

      @Override
      public boolean tryAdvance(Consumer<? super T> consumer) {
        if (eof) {
          return false;
        } else if (currentBatch == null) {
          currentBatch = initial.iterator();
        } else if (!currentBatch.hasNext()) {
          if (previous == null) {
            eof = true;
            return false;
          }
          List<Hash> page = nextPage.apply(previous);
          previous = null;
          if (!page.isEmpty()) {
            currentBatch = fetcher.apply(ctx, page).iterator();
            if (!currentBatch.hasNext()) {
              eof = true;
              return false;
            }
          } else {
            eof = true;
            return false;
          }
        }
        T v = currentBatch.next();
        if (v != null) {
          consumer.accept(v);
          previous = v;
        }
        return true;
      }
    };
  }

  /**
   * Builds a {@link CommitLogEntry} using the given values. This function also includes a {@link
   * KeyList}, if triggered by the values of {@code currentKeyListDistance} and {@link
   * DatabaseAdapterConfig#getKeyListDistance()}, so read operations may happen.
   */
  protected CommitLogEntry buildIndividualCommit(
      OP_CONTEXT ctx,
      long timeInMicros,
      List<Hash> parentHashes,
      long commitSeq,
      ByteString commitMeta,
      Iterable<KeyWithBytes> puts,
      Iterable<Key> deletes,
      int currentKeyListDistance,
      Consumer<Hash> newKeyLists,
      @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits)
      throws ReferenceNotFoundException {
    Hash commitHash = individualCommitHash(parentHashes, commitMeta, puts, deletes);

    int keyListDistance = currentKeyListDistance + 1;

    CommitLogEntry entry =
        CommitLogEntry.of(
            timeInMicros,
            commitHash,
            commitSeq,
            parentHashes,
            commitMeta,
            puts,
            deletes,
            keyListDistance,
            null,
            Collections.emptyList());

    if (keyListDistance >= config.getKeyListDistance()) {
      entry = buildKeyList(ctx, entry, newKeyLists, inMemoryCommits);
    }
    return entry;
  }

  /** Calculate the hash for the content of a {@link CommitLogEntry}. */
  @SuppressWarnings("UnstableApiUsage")
  protected Hash individualCommitHash(
      List<Hash> parentHashes,
      ByteString commitMeta,
      Iterable<KeyWithBytes> puts,
      Iterable<Key> deletes) {
    Hasher hasher = newHasher();
    hasher.putLong(COMMIT_LOG_HASH_SEED);
    parentHashes.forEach(h -> hasher.putBytes(h.asBytes().asReadOnlyByteBuffer()));
    hasher.putBytes(commitMeta.asReadOnlyByteBuffer());
    puts.forEach(
        e -> {
          hashKey(hasher, e.getKey());
          hasher.putString(e.getContentId().getId(), StandardCharsets.UTF_8);
          hasher.putBytes(e.getValue().asReadOnlyByteBuffer());
        });
    deletes.forEach(e -> hashKey(hasher, e));
    return Hash.of(UnsafeByteOperations.unsafeWrap(hasher.hash().asBytes()));
  }

  /** Helper object for {@link #buildKeyList(AutoCloseable, CommitLogEntry, Consumer, Function)}. */
  private static class KeyListBuildState {
    final ImmutableCommitLogEntry.Builder newCommitEntry;
    /** Builder for {@link CommitLogEntry#getKeyList()}. */
    ImmutableKeyList.Builder embeddedBuilder = ImmutableKeyList.builder();
    /** Builder for {@link KeyListEntity}. */
    ImmutableKeyList.Builder currentKeyList;
    /** Already built {@link KeyListEntity}s. */
    List<KeyListEntity> newKeyListEntities = new ArrayList<>();

    /** Flag whether {@link CommitLogEntry#getKeyList()} is being filled. */
    boolean embedded = true;

    /** Current size of either the {@link CommitLogEntry} or current {@link KeyListEntity}. */
    int currentSize;

    KeyListBuildState(int initialSize, ImmutableCommitLogEntry.Builder newCommitEntry) {
      this.currentSize = initialSize;
      this.newCommitEntry = newCommitEntry;
    }

    void finishKeyListEntity() {
      Hash id = randomHash();
      newKeyListEntities.add(KeyListEntity.of(id, currentKeyList.build()));
      newCommitEntry.addKeyListsIds(id);
    }

    void newKeyListEntity() {
      currentSize = 0;
      currentKeyList = ImmutableKeyList.builder();
    }

    void addToKeyListEntity(KeyListEntry keyListEntry, int keyTypeSize) {
      currentSize += keyTypeSize;
      currentKeyList.addKeys(keyListEntry);
    }

    void addToEmbedded(KeyListEntry keyListEntry, int keyTypeSize) {
      currentSize += keyTypeSize;
      embeddedBuilder.addKeys(keyListEntry);
    }
  }

  /**
   * Adds a complete key-list to the given {@link CommitLogEntry}, will read from the database.
   *
   * <p>The implementation fills {@link CommitLogEntry#getKeyList()} with the most recently updated
   * {@link Key}s.
   *
   * <p>If the calculated size of the database-object/row gets larger than {@link
   * DatabaseAdapterConfig#getMaxKeyListSize()}, the next {@link Key}s will be added to new {@link
   * KeyListEntity}s, each with a maximum size of {@link DatabaseAdapterConfig#getMaxKeyListSize()}.
   *
   * <p>The current implementation fetches all keys and "blindly" populated {@link
   * CommitLogEntry#getKeyList()} and nested {@link KeyListEntity} via {@link
   * CommitLogEntry#getKeyListsIds()}. So this implementation does not yet reuse previous {@link
   * KeyListEntity}s. A follow-up improvement should check if already existing {@link
   * KeyListEntity}s contain the same keys. This proposed optimization should be accompanied by an
   * optimized read of the keys: for example, if the set of changed keys only affects {@link
   * CommitLogEntry#getKeyList()} but not the keys via {@link KeyListEntity}, it is just unnecessary
   * to both read and re-write those rows for {@link KeyListEntity}.
   */
  protected CommitLogEntry buildKeyList(
      OP_CONTEXT ctx,
      CommitLogEntry unwrittenEntry,
      Consumer<Hash> newKeyLists,
      @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits)
      throws ReferenceNotFoundException {
    // Read commit-log until the previous persisted key-list

    Hash startHash = unwrittenEntry.getParents().get(0);

    // Return the new commit-log-entry with the complete-key-list
    ImmutableCommitLogEntry.Builder newCommitEntry =
        ImmutableCommitLogEntry.builder().from(unwrittenEntry).keyListDistance(0);

    KeyListBuildState buildState =
        new KeyListBuildState(entitySize(unwrittenEntry), newCommitEntry);

    Set<Key> keysToEnhanceWithCommitId = new HashSet<>();

    Consumer<KeyListEntry> addKeyListEntry =
        keyListEntry -> {
          int keyTypeSize = entitySize(keyListEntry);
          if (buildState.embedded) {
            // filling the embedded key-list in CommitLogEntry

            if (buildState.currentSize + keyTypeSize < maxEntitySize(config.getMaxKeyListSize())) {
              // CommitLogEntry.keyList still has room
              buildState.addToEmbedded(keyListEntry, keyTypeSize);
            } else {
              // CommitLogEntry.keyList is "full", switch to the first KeyListEntity
              buildState.embedded = false;
              buildState.newKeyListEntity();
              buildState.addToKeyListEntity(keyListEntry, keyTypeSize);
            }
          } else {
            // filling linked key-lists via CommitLogEntry.keyListIds

            if (buildState.currentSize + keyTypeSize
                > maxEntitySize(config.getMaxKeyListEntitySize())) {
              // current KeyListEntity is "full", switch to a new one
              buildState.finishKeyListEntity();
              buildState.newKeyListEntity();
            }
            buildState.addToKeyListEntity(keyListEntry, keyTypeSize);
          }
        };

    keysForCommitEntry(ctx, startHash, null, inMemoryCommits)
        .forEach(
            keyListEntry -> {
              if (keyListEntry.getCommitId() == null) {
                keysToEnhanceWithCommitId.add(keyListEntry.getKey());
              } else {
                addKeyListEntry.accept(keyListEntry);
              }
            });

    if (!keysToEnhanceWithCommitId.isEmpty()) {
      // Found KeyListEntry w/o commitId, need to add that information.
      Spliterator<CommitLogEntry> clSplit = readCommitLog(ctx, startHash, inMemoryCommits);
      while (true) {
        boolean more =
            clSplit.tryAdvance(
                e -> {
                  e.getDeletes().forEach(keysToEnhanceWithCommitId::remove);
                  for (KeyWithBytes put : e.getPuts()) {
                    if (keysToEnhanceWithCommitId.remove(put.getKey())) {
                      KeyListEntry entry =
                          KeyListEntry.of(
                              put.getKey(), put.getContentId(), put.getType(), e.getHash());
                      addKeyListEntry.accept(entry);
                    }
                  }
                });
        if (!more || keysToEnhanceWithCommitId.isEmpty()) {
          break;
        }
      }
    }

    // If there's an "unfinished" KeyListEntity, build it.
    if (buildState.currentKeyList != null) {
      buildState.finishKeyListEntity();
    }

    // Inform the (CAS)-op-loop about the IDs of the KeyListEntities being optimistically written.
    buildState.newKeyListEntities.stream().map(KeyListEntity::getId).forEach(newKeyLists);

    // Write the new KeyListEntities
    if (!buildState.newKeyListEntities.isEmpty()) {
      writeKeyListEntities(ctx, buildState.newKeyListEntities);
    }

    // Return the new commit-log-entry with the complete-key-list
    return newCommitEntry.keyList(buildState.embeddedBuilder.build()).build();
  }

  protected int maxEntitySize(int value) {
    return value;
  }

  /** Calculate the expected size of the given {@link CommitLogEntry} in the database. */
  protected abstract int entitySize(CommitLogEntry entry);

  /** Calculate the expected size of the given {@link CommitLogEntry} in the database. */
  protected abstract int entitySize(KeyListEntry entry);

  /**
   * If the current HEAD of the target branch for a commit/transplant/merge is not equal to the
   * expected/reference HEAD, verify that there is no conflict, like keys in the operations of the
   * commit(s) contained in keys of the commits 'expectedHead (excluding) .. currentHead
   * (including)'.
   *
   * @return commit log entry at {@code branchHead}
   */
  protected CommitLogEntry checkForModifiedKeysBetweenExpectedAndCurrentCommit(
      OP_CONTEXT ctx, CommitParams commitParams, Hash branchHead, List<String> mismatches)
      throws ReferenceNotFoundException {

    CommitLogEntry commitAtHead = null;
    if (commitParams.getExpectedHead().isPresent()) {
      Hash expectedHead = commitParams.getExpectedHead().get();
      if (!expectedHead.equals(branchHead)) {
        Set<Key> operationKeys =
            Sets.newHashSetWithExpectedSize(
                commitParams.getDeletes().size()
                    + commitParams.getUnchanged().size()
                    + commitParams.getPuts().size());
        operationKeys.addAll(commitParams.getDeletes());
        operationKeys.addAll(commitParams.getUnchanged());
        commitParams.getPuts().stream().map(KeyWithBytes::getKey).forEach(operationKeys::add);

        ConflictingKeyCheckResult conflictingKeyCheckResult =
            checkConflictingKeysForCommit(
                ctx, branchHead, expectedHead, operationKeys, mismatches::add);

        // If the expectedHead is the special value NO_ANCESTOR, which is not persisted,
        // ignore the fact that it has not been seen. Otherwise, raise a
        // ReferenceNotFoundException that the expected-hash does not exist on the target
        // branch.
        if (!conflictingKeyCheckResult.sinceSeen && !expectedHead.equals(NO_ANCESTOR)) {
          throw hashNotFound(commitParams.getToBranch(), expectedHead);
        }

        commitAtHead = conflictingKeyCheckResult.headCommit;
      }
    }

    if (commitAtHead == null) {
      commitAtHead = fetchFromCommitLog(ctx, branchHead);
    }
    return commitAtHead;
  }

  /** Retrieve the content-keys and their types for the commit-log-entry with the given hash. */
  protected Stream<KeyListEntry> keysForCommitEntry(
      OP_CONTEXT ctx, Hash hash, KeyFilterPredicate keyFilter) throws ReferenceNotFoundException {
    return keysForCommitEntry(ctx, hash, keyFilter, h -> null);
  }

  /** Retrieve the content-keys and their types for the commit-log-entry with the given hash. */
  protected Stream<KeyListEntry> keysForCommitEntry(
      OP_CONTEXT ctx,
      Hash hash,
      KeyFilterPredicate keyFilter,
      @Nonnull Function<Hash, CommitLogEntry> inMemoryCommits)
      throws ReferenceNotFoundException {
    // walk the commit-logs in reverse order - starting with the last persisted key-list

    Set<Key> seen = new HashSet<>();

    Predicate<KeyListEntry> predicate = keyListEntry -> seen.add(keyListEntry.getKey());
    if (keyFilter != null) {
      predicate =
          predicate.and(kt -> keyFilter.check(kt.getKey(), kt.getContentId(), kt.getType()));
    }
    Predicate<KeyListEntry> keyPredicate = predicate;

    Stream<CommitLogEntry> log = readCommitLogStream(ctx, hash, inMemoryCommits);
    log = takeUntilIncludeLast(log, e -> e.getKeyList() != null);
    return log.flatMap(
        e -> {

          // Add CommitLogEntry.deletes to "seen" so these keys won't be returned
          seen.addAll(e.getDeletes());

          // Return from CommitLogEntry.puts first
          Stream<KeyListEntry> stream =
              e.getPuts().stream()
                  .map(
                      put ->
                          KeyListEntry.of(
                              put.getKey(), put.getContentId(), put.getType(), e.getHash()))
                  .filter(keyPredicate);

          if (e.getKeyList() != null) {

            // Return from CommitLogEntry.keyList after the keys in CommitLogEntry.puts
            Stream<KeyListEntry> embedded = e.getKeyList().getKeys().stream().filter(keyPredicate);
            stream = Stream.concat(stream, embedded);

            if (!e.getKeyListsIds().isEmpty()) {
              // If there are nested key-lists, retrieve those lazily and add the keys from these

              Stream<KeyListEntry> entities =
                  Stream.of(e.getKeyListsIds())
                      .flatMap(ids -> fetchKeyLists(ctx, ids))
                      .map(KeyListEntity::getKeys)
                      .map(KeyList::getKeys)
                      .flatMap(Collection::stream)
                      .filter(keyPredicate);
              stream = Stream.concat(stream, entities);
            }
          }

          return stream;
        });
  }

  /**
   * Fetch the global-state and per-ref content for the given {@link Key}s and {@link Hash
   * commitSha}. Non-existing keys must not be present in the returned map.
   */
  protected Map<Key, ContentAndState<ByteString>> fetchValues(
      OP_CONTEXT ctx, Hash refHead, Collection<Key> keys, KeyFilterPredicate keyFilter)
      throws ReferenceNotFoundException {
    Set<Key> remainingKeys = new HashSet<>(keys);

    Map<Key, ByteString> nonGlobal = new HashMap<>();
    Map<Key, ContentId> keyToContentIds = new HashMap<>();
    Set<ContentId> contentIdsForGlobal = new HashSet<>();

    Consumer<CommitLogEntry> commitLogEntryHandler =
        entry -> {
          // remove deleted keys from keys to look for
          entry.getDeletes().forEach(remainingKeys::remove);

          // handle put operations
          for (KeyWithBytes put : entry.getPuts()) {
            if (!remainingKeys.remove(put.getKey())) {
              continue;
            }

            if (!keyFilter.check(put.getKey(), put.getContentId(), put.getType())) {
              continue;
            }
            nonGlobal.put(put.getKey(), put.getValue());
            keyToContentIds.put(put.getKey(), put.getContentId());
            if (storeWorker.requiresGlobalState(put.getValue())) {
              contentIdsForGlobal.add(put.getContentId());
            }
          }
        };

    // The algorithm is a bit complex, but not horribly complex.
    //
    // The commit-log `Stream` in the following try-with-resources fetches the commit-log until
    // all requested keys have been seen.
    //
    // When a commit-log-entry with key-lists is encountered, use the key-lists to determine if
    // and how the remaining keys need to be retrieved.
    // - If any of the requested keys is not in the key-lists, ignore it - it doesn't exist.
    // - If the `KeyListEntry` for a requested key contains the commit-ID, use the commit-ID to
    //   directly access the commit that contains the `Put` operation for that key.
    //
    // Both the "outer" `Stream` and the result of the latter case (list of commit-log-entries)
    // share common functionality implemented in the function `commitLogEntryHandler`, which
    // handles the 'Put` operations.

    AtomicBoolean keyListProcessed = new AtomicBoolean();
    try (Stream<CommitLogEntry> log =
        takeUntilExcludeLast(readCommitLogStream(ctx, refHead), e -> remainingKeys.isEmpty())) {
      log.forEach(
          entry -> {
            commitLogEntryHandler.accept(entry);

            if (entry.getKeyList() != null && keyListProcessed.compareAndSet(false, true)) {
              // CommitLogEntry has a KeyList.
              // All keys in 'remainingKeys', that are _not_ in the KeyList(s), can be removed,
              // because at this point we know that these do not exist.
              //
              // But do only process the "newest" key-list - older key lists are irrelevant.
              // KeyListEntry written before Nessie 0.22.0 do not have the commit-ID field set,
              // which means the remaining commit-log needs to be searched for the commit that
              // added the key - but processing older key-lists "on the way" makes no sense,
              // because those will not have the key either.

              Set<KeyListEntry> remainingInKeyList = new HashSet<>();
              try (Stream<KeyList> keyLists =
                  Stream.concat(
                      Stream.of(entry.getKeyList()),
                      fetchKeyLists(ctx, entry.getKeyListsIds()).map(KeyListEntity::getKeys))) {
                keyLists
                    .flatMap(keyList -> keyList.getKeys().stream())
                    .filter(keyListEntry -> remainingKeys.contains(keyListEntry.getKey()))
                    .forEach(remainingInKeyList::add);
              }

              if (!remainingInKeyList.isEmpty()) {
                List<CommitLogEntry> commitLogEntries =
                    fetchMultipleFromCommitLog(
                        ctx,
                        remainingInKeyList.stream()
                            .map(KeyListEntry::getCommitId)
                            .filter(Objects::nonNull)
                            .distinct()
                            .collect(Collectors.toList()),
                        h -> null);
                commitLogEntries.forEach(commitLogEntryHandler);
              }

              remainingKeys.retainAll(
                  remainingInKeyList.stream()
                      .map(KeyListEntry::getKey)
                      .collect(Collectors.toSet()));
            }
          });
    }

    Map<ContentId, ByteString> globals =
        contentIdsForGlobal.isEmpty()
            ? Collections.emptyMap()
            : fetchGlobalStates(ctx, contentIdsForGlobal);

    return nonGlobal.entrySet().stream()
        .collect(
            Collectors.toMap(
                Entry::getKey,
                e ->
                    ContentAndState.of(
                        e.getValue(), globals.get(keyToContentIds.get(e.getKey())))));
  }

  /**
   * Fetches the global-state information for the given content-ids.
   *
   * @param ctx technical context
   * @param contentIds the content-ids to fetch
   * @return map of content-id to state
   */
  protected final Map<ContentId, ByteString> fetchGlobalStates(
      OP_CONTEXT ctx, Set<ContentId> contentIds) throws ReferenceNotFoundException {
    try (Traced ignore = trace("fetchGlobalStates").tag(TAG_COUNT, contentIds.size())) {
      return doFetchGlobalStates(ctx, contentIds);
    }
  }

  protected abstract Map<ContentId, ByteString> doFetchGlobalStates(
      OP_CONTEXT ctx, Set<ContentId> contentIds) throws ReferenceNotFoundException;

  @VisibleForTesting
  public final Stream<KeyListEntity> fetchKeyLists(OP_CONTEXT ctx, List<Hash> keyListsIds) {
    if (keyListsIds.isEmpty()) {
      return Stream.empty();
    }
    try (Traced ignore = trace("fetchKeyLists").tag(TAG_COUNT, keyListsIds.size())) {
      return doFetchKeyLists(ctx, keyListsIds);
    }
  }

  protected abstract Stream<KeyListEntity> doFetchKeyLists(OP_CONTEXT ctx, List<Hash> keyListsIds);

  /**
   * Write a new commit-entry, the given commit entry is to be persisted as is. All values of the
   * given {@link CommitLogEntry} can be considered valid and consistent.
   *
   * <p>Implementations however can enforce strict consistency checks/guarantees, like a best-effort
   * approach to prevent hash-collisions but without any other consistency checks/guarantees.
   */
  protected final void writeIndividualCommit(OP_CONTEXT ctx, CommitLogEntry entry)
      throws ReferenceConflictException {
    try (Traced ignore = trace("writeIndividualCommit")) {
      doWriteIndividualCommit(ctx, entry);
    }
  }

  protected abstract void doWriteIndividualCommit(OP_CONTEXT ctx, CommitLogEntry entry)
      throws ReferenceConflictException;

  /**
   * Write multiple new commit-entries, the given commit entries are to be persisted as is. All
   * values of the * given {@link CommitLogEntry} can be considered valid and consistent.
   *
   * <p>Implementations however can enforce strict consistency checks/guarantees, like a best-effort
   * approach to prevent hash-collisions but without any other consistency checks/guarantees.
   */
  protected final void writeMultipleCommits(OP_CONTEXT ctx, List<CommitLogEntry> entries)
      throws ReferenceConflictException {
    try (Traced ignore = trace("writeMultipleCommits").tag(TAG_COUNT, entries.size())) {
      doWriteMultipleCommits(ctx, entries);
    }
  }

  protected abstract void doWriteMultipleCommits(OP_CONTEXT ctx, List<CommitLogEntry> entries)
      throws ReferenceConflictException;

  @VisibleForTesting
  public final void writeKeyListEntities(OP_CONTEXT ctx, List<KeyListEntity> newKeyListEntities) {
    try (Traced ignore = trace("writeKeyListEntities").tag(TAG_COUNT, newKeyListEntities.size())) {
      doWriteKeyListEntities(ctx, newKeyListEntities);
    }
  }

  protected abstract void doWriteKeyListEntities(
      OP_CONTEXT ctx, List<KeyListEntity> newKeyListEntities);

  protected static final class ConflictingKeyCheckResult {
    private boolean sinceSeen;
    private CommitLogEntry headCommit;

    public boolean isSinceSeen() {
      return sinceSeen;
    }

    public CommitLogEntry getHeadCommit() {
      return headCommit;
    }
  }

  /**
   * Check whether the commits in the range {@code sinceCommitExcluding] .. [upToCommitIncluding}
   * contain any of the given {@link Key}s.
   *
   * <p>Conflicts are reported via {@code mismatches}.
   */
  protected ConflictingKeyCheckResult checkConflictingKeysForCommit(
      OP_CONTEXT ctx,
      Hash upToCommitIncluding,
      Hash sinceCommitExcluding,
      Set<Key> keys,
      Consumer<String> mismatches)
      throws ReferenceNotFoundException {
    ConflictingKeyCheckResult result = new ConflictingKeyCheckResult();

    Stream<CommitLogEntry> log = readCommitLogStream(ctx, upToCommitIncluding);
    log =
        takeUntilExcludeLast(
            log,
            e -> {
              if (e.getHash().equals(upToCommitIncluding)) {
                result.headCommit = e;
              }
              if (e.getHash().equals(sinceCommitExcluding)) {
                result.sinceSeen = true;
                return true;
              }
              return false;
            });

    Set<Key> handled = new HashSet<>();
    log.forEach(
        e -> {
          e.getPuts()
              .forEach(
                  a -> {
                    if (keys.contains(a.getKey()) && handled.add(a.getKey())) {
                      mismatches.accept(
                          String.format(
                              "Key '%s' has conflicting put-operation from commit '%s'.",
                              a.getKey(), e.getHash().asString()));
                    }
                  });
          e.getDeletes()
              .forEach(
                  a -> {
                    if (keys.contains(a) && handled.add(a)) {
                      mismatches.accept(
                          String.format(
                              "Key '%s' has conflicting delete-operation from commit '%s'.",
                              a, e.getHash().asString()));
                    }
                  });
        });

    return result;
  }

  protected final class CommonAncestorState {
    final Iterator<Hash> toLog;
    final List<Hash> toCommitHashesList;
    final Set<Hash> toCommitHashes = new HashSet<>();

    public CommonAncestorState(OP_CONTEXT ctx, Hash toHead, boolean trackCount) {
      this.toLog = Spliterators.iterator(readCommitLogHashes(ctx, toHead));
      this.toCommitHashesList = trackCount ? new ArrayList<>() : null;
    }

    boolean fetchNext() {
      if (toLog.hasNext()) {
        Hash hash = toLog.next();
        toCommitHashes.add(hash);
        if (toCommitHashesList != null) {
          toCommitHashesList.add(hash);
        }
        return true;
      }
      return false;
    }

    public boolean contains(Hash candidate) {
      return toCommitHashes.contains(candidate);
    }

    public int indexOf(Hash hash) {
      return toCommitHashesList.indexOf(hash);
    }
  }

  /**
   * Finds the common-ancestor of two commit-log-entries. If no common-ancestor is found, throws a
   * {@link ReferenceConflictException} or. Otherwise, this method returns the hash of the
   * common-ancestor.
   */
  protected Hash findCommonAncestor(OP_CONTEXT ctx, Hash from, NamedRef toBranch, Hash toHead)
      throws ReferenceConflictException {

    // TODO this implementation requires guardrails:
    //  max number of "to"-commits to fetch, max number of "from"-commits to fetch,
    //  both impact the cost (CPU, memory, I/O) of a merge operation.

    CommonAncestorState commonAncestorState = new CommonAncestorState(ctx, toHead, false);

    Hash commonAncestorHash =
        findCommonAncestor(ctx, from, commonAncestorState, (dist, hash) -> hash);
    if (commonAncestorHash == null) {
      throw new ReferenceConflictException(
          String.format(
              "No common ancestor found for merge of '%s' into branch '%s'",
              from, toBranch.getName()));
    }
    return commonAncestorHash;
  }

  protected <R> R findCommonAncestor(
      OP_CONTEXT ctx, Hash from, CommonAncestorState state, BiFunction<Integer, Hash, R> result) {
    Iterator<Hash> fromLog = Spliterators.iterator(readCommitLogHashes(ctx, from));
    List<Hash> fromCommitHashes = new ArrayList<>();
    while (true) {
      boolean anyFetched = false;
      for (int i = 0; i < config.getParentsPerCommit(); i++) {
        if (state.fetchNext()) {
          anyFetched = true;
        }
        if (fromLog.hasNext()) {
          fromCommitHashes.add(fromLog.next());
          anyFetched = true;
        }
      }
      if (!anyFetched) {
        return null;
      }

      for (int diffOnFrom = 0; diffOnFrom < fromCommitHashes.size(); diffOnFrom++) {
        Hash f = fromCommitHashes.get(diffOnFrom);
        if (state.contains(f)) {
          return result.apply(diffOnFrom, f);
        }
      }
    }
  }

  /**
   * For merge/transplant, verifies that the given commits do not touch any of the given keys.
   *
   * @param commitsChronological list of commit-log-entries, in order of commit-operations,
   *     chronological order
   */
  protected boolean hasKeyCollisions(
      OP_CONTEXT ctx,
      Hash refHead,
      Set<Key> keysTouchedOnTarget,
      List<CommitLogEntry> commitsChronological,
      Function<Key, ImmutableKeyDetails.Builder> keyDetails)
      throws ReferenceNotFoundException {
    Set<Key> keyCollisions = new HashSet<>();
    for (int i = commitsChronological.size() - 1; i >= 0; i--) {
      CommitLogEntry sourceCommit = commitsChronological.get(i);
      Stream.concat(
              sourceCommit.getPuts().stream().map(KeyWithBytes::getKey),
              sourceCommit.getDeletes().stream())
          .filter(keysTouchedOnTarget::contains)
          .forEach(keyCollisions::add);
    }

    if (!keyCollisions.isEmpty()) {
      removeKeyCollisionsForNamespaces(
          ctx,
          refHead,
          commitsChronological.get(commitsChronological.size() - 1).getHash(),
          keyCollisions);
      if (!keyCollisions.isEmpty()) {
        keyCollisions.forEach(key -> keyDetails.apply(key).conflictType(ConflictType.UNRESOLVABLE));
        return true;
      }
    }
    return false;
  }

  /**
   * If any key collision was found, we need to check whether the key collision was happening on a
   * Namespace and if so, remove that key collision, since Namespaces can be merged/transplanted
   * without problems.
   *
   * @param ctx The context
   * @param hashFromTarget The hash from the target branch
   * @param hashFromSource The hash from the source branch
   * @param keyCollisions The found key collisions
   * @throws ReferenceNotFoundException If the given reference could not be found
   */
  private void removeKeyCollisionsForNamespaces(
      OP_CONTEXT ctx, Hash hashFromTarget, Hash hashFromSource, Set<Key> keyCollisions)
      throws ReferenceNotFoundException {
    Predicate<Entry<Key, ContentAndState<ByteString>>> isNamespace =
        e -> storeWorker.isNamespace(e.getValue().getRefState());
    Set<Key> namespacesOnTarget =
        fetchValues(ctx, hashFromTarget, keyCollisions, ALLOW_ALL).entrySet().stream()
            .filter(isNamespace)
            .map(Entry::getKey)
            .collect(Collectors.toSet());

    // this will be an implicit set intersection between the namespaces on source & target
    Set<Key> intersection =
        fetchValues(ctx, hashFromSource, namespacesOnTarget, ALLOW_ALL).entrySet().stream()
            .filter(isNamespace)
            .map(Entry::getKey)
            .collect(Collectors.toSet());

    // remove all keys related to namespaces from the existing collisions
    intersection.forEach(keyCollisions::remove);
  }

  /**
   * For merge/transplant, applies one squashed commit derived from the given commits onto the
   * target-hash.
   */
  protected CommitLogEntry squashCommits(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash toHead,
      List<CommitLogEntry> commitsToMergeChronological,
      Consumer<Hash> newKeyLists,
      MetadataRewriter<ByteString> rewriteMetadata,
      Predicate<Key> includeKeyPredicate)
      throws ReferenceConflictException, ReferenceNotFoundException {

    List<ByteString> commitMeta = new ArrayList<>();
    Map<Key, KeyWithBytes> puts = new HashMap<>();
    Set<Key> deletes = new HashSet<>();
    for (int i = commitsToMergeChronological.size() - 1; i >= 0; i--) {
      CommitLogEntry source = commitsToMergeChronological.get(i);
      for (Key delete : source.getDeletes()) {
        if (includeKeyPredicate.test(delete)) {
          deletes.add(delete);
          puts.remove(delete);
        }
      }
      for (KeyWithBytes put : source.getPuts()) {
        if (includeKeyPredicate.test(put.getKey())) {
          deletes.remove(put.getKey());
          puts.put(put.getKey(), put);
        }
      }

      commitMeta.add(source.getMetadata());
    }

    if (puts.isEmpty() && deletes.isEmpty()) {
      // Copied commit will not contain any operation, skip.
      return null;
    }

    ByteString newCommitMeta = rewriteMetadata.squash(commitMeta);

    CommitLogEntry targetHeadCommit = fetchFromCommitLog(ctx, toHead);

    int parentsPerCommit = config.getParentsPerCommit();
    List<Hash> parents = new ArrayList<>(parentsPerCommit);
    parents.add(toHead);
    long commitSeq;
    int keyListDistance;
    if (targetHeadCommit != null) {
      List<Hash> p = targetHeadCommit.getParents();
      parents.addAll(p.subList(0, Math.min(p.size(), parentsPerCommit - 1)));
      commitSeq = targetHeadCommit.getCommitSeq() + 1;
      keyListDistance = targetHeadCommit.getKeyListDistance();
    } else {
      commitSeq = 1;
      keyListDistance = 0;
    }

    CommitLogEntry squashedCommit =
        buildIndividualCommit(
            ctx,
            timeInMicros,
            parents,
            commitSeq,
            newCommitMeta,
            puts.values(),
            deletes,
            keyListDistance,
            newKeyLists,
            h -> null);

    writeIndividualCommit(ctx, squashedCommit);

    return squashedCommit;
  }

  /** For merge/transplant, applies the given commits onto the target-hash. */
  protected Hash copyCommits(
      OP_CONTEXT ctx,
      long timeInMicros,
      Hash targetHead,
      List<CommitLogEntry> commitsChronological,
      Consumer<Hash> newKeyLists,
      MetadataRewriter<ByteString> rewriteMetadata,
      Predicate<Key> includeKeyPredicate)
      throws ReferenceNotFoundException {
    int parentsPerCommit = config.getParentsPerCommit();

    List<Hash> parents = new ArrayList<>(parentsPerCommit);
    CommitLogEntry targetHeadCommit = fetchFromCommitLog(ctx, targetHead);
    long commitSeq;
    if (targetHeadCommit != null) {
      parents.addAll(targetHeadCommit.getParents());
      commitSeq = targetHeadCommit.getCommitSeq() + 1;
    } else {
      commitSeq = 1L;
    }

    int keyListDistance = targetHeadCommit != null ? targetHeadCommit.getKeyListDistance() : 0;

    Map<Hash, CommitLogEntry> unwrittenCommits = new HashMap<>();

    // Rewrite commits to transplant and store those in 'commitsToTransplantReverse'
    for (int i = commitsChronological.size() - 1; i >= 0; i--, commitSeq++) {
      CommitLogEntry sourceCommit = commitsChronological.get(i);

      List<KeyWithBytes> puts =
          sourceCommit.getPuts().stream()
              .filter(p -> includeKeyPredicate.test(p.getKey()))
              .collect(Collectors.toList());
      List<Key> deletes =
          sourceCommit.getDeletes().stream()
              .filter(includeKeyPredicate)
              .collect(Collectors.toList());

      if (puts.isEmpty() && deletes.isEmpty()) {
        // Copied commit will not contain any operation, skip.
        commitsChronological.remove(i);
        continue;
      }

      while (parents.size() > parentsPerCommit - 1) {
        parents.remove(parentsPerCommit - 1);
      }
      if (parents.isEmpty()) {
        parents.add(targetHead);
      } else {
        parents.add(0, targetHead);
      }

      ByteString updatedMetadata = rewriteMetadata.rewriteSingle(sourceCommit.getMetadata());

      CommitLogEntry newEntry =
          buildIndividualCommit(
              ctx,
              timeInMicros,
              parents,
              commitSeq,
              updatedMetadata,
              puts,
              deletes,
              keyListDistance,
              newKeyLists,
              unwrittenCommits::get);
      keyListDistance = newEntry.getKeyListDistance();

      unwrittenCommits.put(newEntry.getHash(), newEntry);

      if (!newEntry.getHash().equals(sourceCommit.getHash())) {
        commitsChronological.set(i, newEntry);
      } else {
        // Newly built CommitLogEntry is equal to the CommitLogEntry to transplant.
        // This can happen, if the commit to transplant has NO_ANCESTOR as its parent.
        commitsChronological.remove(i);
      }

      targetHead = newEntry.getHash();
    }
    return targetHead;
  }

  /**
   * Verifies that the current global-states match the {@code expectedStates}, produces human
   * readable messages for the violations.
   */
  protected void checkExpectedGlobalStates(
      OP_CONTEXT ctx, CommitParams commitParams, Consumer<String> mismatches)
      throws ReferenceNotFoundException {
    Map<ContentId, Optional<ByteString>> expectedStates = commitParams.getExpectedStates();
    if (expectedStates.isEmpty()) {
      return;
    }

    Map<ContentId, ByteString> globalStates = fetchGlobalStates(ctx, expectedStates.keySet());
    for (Entry<ContentId, Optional<ByteString>> expectedState : expectedStates.entrySet()) {
      ByteString currentState = globalStates.get(expectedState.getKey());
      if (currentState == null) {
        if (expectedState.getValue().isPresent()) {
          mismatches.accept(
              String.format(
                  "No current global-state for content-id '%s'.", expectedState.getKey()));
        }
      } else {
        if (!expectedState.getValue().isPresent()) {
          // This happens, when a table's being created on a branch, but that table already exists.
          mismatches.accept(
              String.format(
                  "Global-state for content-id '%s' already exists.", expectedState.getKey()));
        } else if (!currentState.equals(expectedState.getValue().get())) {
          mismatches.accept(
              String.format(
                  "Mismatch in global-state for content-id '%s'.", expectedState.getKey()));
        }
      }
    }
  }

  /** Load the refLog entry for the given hash, return {@code null}, if not found. */
  protected final RefLog fetchFromRefLog(OP_CONTEXT ctx, Hash refLogId) {
    try (Traced ignore =
        trace("fetchFromRefLog").tag(TAG_HASH, refLogId != null ? refLogId.asString() : "HEAD")) {
      return doFetchFromRefLog(ctx, refLogId);
    }
  }

  protected abstract RefLog doFetchFromRefLog(OP_CONTEXT ctx, Hash refLogId);

  /**
   * Fetch multiple {@link RefLog refLog-entries} from the refLog. The returned list must have
   * exactly as many elements as in the parameter {@code hashes}. Non-existing hashes are returned
   * as {@code null}.
   */
  protected final List<RefLog> fetchPageFromRefLog(OP_CONTEXT ctx, List<Hash> hashes) {
    if (hashes.isEmpty()) {
      return Collections.emptyList();
    }
    try (Traced ignore =
        trace("fetchPageFromRefLog")
            .tag(TAG_HASH, hashes.get(0).asString())
            .tag(TAG_COUNT, hashes.size())) {
      return doFetchPageFromRefLog(ctx, hashes);
    }
  }

  protected abstract List<RefLog> doFetchPageFromRefLog(OP_CONTEXT ctx, List<Hash> hashes);

  /** Reads from the refLog starting at the given refLog-hash. */
  protected Stream<RefLog> readRefLogStream(OP_CONTEXT ctx, Hash initialHash)
      throws RefLogNotFoundException {
    Spliterator<RefLog> split = readRefLog(ctx, initialHash);
    return StreamSupport.stream(split, false);
  }

  protected abstract Spliterator<RefLog> readRefLog(OP_CONTEXT ctx, Hash initialHash)
      throws RefLogNotFoundException;

  protected void tryLoopStateCompletion(@Nonnull Boolean success, TryLoopState state) {
    tryLoopFinished(
        success ? "success" : "fail", state.getRetries(), state.getDuration(NANOSECONDS));
  }

  protected void repositoryEvent(Supplier<? extends AdapterEvent.Builder<?, ?>> eventBuilder) {
    if (eventConsumer != null && eventBuilder != null) {
      AdapterEvent event = eventBuilder.get().eventTimeMicros(commitTimeInMicros()).build();
      try {
        eventConsumer.accept(event);
      } catch (RuntimeException e) {
        repositoryEventDeliveryFailed(event, e);
      }
    }
  }

  private static void repositoryEventDeliveryFailed(AdapterEvent event, RuntimeException e) {
    LOGGER.warn(
        "Repository event delivery failed for operation type {}", event.getOperationType(), e);
    Tracer t = GlobalTracer.get();
    Span span = t.activeSpan();
    Span log =
        span.log(
            ImmutableMap.of(
                Fields.EVENT,
                Tags.ERROR.getKey(),
                Fields.MESSAGE,
                "Repository event delivery failed",
                Fields.ERROR_OBJECT,
                e));
    Tags.ERROR.set(log, true);
  }
}
