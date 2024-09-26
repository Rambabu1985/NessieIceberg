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
  alias(libs.plugins.avro)
  id("nessie-conventions-server")
}

extra["maven.name"] = "Nessie - Events - SPI Reference Implementation"

dependencies {
  implementation(project(":nessie-model"))
  implementation(project(":nessie-events-api"))
  implementation(project(":nessie-events-spi"))

  compileOnly(libs.microprofile.openapi)

  implementation(libs.slf4j.api)
  implementation(libs.kafka.clients)

  // Avro serialization examples
  implementation(libs.avro)

  // Jackson serialization examples
  implementation(platform(libs.jackson.bom))
  implementation("com.fasterxml.jackson.core:jackson-databind")
  implementation("com.fasterxml.jackson.core:jackson-annotations")
  implementation("com.fasterxml.jackson.datatype:jackson-datatype-jdk8")

  testCompileOnly(libs.microprofile.openapi)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
  testImplementation(libs.kafka.streams.test.utils)
  testImplementation(libs.logback.classic)
  testImplementation(libs.kafka.avro.serializer)
  testImplementation(libs.kafka.json.schema.serializer)

  testCompileOnly(libs.microprofile.openapi)
  testCompileOnly(libs.immutables.value.annotations)

  intTestImplementation(platform(libs.testcontainers.bom))
  intTestImplementation("org.testcontainers:junit-jupiter")
  intTestImplementation("org.testcontainers:kafka")
  intTestImplementation(project(":nessie-container-spec-helper"))

  intTestCompileOnly(libs.microprofile.openapi)
  intTestCompileOnly(libs.immutables.value.annotations)
}

tasks.withType<Checkstyle> { exclude("com/example/**/generated/**") }
