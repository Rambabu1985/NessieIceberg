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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.Optional;
import org.immutables.value.Value;

/** A {@link Content} object that represents an Iceberg view. */
@Value.Immutable
@JsonSerialize(as = ImmutableIcebergView.class)
@JsonDeserialize(as = ImmutableIcebergView.class)
public interface IcebergView extends Content {

  @Override
  @Value.Default
  default ContentType getType() {
    return ContentType.ICEBERG_VIEW;
  }

  /**
   * Location where Iceberg stored its {@code TableMetadata} file. The location depends on the
   * (implementation of) Iceberg's {@code FileIO} configured for the particular Iceberg table.
   */
  String getMetadataLocation();

  /** Corresponds to Iceberg's {@code currentVersionId}. */
  long getVersionId();

  int getSchemaId();

  String getSqlText();

  Optional<String> getDialect();
}
