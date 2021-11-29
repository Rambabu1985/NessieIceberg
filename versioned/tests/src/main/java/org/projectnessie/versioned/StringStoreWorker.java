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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.protobuf.ByteString;
import java.util.Optional;
import org.projectnessie.versioned.StringStoreWorker.TestEnum;

public final class StringStoreWorker implements StoreWorker<String, String, TestEnum> {

  public enum TestEnum {
    YES,
    NO,
    NULL
  }

  public static final StringStoreWorker INSTANCE = new StringStoreWorker();

  private static final Serializer<String> METADATA =
      new Serializer<String>() {
        @Override
        public String fromBytes(ByteString bytes) {
          return bytes.toString(UTF_8);
        }

        @Override
        public ByteString toBytes(String value) {
          return ByteString.copyFrom(value, UTF_8);
        }
      };

  private StringStoreWorker() {}

  public static String withStateAndId(String state, String value, String contentId) {
    return state + '|' + value + '@' + contentId;
  }

  public static String withId(String value, String contentId) {
    return value + '@' + contentId;
  }

  @Override
  public ByteString toStoreOnReferenceState(String content) {
    int i = content.indexOf('|');
    if (i != -1) {
      content = content.substring(i + 1);
    }
    return ByteString.copyFromUtf8(content);
  }

  @Override
  public ByteString toStoreGlobalState(String content) {
    int i = content.indexOf('@');
    String cid = content.substring(i);
    i = content.indexOf('|');
    if (i != -1) {
      content = content.substring(0, i) + cid;
    }
    return ByteString.copyFromUtf8(content);
  }

  @Override
  public String valueFromStore(ByteString onReferenceValue, Optional<ByteString> globalState) {
    return globalState
        .map(bytes -> stripContentId(bytes.toStringUtf8()) + '|' + onReferenceValue.toStringUtf8())
        .orElseGet(onReferenceValue::toStringUtf8);
  }

  @Override
  public String getId(String content) {
    int i = content.indexOf('@');
    return i != -1 ? content.substring(i + 1) : "FIXED";
  }

  @Override
  public Byte getPayload(String content) {
    return 0;
  }

  @Override
  public TestEnum getType(Byte payload) {
    if (payload == null) {
      return StringStoreWorker.TestEnum.NULL;
    }
    return payload > 60 ? StringStoreWorker.TestEnum.YES : StringStoreWorker.TestEnum.NO;
  }

  @Override
  public boolean requiresGlobalState(String content) {
    return content.indexOf('|') != -1 || content.indexOf('@') != -1;
  }

  @Override
  public boolean requiresGlobalState(TestEnum testEnum) {
    return true;
  }

  @Override
  public Serializer<String> getMetadataSerializer() {
    return METADATA;
  }

  private static String stripContentId(String s) {
    int i = s.indexOf('@');
    return i == -1 ? s : s.substring(0, i);
  }
}
