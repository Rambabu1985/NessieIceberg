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
  `maven-publish`
  signing
  `nessie-conventions`
}

extra["maven.name"] = "Nessie - GC - S3 mock"

description = "Rudimentary S3 endpoint delegating to functions to serve content."

dependencies {

  // javax/jakarta
  compileOnly(libs.jakarta.ws.rs.api)
  compileOnly(libs.javax.ws.rs21)
  compileOnly(libs.javax.ws.rs)
  compileOnly(libs.jakarta.validation.api)
  compileOnly(libs.javax.validation.api)
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)
  compileOnly(libs.jakarta.inject.api)

  compileOnly(libs.microprofile.openapi)

  implementation(platform(libs.jersey.bom))
  implementation("org.glassfish.jersey.core:jersey-server")
  implementation("org.glassfish.jersey.containers:jersey-container-servlet")
  implementation("org.glassfish.jersey.containers:jersey-container-jetty-http")
  implementation("org.glassfish.jersey.inject:jersey-hk2")
  implementation("org.glassfish.jersey.media:jersey-media-json-jackson")

  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  implementation(libs.guava)

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.annotations)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.dataformat.xml)
  implementation(libs.jackson.jaxrs.json.provider)
  implementation(libs.jackson.jaxrs.xml.provider)

  compileOnly(libs.avro)

  implementation(libs.slf4j.api)

  testRuntimeOnly(libs.logback.classic)

  testImplementation(platform(libs.awssdk.bom))
  testImplementation(libs.awssdk.s3)
  testImplementation(libs.awssdk.url.connection.client)

  testCompileOnly(libs.immutables.value.annotations)
  testAnnotationProcessor(libs.immutables.value.processor)

  testCompileOnly(libs.microprofile.openapi)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
}

tasks.withType(Test::class.java).configureEach { systemProperty("aws.region", "us-east-1") }
