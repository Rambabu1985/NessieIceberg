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

package com.dremio.nessie.tiered.builder.base;

import java.util.stream.Stream;

import com.dremio.nessie.tiered.builder.Fragment;
import com.dremio.nessie.versioned.Key;

/**
 * Abstract implementation of {@link Fragment}, all methods return {@code this}.
 */
public abstract class AbstractFragment extends AbstractBaseValue<Fragment> implements Fragment {
  @Override
  public Fragment keys(Stream<Key> keys) {
    return this;
  }
}
