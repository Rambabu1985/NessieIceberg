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
package com.dremio.nessie.tiered.builder;

import java.util.stream.Stream;

import com.dremio.nessie.versioned.store.KeyDelta;

/**
 * Consumer for L2s.
 */
public interface L3Consumer extends BaseConsumer<L3Consumer> {

  /**
   * Adds {@link KeyDelta}s for this L3.
   * <p>Must be called exactly once.</p>
   *
   * @param keyDelta The stream providing the {@link KeyDelta}s of this L3
   * @return This consumer.
   */
  L3Consumer keyDelta(Stream<KeyDelta> keyDelta);
}
