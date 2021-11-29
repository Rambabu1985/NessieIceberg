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

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import java.util.List;
import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.immutables.value.Value;

@Value.Immutable
@Schema(type = SchemaType.OBJECT, title = "LogResponse")
@JsonSerialize(as = ImmutableLogResponse.class)
@JsonDeserialize(as = ImmutableLogResponse.class)
public interface LogResponse extends PaginatedResponse {

  @NotNull
  List<LogEntry> getLogEntries();

  @Value.Immutable
  @Schema(type = SchemaType.OBJECT, title = "LogEntry")
  @JsonSerialize(as = ImmutableLogEntry.class)
  @JsonDeserialize(as = ImmutableLogEntry.class)
  interface LogEntry {
    static ImmutableLogEntry.Builder builder() {
      return ImmutableLogEntry.builder();
    }

    @NotNull
    CommitMeta getCommitMeta();

    @Nullable
    String getParentCommitHash();

    @Nullable
    List<Operation> getOperations();
  }
}
