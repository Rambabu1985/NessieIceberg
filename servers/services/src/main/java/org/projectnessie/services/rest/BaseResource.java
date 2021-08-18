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
package org.projectnessie.services.rest;

import java.security.Principal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.annotation.Nullable;
import javax.ws.rs.core.SecurityContext;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Contents;
import org.projectnessie.services.authz.AccessChecker;
import org.projectnessie.services.authz.ServerAccessContext;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.versioned.Hash;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.Ref;
import org.projectnessie.versioned.ReferenceNotFoundException;
import org.projectnessie.versioned.VersionStore;
import org.projectnessie.versioned.WithHash;

abstract class BaseResource {
  private final ServerConfig config;

  private final MultiTenant multiTenant;

  private final VersionStore<Contents, CommitMeta, Contents.Type> store;

  private final AccessChecker accessChecker;

  // Mandated by CDI 2.0
  protected BaseResource() {
    this(null, null, null, null);
  }

  protected BaseResource(
      ServerConfig config,
      MultiTenant multiTenant,
      VersionStore<Contents, CommitMeta, Contents.Type> store,
      AccessChecker accessChecker) {
    this.config = config;
    this.multiTenant = multiTenant;
    this.store = store;
    this.accessChecker = accessChecker;
  }

  Optional<WithHash<Ref>> getRefWithHash(String ref) {
    try {
      WithHash<Ref> whr = store.toRef(Optional.ofNullable(ref).orElse(config.getDefaultBranch()));
      return Optional.of(whr);
    } catch (ReferenceNotFoundException e) {
      return Optional.empty();
    }
  }

  WithHash<NamedRef> namedRefWithHashOrThrow(String namedRef, @Nullable String hashOnRef)
      throws NessieNotFoundException {
    List<WithHash<NamedRef>> collect;
    try (Stream<WithHash<NamedRef>> refsStream = store.getNamedRefs()) {
      collect =
          refsStream
              .filter(
                  r ->
                      r.getValue().getName().equals(namedRef)
                          || r.getValue().getName().equals(config.getDefaultBranch()))
              .collect(Collectors.toList());
    }
    WithHash<NamedRef> namedRefWithHash;
    if (collect.size() == 1) {
      namedRefWithHash = collect.get(0);
    } else {
      namedRefWithHash =
          collect.stream()
              .filter(r -> r.getValue().getName().equals(namedRef))
              .findFirst()
              .orElseThrow(
                  () ->
                      new NessieNotFoundException(
                          String.format("Named reference '%s' not found.", namedRef)));
    }

    try {
      if (null == hashOnRef) {
        return namedRefWithHash;
      }

      // we need to make sure that the hash in fact exists on the named ref
      Hash hash = Hash.of(hashOnRef);
      try (Stream<WithHash<CommitMeta>> commits = store.getCommits(namedRefWithHash.getValue())) {
        if (commits.noneMatch(c -> c.getHash().equals(hash))) {
          throw new NessieNotFoundException(
              String.format("Could not find commit '%s' in reference '%s'.", hashOnRef, namedRef));
        }
      }
      return WithHash.of(hash, namedRefWithHash.getValue());
    } catch (ReferenceNotFoundException e) {
      throw new NessieNotFoundException(
          String.format("Could not find commit '%s' in reference '%s'.", hashOnRef, namedRef));
    }
  }

  protected ServerConfig getConfig() {
    return config;
  }

  protected VersionStore<Contents, CommitMeta, Contents.Type> getStore() {
    return store;
  }

  protected abstract SecurityContext getSecurityContext();

  protected Principal getPrincipal() {
    return null == getSecurityContext() ? null : getSecurityContext().getUserPrincipal();
  }

  public MultiTenant getMultiTenant() {
    return multiTenant;
  }

  protected AccessChecker getAccessChecker() {
    return accessChecker;
  }

  protected ServerAccessContext createAccessContext() {
    return ServerAccessContext.of(UUID.randomUUID().toString(), getPrincipal());
  }
}
