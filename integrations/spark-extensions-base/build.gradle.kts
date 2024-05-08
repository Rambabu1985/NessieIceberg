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
  id("nessie-conventions-spark")
  id("nessie-jacoco")
}

val sparkScala = getSparkScalaVersionsForProject()

dependencies {
  // picks the right dependencies for scala compilation
  forScala(sparkScala.scalaVersion)

  compileOnly(platform(libs.iceberg.bom))
  compileOnly("org.apache.iceberg:iceberg-api")
  compileOnly("org.apache.iceberg:iceberg-core")

  val versionIceberg = libs.versions.iceberg.get()
  compileOnly(
    "org.apache.iceberg:iceberg-spark-${sparkScala.sparkMajorVersion}_${sparkScala.scalaMajorVersion}:$versionIceberg"
  )

  implementation(nessieProject("nessie-cli-grammar"))
  compileOnly("org.apache.spark:spark-hive_${sparkScala.scalaMajorVersion}") {
    forSpark(sparkScala.sparkVersion)
  }
  compileOnly(libs.microprofile.openapi)
  implementation(nessieClientForIceberg())
}
