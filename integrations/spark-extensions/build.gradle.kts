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

import com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar

plugins {
  jacoco
  `maven-publish`
  signing
  scala
  alias(libs.plugins.nessie.run)
  `nessie-conventions`
}

apply<NessieShadowJarPlugin>()

val sparkScala = getSparkScalaVersionsForProject()

dependencies {
  // picks the right dependencies for scala compilation
  forScala(sparkScala.scalaVersion)

  implementation(project(":nessie-spark-extensions-grammar"))
  implementation(project(":nessie-spark-extensions-base_${sparkScala.scalaMajorVersion}"))
  compileOnly("org.apache.spark:spark-sql_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }
  compileOnly("org.apache.spark:spark-core_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }
  compileOnly("org.apache.spark:spark-hive_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }
  implementation(nessieClientForIceberg())

  if (sparkScala.sparkMajorVersion == "3.3") {
    implementation(platform(libs.jackson.bom))
    testFixturesImplementation(platform(libs.jackson.bom))
  }

  testFixturesApi(project(":nessie-spark-extensions-basetests_${sparkScala.scalaMajorVersion}"))

  val versionIceberg = libs.versions.iceberg.get()
  testFixturesImplementation("org.apache.iceberg:iceberg-nessie:$versionIceberg")
  testFixturesImplementation("org.apache.iceberg:iceberg-spark-${sparkScala.sparkMajorVersion}_${sparkScala.scalaMajorVersion}:$versionIceberg")
  testFixturesImplementation("org.apache.iceberg:iceberg-spark-extensions-${sparkScala.sparkMajorVersion}_${sparkScala.scalaMajorVersion}:$versionIceberg")
  testFixturesImplementation("org.apache.iceberg:iceberg-hive-metastore:$versionIceberg")

  testFixturesRuntimeOnly(libs.bundles.logback.logging)
  testFixturesImplementation(libs.slf4j.log4j.over.slf4j)
  testFixturesImplementation("org.apache.spark:spark-sql_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }
  testFixturesImplementation("org.apache.spark:spark-core_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }
  testFixturesImplementation("org.apache.spark:spark-hive_${sparkScala.scalaMajorVersion}") { forSpark(sparkScala.sparkVersion) }

  testFixturesApi(platform(libs.junit.bom))
  testFixturesApi(libs.bundles.junit.testing)

  nessieQuarkusServer(nessieQuarkusServerRunner())
}

nessieQuarkusApp {
  includeTask(tasks.named<Test>("intTest"))
  environmentNonInput.put("HTTP_ACCESS_LOG_LEVEL", testLogLevel())
  jvmArgumentsNonInput.add("-XX:SelfDestructTimer=30")
  systemProperties.put("nessie.server.send-stacktrace-to-client", "true")
}

forceJava11ForTests()

tasks.named<ShadowJar>("shadowJar") {
  dependencies {
    include(dependency("org.projectnessie.nessie:.*:.*"))
    include(dependency("org.projectnessie.nessie-integrations:.*:.*"))
  }
}
