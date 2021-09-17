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
package org.projectnessie.server.store;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;
import java.io.IOException;
import java.util.Optional;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Contents;
import org.projectnessie.model.Contents.Type;
import org.projectnessie.model.DeltaLakeTable;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.ImmutableCommitMeta;
import org.projectnessie.model.ImmutableDeltaLakeTable;
import org.projectnessie.model.ImmutableDeltaLakeTable.Builder;
import org.projectnessie.model.ImmutableSqlView;
import org.projectnessie.model.SqlView;
import org.projectnessie.model.SqlView.Dialect;
import org.projectnessie.store.ObjectTypes;
import org.projectnessie.store.ObjectTypes.IcebergTableMetadata;
import org.projectnessie.versioned.Serializer;
import org.projectnessie.versioned.SerializerWithPayload;
import org.projectnessie.versioned.StoreWorker;

public class TableCommitMetaStoreWorker
    implements StoreWorker<Contents, CommitMeta, Contents.Type> {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final SerializerWithPayload<Contents, Contents.Type> tableSerializer =
      new TableValueSerializer();
  private final Serializer<CommitMeta> metaSerializer = new MetadataSerializer();

  @Override
  public ByteString toStoreOnReferenceState(Contents contents) {
    ObjectTypes.Contents.Builder builder =
        ObjectTypes.Contents.newBuilder().setId(contents.getId());
    if (contents instanceof IcebergTable) {
      IcebergTable state = (IcebergTable) contents;
      ObjectTypes.IcebergSnapshot.Builder stateBuilder =
          ObjectTypes.IcebergSnapshot.newBuilder().setSnapshotId(state.getSnapshotId());
      builder.setIcebergSnapshot(stateBuilder);

    } else if (contents instanceof DeltaLakeTable) {
      ObjectTypes.DeltaLakeTable.Builder table =
          ObjectTypes.DeltaLakeTable.newBuilder()
              .addAllMetadataLocationHistory(
                  ((DeltaLakeTable) contents).getMetadataLocationHistory())
              .addAllCheckpointLocationHistory(
                  ((DeltaLakeTable) contents).getCheckpointLocationHistory());
      String lastCheckpoint = ((DeltaLakeTable) contents).getLastCheckpoint();
      if (lastCheckpoint != null) {
        table.setLastCheckpoint(lastCheckpoint);
      }
      builder.setDeltaLakeTable(table);

    } else if (contents instanceof SqlView) {
      SqlView view = (SqlView) contents;
      builder.setSqlView(
          ObjectTypes.SqlView.newBuilder()
              .setDialect(view.getDialect().name())
              .setSqlText(view.getSqlText()));
    } else {
      throw new IllegalArgumentException("Unknown type " + contents);
    }

    return builder.build().toByteString();
  }

  @Override
  public ByteString toStoreGlobalState(Contents contents) {
    ObjectTypes.Contents.Builder builder =
        ObjectTypes.Contents.newBuilder().setId(contents.getId());
    if (contents instanceof IcebergTable) {
      IcebergTable state = (IcebergTable) contents;
      ObjectTypes.IcebergTableMetadata.Builder stateBuilder =
          ObjectTypes.IcebergTableMetadata.newBuilder()
              .setMetadataLocation(state.getMetadataLocation());
      builder.setIcebergTableMetadata(stateBuilder);
    } else {
      throw new IllegalArgumentException("Unknown type " + contents);
    }

    return builder.build().toByteString();
  }

  @Override
  public Contents valueFromStore(ByteString onReferenceValue, Optional<ByteString> globalState) {
    ObjectTypes.Contents contents = parse(onReferenceValue);
    Optional<ObjectTypes.Contents> globalContents =
        globalState.map(TableCommitMetaStoreWorker::parse);
    switch (contents.getObjectTypeCase()) {
      case DELTA_LAKE_TABLE:
        Builder builder =
            ImmutableDeltaLakeTable.builder()
                .id(contents.getId())
                .addAllMetadataLocationHistory(
                    contents.getDeltaLakeTable().getMetadataLocationHistoryList())
                .addAllCheckpointLocationHistory(
                    contents.getDeltaLakeTable().getCheckpointLocationHistoryList());
        if (contents.getDeltaLakeTable().getLastCheckpoint() != null) {
          builder.lastCheckpoint(contents.getDeltaLakeTable().getLastCheckpoint());
        }
        return builder.build();

      case ICEBERG_SNAPSHOT:
        return IcebergTable.of(
            globalContents
                .map(ObjectTypes.Contents::getIcebergTableMetadata)
                .map(IcebergTableMetadata::getMetadataLocation)
                .orElseThrow(IllegalStateException::new),
            contents.getIcebergSnapshot().getSnapshotId(),
            contents.getId());

      case SQL_VIEW:
        ObjectTypes.SqlView view = contents.getSqlView();
        return ImmutableSqlView.builder()
            .dialect(Dialect.valueOf(view.getDialect()))
            .sqlText(view.getSqlText())
            .id(contents.getId())
            .build();

      case OBJECTTYPE_NOT_SET:
      default:
        throw new IllegalArgumentException("Unknown type " + contents.getObjectTypeCase());
    }
  }

  @Override
  public String getId(Contents contents) {
    return contents.getId();
  }

  @Override
  public Byte getPayload(Contents contents) {
    if (contents instanceof IcebergTable) {
      return (byte) Contents.Type.ICEBERG_TABLE.ordinal();
    } else if (contents instanceof DeltaLakeTable) {
      return (byte) Contents.Type.DELTA_LAKE_TABLE.ordinal();
    } else if (contents instanceof SqlView) {
      return (byte) Contents.Type.VIEW.ordinal();
    } else {
      throw new IllegalArgumentException("Unknown type " + contents);
    }
  }

  @Override
  public Type getType(Byte payload) {
    if (payload == null || payload > Contents.Type.values().length || payload < 0) {
      throw new IllegalArgumentException(
          String.format("Cannot create type from payload. Payload %d does not exist", payload));
    }
    return Contents.Type.values()[payload];
  }

  @Override
  public boolean requiresGlobalState(Contents contents) {
    return contents instanceof IcebergTable;
  }

  private static ObjectTypes.Contents parse(ByteString onReferenceValue) {
    try {
      return ObjectTypes.Contents.parseFrom(onReferenceValue);
    } catch (InvalidProtocolBufferException e) {
      throw new RuntimeException("Failure parsing data", e);
    }
  }

  @Override
  public SerializerWithPayload<Contents, Type> getValueSerializer() {
    return tableSerializer;
  }

  @Override
  public Serializer<CommitMeta> getMetadataSerializer() {
    return metaSerializer;
  }

  @Deprecated // TODO this class is going to be removed
  private static class TableValueSerializer
      implements SerializerWithPayload<Contents, Contents.Type> {
    @Override
    public ByteString toBytes(Contents value) {
      ObjectTypes.Contents.Builder builder = ObjectTypes.Contents.newBuilder().setId(value.getId());
      if (value instanceof IcebergTable) {
        builder.setIcebergTableMetadata(
            ObjectTypes.IcebergTableMetadata.newBuilder()
                .setMetadataLocation(((IcebergTable) value).getMetadataLocation()));

      } else if (value instanceof DeltaLakeTable) {

        ObjectTypes.DeltaLakeTable.Builder table =
            ObjectTypes.DeltaLakeTable.newBuilder()
                .addAllMetadataLocationHistory(
                    ((DeltaLakeTable) value).getMetadataLocationHistory())
                .addAllCheckpointLocationHistory(
                    ((DeltaLakeTable) value).getCheckpointLocationHistory());
        String lastCheckpoint = ((DeltaLakeTable) value).getLastCheckpoint();
        if (lastCheckpoint != null) {
          table.setLastCheckpoint(lastCheckpoint);
        }
        builder.setDeltaLakeTable(table);
      } else if (value instanceof SqlView) {
        SqlView view = (SqlView) value;
        builder.setSqlView(
            ObjectTypes.SqlView.newBuilder()
                .setDialect(view.getDialect().name())
                .setSqlText(view.getSqlText()));
      } else {
        throw new IllegalArgumentException("Unknown type " + value);
      }

      return builder.build().toByteString();
    }

    @Override
    public Contents fromBytes(ByteString bytes) {
      ObjectTypes.Contents contents = parse(bytes);
      switch (contents.getObjectTypeCase()) {
        case DELTA_LAKE_TABLE:
          Builder builder =
              ImmutableDeltaLakeTable.builder()
                  .id(contents.getId())
                  .addAllMetadataLocationHistory(
                      contents.getDeltaLakeTable().getMetadataLocationHistoryList())
                  .addAllCheckpointLocationHistory(
                      contents.getDeltaLakeTable().getCheckpointLocationHistoryList());
          if (contents.getDeltaLakeTable().getLastCheckpoint() != null) {
            builder.lastCheckpoint(contents.getDeltaLakeTable().getLastCheckpoint());
          }
          return builder.build();

        case ICEBERG_TABLE_METADATA:
          return IcebergTable.of(
              contents.getIcebergTableMetadata().getMetadataLocation(), -1L, contents.getId());

        case SQL_VIEW:
          ObjectTypes.SqlView view = contents.getSqlView();
          return ImmutableSqlView.builder()
              .dialect(Dialect.valueOf(view.getDialect()))
              .sqlText(view.getSqlText())
              .id(contents.getId())
              .build();

        case OBJECTTYPE_NOT_SET:
        default:
          throw new IllegalArgumentException("Unknown type " + contents.getObjectTypeCase());
      }
    }

    @Override
    public Byte getPayload(Contents value) {
      if (value instanceof IcebergTable) {
        return (byte) Contents.Type.ICEBERG_TABLE.ordinal();
      } else if (value instanceof DeltaLakeTable) {
        return (byte) Contents.Type.DELTA_LAKE_TABLE.ordinal();
      } else if (value instanceof SqlView) {
        return (byte) Contents.Type.VIEW.ordinal();
      } else {
        throw new IllegalArgumentException("Unknown type " + value);
      }
    }

    @Override
    public Contents.Type getType(Byte payload) {
      if (payload == null || payload > Contents.Type.values().length || payload < 0) {
        throw new IllegalArgumentException(
            String.format("Cannot create type from payload. Payload %d does not exist", payload));
      }
      return Contents.Type.values()[payload];
    }
  }

  private static class MetadataSerializer implements Serializer<CommitMeta> {
    @Override
    public ByteString toBytes(CommitMeta value) {
      try {
        return ByteString.copyFrom(MAPPER.writeValueAsBytes(value));
      } catch (JsonProcessingException e) {
        throw new RuntimeException(String.format("Couldn't serialize commit meta %s", value), e);
      }
    }

    @Override
    public CommitMeta fromBytes(ByteString bytes) {
      try {
        return MAPPER.readValue(bytes.toByteArray(), CommitMeta.class);
      } catch (IOException e) {
        return ImmutableCommitMeta.builder()
            .message("unknown")
            .committer("unknown")
            .hash("unknown")
            .build();
      }
    }
  }
}
