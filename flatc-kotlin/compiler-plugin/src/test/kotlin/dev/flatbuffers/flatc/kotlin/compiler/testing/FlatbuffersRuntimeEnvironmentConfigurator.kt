package dev.flatbuffers.flatc.kotlin.compiler.testing

import java.io.File
import org.jetbrains.kotlin.cli.jvm.config.addJvmClasspathRoot
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.RuntimeClasspathProvider
import org.jetbrains.kotlin.test.services.TestServices

private val flatbuffersRuntimeClasspath: List<File> =
  System.getProperty("flatbuffers.compilerPlugin.runtimeClasspath")
    ?.takeIf { it.isNotBlank() }
    ?.split(File.pathSeparatorChar)
    ?.filter { it.isNotBlank() }
    ?.map(::File)
    ?.onEach { file ->
      require(file.exists()) {
        "Runtime classpath entry does not exist: $file"
      }
    }
    ?: error("Unable to get a valid classpath from 'flatbuffers.compilerPlugin.runtimeClasspath' property")

class FlatbuffersRuntimeEnvironmentConfigurator(testServices: TestServices) :
  EnvironmentConfigurator(testServices) {
  override fun configureCompilerConfiguration(
    configuration: CompilerConfiguration,
    module: TestModule,
  ) {
    for (file in flatbuffersRuntimeClasspath) {
      configuration.addJvmClasspathRoot(file)
    }
  }
}

class FlatbuffersRuntimeClassPathProvider(testServices: TestServices) :
  RuntimeClasspathProvider(testServices) {
  override fun runtimeClassPaths(module: TestModule): List<File> {
    return flatbuffersRuntimeClasspath
  }
}
