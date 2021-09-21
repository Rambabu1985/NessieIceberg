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
package org.projectnessie.versioned.persist.rocks;

import org.projectnessie.versioned.persist.adapter.DatabaseAdapter;
import org.projectnessie.versioned.persist.adapter.DatabaseAdapterFactory;

public class RocksDatabaseAdapterFactory
    implements DatabaseAdapterFactory<RocksDatabaseAdapterConfig> {

  public static final String NAME = "RocksDB";

  @Override
  public String getName() {
    return NAME;
  }

  @Override
  public Builder<RocksDatabaseAdapterConfig> newBuilder() {
    return new Builder<RocksDatabaseAdapterConfig>() {
      @Override
      protected RocksDatabaseAdapterConfig getDefaultConfig() {
        return ImmutableRocksDatabaseAdapterConfig.builder().build();
      }

      @Override
      public DatabaseAdapter build() {
        return new RocksDatabaseAdapter(getConfig());
      }
    };
  }
}
