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

/**
 * Used to provide custom {@link org.projectnessie.model.Content} implementations via the Java
 * {@link java.util.ServiceLoader service loader} mechanism.
 *
 * <p><em>The functionality to actually use custom types is incomplete as long as ther is no
 * store-worker support for custom content. </em>
 */
public interface ContentTypeBundle {
  void register(ContentTypes.Registrar registrar);
}
