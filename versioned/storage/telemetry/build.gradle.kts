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

plugins {
  `java-library`
  jacoco
  `maven-publish`
  signing
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - Storage - Telemetry"

description = "Telemetry support for OpenTelemetry and OpenTracing (deprecated)."

dependencies {
  implementation(project(":nessie-versioned-storage-common"))

  // javax/jakarta
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)

  implementation(libs.guava)
  implementation(platform(libs.opentelemetry.bom))
  implementation(libs.opentelemetry.api)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testRuntimeOnly(libs.junit.jupiter.engine)
}
