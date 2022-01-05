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
package org.projectnessie.model.iceberg;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import javax.annotation.Nullable;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.immutables.value.Value;

@Value.Immutable
@JsonSerialize(as = ImmutableIcebergPartitionSpec.class)
@JsonDeserialize(as = ImmutableIcebergPartitionSpec.class)
@Schema(type = SchemaType.OBJECT, title = "Iceberg partition spec")
public interface IcebergPartitionSpec {
  int getSchemaId();

  int getSpecId();

  @Nullable
  List<PartitionField> getFields();

  static ImmutableIcebergPartitionSpec.Builder builder() {
    return ImmutableIcebergPartitionSpec.builder();
  }

  @Value.Immutable
  @JsonSerialize(as = ImmutablePartitionField.class)
  @JsonDeserialize(as = ImmutablePartitionField.class)
  @Schema(type = SchemaType.OBJECT, title = "Iceberg partition field")
  interface PartitionField {
    int getSourceId();

    @Nullable
    Integer getFieldId();

    String getName();

    String getTransform();

    static ImmutablePartitionField.Builder builder() {
      return ImmutablePartitionField.builder();
    }
  }
}
