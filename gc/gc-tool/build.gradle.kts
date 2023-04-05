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
  `java-library`
  jacoco
  `maven-publish`
  signing
  id("com.github.johnrengelman.shadow")
  `nessie-conventions`
}

apply<NessieShadowJarPlugin>()

extra["maven.name"] = "Nessie - GC - Standalone command line tool"

dependencies {
  implementation(nessieProject("nessie-client"))
  implementation(nessieProject("nessie-gc-base"))
  implementation(nessieProject("nessie-gc-iceberg"))
  implementation(nessieProject("nessie-gc-iceberg-files"))
  implementation(nessieProject("nessie-gc-repository-jdbc"))

  compileOnly(libs.errorprone.annotations)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  implementation(libs.iceberg.core)
  runtimeOnly(libs.iceberg.hive.metastore)
  runtimeOnly(libs.iceberg.aws)

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
  // Bump the jabx-impl version 2.2.3-1 via hadoop-common to make it work with Java 17+
  implementation(libs.jaxb.impl)

  implementation(platform(libs.awssdk.bom))
  runtimeOnly(libs.awssdk.s3)
  runtimeOnly(libs.awssdk.url.connection.client)

  implementation(libs.picocli)
  annotationProcessor(libs.picocli.codegen)

  implementation(libs.slf4j.api)
  runtimeOnly(libs.logback.classic)

  compileOnly(libs.microprofile.openapi)
  compileOnly(libs.jackson.annotations)

  // javax/jakarta
  compileOnly(libs.jakarta.validation.api)
  compileOnly(libs.javax.validation.api)
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)

  runtimeOnly(libs.h2)
  runtimeOnly(libs.postgresql)

  testCompileOnly(platform(libs.jackson.bom))

  testImplementation(nessieProject("nessie-jaxrs-testextension"))
  testImplementation(nessieProject("nessie-versioned-persist-in-memory"))
  testImplementation(nessieProject("nessie-versioned-persist-in-memory-test"))

  testRuntimeOnly(libs.logback.classic)

  testCompileOnly(libs.immutables.value.annotations)
  testAnnotationProcessor(libs.immutables.value.processor)

  testCompileOnly(libs.jackson.annotations)
  testCompileOnly(libs.microprofile.openapi)

  testImplementation(platform(libs.junit.bom))
  testImplementation(libs.bundles.junit.testing)
}

tasks.named<Test>("test") { systemProperty("expectedNessieVersion", project.version) }

val mainClassName = "org.projectnessie.gc.tool.cli.CLI"

val generateAutoComplete by
  tasks.creating(JavaExec::class.java) {
    group = "build"
    description = "Generates the bash/zsh autocompletion scripts"

    val compileJava = tasks.named<JavaCompile>("compileJava")

    dependsOn(compileJava)

    val completionScriptsDir = project.buildDir.resolve("classes/java/main/META-INF/completion")

    doFirst { completionScriptsDir.mkdirs() }

    mainClass.set("picocli.AutoComplete")
    classpath(configurations.named("runtimeClasspath"), compileJava)
    args(
      "--force",
      "-o",
      completionScriptsDir.resolve("nessie-gc-completion").toString(),
      mainClassName
    )

    inputs.files("src/main").withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.dir(completionScriptsDir)
  }

// generateAutoComplete writes the bash/zsh completion script into the main resource output,
// which is a bit ugly, but works. But the following tasks need a dependency to that task so that
// Gradle can properly evaluate the dependencies.
listOf("compileTestJava", "jandexMain", "jar", "shadowJar").forEach { t ->
  tasks.named(t) { dependsOn(generateAutoComplete) }
}

val shadowJar = tasks.named<ShadowJar>("shadowJar")

val unixExecutable by
  tasks.registering {
    group = "build"
    description = "Generates the Unix executable"

    dependsOn(shadowJar)
    val dir = buildDir.resolve("executable")
    val executable = dir.resolve("nessie-gc")
    inputs.files(shadowJar.get().archiveFile).withPathSensitivity(PathSensitivity.RELATIVE)
    outputs.files(executable)
    outputs.cacheIf { false } // very big file
    doFirst {
      dir.mkdirs()
      executable.outputStream().use { out ->
        projectDir.resolve("src/exec/exec-preamble.sh").inputStream().use { i -> i.transferTo(out) }
        shadowJar.get().archiveFile.get().asFile.inputStream().use { i -> i.transferTo(out) }
      }
      executable.setExecutable(true)
    }
  }

shadowJar {
  manifest { attributes["Main-Class"] = mainClassName }
  finalizedBy(unixExecutable)
}
