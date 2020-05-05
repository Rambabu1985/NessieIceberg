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

package com.dremio.iceberg.client;

import com.dremio.iceberg.model.Tag;
import com.dremio.iceberg.model.VersionedWrapper;
import java.util.function.Supplier;
import javax.ws.rs.core.GenericType;

/**
 * Tag client to make http requests for tags.
 */
public class TagClient extends BaseClient<Tag> {

  public static final GenericType<VersionedWrapper<Tag>> TAG_TYPE =
      new GenericType<VersionedWrapper<Tag>>() { };

  public TagClient(ClientWithHelpers client,
                   String base,
                   String endpoint,
                   Supplier<String> authHeader) {
    super(client,
          "tags",
          base,
          endpoint,
          authHeader,
          Tag[].class,
          TAG_TYPE);
  }
}
