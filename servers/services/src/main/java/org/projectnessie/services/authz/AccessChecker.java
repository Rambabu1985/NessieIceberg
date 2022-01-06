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
package org.projectnessie.services.authz;

import java.security.AccessControlException;
import org.projectnessie.model.Content;
import org.projectnessie.model.ContentKey;
import org.projectnessie.versioned.NamedRef;

/**
 * The purpose of the {@link AccessChecker} is to make sure that a particular role is allowed to
 * perform an action (such as creation, deletion) on a {@link NamedRef} (Branch/Tag). Additionally,
 * this interface also provides checks based on a given {@link ContentKey}.
 */
public interface AccessChecker {

  /**
   * Checks whether the given role/principal is allowed to view/list the given Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to view/list a Branch/Tag is not granted.
   */
  void canViewReference(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to create a Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to create a Branch/Tag is not granted.
   */
  void canCreateReference(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to assign the given Branch/Tag to a Hash.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to assign the given Branch/Tag to a Hash is
   *     not granted.
   */
  void canAssignRefToHash(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to delete a Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to delete a Branch/Tag is not granted.
   */
  void canDeleteReference(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to read entries content for the given
   * Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to read entries content is not granted.
   */
  void canReadEntries(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to list the commit log for the given
   * Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to list the commit log is not granted.
   */
  void canListCommitLog(AccessContext context, NamedRef ref) throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to commit changes against the given
   * Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @throws AccessControlException When the permission to commit changes is not granted.
   */
  void canCommitChangeAgainstReference(AccessContext context, NamedRef ref)
      throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to read an entity value as defined by the
   * {@link ContentKey} for the given Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @param key The {@link ContentKey} to check
   * @param contentId The ID of the {@link Content} object. See the <a
   *     href="https://projectnessie.org/features/metadata_authorization/#contentid">ContentId
   *     docs</a> for how to use this.
   * @throws AccessControlException When the permission to read an entity value is not granted.
   */
  void canReadEntityValue(AccessContext context, NamedRef ref, ContentKey key, String contentId)
      throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to update an entity value as defined by the
   * {@link ContentKey} for the given Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @param key The {@link ContentKey} to check
   * @param contentId The ID of the {@link Content} object. See the <a
   *     href="https://projectnessie.org/features/metadata_authorization/#contentid">ContentId
   *     docs</a> for how to use this.
   * @throws AccessControlException When the permission to update an entity value is not granted.
   */
  void canUpdateEntity(AccessContext context, NamedRef ref, ContentKey key, String contentId)
      throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to delete an entity value as defined by the
   * {@link ContentKey} for the given Branch/Tag.
   *
   * @param context The context carrying the principal information.
   * @param ref The {@link NamedRef} to check
   * @param key The {@link ContentKey} to check
   * @param contentId The ID of the {@link Content} object. See the <a
   *     href="https://projectnessie.org/features/metadata_authorization/#contentid">ContentId
   *     docs</a> for how to use this.
   * @throws AccessControlException When the permission to delete an entity value is not granted.
   */
  void canDeleteEntity(AccessContext context, NamedRef ref, ContentKey key, String contentId)
      throws AccessControlException;

  /**
   * Checks whether the given role/principal is allowed to view the reflog entries.
   *
   * @param context The context carrying the principal information.
   * @throws AccessControlException When the permission to view the reflog entries is not granted.
   */
  void canViewRefLog(AccessContext context) throws AccessControlException;
}
