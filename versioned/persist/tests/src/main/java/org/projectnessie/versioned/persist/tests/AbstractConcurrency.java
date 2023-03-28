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

import static io.micrometer.core.instrument.Metrics.globalRegistry;
import static org.assertj.core.api.Assertions.assertThat;
import static org.projectnessie.versioned.store.DefaultStoreWorker.payloadForContent;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntFunction;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.model.ContentKey;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.ReferenceConflictException;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.ReferenceRetryFailureException;
import org.projectnessie.versioned.persist.adapter.CommitParams;
import org.projectnessie.versioned.persist.adapter.ContentId;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.ImmutableCommitParams;
import org.projectnessie.versioned.persist.adapter.KeyFilterPredicate;
import org.projectnessie.versioned.persist.adapter.KeyWithBytes;
import org.projectnessie.versioned.persist.adapter.spi.DatabaseAdapterMetrics;
import org.projectnessie.versioned.store.DefaultStoreWorker;
import org.projectnessie.versioned.testworker.OnRefOnly;

/**
 * Performs concurrent commits with four different strategies, just verifying that either no
 * exception or only {@link org.projectnessie.versioned.ReferenceConflictException}s are thrown.
 *
 * <p>Strategies are:
 *
 * <ul>
 *   <li>Single branch, shared keys - contention on the branch and on the keys
 *   <li>Single branch, distinct keys - contention on the branch
 *   <li>Branch per thread, shared keys - contention on the content-ids
 *   <li>Branch per thread, distinct keys - no contention, except for implementations that use a
 *       single "root state pointer", like the non-transactional database-adapters
 * </ul>
 *
 * <p>The implementation allows using varying numbers of tables (= keys), but 3 turned out to be
 * good enough.
 */
public abstract class AbstractConcurrency {

  private final DatabaseAdapter databaseAdapter;

  protected AbstractConcurrency(DatabaseAdapter databaseAdapter) {
    this.databaseAdapter = databaseAdapter;
  }

  static class Variation {
    final int threads = Math.max(4, Runtime.getRuntime().availableProcessors());
    final boolean singleBranch;
    final int tables;

    Variation(boolean singleBranch, int tables) {
      this.singleBranch = singleBranch;
      this.tables = tables;
    }

    @Override
    public String toString() {
      return "threads=" + threads + ", singleBranch=" + singleBranch + ", tables=" + tables;
    }
  }

  /** Cartesian product of all {@link Variation}s for {@link #concurrency(Variation)}. */
  @SuppressWarnings("unused")
  static Stream<Variation> concurrencyVariations() {
    return Stream.of(Boolean.FALSE, Boolean.TRUE)
        .flatMap(singleBranch -> Stream.of(3).map(tables -> new Variation(singleBranch, tables)));
  }

