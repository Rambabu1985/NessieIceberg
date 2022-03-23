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

import static org.projectnessie.model.Operation.Delete;
import static org.projectnessie.model.Operation.Put;

import java.security.Principal;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import org.projectnessie.api.params.CommitLogParams;
import org.projectnessie.api.params.EntriesParams;
import org.projectnessie.api.params.GetReferenceParams;
import org.projectnessie.api.params.ReferencesParams;
import org.projectnessie.error.NessieConflictException;
import org.projectnessie.error.NessieNotFoundException;
import org.projectnessie.model.Branch;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Content;
import org.projectnessie.model.Content.Type;
import org.projectnessie.model.ContentKey;
import org.projectnessie.model.EntriesResponse;
import org.projectnessie.model.ImmutableEntriesResponse;
import org.projectnessie.model.ImmutableLogEntry;
import org.projectnessie.model.ImmutableLogResponse;
import org.projectnessie.model.ImmutableReferencesResponse;
import org.projectnessie.model.LogResponse;
import org.projectnessie.model.LogResponse.LogEntry;
import org.projectnessie.model.Merge;
import org.projectnessie.model.Operation;
import org.projectnessie.model.Operations;
import org.projectnessie.model.Reference;
import org.projectnessie.model.ReferencesResponse;
import org.projectnessie.model.Transplant;
import org.projectnessie.services.authz.Authorizer;
import org.projectnessie.services.authz.BatchAccessChecker;
import org.projectnessie.services.authz.Check;
import org.projectnessie.services.config.ServerConfig;
import org.projectnessie.versioned.BranchName;
import org.projectnessie.versioned.NamedRef;
import org.projectnessie.versioned.VersionStore;

/** Does authorization checks (if enabled) on the {@link TreeApiImpl}. */
public class TreeApiImplWithAuthorization extends TreeApiImpl {

  public TreeApiImplWithAuthorization(
      ServerConfig config,
      VersionStore<Content, CommitMeta, Type> store,
      Authorizer authorizer,
      Principal principal) {
    super(config, store, authorizer, principal);
  }

