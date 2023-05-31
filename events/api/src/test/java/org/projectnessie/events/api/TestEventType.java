/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.events.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TestEventType {

  private final Reference branch1 =
      ImmutableReference.builder()
          .simpleName("branch1")
          .fullName("refs/heads/branch1")
          .type(Reference.BRANCH)
          .build();
  private final Reference branch2 =
      ImmutableReference.builder()
          .simpleName("branch2")
          .fullName("refs/heads/branch2")
          .type(Reference.BRANCH)
          .build();

  @Test
  void commit() {
    CommitEvent event =
        ImmutableCommitEvent.builder()
            .reference(branch2)
            .hashBefore("hash1")
            .hashAfter("hash2")
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .commitMeta(
                ImmutableCommitMeta.builder()
                    .commitTimestamp(Instant.now())
                    .committer("committer")
                    .message("message")
                    .authorTimestamp(Instant.now())
                    .build())
            .build();
    assertThat(event.getType()).isEqualTo(EventType.COMMIT);
  }

  @Test
  void merge() {
    MergeEvent event =
        ImmutableMergeEvent.builder()
            .sourceReference(branch1)
            .targetReference(branch2)
            .hashBefore("hash1")
            .hashAfter("hash2")
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .commonAncestorHash("hash0")
            .build();
    assertThat(event.getType()).isEqualTo(EventType.MERGE);
  }

  @Test
  void transplant() {
    TransplantEvent event =
        ImmutableTransplantEvent.builder()
            .sourceReference(branch1)
            .targetReference(branch2)
            .hashBefore("hash1")
            .hashAfter("hash2")
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .build();
    assertThat(event.getType()).isEqualTo(EventType.TRANSPLANT);
  }

  @Test
  void referenceCreated() {
    ReferenceCreatedEvent event =
        ImmutableReferenceCreatedEvent.builder()
            .reference(branch1)
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .hashAfter("hash2")
            .build();
    assertThat(event.getType()).isEqualTo(EventType.REFERENCE_CREATED);
  }

  @Test
  void referenceUpdated() {
    ReferenceUpdatedEvent event =
        ImmutableReferenceUpdatedEvent.builder()
            .reference(branch1)
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .hashBefore("hash1")
            .hashAfter("hash2")
            .build();
    assertThat(event.getType()).isEqualTo(EventType.REFERENCE_UPDATED);
  }

  @Test
  void referenceDeleted() {
    ReferenceDeletedEvent event =
        ImmutableReferenceDeletedEvent.builder()
            .reference(branch1)
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .hashBefore("hash1")
            .build();
    assertThat(event.getType()).isEqualTo(EventType.REFERENCE_DELETED);
  }

  @Test
  void contentStored() {
    ContentStoredEvent event =
        ImmutableContentStoredEvent.builder()
            .reference(branch1)
            .hash("hash1")
            .contentKey(ContentKey.of("ns", "table1"))
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .content(mock(Content.class))
            .commitCreationTimestamp(Instant.now())
            .build();
    assertThat(event.getType()).isEqualTo(EventType.CONTENT_STORED);
  }

  @Test
  void contentRemoved() {
    ContentRemovedEvent event =
        ImmutableContentRemovedEvent.builder()
            .reference(branch1)
            .hash("hash1")
            .contentKey(ContentKey.of("ns", "table1"))
            .id(UUID.randomUUID())
            .repositoryId("repo1")
            .eventCreationTimestamp(Instant.now())
            .eventInitiator("Alice")
            .commitCreationTimestamp(Instant.now())
            .build();
    assertThat(event.getType()).isEqualTo(EventType.CONTENT_REMOVED);
  }
}
