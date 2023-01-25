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
package org.projectnessie.services.authz;

import static java.util.Collections.emptyMap;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.services.authz.Check.CheckType;
import org.projectnessie.versioned.NamedRef;

public abstract class AbstractBatchAccessChecker implements BatchAccessChecker {
  public static final BatchAccessChecker NOOP_ACCESS_CHECKER =
      new AbstractBatchAccessChecker() {
        @Override
        public Map<Check, String> check() {
          return emptyMap();
        }

        @Override
        public BatchAccessChecker can(Check check) {
          return this;
        }
      };

  private final Collection<Check> checks = new LinkedHashSet<>();

  private BatchAccessChecker add(ImmutableCheck.Builder builder) {
    return can(builder.build());
  }

  protected Collection<Check> getChecks() {
    return checks;
  }

  @Override
  public BatchAccessChecker can(Check check) {
    checks.add(check);
    return this;
  }

  @Override
  public BatchAccessChecker canViewReference(NamedRef ref) {
    return add(Check.builder(CheckType.VIEW_REFERENCE).ref(ref));
  }

  @Override
  public BatchAccessChecker canCreateReference(NamedRef ref) {
    return add(Check.builder(CheckType.CREATE_REFERENCE).ref(ref));
  }

  @Override
  public BatchAccessChecker canAssignRefToHash(NamedRef ref) {
    canViewReference(ref);
    return add(Check.builder(CheckType.ASSIGN_REFERENCE_TO_HASH).ref(ref));
  }

  @Override
  public BatchAccessChecker canDeleteReference(NamedRef ref) {
    canViewReference(ref);
    return add(Check.builder(CheckType.DELETE_REFERENCE).ref(ref));
  }

  @Override
  public BatchAccessChecker canReadEntries(NamedRef ref) {
    canViewReference(ref);
    return add(Check.builder(CheckType.READ_ENTRIES).ref(ref));
  }

  @Override
  public BatchAccessChecker canReadContentKey(NamedRef ref, ContentKey key, String contentId) {
    canViewReference(ref);
    ImmutableCheck.Builder builder = Check.builder(CheckType.READ_CONTENT_KEY).ref(ref).key(key);
    if (contentId != null) {
      builder.contentId(contentId);
    }
    return add(builder);
  }

  @Override
  public BatchAccessChecker canListCommitLog(NamedRef ref) {
    canViewReference(ref);
    return add(Check.builder(CheckType.LIST_COMMIT_LOG).ref(ref));
  }

  @Override
  public BatchAccessChecker canCommitChangeAgainstReference(NamedRef ref) {
    canViewReference(ref);
    return add(Check.builder(CheckType.COMMIT_CHANGE_AGAINST_REFERENCE).ref(ref));
  }

  @Override
  public BatchAccessChecker canReadEntityValue(NamedRef ref, ContentKey key, String contentId) {
    canViewReference(ref);
    return add(Check.builder(CheckType.READ_ENTITY_VALUE).ref(ref).key(key).contentId(contentId));
  }

  @Override
  public BatchAccessChecker canUpdateEntity(
      NamedRef ref, ContentKey key, String contentId, Content.Type contentType) {
    canViewReference(ref);
    return add(
        Check.builder(CheckType.UPDATE_ENTITY)
            .ref(ref)
            .key(key)
            .contentId(contentId)
            .contentType(contentType));
  }

  @Override
  public BatchAccessChecker canDeleteEntity(NamedRef ref, ContentKey key, String contentId) {
    canViewReference(ref);
    return add(Check.builder(CheckType.DELETE_ENTITY).ref(ref).key(key).contentId(contentId));
  }

  @Override
  public BatchAccessChecker canViewRefLog() {
    return add(Check.builder(CheckType.VIEW_REFLOG));
  }
}
