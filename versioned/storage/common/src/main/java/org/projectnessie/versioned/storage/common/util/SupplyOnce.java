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
package org.projectnessie.versioned.storage.common.util;

import java.util.function.Supplier;

/**
 * This is an <em>internal</em> utility class, which provides an unsynchronized, not thread-safe
 * {@link Supplier} that memoizes returned value but also a thrown exception.
 */
public final class SupplyOnce {

  private SupplyOnce() {}

  public static <T> Supplier<T> memoize(Supplier<T> loader) {
    return new NonLockingSupplyOnce<>(loader);
  }

  private static final class NonLockingSupplyOnce<T> implements Supplier<T> {
    private final Supplier<T> loader;
    private Object result;
    private boolean loaded;

    private NonLockingSupplyOnce(Supplier<T> loader) {
      this.loader = loader;
    }

    @Override
    public T get() {
      if (loaded) {
        return eval(result);
      }

      try {
        T obj = loader.get();
        result = obj;
        return obj;
      } catch (RuntimeException re) {
        result = re;
        throw re;
      } finally {
        loaded = true;
      }
    }

    @SuppressWarnings("unchecked")
    private T eval(Object i) {
      if (i instanceof RuntimeException) {
        throw (RuntimeException) i;
      }
      return (T) i;
    }
  }
}
