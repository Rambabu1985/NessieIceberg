/*
 * Copyright (C) 2024 Dremio
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
package org.projectnessie.catalog.files.s3;

import org.immutables.value.Value;
import org.projectnessie.nessie.immutables.NessieImmutable;

@SuppressWarnings(
    "DefaultAnnotationParam") // MUST specify 'all Parameters = false' for some reason :shrug_
@NessieImmutable
@Value.Style(allParameters = false)
public interface S3ServerIam extends S3Iam {}
