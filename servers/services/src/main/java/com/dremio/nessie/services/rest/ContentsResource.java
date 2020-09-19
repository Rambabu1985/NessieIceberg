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

package com.dremio.nessie.services.rest;

import java.security.Principal;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import javax.enterprise.context.RequestScoped;
import javax.inject.Inject;

import org.eclipse.microprofile.metrics.annotation.Metered;
import org.eclipse.microprofile.metrics.annotation.Timed;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.dremio.nessie.api.ContentsApi;
import com.dremio.nessie.error.NessieConflictException;
import com.dremio.nessie.error.NessieNotFoundException;
import com.dremio.nessie.model.CommitMeta;
import com.dremio.nessie.model.Contents;
import com.dremio.nessie.model.ContentsKey;
import com.dremio.nessie.model.ImmutableCommitMeta;
import com.dremio.nessie.services.config.ServerConfig;
import com.dremio.nessie.versioned.BranchName;
import com.dremio.nessie.versioned.Delete;
import com.dremio.nessie.versioned.Hash;
import com.dremio.nessie.versioned.Key;
import com.dremio.nessie.versioned.Put;
import com.dremio.nessie.versioned.ReferenceConflictException;
import com.dremio.nessie.versioned.ReferenceNotFoundException;

/**
 * REST endpoint for contents.
 */
@RequestScoped
public class ContentsResource extends BaseResource implements ContentsApi {

  private static final Logger logger = LoggerFactory.getLogger(ContentsResource.class);

  @Inject
  private ServerConfig config;

  @Metered
  @Timed(name = "timed-contents-get")
  @Override
  public Contents getContents(ContentsKey key, String incomingRef) throws NessieNotFoundException {
    Hash ref = getHashOrThrow(incomingRef);
    try {
      Contents obj = store.getValue(ref, toKey(key));
      if (obj != null) {
        return obj;
      }
      throw new NessieNotFoundException("Requested contents do not exist for specified reference.");
    } catch (ReferenceNotFoundException e) {
      throw new NessieNotFoundException(String.format("Provided reference [%s] does not exist.", incomingRef), e);
    }
  }

  @Metered
  @Timed(name = "timed-contents-delete")
  @Override
  public Contents getContents(ContentsKey key) throws NessieNotFoundException {
    return getContents(key, config.getDefaultBranch());
  }

  @Metered
  @Timed(name = "timed-contents-set")
  @Override
  public void setContents(ContentsKey key, String branch, String hash, String message, Contents contents)
      throws NessieNotFoundException, NessieConflictException {
    doOps(branch, hash, message, Arrays.asList(Put.of(toKey(key), contents)));
  }

  @Metered
  @Timed(name = "timed-contents-set")
  @Override
  public void setContents(ContentsKey key, String hash, String message, Contents contents)
      throws NessieNotFoundException, NessieConflictException {
    setContents(key, config.getDefaultBranch(), hash, message, contents);
  }

  @Metered
  @Timed(name = "timed-contents-delete")
  @Override
  public void deleteContents(ContentsKey key, String branch, String hash, String message)
      throws NessieNotFoundException, NessieConflictException {
    doOps(branch, hash, message, Arrays.asList(Delete.of(toKey(key))));
  }

  @Metered
  @Timed(name = "timed-contents-delete")
  @Override
  public void deleteContents(ContentsKey key, String hash, String message)
      throws NessieNotFoundException, NessieConflictException {
    deleteContents(key, config.getDefaultBranch(), hash, message);
  }

  void doOps(String branch,
      String hash, String message, List<com.dremio.nessie.versioned.Operation<Contents>> operations)
      throws NessieConflictException, NessieNotFoundException {
    try {
      store.commit(
          BranchName.of(branch),
          Optional.of(Hash.of(hash)),
          meta(principal, message),
          operations);
    } catch (ReferenceConflictException e) {
      throw new NessieConflictException("Failed to commit data. Provided hash does not match current value.", e);
    } catch (ReferenceNotFoundException e) {
      throw new NessieConflictException("Failed to commit data. Provided ref was not found.", e);
    }
  }

  static CommitMeta meta(Principal principal, String message) {
    return ImmutableCommitMeta.builder()
        .commiter(principal == null ? "" : principal.getName())
        .message(message == null ? "" : message)
        .commitTime(System.currentTimeMillis())
        .build();
  }

  private static Key toKey(ContentsKey key) {
    return Key.of(key.getElements().toArray(new String[key.getElements().size()]));
  }

}
