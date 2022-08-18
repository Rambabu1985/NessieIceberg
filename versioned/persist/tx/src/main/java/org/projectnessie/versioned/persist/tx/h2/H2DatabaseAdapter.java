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
package org.projectnessie.versioned.persist.tx.h2;

import com.google.common.collect.ImmutableMap;
import java.util.Map;
import org.projectnessie.versioned.persist.adapter.events.AdapterEventConsumer;
import org.projectnessie.versioned.persist.tx.TxConnectionProvider;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapter;
import org.projectnessie.versioned.persist.tx.TxDatabaseAdapterConfig;

public class H2DatabaseAdapter extends TxDatabaseAdapter {

  public H2DatabaseAdapter(
      TxDatabaseAdapterConfig config,
      TxConnectionProvider<?> db,
      AdapterEventConsumer eventConsumer) {
    super(config, db, eventConsumer);
  }

  @Override
  protected Map<NessieSqlDataType, String> databaseSqlFormatParameters() {
    return ImmutableMap.<NessieSqlDataType, String>builder()
        .put(NessieSqlDataType.BLOB, "VARBINARY(390000)")
        .put(NessieSqlDataType.HASH, "VARCHAR")
        .put(NessieSqlDataType.KEY_PREFIX, "VARCHAR")
        .put(NessieSqlDataType.KEY, "VARCHAR")
        .put(NessieSqlDataType.NAMED_REF, "VARCHAR")
        .put(NessieSqlDataType.NAMED_REF_TYPE, "VARCHAR")
        .put(NessieSqlDataType.CONTENT_ID, "VARCHAR")
        .put(NessieSqlDataType.INTEGER, "BIGINT")
        .build();
  }
}
