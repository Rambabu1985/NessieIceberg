/*
 * Copyright (C) 2022 Dremio
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
package org.projectnessie.versioned.store;

import com.google.protobuf.ByteString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;
import javax.annotation.Nonnull;
import org.projectnessie.model.Content;
import org.projectnessie.versioned.ContentAttachment;
import org.projectnessie.versioned.ContentAttachmentKey;
import org.projectnessie.versioned.StoreWorker;

/**
 * Main {@link StoreWorker} implementation that maintains a registry of available {@link
 * org.projectnessie.versioned.store.ContentSerializer content serializers} and delegates to these.
 */
public class DefaultStoreWorker implements StoreWorker {

  public static StoreWorker instance() {
    return Lazy.INSTANCE;
  }

  private static final class Lazy {
    private static final DefaultStoreWorker INSTANCE = new DefaultStoreWorker();
  }

  private static final class Registry {
    private static final ContentSerializer<?>[] BY_PAYLOAD;
    private static final Map<Content.Type, ContentSerializer<?>> BY_TYPE;

    static {
      Set<String> byName = new HashSet<>();
      Map<Content.Type, ContentSerializer<?>> byType = new HashMap<>();
      List<ContentSerializer<?>> byPayload = new ArrayList<>();

      for (ContentSerializerBundle bundle : ServiceLoader.load(ContentSerializerBundle.class)) {
        bundle.register(
            contentTypeSerializer -> {
              Content.Type contentType = contentTypeSerializer.contentType();
              if (!byName.add(contentType.name())) {
                throw new IllegalStateException(
                    "Found more than one ContentTypeSerializer for content type "
                        + contentType.name());
              }
              if (contentType.payload() != 0
                  && byType.put(contentType, contentTypeSerializer) != null) {
                throw new IllegalStateException(
                    "Found more than one ContentTypeSerializer for content type "
                        + contentType.type());
              }
              while (byPayload.size() <= contentType.payload()) {
                byPayload.add(null);
              }
              if (byPayload.set(contentType.payload(), contentTypeSerializer) != null) {
                throw new IllegalStateException(
                    "Found more than one ContentTypeSerializer for content payload "
                        + contentType.payload());
              }
            });
      }

      BY_PAYLOAD = byPayload.toArray(new ContentSerializer[0]);
      BY_TYPE = byType;
    }
  }

  private @Nonnull <C extends Content> ContentSerializer<C> serializer(C content) {
    @SuppressWarnings("unchecked")
    ContentSerializer<C> serializer =
        (ContentSerializer<C>) Registry.BY_TYPE.get(content.getType());
    if (serializer == null) {
      throw new IllegalArgumentException("Unknown type " + content);
    }
    return serializer;
  }

  private @Nonnull <C extends Content> ContentSerializer<C> serializer(byte payload) {
    @SuppressWarnings("unchecked")
    ContentSerializer<C> serializer =
        payload >= 0 && payload < Registry.BY_PAYLOAD.length
            ? (ContentSerializer<C>) Registry.BY_PAYLOAD[payload]
            : null;
    if (serializer == null) {
      throw new IllegalArgumentException("Unknown payload " + payload);
    }
    return serializer;
  }

  @Override
  public ByteString toStoreOnReferenceState(
      Content content, Consumer<ContentAttachment> attachmentConsumer) {
    return serializer(content).toStoreOnReferenceState(content, attachmentConsumer);
  }

  @Override
  public Content applyId(Content content, String id) {
    return serializer(content).applyId(content, id);
  }

  @Override
  public boolean requiresGlobalState(Content content) {
    return serializer(content).requiresGlobalState(content);
  }

  @Override
  public Content valueFromStore(
      byte payload,
      ByteString onReferenceValue,
      Supplier<ByteString> globalState,
      Function<Stream<ContentAttachmentKey>, Stream<ContentAttachment>> attachmentsRetriever) {
    return serializer(payload)
        .valueFromStore(payload, onReferenceValue, globalState, attachmentsRetriever);
  }

  @Override
  public boolean requiresGlobalState(byte payload, ByteString onReferenceValue) {
    return serializer(payload).requiresGlobalState(payload, onReferenceValue);
  }

  @Override
  public Content.Type getType(byte payload, ByteString onReferenceValue) {
    return serializer(payload).getType(payload, onReferenceValue);
  }
}
