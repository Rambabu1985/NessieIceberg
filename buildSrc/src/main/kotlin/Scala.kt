/*
 * Copyright (C) 2023 Dremio
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.scala.ScalaPlugin
import org.gradle.api.publish.PublishingExtension
import org.gradle.api.publish.maven.MavenPublication
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.scala.ScalaCompile
import org.gradle.api.tasks.scala.ScalaDoc
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByName
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType
import org.gradle.language.scala.tasks.KeepAliveMode

class NessieScalaPlugin : Plugin<Project> {
  override fun apply(project: Project): Unit =
    project.run {
      plugins.withType<ScalaPlugin>().configureEach {
        tasks.withType<ScalaCompile>().configureEach {
          scalaCompileOptions.keepAliveMode.set(KeepAliveMode.DAEMON)
          scalaCompileOptions.encoding = "UTF-8"
        }

        val scaladoc = tasks.named<ScalaDoc>("scaladoc")

        if (extensions.findByName("jandex") != null) {
          scaladoc.configure { dependsOn(tasks.named("processJandexIndex")) }
        }

        val scaladocJar =
          tasks.register<Jar>("scaladocJar") {
            dependsOn(scaladoc)
            val baseJar = tasks.getByName<Jar>("jar")
            from(scaladoc.get().destinationDir)
            destinationDirectory.set(baseJar.destinationDirectory)
            archiveClassifier.set("scaladoc")
          }

        tasks.named("assemble") { dependsOn(scaladocJar) }

        configure<PublishingExtension> {
          publications {
            withType(MavenPublication::class.java) {
              if (name == "maven") {
                artifact(scaladocJar)
              }
            }
          }
        }
      }
    }
}
