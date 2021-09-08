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

import com.fasterxml.jackson.annotation.JsonTypeName;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.immutables.value.Value;

/**
 * Represents the global state of an Iceberg table in Nessie. An Iceberg table is globally
 * identified via its {@link Contents#getId() unique ID}.
 *
 * <p>A Nessie commit-operation, performed via {@link
 * org.projectnessie.api.TreeApi#commitMultipleOperations(String, String, Operations)}, for Iceberg
 * consists of a {@link Operation.Put} with an {@link IcebergSnapshot} <em>and</em> an {@link
 * IcebergTable} via a {@link Operation.PutGlobal} for the global-state.
 */
@Schema(
    type = SchemaType.OBJECT,
    title = "Iceberg table global state",
    description =
        "Represents the global state of an Iceberg table in Nessie. An Iceberg table is globally "
            + "identified via its unique 'Contents.id'.\n"
            + "\n"
            + "A Nessie commit-operation, performed via 'TreeApi.commitMultipleOperations', for Iceberg "
            + "for Iceberg consists of a 'Operation.Put' with an 'IcebergSnapshot' and an "
            + "'IcebergTable' as the expected-global-state via 'Operation.PutGlobal' using the same "
            + "'ContentsKey' and 'Contents.id'.\n"
            + "\n"
            + "During a commit-operation, Nessie checks whether the known global state of the "
            + "Iceberg table is compatible (think: equal) to 'Operation.Put.expectedContents'.")
@Value.Immutable(prehash = true)
@JsonSerialize(as = ImmutableIcebergTable.class)
@JsonDeserialize(as = ImmutableIcebergTable.class)
@JsonTypeName("ICEBERG_TABLE")
public abstract class IcebergTable extends Contents implements GlobalState {

  /**
   * Location where Iceberg stored its {@code TableMetadata} file. The location depends on the
   * (implementation of) Iceberg's {@code FileIO} configured for the particular Iceberg table.
   */
  @NotNull
  @NotBlank
  public abstract String getMetadataLocation();

  public static IcebergTable of(String metadataLocation) {
    return ImmutableIcebergTable.builder().metadataLocation(metadataLocation).build();
  }

  public static IcebergTable of(String metadataLocation, String contentsId) {
    return ImmutableIcebergTable.builder()
        .metadataLocation(metadataLocation)
        .id(contentsId)
        .build();
  }
}
