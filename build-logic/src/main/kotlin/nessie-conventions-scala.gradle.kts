/*
 * Copyright (C) 2023 Dremio
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

// Conventions for "Nessie internal" Scala projects, not Spark, not client facing.

plugins {
  id("nessie-common-java")
  scala
  id("nessie-scala")
}

tasks.withType<JavaCompile>().configureEach { options.release = 21 }

tasks.withType<ScalaCompile>().configureEach {
  options.release = 21
  scalaCompileOptions.additionalParameters.add("-release:21")
  sourceCompatibility = "21"
  targetCompatibility = "21"
}
