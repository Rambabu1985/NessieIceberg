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

import com.google.protobuf.gradle.GenerateProtoTask
import com.google.protobuf.gradle.ProtobufExtension

plugins {
  alias(libs.plugins.nessie.reflectionconfig)
  id("nessie-conventions-server")
  alias(libs.plugins.protobuf)
}

extra["maven.name"] = "Nessie - Export/Import - Serialization (Proto)"

dependencies { api(project(path = ":nessie-protobuf-relocated", configuration = "shadow")) }

extensions.configure<ProtobufExtension> {
  // Configure the protoc executable
  protoc {
    // Download from repositories
    artifact = "com.google.protobuf:protoc:${libs.versions.protobuf.get()}"
  }
}

tasks.named<GenerateProtoTask>("generateProto") {
  doLast(
    ReplaceInFiles(
      fileTree(project.buildDir.resolve("generated/source/proto/main")),
      mapOf("com.google.protobuf" to "org.projectnessie.nessie.relocated.protobuf")
    )
  )
}

reflectionConfig {
  // Consider classes that extend one of these classes...
  classExtendsPatterns.set(
    listOf(
      "org.projectnessie.nessie.relocated.protobuf.GeneratedMessageV3",
      "org.projectnessie.nessie.relocated.protobuf.GeneratedMessageV3.Builder"
    )
  )
  // ... and classes the implement this interface.
  classImplementsPatterns.set(
    listOf("org.projectnessie.nessie.relocated.protobuf.ProtocolMessageEnum")
  )
  // Also include generated classes (e.g. google.protobuf.Empty) via the "runtimeClasspath",
  // which contains the the "com.google.protobuf:protobuf-java" dependency.
  includeConfigurations.set(listOf("runtimeClasspath"))
}

// The protobuf-plugin should ideally do this
tasks.named<Jar>("sourcesJar") {
  dependsOn(tasks.named("generateProto"), tasks.named("generateReflectionConfig"))
}

tasks.withType(com.google.protobuf.gradle.ProtobufExtract::class).configureEach {
  when (name) {
    "extractIncludeTestProto" -> dependsOn(tasks.named("processJandexIndex"))
    "extractIncludeTestFixturesProto" -> dependsOn(tasks.named("processJandexIndex"))
  }
}
