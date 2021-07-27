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
package org.projectnessie.server.authz;

import com.google.common.collect.ImmutableMap;
import javax.enterprise.context.ApplicationScoped;
import javax.inject.Inject;
import javax.ws.rs.ForbiddenException;
import org.projectnessie.cel.tools.ScriptException;
import org.projectnessie.model.ContentsKey;
import org.projectnessie.server.config.QuarkusNessieAuthorizationConfig;
import org.projectnessie.services.authz.AccessChecker;
import org.projectnessie.services.authz.AccessContext;
import org.projectnessie.versioned.NamedRef;

/**
 * A reference implementation of the {@link AccessChecker} that performs access checks using CEL
 * expressions.
 */
@ApplicationScoped
public class CelAccessChecker implements AccessChecker {
  private final QuarkusNessieAuthorizationConfig config;
  private final CompiledAuthorizationRules compiledRules;

  public enum AuthorizationRuleType {
    CREATE_REFERENCE,
    DELETE_REFERENCE,
    LIST_COMMIT_LOG,
    READ_ENTRIES,
    ASSIGN_REFERENCE_TO_HASH,
    COMMIT_CHANGE_AGAINST_REFERENCE,
    READ_ENTITY_VALUE,
    UPDATE_ENTITY,
    DELETE_ENTITY;
  }

  @Inject
  public CelAccessChecker(
      QuarkusNessieAuthorizationConfig config, CompiledAuthorizationRules compiledRules) {
    this.config = config;
    this.compiledRules = compiledRules;
  }

  @Override
  public void canCreateReference(AccessContext context, NamedRef ref) throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.CREATE_REFERENCE);
  }

  @Override
  public void canAssignRefToHash(AccessContext context, NamedRef ref) throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.ASSIGN_REFERENCE_TO_HASH);
  }

  @Override
  public void canDeleteReference(AccessContext context, NamedRef ref) throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.DELETE_REFERENCE);
  }

  @Override
  public void canReadEntries(AccessContext context, NamedRef ref) throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.READ_ENTRIES);
  }

  @Override
  public void canListCommitLog(AccessContext context, NamedRef ref) throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.LIST_COMMIT_LOG);
  }

  @Override
  public void canCommitChangeAgainstReference(AccessContext context, NamedRef ref)
      throws ForbiddenException {
    canPerformOpOnReference(context, ref, AuthorizationRuleType.COMMIT_CHANGE_AGAINST_REFERENCE);
  }

  @Override
  public void canReadEntityValue(AccessContext context, NamedRef ref, ContentsKey key)
      throws ForbiddenException {
    canPerformOpOnPath(context, key, AuthorizationRuleType.READ_ENTITY_VALUE);
  }

  @Override
  public void canUpdateEntity(AccessContext context, NamedRef ref, ContentsKey key)
      throws ForbiddenException {
    canPerformOpOnPath(context, key, AuthorizationRuleType.UPDATE_ENTITY);
  }

  @Override
  public void canDeleteEntity(AccessContext context, NamedRef ref, ContentsKey key)
      throws ForbiddenException {
    canPerformOpOnPath(context, key, AuthorizationRuleType.DELETE_ENTITY);
  }

  private void canPerformOpOnReference(
      AccessContext context, NamedRef ref, AuthorizationRuleType type) {
    if (!config.enabled()) {
      return;
    }
    boolean allowed =
        compiledRules.getRules().entrySet().stream()
            .anyMatch(
                entry -> {
                  try {
                    return entry
                        .getValue()
                        .execute(
                            Boolean.class,
                            ImmutableMap.of(
                                "ref",
                                ref.getName(),
                                "role",
                                getRoleName(context),
                                "op",
                                type.name()));
                  } catch (ScriptException e) {
                    throw new RuntimeException(
                        String.format(
                            "Failed to execute query expression with id '%s' and expression '%s' due to: %s",
                            entry.getKey(), entry.getValue(), e.getMessage()));
                  }
                });
    if (!allowed) {
      throw new ForbiddenException(
          String.format(
              "'%s' is not allowed for Role '%s' on Reference '%s'",
              type, getRoleName(context), ref.getName()));
    }
  }

  private String getRoleName(AccessContext context) {
    return null != context.user() ? context.user().getName() : "";
  }

  private void canPerformOpOnPath(
      AccessContext context, ContentsKey contentsKey, AuthorizationRuleType type) {
    if (!config.enabled()) {
      return;
    }
    boolean allowed =
        compiledRules.getRules().entrySet().stream()
            .anyMatch(
                entry -> {
                  try {
                    return entry
                        .getValue()
                        .execute(
                            Boolean.class,
                            ImmutableMap.of(
                                "path",
                                contentsKey.toPathString(),
                                "role",
                                getRoleName(context),
                                "op",
                                type.name()));
                  } catch (ScriptException e) {
                    throw new RuntimeException(
                        String.format(
                            "Failed to execute query expression with id '%s' and expression '%s' due to: %s",
                            entry.getKey(), entry.getValue(), e.getMessage()));
                  }
                });
    if (!allowed) {
      throw new ForbiddenException(
          String.format(
              "'%s' is not allowed for Role '%s' on Content '%s'",
              type, getRoleName(context), contentsKey.toPathString()));
    }
  }
}
