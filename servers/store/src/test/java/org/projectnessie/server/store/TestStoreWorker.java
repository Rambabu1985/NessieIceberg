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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.fail;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.protobuf.ByteString;
import java.net.URL;
import java.time.Instant;
import java.util.AbstractMap;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.assertj.core.api.InstanceOfAssertFactories;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.projectnessie.model.CommitMeta;
import org.projectnessie.model.Content;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.IcebergView;
import org.projectnessie.model.ImmutableCommitMeta;
import org.projectnessie.model.ImmutableDeltaLakeTable;
import org.projectnessie.model.ImmutableNamespace;
import org.projectnessie.model.Namespace;
import org.projectnessie.server.store.proto.ObjectTypes;
import org.projectnessie.server.store.proto.ObjectTypes.ContentPartReference;
import org.projectnessie.server.store.proto.ObjectTypes.ContentPartType;
import org.projectnessie.server.store.proto.ObjectTypes.IcebergMetadataPointer;
import org.projectnessie.server.store.proto.ObjectTypes.IcebergRefState;
import org.projectnessie.server.store.proto.ObjectTypes.IcebergViewState;
import org.projectnessie.versioned.ContentAttachment;
import org.projectnessie.versioned.ContentAttachmentKey;

class TestStoreWorker {

  private static final ObjectMapper MAPPER = new ObjectMapper();
  private static final String ID = "x";
  public static final String CID = "cid";
  private final TableCommitMetaStoreWorker worker = new TableCommitMetaStoreWorker();

  @SuppressWarnings("UnnecessaryLambda")
  private static final Consumer<ContentAttachment> ALWAYS_THROWING_ATTACHMENT_CONSUMER =
      attachment -> fail("Unexpected use of Consumer<ContentAttachment>");

  @SuppressWarnings("UnnecessaryLambda")
  private static final Supplier<ByteString> ALWAYS_THROWING_BYTE_STRING_SUPPLIER =
      () -> fail("Unexpected use of Supplier<ByteString>");

  @SuppressWarnings("UnnecessaryLambda")
  private static final Function<Stream<ContentAttachmentKey>, Stream<ContentAttachment>>
      NO_ATTACHMENTS_RETRIEVER = contentAttachmentKeyStream -> Stream.empty();

