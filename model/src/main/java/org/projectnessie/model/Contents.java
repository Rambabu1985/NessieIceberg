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

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import javax.annotation.Nullable;

import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.DiscriminatorMapping;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.immutables.value.Value;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonSubTypes.Type;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

/**
 * Abstract implementation of contents within Nessie.
 */
@Schema(
    type = SchemaType.OBJECT,
    title = "Contents",
    oneOf = { IcebergTable.class, DeltaLakeTable.class, SqlView.class, HiveTable.class, HiveDatabase.class },
    discriminatorMapping = {
        @DiscriminatorMapping(value = "ICEBERG_TABLE", schema = IcebergTable.class),
        @DiscriminatorMapping(value = "DELTA_LAKE_TABLE", schema = DeltaLakeTable.class),
        @DiscriminatorMapping(value = "VIEW", schema = SqlView.class),
        @DiscriminatorMapping(value = "HIVE_TABLE", schema = HiveTable.class),
        @DiscriminatorMapping(value = "HIVE_DATABASE", schema = HiveDatabase.class)
    },
    discriminatorProperty = "type"
  )
@JsonSubTypes({
    @Type(IcebergTable.class),
    @Type(DeltaLakeTable.class),
    @Type(SqlView.class),
    @Type(HiveTable.class),
    @Type(HiveDatabase.class)
})
@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, include = JsonTypeInfo.As.PROPERTY, property = "type")
public abstract class Contents {

  public static enum Type {
    UNKNOWN, ICEBERG_TABLE, DELTA_LAKE_TABLE, HIVE_TABLE, HIVE_DATABASE, VIEW;
  }

  /**
   * Unique id for this object.
   *
   * <p>This id is unique for the entire lifetime of this Contents object and persists across renames. Two contents with the same key
   * will have different id.
   *
   * <p>todo this is nullable to maintain backwards compatibility with the version of nessie deployed with iceberg. Remove after 0.6.0 is
   *     finished (#1054)
   */
  @Nullable
  @Value.Default
  public String getId() {
    return UUID.randomUUID().toString();
  }

  /**
   * Unwrap object if possible, otherwise throw.
   * @param <T> Type to wrap to.
   * @param clazz Class we're trying to return.
   * @return The return value
   */
  public <T> Optional<T> unwrap(Class<T> clazz) {
    Objects.requireNonNull(clazz);
    if (clazz.isAssignableFrom(getClass())) {
      return Optional.of(clazz.cast(this));
    }
    return Optional.empty();
  }

}