  @ParameterizedTest
  @MethodSource("concurrencyVariations")
  void concurrency(Variation variation) throws Exception {
    new ArrayList<>(globalRegistry.getRegistries()).forEach(globalRegistry::remove);
    globalRegistry.add(new SimpleMeterRegistry());

    ExecutorService executor = Executors.newFixedThreadPool(variation.threads);
    AtomicInteger commitsOK = new AtomicInteger();
    AtomicInteger retryFailures = new AtomicInteger();
    AtomicBoolean stopFlag = new AtomicBoolean();
    List<Runnable> tasks = new ArrayList<>(variation.threads);
    Map<ContentKey, ContentId> keyToContentId = new HashMap<>();
    Map<BranchName, Map<ContentKey, ByteString>> onRefStates = new ConcurrentHashMap<>();
    try {
      CountDownLatch startLatch = new CountDownLatch(1);
      Map<BranchName, Set<ContentKey>> keysPerBranch = new HashMap<>();
      for (int i = 0; i < variation.threads; i++) {
        BranchName branch = BranchName.of("concurrency-" + (variation.singleBranch ? "shared" : i));
        List<ContentKey> keys = new ArrayList<>(variation.tables);

        for (int k = 0; k < variation.tables; k++) {
          String variationKey = Integer.toString(i);
          ContentKey key = ContentKey.of("some-key-" + variationKey + "-table-" + k);
          keys.add(key);
          keyToContentId.put(key, ContentId.of(String.format("%s-table-%d", variationKey, k)));
          keysPerBranch.computeIfAbsent(branch, x -> new HashSet<>()).add(key);
        }

        tasks.add(
            () -> {
              try {
                assertThat(startLatch.await(2, TimeUnit.SECONDS)).isTrue();

                for (int commit = 0; ; commit++) {
                  if (stopFlag.get()) {
                    break;
                  }

                  ImmutableCommitParams.Builder commitAttempt = ImmutableCommitParams.builder();

                  for (int ki = 0; ki < keys.size(); ki++) {
                    ContentKey key = keys.get(ki);
                    ContentId contentId = keyToContentId.get(key);
                    OnRefOnly c = OnRefOnly.onRef("", contentId.getId());
                    commitAttempt.addPuts(
                        KeyWithBytes.of(
                            keys.get(ki),
                            contentId,
                            payloadForContent(c),
                            DefaultStoreWorker.instance().toStoreOnReferenceState(c)));
                  }

                  try {
                    commitAttempt
                        .toBranch(branch)
                        .commitMetaSerialized(
                            ByteString.copyFromUtf8(
                                "commit #"
                                    + commit
                                    + " to "
                                    + branch.getName()
                                    + " something "
                                    + ThreadLocalRandom.current().nextLong()));
                    commitAndRecord(onRefStates, branch, commitAttempt);
                    commitsOK.incrementAndGet();
                  } catch (ReferenceRetryFailureException retry) {
                    retryFailures.incrementAndGet();
                  }
                }
              } catch (Exception e) {
                e.printStackTrace();
                throw new RuntimeException(e);
              }
            });
      }

      for (Entry<BranchName, Set<ContentKey>> branchKeys : keysPerBranch.entrySet()) {
        BranchName branch = branchKeys.getKey();
        databaseAdapter.create(
            branch, databaseAdapter.hashOnReference(BranchName.of("main"), Optional.empty()));
        ImmutableCommitParams.Builder commitAttempt =
            ImmutableCommitParams.builder()
                .toBranch(branchKeys.getKey())
                .commitMetaSerialized(
                    ByteString.copyFromUtf8("initial commit for " + branch.getName()));
        for (ContentKey k : branchKeys.getValue()) {
          ContentId contentId = keyToContentId.get(k);
          OnRefOnly c = OnRefOnly.onRef("", contentId.getId());
          commitAttempt.addPuts(
              KeyWithBytes.of(
                  k,
                  contentId,
                  payloadForContent(c),
                  DefaultStoreWorker.instance().toStoreOnReferenceState(c)));
        }
        commitAndRecord(onRefStates, branch, commitAttempt);
      }

      // Submit all 'Runnable's as 'CompletableFuture's and construct a combined 'CompletableFuture'
      // that we can wait for.
      CompletableFuture<Void> combinedFuture =
          CompletableFuture.allOf(
              tasks.stream()
                  .map(r -> CompletableFuture.runAsync(r, executor))
                  .toArray((IntFunction<CompletableFuture<?>[]>) CompletableFuture[]::new));

      startLatch.countDown();
      Thread.sleep(2_000);
      stopFlag.set(true);

      // 30 seconds is long, but necessary to let transactional databases detect deadlocks, which
      // cause Nessie-commit-retries.
      combinedFuture.get(30, TimeUnit.SECONDS);

      for (Entry<BranchName, Set<ContentKey>> branchKeys : keysPerBranch.entrySet()) {
        BranchName branch = branchKeys.getKey();
        Hash hash = databaseAdapter.hashOnReference(branch, Optional.empty());
        ArrayList<ContentKey> keys = new ArrayList<>(branchKeys.getValue());
        // Note: only fetch the values, cannot assert those here.
        databaseAdapter.values(hash, keys, KeyFilterPredicate.ALLOW_ALL);
      }
    } finally {
      stopFlag.set(true);

      executor.shutdownNow();

      // 30 seconds is long, but necessary to let transactional databases detect deadlocks, which
      // cause Nessie-commit-retries.
      assertThat(executor.awaitTermination(30, TimeUnit.SECONDS)).isTrue();

      System.out.printf(
          "AbstractConcurrency.concurrency - %s : Commits OK: %s  Retry-Failures: %s%n",
          variation, commitsOK, retryFailures);
      System.out.printf(
          "AbstractConcurrency.concurrency - %s : try-loop success: count: %6d  retries: %6d  total-time-millis: %d%n",
          variation,
          (long) DatabaseAdapterMetrics.tryLoopCounts("success").count(),
          (long) DatabaseAdapterMetrics.tryLoopRetries("success").count(),
          (long)
              DatabaseAdapterMetrics.tryLoopDuration("success").totalTime(TimeUnit.MILLISECONDS));
      System.out.printf(
          "AbstractConcurrency.concurrency - %s : try-loop failure: count: %6d  retries: %6d  total-time-millis: %d%n",
          variation,
          (long) DatabaseAdapterMetrics.tryLoopCounts("fail").count(),
          (long) DatabaseAdapterMetrics.tryLoopRetries("fail").count(),
          (long) DatabaseAdapterMetrics.tryLoopDuration("fail").totalTime(TimeUnit.MILLISECONDS));

      new ArrayList<>(globalRegistry.getRegistries()).forEach(globalRegistry::remove);
    }
  }

  private void commitAndRecord(
      Map<BranchName, Map<ContentKey, ByteString>> onRefStates,
      BranchName branch,
      ImmutableCommitParams.Builder commitAttempt)
      throws ReferenceConflictException, ReferenceNotFoundException {
    CommitParams c = commitAttempt.build();
    databaseAdapter.commit(c);
    Map<ContentKey, ByteString> onRef =
        onRefStates.computeIfAbsent(branch, b -> new ConcurrentHashMap<>());
    c.getPuts().forEach(kwb -> onRef.put(kwb.getKey(), kwb.getValue()));
  }
}
