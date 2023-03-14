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
package org.projectnessie.model.types;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.ServiceLoader;
import org.projectnessie.model.Content;

/**
 * Provides the registry for all {@link Content content types}.
 *
 * <p>Content types are loaded via {@link ContentTypeBundle}s using Java's {@link ServiceLoader
 * service loader} mechanism.
 */
public final class ContentTypes {

  /**
   * An implementation of this interface is passed to {@link
   * org.projectnessie.model.types.ContentTypeBundle}s.
   */
  public interface Registrar {
    void register(String name, Class<? extends Content> type);
  }

  /** Retrieve an array of all registered content types. */
  public static Content.Type[] all() {
    return Registry.all();
  }

  public static Content.Type forName(String name) {
    return Registry.forName(name);
  }

  /**
   * Internal class providing the actual registry. This is a separate class to implicitly use lazy
   * initialization.
   */
  private static final class Registry {

    private static final Content.Type[] all;
    private static final Map<String, Content.Type> byName;

    static {
      List<Content.Type> list = new ArrayList<>();
      Map<String, Content.Type> names = new HashMap<>();

      // Add the "DEFAULT" type.
      Content.Type unknownContentType = new DefaultContentTypeImpl();
      list.add(unknownContentType);
      names.put(unknownContentType.name(), unknownContentType);

      for (ContentTypeBundle bundle : ServiceLoader.load(ContentTypeBundle.class)) {
        bundle.register(
            (name, type) -> {
              if (name == null
                  || name.trim().isEmpty()
                  || !name.trim().equals(name)
                  || type == null) {
                throw new IllegalArgumentException(
                    String.format(
                        "Illegal content-type registration: name=%s, type=%s", name, type));
              }
              Content.Type contentType = new ContentTypeImpl(name, type);
              Content.Type ex = names.get(name);
              if (ex != null) {
                throw new IllegalStateException(
                    String.format(
                        "Duplicate content type registration for %s/%s, existing: %s/%s",
                        name, type, ex.name(), ex.type()));
              }
              list.add(contentType);
              names.put(name, contentType);
            });
      }

      byName = Collections.unmodifiableMap(names);
      all = list.toArray(new Content.Type[0]);
    }

    private static Content.Type[] all() {
      return all.clone();
    }

    private static Content.Type forName(String name) {
      return Objects.requireNonNull(
          byName.get(name), "No content type registered for name " + name);
    }
  }

  /** Internally used wrapper for a {@link Content.Type}. */
  private static final class ContentTypeImpl implements Content.Type {

    private final String name;

    private final Class<? extends Content> type;

    private ContentTypeImpl(String name, Class<? extends Content> type) {
      this.name = name;
      this.type = type;
    }

    @Override
    public String name() {
      return name;
    }

    @Override
    public Class<? extends Content> type() {
      return type;
    }

    @Override
    public String toString() {
      return name;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof ContentTypeImpl)) {
        return false;
      }
      ContentTypeImpl that = (ContentTypeImpl) o;
      return name.equals(that.name);
    }

    @Override
    public int hashCode() {
      return name.hashCode();
    }
  }

  /** Internally used wrapper for the {@code UNKNOWN} content type. */
  private static final class DefaultContentTypeImpl implements Content.Type {
    @Override
    public int hashCode() {
      return 0;
    }

    @Override
    public boolean equals(Object obj) {
      return obj == this;
    }

    @Override
    public String toString() {
      return name();
    }

    @Override
    public String name() {
      return "UNKNOWN";
    }

    @Override
    public Class<? extends Content> type() {
      throw new IllegalStateException("UNKNOWN Content.Type has no type");
    }
  }
}
