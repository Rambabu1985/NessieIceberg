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
package org.projectnessie.model;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.immutables.value.Value;

@Schema(
    type = SchemaType.OBJECT,
    title = "Operation",
    oneOf = {Operation.Put.class, Operation.Unchanged.class, Operation.Delete.class},
    discriminatorMapping = {
      @DiscriminatorMapping(value = "PUT", schema = Operation.Put.class),
      @DiscriminatorMapping(value = "UNCHANGED", schema = Operation.Unchanged.class),
      @DiscriminatorMapping(value = "DELETE", schema = Operation.Delete.class)
    },
    discriminatorProperty = "type")
@JsonSubTypes({
  @JsonSubTypes.Type(Operation.Put.class),
  @JsonSubTypes.Type(Operation.Delete.class),
  @JsonSubTypes.Type(Operation.Unchanged.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "type")
public interface Operation {

  @NotNull
  ContentKey getKey();

  @Schema(
      type = SchemaType.OBJECT,
      title = "Put-'Content'-operation for a 'ContentKey'.",
      description =
          "Add or replace (put) a 'Content' object for a 'ContentKey'. "
              + "If the actual table type tracks the 'global state' of individual tables (Iceberg "
              + "as of today), every 'Put'-operation must contain a non-null value for 'expectedContent'.")
  @Value.Immutable
  @JsonSerialize(as = ImmutablePut.class)
  @JsonDeserialize(as = ImmutablePut.class)
  @JsonTypeName("PUT")
  interface Put extends Operation {
    @NotNull
    Content getContent();

    @Nullable
    @jakarta.annotation.Nullable
    Content getExpectedContent();

    static Put of(ContentKey key, Content content) {
      return ImmutablePut.builder().key(key).content(content).build();
    }

    static Put of(ContentKey key, Content content, Content expectedContent) {
      return ImmutablePut.builder()
          .key(key)
          .content(content)
          .expectedContent(expectedContent)
          .build();
    }
  }

  @Value.Immutable
  @JsonSerialize(as = ImmutableDelete.class)
  @JsonDeserialize(as = ImmutableDelete.class)
  @JsonTypeName("DELETE")
  interface Delete extends Operation {

    static Delete of(ContentKey key) {
      return ImmutableDelete.builder().key(key).build();
    }
  }

  @Value.Immutable
  @JsonSerialize(as = ImmutableUnchanged.class)
  @JsonDeserialize(as = ImmutableUnchanged.class)
  @JsonTypeName("UNCHANGED")
  interface Unchanged extends Operation {

    static Unchanged of(ContentKey key) {
      return ImmutableUnchanged.builder().key(key).build();
    }
  }
}
