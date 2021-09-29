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

import static org.projectnessie.model.Validation.validateHash;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import javax.annotation.Nullable;
import javax.validation.constraints.NotBlank;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.media.SchemaProperty;
import org.immutables.value.Value;

@Schema(
    type = SchemaType.OBJECT,
    title = "Reference",
    oneOf = {Branch.class, Tag.class},
    discriminatorMapping = {
      @DiscriminatorMapping(value = "TAG", schema = Tag.class),
      @DiscriminatorMapping(value = "BRANCH", schema = Branch.class),
    },
    discriminatorProperty = "type",
    // Smallrye does neither support JsonFormat nor javax.validation.constraints.Pattern :(
    properties = {
      @SchemaProperty(name = "name", pattern = Validation.REF_NAME_REGEX),
      @SchemaProperty(name = "hash", pattern = Validation.HASH_REGEX)
    })
@JsonSubTypes({@Type(Branch.class), @Type(Tag.class)})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public interface Reference extends Base {
  /** Human readable reference name. */
  @NotBlank
  @JsonFormat(pattern = Validation.REF_NAME_REGEX)
  String getName();

  /** backend system id. Usually the 32-byte hash of the commit this reference points to. */
  @Nullable
  @JsonFormat(pattern = Validation.HASH_REGEX)
  String getHash();

  /**
   * Validation rule using {@link org.projectnessie.model.Validation#validateHash(String)}
   * (String)}.
   */
  @Value.Check
  default void checkHash() {
    String hash = getHash();
    if (hash != null) {
      validateHash(hash);
    }
  }
}
