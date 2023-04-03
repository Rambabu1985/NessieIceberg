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
  alias(libs.plugins.annotations.stripper)
}

extra["maven.name"] = "Nessie - Client"

dependencies {
  api(project(":nessie-model"))

  implementation(platform(libs.jackson.bom))
  implementation(libs.jackson.core)
  implementation(libs.jackson.databind)
  implementation(libs.jackson.annotations)
  implementation(libs.microprofile.openapi)

  // javax/jakarta
  compileOnly(libs.jakarta.annotation.api)
  compileOnly(libs.findbugs.jsr305)
  compileOnly(libs.jakarta.validation.api)
  compileOnly(libs.javax.validation.api)
  compileOnly(libs.jakarta.ws.rs.api)
  compileOnly(libs.javax.ws.rs)

  implementation(libs.slf4j.api)
  compileOnly(libs.errorprone.annotations)

  compileOnly(libs.immutables.builder)
  compileOnly(libs.immutables.value.annotations)
  annotationProcessor(libs.immutables.value.processor)

  testFixturesApi(libs.guava)
  testFixturesApi(libs.bouncycastle.bcprov)
  testFixturesApi(libs.bouncycastle.bcpkix)
  testFixturesApi(libs.mockito.core)

  testFixturesApi(platform(libs.junit.bom))
  testFixturesApi(libs.bundles.junit.testing)

  compileOnly(platform(libs.opentelemetry.bom))
  compileOnly(libs.opentelemetry.api)
  compileOnly(libs.opentelemetry.semconv)

  compileOnly(platform(libs.awssdk.bom))
  compileOnly(libs.awssdk.auth)

  // javax/jakarta
  testFixturesApi(libs.jakarta.annotation.api)

  testFixturesApi(platform(libs.opentelemetry.bom))
  testFixturesApi(libs.opentelemetry.api)
  testFixturesApi(libs.opentelemetry.sdk)
  testFixturesApi(libs.opentelemetry.semconv)
  testFixturesApi(libs.opentelemetry.exporter.otlp)
  testFixturesApi(platform(libs.awssdk.bom))
  testFixturesApi(libs.awssdk.auth)
  testFixturesApi(libs.undertow.core)
  testFixturesApi(libs.undertow.servlet)
  testFixturesImplementation(libs.bundles.logback.logging)
}

jandex { skipDefaultProcessing() }

val jacksonTestVersions =
  setOf(
    "2.10.0", // Spark 3.1.2+3.1.3
    "2.11.4", // Spark 3.?.? (reason unknown)
    "2.12.3", // Spark 3.2.1+3.2.2
    "2.13.3" // Spark 3.3.0
  )

@Suppress("UnstableApiUsage")
fun JvmComponentDependencies.forJacksonVersion(jacksonVersion: String) {
  implementation(project())

  implementation("com.fasterxml.jackson.core:jackson-core:$jacksonVersion")
  implementation("com.fasterxml.jackson.core:jackson-annotations:$jacksonVersion")
  implementation("com.fasterxml.jackson.core:jackson-databind:$jacksonVersion")
}

@Suppress("UnstableApiUsage")
fun JvmTestSuite.commonCompatSuite() {
  useJUnitJupiter(libsRequiredVersion("junit"))

  sources { java.srcDirs(sourceSets.getByName("test").java.srcDirs) }

  targets { all { tasks.named("check") { dependsOn(testTask) } } }
}

@Suppress("UnstableApiUsage")
fun JvmTestSuite.useJava8() {
  targets {
    all {
      testTask.configure {
        val javaToolchains = project.extensions.findByType(JavaToolchainService::class.java)
        javaLauncher.set(
          javaToolchains!!.launcherFor { languageVersion.set(JavaLanguageVersion.of(8)) }
        )
      }
    }
  }
}

@Suppress("UnstableApiUsage")
testing {
  suites {
    register("testJava8", JvmTestSuite::class.java) {
      commonCompatSuite()

      dependencies { forJacksonVersion(libs.jackson.bom.get().version!!) }

      useJava8()
    }

    configurations.named("testJava8Implementation") {
      extendsFrom(configurations.getByName("testImplementation"))
    }

    jacksonTestVersions.forEach { jacksonVersion ->
      val safeName = jacksonVersion.replace("[.]".toRegex(), "_")
      register("testJackson_$safeName", JvmTestSuite::class.java) {
        commonCompatSuite()

        dependencies { forJacksonVersion(jacksonVersion) }
      }

      configurations.named("testJackson_${safeName}Implementation") {
        extendsFrom(configurations.getByName("testImplementation"))
      }

      register("testJackson_${safeName}_java8", JvmTestSuite::class.java) {
        commonCompatSuite()

        dependencies { forJacksonVersion(jacksonVersion) }

        useJava8()

        configurations.named("testJackson_${safeName}_java8Implementation") {
          extendsFrom(configurations.getByName("testImplementation"))
        }
      }
    }
  }
}

annotationStripper {
  registerDefault().configure {
    annotationsToDrop("^jakarta[.].+".toRegex())
    unmodifiedClassesForJavaVersion.set(11)
  }
}
