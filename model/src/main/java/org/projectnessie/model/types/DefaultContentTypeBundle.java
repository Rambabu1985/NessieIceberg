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

import org.projectnessie.model.DeltaLakeTable;
import org.projectnessie.model.IcebergTable;
import org.projectnessie.model.IcebergView;
import org.projectnessie.model.Namespace;
import org.projectnessie.model.types.ContentTypes.Register;

/**
 * Provides the {@link org.projectnessie.model.Content.Type content types} for Iceberg table + view,
 * Delta Lake table and namespaces.
 */
public final class DefaultContentTypeBundle implements ContentTypeBundle {

  @Override
  public void register(Register register) {
    register.register("ICEBERG_TABLE", (byte) 1, IcebergTable.class);
    register.register("DELTA_LAKE_TABLE", (byte) 2, DeltaLakeTable.class);
    register.register("ICEBERG_VIEW", (byte) 3, IcebergView.class);
    register.register("NAMESPACE", (byte) 4, Namespace.class);
  }
}
