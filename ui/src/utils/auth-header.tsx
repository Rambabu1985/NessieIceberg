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
import {authenticationService} from '../services';
import {nessieVersion} from "./version-numbers";

export function nessieRequestHeaders() : Record<string, string> {
  // return authorization header with jwt token
  const currentUser = authenticationService.currentUserValue;
  // TODO how can we get the current Nessie version here??
  if (currentUser && currentUser.token) {
    return {Authorization: `Bearer ${currentUser.token}`, "Nessie-Version": nessieVersion};
  } else {
    return {"Nessie-Version": nessieVersion};
  }
}
