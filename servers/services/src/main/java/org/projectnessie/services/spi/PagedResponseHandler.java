/*
 * Copyright (C) 2023 Dremio
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
package org.projectnessie.services.spi;

/**
 * Interface implemented by network protocol specific implementations (like HTTP/REST) to support
 * response pagination using tokens.
 *
 * <p>Protocol specific implementations "tell" the backend (Nessie service implementations) whether
 * a response entry fits in the current response page sent to the client or not via the result of
 * the {@link #addEntry(Object)} method.
 *
 * @param <R> Final response object type.
 * @param <E> Entry type.
 */
public interface PagedResponseHandler<R, E> {

  /** Produce the response page from a response page builder instance. */
  R build();

  /**
   * Add an entry to the response page builder.
   *
   * @param entry the entry to add
   * @return {@code true} if {@code entry} could be added to the response page or {@code false} if
   *     not
   */
  boolean addEntry(E entry);

  /**
   * Called to indicate that there are more entries to retrieve using the given paging token.
   *
   * @param pagingToken paging token to be used to access the next page
   */
  void hasMore(String pagingToken);
}
