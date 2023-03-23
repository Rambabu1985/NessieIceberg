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
package org.projectnessie.versioned.storage.dynamodb;

import static org.projectnessie.versioned.storage.common.objtypes.ContentValueObj.contentValue;

import org.assertj.core.api.SoftAssertions;
import org.assertj.core.api.junit.jupiter.InjectSoftAssertions;
import org.assertj.core.api.junit.jupiter.SoftAssertionsExtension;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.projectnessie.nessie.relocated.protobuf.ByteString;
import org.projectnessie.versioned.storage.common.exceptions.ObjTooLargeException;
import org.projectnessie.versioned.storage.common.persist.Obj;
import org.projectnessie.versioned.storage.common.persist.ObjId;
import org.projectnessie.versioned.storage.common.persist.Persist;
import org.projectnessie.versioned.storage.commontests.AbstractPersistTests;
import org.projectnessie.versioned.storage.testextension.NessieBackend;
import org.projectnessie.versioned.storage.testextension.NessiePersist;
import org.projectnessie.versioned.storage.testextension.PersistExtension;

@NessieBackend(DynamoDBBackendTestFactory.class)
@ExtendWith({PersistExtension.class, SoftAssertionsExtension.class})
public class ITDynamoDBPersist extends AbstractPersistTests {
  @InjectSoftAssertions protected SoftAssertions soft;

  @NessiePersist protected Persist persist;

  @Test
  public void dynamoDbHardItemSizeLimit() throws Exception {
    persist.storeObj(
        contentValue(ObjId.randomObjId(), "foo", 42, ByteString.copyFrom(new byte[350 * 1024])));

    persist.storeObjs(
        new Obj[] {
          contentValue(ObjId.randomObjId(), "foo", 42, ByteString.copyFrom(new byte[350 * 1024]))
        });

    // DynamoDB's hard 400k limit
    soft.assertThatThrownBy(
            () ->
                persist.storeObj(
                    contentValue(
                        ObjId.randomObjId(), "foo", 42, ByteString.copyFrom(new byte[400 * 1024]))))
        .isInstanceOf(ObjTooLargeException.class);
    soft.assertThatThrownBy(
            () ->
                persist.storeObjs(
                    new Obj[] {
                      contentValue(
                          ObjId.randomObjId(), "foo", 42, ByteString.copyFrom(new byte[400 * 1024]))
                    }))
        .isInstanceOf(ObjTooLargeException.class);
  }
}