  @Test
  void tableMetadataLocationGlobalNotAvailable() {
    assertThatThrownBy(
            () ->
                worker.valueFromStore(
                    ObjectTypes.Content.newBuilder()
                        .setId(CID)
                        .setIcebergRefState(
                            ObjectTypes.IcebergRefState.newBuilder()
                                .setSnapshotId(42)
                                .setSchemaId(43)
                                .setSpecId(44)
                                .setSortOrderId(45))
                        .build()
                        .toByteString(),
                    () -> null,
                    NO_ATTACHMENTS_RETRIEVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Iceberg content from reference must have global state, but has none");
  }

  @Test
  void tableMetadataLocationGlobal() {
    Content value =
        worker.valueFromStore(
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergRefState(
                    IcebergRefState.newBuilder()
                        .setSnapshotId(42)
                        .setSchemaId(43)
                        .setSpecId(44)
                        .setSortOrderId(45))
                .build()
                .toByteString(),
            () ->
                ObjectTypes.Content.newBuilder()
                    .setId(CID)
                    .setIcebergMetadataPointer(
                        IcebergMetadataPointer.newBuilder()
                            .setMetadataLocation("metadata-location"))
                    .build()
                    .toByteString(),
            NO_ATTACHMENTS_RETRIEVER);
    assertThat(value)
        .isInstanceOf(IcebergTable.class)
        .asInstanceOf(InstanceOfAssertFactories.type(IcebergTable.class))
        .extracting(
            IcebergTable::getMetadataLocation,
            IcebergTable::getSnapshotId,
            IcebergTable::getSchemaId,
            IcebergTable::getSpecId,
            IcebergTable::getSortOrderId)
        .containsExactly("metadata-location", 42L, 43, 44, 45);
  }

  @Test
  void tableMetadataLocationOnRef() {
    Content value =
        worker.valueFromStore(
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergRefState(
                    IcebergRefState.newBuilder()
                        .setSnapshotId(42)
                        .setSchemaId(43)
                        .setSpecId(44)
                        .setSortOrderId(45)
                        .setMetadataLocation("metadata-location"))
                .build()
                .toByteString(),
            () -> null,
            NO_ATTACHMENTS_RETRIEVER);
    assertThat(value)
        .isInstanceOf(IcebergTable.class)
        .asInstanceOf(InstanceOfAssertFactories.type(IcebergTable.class))
        .extracting(
            IcebergTable::getMetadataLocation,
            IcebergTable::getSnapshotId,
            IcebergTable::getSchemaId,
            IcebergTable::getSpecId,
            IcebergTable::getSortOrderId)
        .containsExactly("metadata-location", 42L, 43, 44, 45);
  }

  @Test
  void viewMetadataLocationGlobalNotAvailable() {
    assertThatThrownBy(
            () ->
                worker.valueFromStore(
                    ObjectTypes.Content.newBuilder()
                        .setId(CID)
                        .setIcebergViewState(
                            ObjectTypes.IcebergViewState.newBuilder().setVersionId(42))
                        .build()
                        .toByteString(),
                    () -> null,
                    NO_ATTACHMENTS_RETRIEVER))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Iceberg content from reference must have global state, but has none");
  }

  @Test
  void viewMetadataLocationGlobal() {
    Content value =
        worker.valueFromStore(
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergViewState(ObjectTypes.IcebergViewState.newBuilder().setVersionId(42))
                .build()
                .toByteString(),
            () ->
                ObjectTypes.Content.newBuilder()
                    .setId(CID)
                    .setIcebergMetadataPointer(
                        IcebergMetadataPointer.newBuilder()
                            .setMetadataLocation("metadata-location"))
                    .build()
                    .toByteString(),
            NO_ATTACHMENTS_RETRIEVER);
    assertThat(value)
        .isInstanceOf(IcebergView.class)
        .asInstanceOf(InstanceOfAssertFactories.type(IcebergView.class))
        .extracting(IcebergView::getMetadataLocation, IcebergView::getVersionId)
        .containsExactly("metadata-location", 42);
  }

  static Stream<Arguments> requiresGlobalStateModelType() {
    return Stream.of(
        Arguments.of(
            withId(Namespace.of("foo")),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setNamespace(ObjectTypes.Namespace.newBuilder().addElements("foo")),
            null,
            false,
            Content.Type.NAMESPACE),
        //
        Arguments.of(
            IcebergTable.of("metadata", 42, 43, 44, 45, CID),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergRefState(
                    ObjectTypes.IcebergRefState.newBuilder()
                        .setSnapshotId(42)
                        .setSchemaId(43)
                        .setSpecId(44)
                        .setSortOrderId(45)),
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergMetadataPointer(
                    ObjectTypes.IcebergMetadataPointer.newBuilder()
                        .setMetadataLocation("metadata")),
            true,
            Content.Type.ICEBERG_TABLE),
        //
        Arguments.of(
            IcebergTable.of("metadata", 42, 43, 44, 45, CID),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergRefState(
                    ObjectTypes.IcebergRefState.newBuilder()
                        .setSnapshotId(42)
                        .setSchemaId(43)
                        .setSpecId(44)
                        .setSortOrderId(45)
                        .setMetadataLocation("metadata")),
            null,
            false,
            Content.Type.ICEBERG_TABLE),
        //
        Arguments.of(
            IcebergView.of(CID, "metadata", 42, 43, "dialect", "sqlText"),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergViewState(
                    ObjectTypes.IcebergViewState.newBuilder()
                        .setVersionId(42)
                        .setSchemaId(43)
                        .setDialect("dialect")
                        .setSqlText("sqlText")),
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergMetadataPointer(
                    ObjectTypes.IcebergMetadataPointer.newBuilder()
                        .setMetadataLocation("metadata")),
            true,
            Content.Type.ICEBERG_VIEW),
        //
        Arguments.of(
            IcebergView.of(CID, "metadata", 42, 43, "dialect", "sqlText"),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergViewState(
                    ObjectTypes.IcebergViewState.newBuilder()
                        .setVersionId(42)
                        .setSchemaId(43)
                        .setDialect("dialect")
                        .setSqlText("sqlText")
                        .setMetadataLocation("metadata")),
            null,
            false,
            Content.Type.ICEBERG_VIEW),
        //
        Arguments.of(
            ImmutableDeltaLakeTable.builder()
                .id(CID)
                .addCheckpointLocationHistory("check")
                .addMetadataLocationHistory("meta")
                .build(),
            false,
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setDeltaLakeTable(
                    ObjectTypes.DeltaLakeTable.newBuilder()
                        .addCheckpointLocationHistory("check")
                        .addMetadataLocationHistory("meta")),
            null,
            false,
            Content.Type.DELTA_LAKE_TABLE));
  }

  @ParameterizedTest
  @MethodSource("requiresGlobalStateModelType")
  void requiresGlobalStateModelType(
      Content content,
      boolean modelGlobal,
      ObjectTypes.Content.Builder onRefBuilder,
      ObjectTypes.Content.Builder globalBuilder,
      boolean storeGlobal,
      Content.Type type) {
    assertThat(content)
        .extracting(worker::requiresGlobalState, worker::getType)
        .containsExactly(modelGlobal, type);

    ByteString onRef = onRefBuilder.build().toByteString();
    ByteString global = globalBuilder != null ? globalBuilder.build().toByteString() : null;

    assertThat(onRef)
        .asInstanceOf(InstanceOfAssertFactories.type(ByteString.class))
        .extracting(worker::requiresGlobalState, worker::getType)
        .containsExactly(storeGlobal, type);
    assertThat(worker.valueFromStore(onRef, () -> global, NO_ATTACHMENTS_RETRIEVER))
        .isEqualTo(content);

    if (storeGlobal) {
      // Add "metadataLocation" to expected on-ref status, because toStoreOnReferenceState() always
      // returns the "metadataLocation", as #3866 changed the type of IcebergTable/View from
      // global state to on-ref state.
      if (onRefBuilder.hasIcebergRefState()) {
        onRef =
            onRefBuilder
                .setIcebergRefState(
                    onRefBuilder
                        .getIcebergRefStateBuilder()
                        .setMetadataLocation(((IcebergTable) content).getMetadataLocation()))
                .build()
                .toByteString();
      } else if (onRefBuilder.hasIcebergViewState()) {
        onRef =
            onRefBuilder
                .setIcebergViewState(
                    onRefBuilder
                        .getIcebergViewStateBuilder()
                        .setMetadataLocation(((IcebergView) content).getMetadataLocation()))
                .build()
                .toByteString();
      }
    }

    assertThat(content)
        .extracting(
            content1 ->
                worker.toStoreOnReferenceState(content1, ALWAYS_THROWING_ATTACHMENT_CONSUMER))
        .isEqualTo(onRef);
    if (storeGlobal) {
      assertThat(content).extracting(worker::toStoreGlobalState).isEqualTo(global);
    }
  }

  @Test
  void viewMetadataLocationOnRef() {
    Content value =
        worker.valueFromStore(
            ObjectTypes.Content.newBuilder()
                .setId(CID)
                .setIcebergViewState(
                    ObjectTypes.IcebergViewState.newBuilder()
                        .setVersionId(42)
                        .setMetadataLocation("metadata-location"))
                .build()
                .toByteString(),
            () -> null,
            NO_ATTACHMENTS_RETRIEVER);
    assertThat(value)
        .isInstanceOf(IcebergView.class)
        .asInstanceOf(InstanceOfAssertFactories.type(IcebergView.class))
        .extracting(IcebergView::getMetadataLocation, IcebergView::getVersionId)
        .containsExactly("metadata-location", 42);
  }

  @ParameterizedTest
  @MethodSource("provideDeserialization")
  void testDeserialization(Map.Entry<ByteString, Content> entry) {
    Content actual = worker.valueFromStore(entry.getKey(), () -> null, x -> Stream.empty());
    assertThat(actual).isEqualTo(entry.getValue());
  }

  @ParameterizedTest
  @MethodSource("provideDeserialization")
  void testSerialization(Map.Entry<ByteString, Content> entry) {
    ByteString actual =
        worker.toStoreOnReferenceState(entry.getValue(), ALWAYS_THROWING_ATTACHMENT_CONSUMER);
    assertThat(actual).isEqualTo(entry.getKey());
  }

  @ParameterizedTest
  @MethodSource("provideDeserialization")
  void testSerde(Map.Entry<ByteString, Content> entry) {
    ByteString actualBytes =
        worker.toStoreOnReferenceState(entry.getValue(), ALWAYS_THROWING_ATTACHMENT_CONSUMER);
    assertThat(worker.valueFromStore(actualBytes, () -> null, x -> Stream.empty()))
        .isEqualTo(entry.getValue());
    Content actualContent = worker.valueFromStore(entry.getKey(), () -> null, x -> Stream.empty());
    assertThat(worker.toStoreOnReferenceState(actualContent, ALWAYS_THROWING_ATTACHMENT_CONSUMER))
        .isEqualTo(entry.getKey());
  }

  private static JsonNode loadJson(String scenario, String name) {
    String resource =
        String.format(
            "org/projectnessie/test-data/iceberg-metadata/%s/%s-%s.json", scenario, scenario, name);
    URL url = TestStoreWorker.class.getClassLoader().getResource(resource);
    try (JsonParser parser =
        new ObjectMapper()
            .createParser(
                Objects.requireNonNull(
                    url, () -> String.format("Resource %s not found", resource)))) {
      return parser.readValueAs(JsonNode.class);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
  }

  @Test
  void testSerdeIcebergTableMetadata() throws Exception {
    IcebergTable table = IcebergTable.of(loadJson("table-three-snapshots", "3"), "foo://bar", ID);

    IcebergRefState baseStoreOnRef =
        IcebergRefState.newBuilder()
            .setSnapshotId(table.getSnapshotId())
            .setSchemaId(table.getSchemaId())
            .setSpecId(table.getSpecId())
            .setSortOrderId(table.getSortOrderId())
            .setMetadataLocation(table.getMetadataLocation())
            .build();

    Map<ContentAttachmentKey, ContentAttachment> attachments = new LinkedHashMap<>();

    ByteString onReferenceState =
        worker.toStoreOnReferenceState(table, att -> attachments.put(att.getKey(), att));

    IcebergRefState.Builder expectedStoreOnRef = baseStoreOnRef.toBuilder();

    attachments.entrySet().stream()
        .map(
            e -> {
              ContentPartReference.Builder partReference =
                  ContentPartReference.newBuilder()
                      .setType(ContentPartType.valueOf(e.getKey().getAttachmentType()))
                      .setAttachmentId(e.getKey().getAttachmentId());
              if (e.getValue().getObjectId() != null) {
                partReference.setObjectId(e.getValue().getObjectId());
              }
              return partReference;
            })
        .collect(Collectors.groupingBy(ContentPartReference.Builder::getType))
        .forEach(
            (type, parts) -> {
              switch (type) {
                case SHALLOW_METADATA:
                  assertThat(parts).hasSize(1);
                  expectedStoreOnRef.setMetadata(parts.get(0));
                  break;

                  // See TableCommitMetaStoreWorker.IcebergAttachmentDefinition for the reason why
                  // table snapshots & view versions are handled this way and all other child types
                  // differently.
                case SNAPSHOT:
                case VERSION:
                  // The last snapshot/version is a "current" part, all other ones are "extra"
                  // parts. Only "current" parts are returned to Nessie clients in an IcebergTable.
                  parts.subList(0, parts.size() - 1).forEach(expectedStoreOnRef::addExtraParts);
                  expectedStoreOnRef.addCurrentParts(parts.get(parts.size() - 1));
                  break;
                default:
                  parts.forEach(expectedStoreOnRef::addCurrentParts);
                  break;
              }
            });

    ObjectTypes.Content parsed = ObjectTypes.Content.parseFrom(onReferenceState);

    // Verify that all parts are present, the actual order within current-parts and extra-parts does
    // not matter, but is sadly not deterministic, not guaranteed for all kinds of childs either.

    assertThat(parsed.getIcebergRefState().getCurrentPartsList())
        .containsExactlyInAnyOrderElementsOf(expectedStoreOnRef.getCurrentPartsList());
    assertThat(parsed.getIcebergRefState().getExtraPartsList())
        .containsExactlyInAnyOrderElementsOf(expectedStoreOnRef.getExtraPartsList());

    // "extra" parts must only contain Iceberg table snapshots + view versions. All other child
    // object types (schema, partition-spec, sort-order) must be referenced as "current" parts and
    // returned to clients requesting an `IcebergTable` content.
    assertThat(parsed.getIcebergRefState().getExtraPartsList())
        .map(ContentPartReference::getType)
        .allSatisfy(
            type ->
                assertThat(type)
                    .isIn(
                        ObjectTypes.ContentPartType.SNAPSHOT, ObjectTypes.ContentPartType.VERSION));

    assertThat(
            parsed.getIcebergRefState().toBuilder().clearCurrentParts().clearExtraParts().build())
        .isEqualTo(expectedStoreOnRef.clearCurrentParts().clearExtraParts().build());

    Content deserialized =
        worker.valueFromStore(
            onReferenceState,
            ALWAYS_THROWING_BYTE_STRING_SUPPLIER,
            keys -> keys.map(attachments::get));

    assertThat(deserialized).isEqualTo(table);
  }

  @Test
  void testSerdeIcebergViewMetadata() throws Exception {
    IcebergView view = IcebergView.of(loadJson("view-simple", "1"), "foo://bar", ID);

    IcebergViewState baseStoreOnRef =
        IcebergViewState.newBuilder()
            .setVersionId(view.getVersionId())
            .setSchemaId(view.getSchemaId())
            .setMetadataLocation(view.getMetadataLocation())
            .setDialect("")
            .setSqlText(view.getSqlText())
            .build();

    Map<ContentAttachmentKey, ContentAttachment> attachments = new LinkedHashMap<>();

    ByteString onReferenceState =
        worker.toStoreOnReferenceState(view, att -> attachments.put(att.getKey(), att));

    IcebergViewState.Builder expectedStoreOnRef = baseStoreOnRef.toBuilder();

    attachments.entrySet().stream()
        .map(
            e -> {
              ContentPartReference.Builder partReference =
                  ContentPartReference.newBuilder()
                      .setType(ContentPartType.valueOf(e.getKey().getAttachmentType()))
                      .setAttachmentId(e.getKey().getAttachmentId());
              if (e.getValue().getObjectId() != null) {
                partReference.setObjectId(e.getValue().getObjectId());
              }
              return partReference;
            })
        .collect(Collectors.groupingBy(ContentPartReference.Builder::getType))
        .forEach(
            (type, parts) -> {
              if (type == ContentPartType.SHALLOW_METADATA) {
                assertThat(parts).hasSize(1);
                expectedStoreOnRef.setMetadata(parts.get(0));
              } else {
                parts.subList(0, parts.size() - 1).forEach(expectedStoreOnRef::addExtraParts);
                expectedStoreOnRef.addCurrentParts(parts.get(parts.size() - 1));
              }
            });

    ObjectTypes.Content parsed = ObjectTypes.Content.parseFrom(onReferenceState);

    // Verify that all parts are present, the actual order within current-parts and extra-parts does
    // not matter, but is sadly not deterministic, not guaranteed for all kinds of childs either.

    assertThat(parsed.getIcebergViewState().getCurrentPartsList())
        .containsExactlyInAnyOrderElementsOf(expectedStoreOnRef.getCurrentPartsList());
    assertThat(parsed.getIcebergViewState().getExtraPartsList())
        .containsExactlyInAnyOrderElementsOf(expectedStoreOnRef.getExtraPartsList());

    assertThat(
            parsed.getIcebergViewState().toBuilder().clearCurrentParts().clearExtraParts().build())
        .isEqualTo(expectedStoreOnRef.clearCurrentParts().clearExtraParts().build());

    // TODO validate that metadata is really shallow (no child object arrays)

    Content deserialized =
        worker.valueFromStore(
            onReferenceState,
            ALWAYS_THROWING_BYTE_STRING_SUPPLIER,
            keys -> keys.map(attachments::get));

    assertThat(deserialized).isEqualTo(view);
  }

  @Test
  void testSerdeIcebergTableNoMetadata() {
    String path = "foo/bar";
    IcebergTable table = IcebergTable.of(path, 42, 43, 44, 45, ID);

    ObjectTypes.Content protoTableGlobal =
        ObjectTypes.Content.newBuilder()
            .setId(ID)
            .setIcebergMetadataPointer(
                IcebergMetadataPointer.newBuilder().setMetadataLocation(path))
            .build();
    ObjectTypes.Content protoOnRef =
        ObjectTypes.Content.newBuilder()
            .setId(ID)
            .setIcebergRefState(
                IcebergRefState.newBuilder()
                    .setSnapshotId(42)
                    .setSchemaId(43)
                    .setSpecId(44)
                    .setSortOrderId(45)
                    .setMetadataLocation(path))
            .build();

    ByteString tableGlobalBytes = worker.toStoreGlobalState(table);
    ByteString snapshotBytes =
        worker.toStoreOnReferenceState(table, ALWAYS_THROWING_ATTACHMENT_CONSUMER);

    assertThat(tableGlobalBytes).isEqualTo(protoTableGlobal.toByteString());
    assertThat(snapshotBytes).isEqualTo(protoOnRef.toByteString());

    Content deserialized =
        worker.valueFromStore(snapshotBytes, () -> tableGlobalBytes, x -> Stream.empty());
    assertThat(deserialized).isEqualTo(table);
  }

  @Test
  void testSerdeIcebergViewNoMetadata() {
    String path = "foo/view";
    String dialect = "Dremio";
    String sqlText = "select * from world";
    IcebergView view = IcebergView.of(ID, path, 1, 123, dialect, sqlText);

    ObjectTypes.Content protoTableGlobal =
        ObjectTypes.Content.newBuilder()
            .setId(ID)
            .setIcebergMetadataPointer(
                IcebergMetadataPointer.newBuilder().setMetadataLocation(path))
            .build();
    ObjectTypes.Content protoOnRef =
        ObjectTypes.Content.newBuilder()
            .setId(ID)
            .setIcebergViewState(
                IcebergViewState.newBuilder()
                    .setVersionId(1)
                    .setDialect(dialect)
                    .setSchemaId(123)
                    .setSqlText(sqlText)
                    .setMetadataLocation(path))
            .build();

    ByteString tableGlobalBytes = worker.toStoreGlobalState(view);
    ByteString snapshotBytes =
        worker.toStoreOnReferenceState(view, ALWAYS_THROWING_ATTACHMENT_CONSUMER);

    assertThat(tableGlobalBytes).isEqualTo(protoTableGlobal.toByteString());
    assertThat(snapshotBytes).isEqualTo(protoOnRef.toByteString());

    Content deserialized =
        worker.valueFromStore(snapshotBytes, () -> tableGlobalBytes, x -> Stream.empty());
    assertThat(deserialized).isEqualTo(view);
  }

  @Test
  void testCommitSerde() throws JsonProcessingException {
    CommitMeta expectedCommit =
        ImmutableCommitMeta.builder()
            .commitTime(Instant.now())
            .authorTime(Instant.now())
            .author("bill")
            .committer("ted")
            .hash("xyz")
            .message("commit msg")
            .build();

    ByteString expectedBytes = ByteString.copyFrom(MAPPER.writeValueAsBytes(expectedCommit));
    CommitMeta actualCommit = worker.getMetadataSerializer().fromBytes(expectedBytes);
    assertThat(actualCommit).isEqualTo(expectedCommit);
    ByteString actualBytes = worker.getMetadataSerializer().toBytes(expectedCommit);
    assertThat(actualBytes).isEqualTo(expectedBytes);
    actualBytes = worker.getMetadataSerializer().toBytes(expectedCommit);
    assertThat(worker.getMetadataSerializer().fromBytes(actualBytes)).isEqualTo(expectedCommit);
    actualCommit = worker.getMetadataSerializer().fromBytes(expectedBytes);
    assertThat(worker.getMetadataSerializer().toBytes(actualCommit)).isEqualTo(expectedBytes);
  }

  private static Stream<Map.Entry<ByteString, Content>> provideDeserialization() {
    return Stream.of(getDelta(), getNamespace(), getNamespaceWithProperties());
  }

  private static Map.Entry<ByteString, Content> getDelta() {
    String path = "foo/bar";
    String cl1 = "xyz";
    String cl2 = "abc";
    String ml1 = "efg";
    String ml2 = "hij";
    Content content =
        ImmutableDeltaLakeTable.builder()
            .lastCheckpoint(path)
            .addCheckpointLocationHistory(cl1)
            .addCheckpointLocationHistory(cl2)
            .addMetadataLocationHistory(ml1)
            .addMetadataLocationHistory(ml2)
            .id(ID)
            .build();
    ByteString bytes =
        ObjectTypes.Content.newBuilder()
            .setId(ID)
            .setDeltaLakeTable(
                ObjectTypes.DeltaLakeTable.newBuilder()
                    .setLastCheckpoint(path)
                    .addCheckpointLocationHistory(cl1)
                    .addCheckpointLocationHistory(cl2)
                    .addMetadataLocationHistory(ml1)
                    .addMetadataLocationHistory(ml2))
            .build()
            .toByteString();
    return new AbstractMap.SimpleImmutableEntry<>(bytes, content);
  }

  private static Map.Entry<ByteString, Content> getNamespace() {
    List<String> elements = Arrays.asList("a", "b.c", "d");
    Namespace namespace = withId(Namespace.of(elements));
    ByteString bytes =
        ObjectTypes.Content.newBuilder()
            .setId(namespace.getId())
            .setNamespace(ObjectTypes.Namespace.newBuilder().addAllElements(elements).build())
            .build()
            .toByteString();
    return new AbstractMap.SimpleImmutableEntry<>(bytes, namespace);
  }

  private static Map.Entry<ByteString, Content> getNamespaceWithProperties() {
    List<String> elements = Arrays.asList("a", "b.c", "d");
    Map<String, String> properties = ImmutableMap.of("key1", "val1");
    Namespace namespace = withId(Namespace.of(elements, properties));
    ByteString bytes =
        ObjectTypes.Content.newBuilder()
            .setId(namespace.getId())
            .setNamespace(
                ObjectTypes.Namespace.newBuilder()
                    .addAllElements(elements)
                    .putAllProperties(properties)
                    .build())
            .build()
            .toByteString();
    return new AbstractMap.SimpleImmutableEntry<>(bytes, namespace);
  }

  private static Namespace withId(Namespace namespace) {
    return ImmutableNamespace.builder().from(namespace).id(CID).build();
  }
}
