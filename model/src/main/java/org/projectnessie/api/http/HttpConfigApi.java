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
package org.projectnessie.api.http;

import javax.ws.rs.Path;
import org.projectnessie.api.ConfigApi;

/**
 * The purpose of this class is to only put the {@link Path} annotation onto it as otherwise other
 * (non-http) implementors of {@link ConfigApi} could be used by CDI when a request comes in.
 */
@Path("config")
public interface HttpConfigApi extends ConfigApi {}
