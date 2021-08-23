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
package org.projectnessie.perftest.gatling

/** All Nessie performance tests start from the `NessieDsl.nessie`
  * [[NessieProtocolBuilder]].
  */
trait NessieDsl {
  val nessie: NessieProtocolBuilder.type = NessieProtocolBuilder

  /** Start building a new action against Nessie.
    *
    * @param tag
    *   tag/name of the action
    * @return
    *   action builder
    */
  def nessie(tag: String): NessieActionBuilder = NessieActionBuilder(tag)
}