  @Override
  public ReferencesResponse getAllReferences(ReferencesParams params) {
    ImmutableReferencesResponse.Builder resp = ReferencesResponse.builder();
    BatchAccessChecker check = startAccessCheck();
    List<Reference> refs =
        super.getAllReferences(params).getReferences().stream()
            .peek(ref -> check.canViewReference(RefUtil.toNamedRef(ref)))
            .collect(Collectors.toList());
    Set<NamedRef> notAllowed =
        check.check().keySet().stream()
            .map(Check::ref)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());
    refs.stream()
        .filter(ref -> !notAllowed.contains(RefUtil.toNamedRef(ref)))
        .forEach(resp::addReferences);
    return resp.build();
  }

  @Override
  public Reference getReferenceByName(GetReferenceParams params) throws NessieNotFoundException {
    Reference ref = super.getReferenceByName(params);
    startAccessCheck().canViewReference(RefUtil.toNamedRef(ref)).checkAndThrow();
    return ref;
  }

  @Override
  public Reference createReference(@Nullable String sourceRefName, Reference reference)
      throws NessieNotFoundException, NessieConflictException {
    BatchAccessChecker check = startAccessCheck().canCreateReference(RefUtil.toNamedRef(reference));

    try {
      check.canViewReference(
          namedRefWithHashOrThrow(sourceRefName, reference.getHash()).getValue());
    } catch (NessieNotFoundException e) {
      // If the default-branch does not exist and hashOnRef points to the "beginning of time",
      // then do not throw a NessieNotFoundException, but re-create the default branch. In all
      // cases, re-throw the exception.
      if (!(reference instanceof Branch
          && reference.getName().equals(getConfig().getDefaultBranch())
          && (null == reference.getHash()
              || getStore().noAncestorHash().asString().equals(reference.getHash())))) {
        throw e;
      }
    }
    check.checkAndThrow();
    return super.createReference(sourceRefName, reference);
  }

  @Override
  protected void assignReference(NamedRef ref, String oldHash, Reference assignTo)
      throws NessieNotFoundException, NessieConflictException {
    startAccessCheck()
        .canViewReference(
            namedRefWithHashOrThrow(assignTo.getName(), assignTo.getHash()).getValue())
        .canAssignRefToHash(ref)
        .checkAndThrow();
    super.assignReference(ref, oldHash, assignTo);
  }

  @Override
  protected void deleteReference(NamedRef ref, String hash)
      throws NessieConflictException, NessieNotFoundException {
    BatchAccessChecker check = startAccessCheck();
    if (ref instanceof BranchName && getConfig().getDefaultBranch().equals(ref.getName())) {
      check.canDeleteDefaultBranch();
    } else {
      check.canDeleteReference(ref);
    }
    check.checkAndThrow();
    super.deleteReference(ref, hash);
  }

  @Override
  public EntriesResponse getEntries(String namedRef, EntriesParams params)
      throws NessieNotFoundException {
    NamedRef ref = namedRefWithHashOrThrow(namedRef, params.hashOnRef()).getValue();
    startAccessCheck().canReadEntries(ref).checkAndThrow();
    EntriesResponse entriesResponse = super.getEntries(namedRef, params);

    // Post-process the response to only contain content-keys to which the user has access to.
    // Note: no optimization here to return the non-post-processed response to ensure that tests
    // catch potential issues during post-processing early (e.g. a new attribute added to
    // EntriesResponse, but not added here).

    // First, collect the content-keys to which the user has no access to.

    BatchAccessChecker responseCheck = startAccessCheck();
    entriesResponse.getEntries().forEach(e -> responseCheck.canReadContentKey(ref, e.getName()));
    Set<ContentKey> notAllowed =
        responseCheck.check().keySet().stream().map(Check::key).collect(Collectors.toSet());

    // Second, only add the `Entry`s for allowed content keys.

    ImmutableEntriesResponse.Builder newEntriesResponse =
        EntriesResponse.builder()
            .isHasMore(entriesResponse.isHasMore())
            .token(entriesResponse.getToken());
    entriesResponse.getEntries().stream()
        .filter(e -> !notAllowed.contains(e.getName()))
        .forEach(newEntriesResponse::addEntries);

    return newEntriesResponse.build();
  }

  @Override
  public LogResponse getCommitLog(String namedRef, CommitLogParams params)
      throws NessieNotFoundException {
    String checkHash = params.pageToken() == null ? params.endHash() : params.pageToken();
    NamedRef ref = namedRefWithHashOrThrow(namedRef, checkHash).getValue();
    startAccessCheck().canListCommitLog(ref).checkAndThrow();
    LogResponse logResponse = super.getCommitLog(namedRef, params);

    Set<ContentKey> allContentKeys =
        logResponse.getLogEntries().stream()
            .map(LogEntry::getOperations)
            .filter(Objects::nonNull)
            .flatMap(Collection::stream)
            .map(Operation::getKey)
            .collect(Collectors.toSet());

    // Post-process the response to only contain content-keys to which the user has access to.
    // Note: no optimization here to return the non-post-processed response to ensure that tests
    // catch potential issues during post-processing early (e.g. a new attribute added to
    // LogResponse, but not added here).

    // First, collect the content-keys to which the user has no access to.

    BatchAccessChecker responseCheck = startAccessCheck();
    allContentKeys.forEach(contentKey -> responseCheck.canReadContentKey(ref, contentKey));
    Set<ContentKey> notAllowed =
        responseCheck.check().keySet().stream().map(Check::key).collect(Collectors.toSet());

    // Second, only add the `Operation`s for allowed content keys.

    ImmutableLogResponse.Builder newLogResponse =
        LogResponse.builder().isHasMore(logResponse.isHasMore()).token(logResponse.getToken());
    logResponse.getLogEntries().stream()
        .map(
            e -> {
              ImmutableLogEntry.Builder newLogEntry =
                  LogEntry.builder()
                      .commitMeta(e.getCommitMeta())
                      .parentCommitHash(e.getParentCommitHash());
              if (e.getOperations() != null) {
                e.getOperations().stream()
                    .filter(op -> !notAllowed.contains(op.getKey()))
                    .forEach(newLogEntry::addOperations);
              }
              return newLogEntry.build();
            })
        .forEach(newLogResponse::addLogEntries);
    return newLogResponse.build();
  }

  @Override
  public void transplantCommitsIntoBranch(
      String branchName, String hash, String message, Transplant transplant)
      throws NessieNotFoundException, NessieConflictException {
    if (transplant.getHashesToTransplant().isEmpty()) {
      throw new IllegalArgumentException("No hashes given to transplant.");
    }

    startAccessCheck()
        .canViewReference(
            namedRefWithHashOrThrow(
                    transplant.getFromRefName(),
                    transplant
                        .getHashesToTransplant()
                        .get(transplant.getHashesToTransplant().size() - 1))
                .getValue())
        .canCommitChangeAgainstReference(BranchName.of(branchName))
        .checkAndThrow();
    super.transplantCommitsIntoBranch(branchName, hash, message, transplant);
  }

  @Override
  public void mergeRefIntoBranch(String branchName, String hash, Merge merge)
      throws NessieNotFoundException, NessieConflictException {
    startAccessCheck()
        .canViewReference(
            namedRefWithHashOrThrow(merge.getFromRefName(), merge.getFromHash()).getValue())
        .canCommitChangeAgainstReference(BranchName.of(branchName))
        .checkAndThrow();
    super.mergeRefIntoBranch(branchName, hash, merge);
  }

  @Override
  public Branch commitMultipleOperations(String branch, String hash, Operations operations)
      throws NessieNotFoundException, NessieConflictException {
    BranchName branchName = BranchName.of(branch);
    BatchAccessChecker check = startAccessCheck().canCommitChangeAgainstReference(branchName);
    operations
        .getOperations()
        .forEach(
            op -> {
              if (op instanceof Delete) {
                check.canDeleteEntity(branchName, op.getKey(), null);
              } else if (op instanceof Put) {
                Put putOp = (Put) op;
                check.canUpdateEntity(
                    branchName,
                    op.getKey(),
                    putOp.getContent().getId(),
                    putOp.getContent().getType());
              }
            });
    check.checkAndThrow();
    return super.commitMultipleOperations(branch, hash, operations);
  }
}
