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
package org.projectnessie.client;

import java.net.URI;
import org.projectnessie.api.ConfigApi;
import org.projectnessie.api.ContentsApi;
import org.projectnessie.api.TreeApi;

public interface NessieClient extends AutoCloseable {

  // Overridden to "remove 'throws Exception'"
  void close();

  String getOwner();

  String getRepo();

  URI getUri();

  /**
   * Tree-API scoped to the repository returned by {@link #getRepo()} for this {@link NessieClient}.
   *
   * @return The {@link TreeApi} instance.
   */
  TreeApi getTreeApi();

  /**
   * Contents-API scoped to the repository returned by {@link #getRepo()} for this {@link
   * NessieClient}.
   *
   * @return The {@link ContentsApi} instance.
   */
  ContentsApi getContentsApi();

  /**
   * The Config-API scoped to the repository returned by {@link #getRepo()} for this {@link
   * NessieClient}.
   *
   * @return The {@link ConfigApi} instance.
   */
  ConfigApi getConfigApi();
}
