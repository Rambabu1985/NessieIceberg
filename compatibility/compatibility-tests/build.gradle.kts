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

import org.apache.tools.ant.taskdefs.condition.Os

plugins {
  `java-library`
  jacoco
  `maven-publish`
  signing
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - Backward Compatibility - Tests"

dependencies {
  implementation(platform(libs.junit.bom))
  implementation(libs.bundles.junit.testing)

  implementation(project(":nessie-compatibility-common"))
  implementation(project(":nessie-client"))
  implementation(libs.microprofile.openapi)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.annotations)

  testImplementation(libs.guava)
  testImplementation(project(":nessie-versioned-persist-adapter"))
  testImplementation(project(":nessie-versioned-persist-non-transactional"))
  testImplementation(project(":nessie-versioned-persist-in-memory"))
  testImplementation(project(":nessie-versioned-persist-in-memory-test"))
  testImplementation(project(":nessie-versioned-persist-rocks"))
  testImplementation(project(":nessie-versioned-persist-rocks-test"))
  testImplementation(project(":nessie-versioned-persist-mongodb"))
  testImplementation(project(":nessie-versioned-persist-mongodb-test"))
}

tasks.withType<Test>().configureEach {
  systemProperty("rocksdb.version", libs.versions.rocksdb.get())
  systemProperty("junit.jupiter.extensions.autodetection.enabled", "true")
}

// Compatibility tests fail on macOS with the following message: `libc++abi: terminating
// with uncaught exception of type std::__1::system_error: mutex lock failed: Invalid argument`
if (Os.isFamily(Os.FAMILY_MAC)) {
  tasks.withType<Test>().configureEach { this.enabled = false }
}
