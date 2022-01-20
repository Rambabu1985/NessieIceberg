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
package org.projectnessie.versioned;

import com.google.protobuf.ByteString;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A set of helpers that users of a VersionStore must implement.
 *
 * @param <CONTENT> The value type saved in the VersionStore.
 * @param <COMMIT_METADATA> The commit metadata type saved in the VersionStore.
 */
public interface StoreWorker<CONTENT, COMMIT_METADATA, CONTENT_TYPE extends Enum<CONTENT_TYPE>> {

  /** Returns the serialized representation of the on-reference part of the given content-object. */
  ByteString toStoreOnReferenceState(CONTENT content);

  ByteString toStoreGlobalState(CONTENT content);

  void toStoreAttachments(CONTENT content, Consumer<ContentAttachment> attachmentConsumer);

  CONTENT valueFromStore(
      ByteString onReferenceValue,
      Supplier<ByteString> globalState,
      Function<Stream<ContentAttachmentKey>, Stream<ContentAttachment>> attachmentsRetriever);

  String getId(CONTENT content);

  Byte getPayload(CONTENT content);

  default boolean requiresGlobalState(ByteString content) {
    return requiresGlobalState(getType(content));
  }

  default boolean requiresGlobalState(CONTENT content) {
    return requiresGlobalState(getType(content));
  }

  boolean requiresGlobalState(Enum<CONTENT_TYPE> contentType);

  default boolean requiresPerContentState(ByteString content) {
    return requiresPerContentState(getType(content));
  }

  default boolean requiresPerContentState(CONTENT content) {
    return requiresPerContentState(getType(content));
  }

  boolean requiresPerContentState(Enum<CONTENT_TYPE> contentType);

  CONTENT_TYPE getType(ByteString onRefContent);

  CONTENT_TYPE getType(Byte payload);

  default CONTENT_TYPE getType(CONTENT content) {
    return getType(getPayload(content));
  }

  Serializer<COMMIT_METADATA> getMetadataSerializer();

  default boolean isNamespace(ByteString type) {
    return false;
  }
}
