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
  alias(libs.plugins.nessie.run)
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - GC - CLI integration test"

val sparkScala = useSparkScalaVersionsForProject("3.3", "2.12")

dependencies {
  implementation(nessieProject("nessie-gc-tool", "shadow"))

  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  // hadoop-common brings Jackson in ancient versions, pulling in the Jackson BOM to avoid that
  implementation(platform(libs.jackson.bom))
  implementation(libs.hadoop.common) {
    exclude("javax.servlet.jsp", "jsp-api")
    exclude("javax.ws.rs", "javax.ws.rs-api")
    exclude("log4j", "log4j")
    exclude("org.slf4j", "slf4j-log4j12")
    exclude("org.slf4j", "slf4j-reload4j")
    exclude("com.sun.jersey")
    exclude("org.eclipse.jetty")
    exclude("org.apache.hadoop")
    exclude("org.apache.zookeeper")
  }

  implementation(libs.slf4j.api)

  runtimeOnly(libs.h2)

  intTestRuntimeOnly(libs.logback.classic)

  intTestImplementation(
    nessieProject("nessie-spark-extensions-basetests_${sparkScala.scalaMajorVersion}")
  )
  intTestImplementation(nessieProject("nessie-s3minio"))

  intTestImplementation("org.apache.spark:spark-sql_${sparkScala.scalaMajorVersion}") {
    forSpark(sparkScala.sparkVersion)
  }
  intTestImplementation("org.apache.spark:spark-core_${sparkScala.scalaMajorVersion}") {
    forSpark(sparkScala.sparkVersion)
  }
  intTestRuntimeOnly("org.apache.spark:spark-hive_${sparkScala.scalaMajorVersion}") {
    forSpark(sparkScala.sparkVersion)
  }

  intTestImplementation(libs.iceberg.core)
  intTestRuntimeOnly(libs.iceberg.hive.metastore)
  intTestRuntimeOnly(libs.iceberg.aws)
  intTestRuntimeOnly(libs.iceberg.nessie)
  intTestRuntimeOnly(libs.iceberg.core)
  intTestRuntimeOnly(
    "org.apache.iceberg:iceberg-spark-${sparkScala.sparkMajorVersion}_${sparkScala.scalaMajorVersion}:${libs.versions.iceberg.get()}"
  )
  intTestRuntimeOnly(libs.iceberg.hive.metastore)
  intTestRuntimeOnly(libs.iceberg.aws)

  intTestRuntimeOnly(libs.hadoop.client)
  intTestRuntimeOnly(libs.hadoop.aws)
  intTestRuntimeOnly(libs.awssdk.sts)

  intTestRuntimeOnly(platform(libs.awssdk.bom))
  intTestRuntimeOnly(libs.awssdk.s3)
  intTestRuntimeOnly(libs.awssdk.url.connection.client)
  // TODO those are needed, because Spark serializes some configuration stuff (Spark broadcast)
  intTestRuntimeOnly(libs.awssdk.dynamodb)
  intTestRuntimeOnly(libs.awssdk.glue)
  intTestRuntimeOnly(libs.awssdk.kms)

  intTestCompileOnly(libs.jackson.annotations)
  intTestCompileOnly(libs.microprofile.openapi)

  intTestImplementation(platform(libs.junit.bom))
  intTestImplementation(libs.bundles.junit.testing)

  nessieQuarkusServer(nessieQuarkusServerRunner())
}

val intTest = tasks.named<Test>("intTest")

intTest { systemProperty("aws.region", "us-east-1") }

nessieQuarkusApp {
  includeTask(intTest)
  environmentNonInput.put("HTTP_ACCESS_LOG_LEVEL", testLogLevel())
  jvmArgumentsNonInput.add("-XX:SelfDestructTimer=30")
  systemProperties.put("nessie.server.send-stacktrace-to-client", "true")
}

forceJava11ForTests()
