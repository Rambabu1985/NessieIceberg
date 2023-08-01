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
package org.projectnessie.services.impl;

import static java.util.Collections.singleton;
import static org.projectnessie.services.authz.Check.canReadContentKey;
import static org.projectnessie.services.authz.Check.canViewReference;
import static org.projectnessie.services.hash.HashValidator.ANY_HASH;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableSet;
import java.security.Principal;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.error.NessieReferenceNotFoundException;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.DiffResponse.DiffEntry;
import org.projectnessie.services.authz.Authorizer;
import org.projectnessie.services.authz.AuthzPaginationIterator;
import org.projectnessie.services.authz.Check;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.services.hash.ResolvedHash;
import org.projectnessie.services.spi.DiffService;
import org.projectnessie.services.spi.PagedResponseHandler;
import org.projectnessie.versioned.Diff;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.VersionStore.KeyRestrictions;
import org.projectnessie.versioned.WithHash;
import org.projectnessie.versioned.paging.PaginationIterator;

public class DiffApiImpl extends BaseApiImpl implements DiffService {

  public DiffApiImpl(
      ServerConfig config,
      VersionStore store,
      Authorizer authorizer,
      Supplier<Principal> principal) {
    super(config, store, authorizer, principal);
  }

  @Override
  public <R> R getDiff(
      String fromRef,
      String fromHash,
      String toRef,
      String toHash,
      String pagingToken,
      PagedResponseHandler<R, DiffEntry> pagedResponseHandler,
      Consumer<WithHash<NamedRef>> fromReference,
      Consumer<WithHash<NamedRef>> toReference,
      ContentKey minKey,
      ContentKey maxKey,
      ContentKey prefixKey,
      List<ContentKey> requestedKeys,
      String filter)
      throws NessieNotFoundException {
    ResolvedHash from =
        getHashResolver().resolveHashOnRef(fromRef, fromHash, "From hash", ANY_HASH);
    ResolvedHash to = getHashResolver().resolveHashOnRef(toRef, toHash, "To hash", ANY_HASH);
    NamedRef fromNamedRef = from.getNamedRef();
    NamedRef toNamedRef = to.getNamedRef();
    fromReference.accept(from);
    toReference.accept(to);

    startAccessCheck().canViewReference(fromNamedRef).canViewReference(toNamedRef).checkAndThrow();

    try {
      Predicate<ContentKey> contentKeyPredicate = null;
      if (requestedKeys != null && !requestedKeys.isEmpty()) {
        contentKeyPredicate = new HashSet<>(requestedKeys)::contains;
      }
      if (!Strings.isNullOrEmpty(filter)) {
        Predicate<ContentKey> filterPredicate = filterOnContentKey(filter);
        contentKeyPredicate =
            contentKeyPredicate != null
                ? contentKeyPredicate.and(filterPredicate)
                : filterPredicate;
      }

      try (PaginationIterator<Diff> diffs =
          getStore()
              .getDiffs(
                  from.getHash(),
                  to.getHash(),
                  pagingToken,
                  KeyRestrictions.builder()
                      .minKey(minKey)
                      .maxKey(maxKey)
                      .prefixKey(prefixKey)
                      .contentKeyPredicate(contentKeyPredicate)
                      .build())) {

        AuthzPaginationIterator<Diff> authz =
            new AuthzPaginationIterator<Diff>(
                diffs, super::startAccessCheck, getServerConfig().accessChecksBatchSize()) {
              @Override
              protected Set<Check> checksForEntry(Diff entry) {
                if (entry.getFromValue().isPresent()) {
                  if (entry.getToValue().isPresent()
                      && !Objects.equals(entry.getFromKey(), entry.getToKey())) {
                    return ImmutableSet.of(
                        canReadContentKey(fromNamedRef, entry.getFromKey()),
                        canReadContentKey(fromNamedRef, entry.getToKey()));
                  } else {
                    return singleton(canReadContentKey(fromNamedRef, entry.getFromKey()));
                  }
                } else {
                  return singleton(canReadContentKey(toNamedRef, entry.getToKey()));
                }
              }
            }.initialCheck(canViewReference(fromNamedRef))
                .initialCheck(canViewReference(toNamedRef));

        while (authz.hasNext()) {
          Diff diff = authz.next();
          ContentKey key = diff.contentKey();

          DiffEntry entry =
              DiffEntry.diffEntry(
                  key, diff.getFromValue().orElse(null), diff.getToValue().orElse(null));

          if (!pagedResponseHandler.addEntry(entry)) {
            pagedResponseHandler.hasMore(authz.tokenForCurrent());
            break;
          }
        }

        return pagedResponseHandler.build();
      }

    } catch (ReferenceNotFoundException e) {
      throw new NessieReferenceNotFoundException(e.getMessage(), e);
    }
  }
}
