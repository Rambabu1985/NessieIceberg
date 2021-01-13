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
package com.dremio.nessie.versioned.impl;

import static com.dremio.nessie.versioned.impl.ValidationHelper.checkCalled;
import static com.dremio.nessie.versioned.impl.ValidationHelper.checkSet;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import com.dremio.nessie.tiered.builder.FragmentConsumer;
import com.dremio.nessie.versioned.Key;
import com.dremio.nessie.versioned.store.Id;
import com.google.common.base.Objects;
import com.google.common.collect.ImmutableList;

public class Fragment extends PersistentBase<FragmentConsumer> {

  private final List<InternalKey> keys;

  public Fragment(List<InternalKey> keys) {
    super();
    this.keys = ImmutableList.copyOf(keys);
  }

  public Fragment(Id id, List<InternalKey> keys) {
    super(id);
    this.keys = ImmutableList.copyOf(keys);
  }

  @Override
  Id generateId() {
    return Id.build(h -> {
      keys.stream().forEach(k -> InternalKey.addToHasher(k, h));
    });
  }

  public List<InternalKey> getKeys() {
    return keys;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    Fragment fragment = (Fragment) o;
    return Objects.equal(keys, fragment.keys);
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(keys);
  }

  @Override
  public FragmentConsumer applyToConsumer(FragmentConsumer consumer) {
    return super.applyToConsumer(consumer)
        .keys(keys.stream().map(InternalKey::toKey));
  }

  /**
   * Create a new {@link Builder} instance that implements
   * {@link FragmentConsumer} to build a {@link Fragment} object.
   *
   * @return new builder instance
   */
  public static Builder builder() {
    return new Builder();
  }

  /**
   * Implements {@link FragmentConsumer} to build a {@link Fragment} object.
   */
  // Needs to be a public class, otherwise class-initialization of ValueType fails with j.l.IllegalAccessError
  public static class Builder extends EntityBuilder<Fragment> implements FragmentConsumer {

    private Id id;
    private List<InternalKey> keys;

    @Override
    public Builder id(Id id) {
      checkCalled(this.id, "id");
      this.id = id;
      return this;
    }

    @Override
    public Builder keys(Stream<Key> keys) {
      checkCalled(this.keys, "keys");
      this.keys = keys.map(InternalKey::new).collect(Collectors.toList());
      return this;
    }

    @Override
    public Fragment build() {
      // null-id is allowed (will be generated)
      checkSet(keys, "keys");

      return new Fragment(id, keys);
    }
  }
}
