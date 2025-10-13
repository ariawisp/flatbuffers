import java.io.File
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  base
  id("org.jetbrains.kotlin.jvm") version "2.2.20" apply false
}

val kotlinVersion = "2.2.20"
val intellijVersion = "241.19416.19"

subprojects {
  apply(plugin = "org.jetbrains.kotlin.jvm")

  repositories {
    mavenCentral()
    maven("https://cache-redirector.jetbrains.com/intellij-repository/releases")
    maven("https://cache-redirector.jetbrains.com/intellij-repository/snapshots")
    maven("https://cache-redirector.jetbrains.com/intellij-dependencies")
  }

  extensions.configure<KotlinJvmProjectExtension> {
    jvmToolchain(21)
  }

  dependencies {
    add("testImplementation", "org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
  }

  tasks.withType<Test>().configureEach {
    useJUnitPlatform()
  }
}

project(":core-parser") {
  dependencies {
    add("implementation", project(":core-ast"))
  }
}

project(":core-semantics") {
  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-parser"))
    add("testImplementation", project(":core-parser"))
  }
}

project(":reflection-writer") {
  dependencies {
    add("implementation", project(":core-semantics"))
  }
}

project(":generator-common") {
  dependencies {
    add("implementation", project(":core-semantics"))
  }
}

project(":generator-kotlin") {
  dependencies {
    add("implementation", project(":generator-common"))
  }
}

project(":generator-java") {
  dependencies {
    add("implementation", project(":generator-common"))
  }
}

project(":cli") {
  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-parser"))
    add("implementation", project(":core-semantics"))
    add("implementation", project(":reflection-writer"))
    add("implementation", project(":generator-kotlin"))
    add("implementation", project(":generator-java"))
  }
}

project(":compiler-plugin") {
  val flatbuffersCompilerPluginRuntime: Configuration by configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
    isTransitive = false
  }

  dependencies {
    add("implementation", project(":core-ast"))
    add("implementation", project(":core-semantics"))
    add("implementation", project(":compat"))
    add("implementation", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    add("testImplementation", kotlin("test-junit5"))
    add("testImplementation", "org.junit.jupiter:junit-jupiter-api:5.10.2")
    add("testRuntimeOnly", "org.junit.jupiter:junit-jupiter-engine:5.10.2")
    add("testRuntimeOnly", "org.junit.platform:junit-platform-launcher:1.10.2")
    add("testImplementation", "org.jetbrains.kotlin:kotlin-compiler-internal-test-framework:$kotlinVersion")
    add("testImplementation", "org.jetbrains.kotlin:kotlin-compiler:$kotlinVersion")
    add("testImplementation", project(":flatbuffers-runtime"))
    add("testRuntimeOnly", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    add("testRuntimeOnly", "org.jetbrains.kotlin:kotlin-test:$kotlinVersion")
    add("testRuntimeOnly", "org.jetbrains.kotlin:kotlin-script-runtime:$kotlinVersion")
    add("testRuntimeOnly", "org.jetbrains.kotlin:kotlin-annotations-jvm:$kotlinVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-rt:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-class-loader:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-base:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-xml-dom:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:core:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:core-impl:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:extensions:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:diagnostic:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:diagnostic-telemetry:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-progress:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.platform:util-coroutines:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.java:java-frontback-psi:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.java:java-frontback-psi-impl:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.java:java-psi:$intellijVersion")
    add("testRuntimeOnly", "com.jetbrains.intellij.java:java-psi-impl:$intellijVersion")
    flatbuffersCompilerPluginRuntime(project(":compat"))
  }

  val pluginJarProvider = tasks.named<Jar>("jar").flatMap { it.archiveFile }

  fun Test.setLibraryProperty(propName: String, jarName: String) {
    val runtimeFiles =
      this@project.configurations.named("testRuntimeClasspath").get().files
    val jar =
      runtimeFiles.firstOrNull { Regex("$jarName-\\d.*\\.jar").matches(it.name) }
        ?: return
    systemProperty(propName, jar.absolutePath)
  }

  tasks.withType<Test>().configureEach {
    dependsOn(tasks.named("jar"))

    val dataRoot = layout.projectDirectory.dir("src/test/data").asFile.absolutePath

    val ideaHomeCandidate = rootDir.toPath().parent?.parent?.resolve("intellij-community")?.toFile()

    doFirst {
      val pluginJar = pluginJarProvider.get().asFile
      val runtimeFiles = flatbuffersCompilerPluginRuntime.files
      val runtimeEntries = runtimeFiles.joinToString(File.pathSeparator) { it.absolutePath }
      val pluginClasspath =
        (listOf(pluginJar.absolutePath) + runtimeFiles.map { it.absolutePath })
          .joinToString(File.pathSeparator)

      systemProperty("flatbuffers.compilerPlugin.jar", pluginJar.absolutePath)
      systemProperty("flatbuffers.compilerPlugin.runtimeClasspath", runtimeEntries)
      systemProperty("flatbuffers.compilerPlugin.classpath", pluginClasspath)
      systemProperty("flatbuffers.tests.dataRoot", dataRoot)
    }

    workingDir = project.projectDir

    systemProperty("idea.ignore.disabled.plugins", "true")
    val ideaHomePath = when {
      ideaHomeCandidate?.exists() == true -> ideaHomeCandidate.absolutePath
      else -> rootDir.absolutePath
    }
    systemProperty("idea.home.path", ideaHomePath)

    setLibraryProperty("kotlin.minimal.stdlib.path", "kotlin-stdlib")
    setLibraryProperty("kotlin.full.stdlib.path", "kotlin-stdlib-jdk8")
    setLibraryProperty("kotlin.reflect.jar.path", "kotlin-reflect")
    setLibraryProperty("kotlin.test.jar.path", "kotlin-test")
    setLibraryProperty("kotlin.script.runtime.path", "kotlin-script-runtime")
    setLibraryProperty("kotlin.annotations.path", "kotlin-annotations-jvm")
  }
}

project(":compat") {
  dependencies {
    add("implementation", "org.jetbrains.kotlin:kotlin-reflect:$kotlinVersion")
    add("implementation", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")
  }
}

tasks.register("cliRun") {
  description = "Delegates to :cli:run"
  group = "application"
  dependsOn(":cli:run")
}
