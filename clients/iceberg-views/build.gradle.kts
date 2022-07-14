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
  `nessie-conventions`
}

dependencies {
  implementation(platform(nessieRootProject()))
  implementation(nessieProject("nessie-client"))
  implementation(nessieProject("nessie-model"))
  implementation("org.apache.iceberg:iceberg-api")
  implementation("org.apache.iceberg:iceberg-core")
  implementation("org.apache.iceberg:iceberg-common")
  implementation(
    "org.apache.iceberg",
    "iceberg-bundled-guava",
    configuration = if (isIntegrationsTestingEnabled()) "shadow" else null
  )
  implementation("org.apache.iceberg:iceberg-nessie") { exclude("org.projectnessie", "*") }
  implementation("org.apache.hadoop:hadoop-client") {
    exclude("javax.ws.rs", "javax.ws.rs-api")
    exclude("log4j", "log4j")
    exclude("org.slf4j", "slf4j-log4j12")
    exclude("org.slf4j", "slf4j-reload4j")
    exclude("com.sun.jersey", "jersey-servlet")
  }
  compileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  testImplementation(platform(nessieRootProject()))
  testImplementation(nessieProject("nessie-versioned-persist-tests"))
  testImplementation(nessieProject("nessie-versioned-persist-in-memory"))
  testImplementation(nessieProject("nessie-jaxrs-testextension"))
  testImplementation("org.slf4j:log4j-over-slf4j")
  testCompileOnly("org.eclipse.microprofile.openapi:microprofile-openapi-api")

  testImplementation("org.assertj:assertj-core")
  testImplementation(platform("org.junit:junit-bom"))
  testImplementation("org.junit.jupiter:junit-jupiter-api")
  testImplementation("org.junit.jupiter:junit-jupiter-params")
  testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine")
}
