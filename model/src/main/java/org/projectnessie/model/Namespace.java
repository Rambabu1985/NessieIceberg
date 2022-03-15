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

import static org.projectnessie.model.UriUtil.ZERO_BYTE_STRING;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import javax.validation.constraints.NotNull;
import org.immutables.value.Value;

/**
 * For a given table name <b>a.b.c.tableName</b>, the {@link Namespace} would be the prefix
 * <b>a.b.c</b>, since the last element <b>tableName</b> always represents the name of the actual
 * table and is not included in the {@link Namespace} itself. Therefore, the {@link Namespace} is
 * always consisting of the first <b>N-1</b> elements.
 */
@Value.Immutable
@JsonSerialize(as = ImmutableNamespace.class)
@JsonDeserialize(as = ImmutableNamespace.class)
@JsonTypeName("NAMESPACE")
public abstract class Namespace extends Content {

  private static final String DOT = ".";
  static final String ERROR_MSG_TEMPLATE =
      "'%s' is not a valid namespace identifier (should not end with '.')";

  public static final Namespace EMPTY = ImmutableNamespace.builder().name("").id("").build();

  @Override
  public Type getType() {
    return Type.NAMESPACE;
  }

  @NotNull
  public abstract String name();

  @JsonIgnore
  @Value.Redacted
  public boolean isEmpty() {
    return name().isEmpty();
  }

  @JsonIgnore
  @Value.Redacted
  public String[] getElements() {
    return name().split("\\.");
  }

  /**
   * Builds a {@link Namespace} instance for the given elements.
   *
   * @param elements The elements to build the namespace from.
   * @return The constructed {@link Namespace} instance. If <b>elements</b> is empty, then {@link
   *     Namespace#name()} will be an empty string.
   */
  public static Namespace of(String... elements) {
    Objects.requireNonNull(elements, "elements must be non-null");
    if (elements.length == 0) {
      return EMPTY;
    }

    for (String e : elements) {
      if (e == null) {
        throw new IllegalArgumentException("A namespace must not contain a null element.");
      }
      if (e.contains(ZERO_BYTE_STRING)) {
        throw new IllegalArgumentException("A namespace must not contain a zero byte.");
      }
    }

    if (DOT.equals(elements[elements.length - 1])) {
      throw new IllegalArgumentException(
          String.format(ERROR_MSG_TEMPLATE, Arrays.toString(elements)));
    }

    String name = String.join(DOT, Arrays.asList(elements));
    return ImmutableNamespace.builder().name(name).id(name).build();
  }

  /**
   * Builds a {@link Namespace} instance for the given elements.
   *
   * @param elements The elements to build the namespace from.
   * @return The constructed {@link Namespace} instance. If <b>elements</b> is empty, then {@link
   *     Namespace#name()} will be an empty string.
   */
  public static Namespace of(List<String> elements) {
    Objects.requireNonNull(elements, "elements must be non-null");
    return Namespace.of(elements.toArray(new String[0]));
  }

  /**
   * Builds a {@link Namespace} instance for the given elements split by the <b>.</b> (dot)
   * character.
   *
   * @param identifier The identifier to build the namespace from.
   * @return Splits the given <b>identifier</b> by <b>.</b> and returns a {@link Namespace}
   *     instance. If <b>identifier</b> is empty, then {@link Namespace#name()} will be an empty
   *     string.
   */
  public static Namespace parse(String identifier) {
    Objects.requireNonNull(identifier, "identifier must be non-null");
    if (identifier.isEmpty()) {
      return EMPTY;
    }
    if (identifier.endsWith(DOT)) {
      throw new IllegalArgumentException(String.format(ERROR_MSG_TEMPLATE, identifier));
    }
    return Namespace.of(identifier.split("\\."));
  }

  /**
   * Convert from path encoded string to normal string.
   *
   * @param encoded Path encoded string
   * @return Actual key.
   */
  public static Namespace fromPathString(String encoded) {
    return Namespace.of(UriUtil.fromPathString(encoded));
  }

  /**
   * Convert this namespace to a URL encoded path string.
   *
   * @return String encoded for path use.
   */
  public String toPathString() {
    return UriUtil.toPathString(Arrays.asList(getElements()));
  }

  @Override
  public String toString() {
    return toPathString();
  }
}
