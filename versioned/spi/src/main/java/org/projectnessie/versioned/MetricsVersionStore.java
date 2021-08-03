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
package org.projectnessie.versioned;

import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Metrics;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.Timer.Sample;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nonnull;

/**
 * A {@link VersionStore} wrapper that publishes metrics via Micrometer.
 *
 * @param <VALUE> see {@link VersionStore}
 * @param <METADATA> see {@link VersionStore}
 */
public final class MetricsVersionStore<VALUE, METADATA, VALUE_TYPE extends Enum<VALUE_TYPE>>
    implements VersionStore<VALUE, METADATA, VALUE_TYPE> {

  private final VersionStore<VALUE, METADATA, VALUE_TYPE> delegate;
  private final MeterRegistry registry;
  private final Clock clock;
  private final Iterable<Tag> commonTags;

  /**
   * Constructor taking the delegate version-store and the metrics-registry.
   *
   * @param delegate delegate version-store
   * @param registry metrics-registry
   */
  MetricsVersionStore(
      VersionStore<VALUE, METADATA, VALUE_TYPE> delegate, MeterRegistry registry, Clock clock) {
    this.delegate = delegate;
    this.registry = registry;
    this.clock = clock;
    this.commonTags = Tags.of("application", "Nessie");
  }

  public MetricsVersionStore(VersionStore<VALUE, METADATA, VALUE_TYPE> delegate) {
    this(delegate, Metrics.globalRegistry, Clock.SYSTEM);
  }

  @Override
  @Nonnull
  public Hash toHash(@Nonnull NamedRef ref) throws ReferenceNotFoundException {
    return delegate1Ex("tohash", () -> delegate.toHash(ref));
  }

  @Override
  public WithHash<NamedRef> toRef(@Nonnull String refOfUnknownType)
      throws ReferenceNotFoundException {
    return delegate1Ex("toref", () -> delegate.toRef(refOfUnknownType));
  }

  @Override
  public Hash commit(
      @Nonnull BranchName branch,
      @Nonnull Optional<Hash> referenceHash,
      @Nonnull METADATA metadata,
      @Nonnull List<Operation<VALUE>> operations)
      throws ReferenceNotFoundException, ReferenceConflictException {
    return this.<Hash, ReferenceNotFoundException, ReferenceConflictException>delegate2ExR(
        "commit", () -> delegate.commit(branch, referenceHash, metadata, operations));
  }

  @Override
  public Hash transplant(
      BranchName targetBranch,
      Optional<Hash> referenceHash,
      NamedRef source,
      List<Hash> sequenceToTransplant)
      throws ReferenceNotFoundException, ReferenceConflictException {
    return this.<Hash, ReferenceNotFoundException, ReferenceConflictException>delegate2ExR(
        "transplant",
        () -> delegate.transplant(targetBranch, referenceHash, source, sequenceToTransplant));
  }

  @Override
  public Hash merge(
      NamedRef from, Optional<Hash> fromHash, BranchName toBranch, Optional<Hash> expectedHash)
      throws ReferenceNotFoundException, ReferenceConflictException {
    return this.<Hash, ReferenceNotFoundException, ReferenceConflictException>delegate2ExR(
        "merge", () -> delegate.merge(from, fromHash, toBranch, expectedHash));
  }

  @Override
  public void assign(
      NamedRef ref, Optional<Hash> expectedHash, NamedRef target, Optional<Hash> targetHash)
      throws ReferenceNotFoundException, ReferenceConflictException {
    this.<ReferenceNotFoundException, ReferenceConflictException>delegate2Ex(
        "assign", () -> delegate.assign(ref, expectedHash, target, targetHash));
  }

  @Override
  public Hash create(NamedRef ref, Optional<NamedRef> target, Optional<Hash> targetHash)
      throws ReferenceNotFoundException, ReferenceAlreadyExistsException {
    return this.<Hash, ReferenceNotFoundException, ReferenceAlreadyExistsException>delegate2ExR(
        "create", () -> delegate.create(ref, target, targetHash));
  }

  @Override
  public void delete(NamedRef ref, Optional<Hash> hash)
      throws ReferenceNotFoundException, ReferenceConflictException {
    this.<ReferenceNotFoundException, ReferenceConflictException>delegate2Ex(
        "delete", () -> delegate.delete(ref, hash));
  }

  @Override
  public Stream<WithHash<NamedRef>> getNamedRefs() {
    return delegateStream("getnamedrefs", delegate::getNamedRefs);
  }

  @Override
  public Stream<WithHash<METADATA>> getCommits(
      NamedRef ref, Optional<Hash> offset, Optional<Hash> untilIncluding)
      throws ReferenceNotFoundException {
    return delegateStream1Ex("getcommits", () -> delegate.getCommits(ref, offset, untilIncluding));
  }

  @Override
  public Stream<WithType<Key, VALUE_TYPE>> getKeys(NamedRef ref, Optional<Hash> hashOnRef)
      throws ReferenceNotFoundException {
    return delegateStream1Ex("getkeys", () -> delegate.getKeys(ref, hashOnRef));
  }

  @Override
  public VALUE getValue(NamedRef ref, Optional<Hash> hashOnRef, Key key)
      throws ReferenceNotFoundException {
    return delegate1Ex("getvalue", () -> delegate.getValue(ref, hashOnRef, key));
  }

  @Override
  public List<Optional<VALUE>> getValues(NamedRef ref, Optional<Hash> hashOnRef, List<Key> keys)
      throws ReferenceNotFoundException {
    return delegate1Ex("getvalues", () -> delegate.getValues(ref, hashOnRef, keys));
  }

  @Override
  public Stream<Diff<VALUE>> getDiffs(
      NamedRef from, Optional<Hash> hashOnFrom, NamedRef to, Optional<Hash> hashOnTo)
      throws ReferenceNotFoundException {
    return delegateStream1Ex("getdiffs", () -> delegate.getDiffs(from, hashOnFrom, to, hashOnTo));
  }

  @Override
  public Collector collectGarbage() {
    return delegate("collectgarbage", delegate::collectGarbage);
  }

  private void measure(String requestName, Sample sample, Exception failure) {
    Timer timer =
        Timer.builder("nessie.versionstore.request")
            .tags(commonTags)
            .tag("request", requestName)
            .tag("error", Boolean.toString(failure != null))
            .publishPercentileHistogram()
            .register(registry);
    sample.stop(timer);
  }

  private <R> Stream<R> delegateStream(String requestName, Delegate<Stream<R>> delegate) {
    Sample sample = Timer.start(clock);
    try {
      return delegate.handle().onClose(() -> measure(requestName, sample, null));
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      measure(requestName, sample, null);
      throw e;
    } catch (RuntimeException e) {
      measure(requestName, sample, e);
      throw e;
    }
  }

  private <R> Stream<R> delegateStream1Ex(
      String requestName, DelegateWith1<Stream<R>, ReferenceNotFoundException> delegate)
      throws ReferenceNotFoundException {
    Sample sample = Timer.start(clock);
    try {
      return delegate.handle().onClose(() -> measure(requestName, sample, null));
    } catch (IllegalArgumentException | ReferenceNotFoundException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      measure(requestName, sample, null);
      throw e;
    } catch (RuntimeException e) {
      measure(requestName, sample, e);
      throw e;
    }
  }

  private <R> R delegate(String requestName, Delegate<R> delegate) {
    Sample sample = Timer.start(clock);
    Exception failure = null;
    try {
      return delegate.handle();
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      throw e;
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      measure(requestName, sample, failure);
    }
  }

  private <R, E1 extends VersionStoreException> R delegate1Ex(
      String requestName, DelegateWith1<R, E1> delegate) throws E1 {
    Sample sample = Timer.start(clock);
    Exception failure = null;
    try {
      return delegate.handle();
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      throw e;
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      measure(requestName, sample, failure);
    }
  }

  private <E1 extends VersionStoreException, E2 extends VersionStoreException> void delegate2Ex(
      String requestName, DelegateWith2<E1, E2> delegate) throws E1, E2 {
    Sample sample = Timer.start(clock);
    Exception failure = null;
    try {
      delegate.handle();
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      throw e;
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      measure(requestName, sample, failure);
    }
  }

  private <R, E1 extends VersionStoreException, E2 extends VersionStoreException> R delegate2ExR(
      String requestName, DelegateWith2R<R, E1, E2> delegate) throws E1, E2 {
    Sample sample = Timer.start(clock);
    Exception failure = null;
    try {
      return delegate.handle();
    } catch (IllegalArgumentException e) {
      // IllegalArgumentException indicates a user-error, not a server error
      throw e;
    } catch (RuntimeException e) {
      failure = e;
      throw e;
    } finally {
      measure(requestName, sample, failure);
    }
  }

  @FunctionalInterface
  interface Delegate<R> {
    R handle();
  }

  @FunctionalInterface
  interface DelegateWith1<R, E1 extends VersionStoreException> {
    R handle() throws E1;
  }

  @FunctionalInterface
  interface DelegateWith2<E1 extends VersionStoreException, E2 extends VersionStoreException> {
    void handle() throws E1, E2;
  }

  @FunctionalInterface
  interface DelegateWith2R<R, E1 extends VersionStoreException, E2 extends VersionStoreException> {
    R handle() throws E1, E2;
  }
}
