package dev.flatbuffers.flatc.kotlin.compiler.testing

import java.io.File
import java.io.File.pathSeparator
import java.io.File.separator
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

/**
 * Resolves Kotlin standard library jars from the current test classpath. This mirrors the helper
 * used in Metro so we can avoid depending on the distribution layout that the internal test framework
 * expects by default.
 */
object ClasspathBasedStandardLibrariesPathProvider : KotlinStandardLibrariesPathProvider() {
  private val sepPattern = "\\$separator"

  private val gradleDependency =
    (".*?" +
        sepPattern +
        "(?<name>[^$sepPattern]*)" +
        sepPattern +
        "(?<version>[^$sepPattern]*)" +
        sepPattern +
        "[^$sepPattern]*" +
        sepPattern +
        "\\1-\\2\\.jar")
      .toRegex()

  private val jarsByName: Map<String, File> =
    System.getProperty("java.class.path")
      .split("\\$pathSeparator".toRegex())
      .dropLastWhile(String::isEmpty)
      .map(::File)
      .associateBy { file ->
        gradleDependency.matchEntire(file.path)?.groups?.get("name")?.value ?: file.name
      }

  private fun jar(name: String): File {
    return jarsByName[name]
      ?: error("Jar $name not found on classpath. Available entries:\n${jarsByName.keys.sorted().joinToString("\n")}")
  }

  override fun runtimeJarForTests(): File = jar("kotlin-stdlib")

  override fun runtimeJarForTestsWithJdk8(): File = jar("kotlin-stdlib-jdk8")

  override fun minimalRuntimeJarForTests(): File = jar("kotlin-stdlib")

  override fun reflectJarForTests(): File = jar("kotlin-reflect")

  override fun kotlinTestJarForTests(): File = jar("kotlin-test")

  override fun scriptRuntimeJarForTests(): File = jar("kotlin-script-runtime")

  override fun jvmAnnotationsForTests(): File = jar("kotlin-annotations-jvm")

  override fun getAnnotationsJar(): File = jar("kotlin-annotations-jvm")

  override fun fullJsStdlib(): File = jar("kotlin-stdlib-js")

  override fun defaultJsStdlib(): File = jar("kotlin-stdlib-js")

  override fun kotlinTestJsKLib(): File = jar("kotlin-test-js")

  override fun scriptingPluginFilesForTests(): Collection<File> = emptyList()

  override fun commonStdlibForTests(): File = jar("kotlin-stdlib-common")
}
