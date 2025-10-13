package dev.flatbuffers.flatc.kotlin.compiler.testing

import dev.flatbuffers.flatc.kotlin.compiler.FlatbuffersCompilerPluginRegistrar
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.options.addBfbsPath
import dev.flatbuffers.flatc.kotlin.compiler.options.addIncludePath
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import dev.flatbuffers.flatc.kotlin.compiler.options.setEnabled
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.directives.model.StringDirective
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configureFlatbuffersPlugin() {
  useDirectives(FlatbuffersDirectives)
  useConfigurators(::FlatbuffersPluginEnvironmentConfigurator, ::FlatbuffersRuntimeEnvironmentConfigurator)
  useCustomRuntimeClasspathProviders(::FlatbuffersRuntimeClassPathProvider)
}

private fun TestModule.collectDirectiveValues(directive: StringDirective): Sequence<String> {
  return sequence {
    yieldAll(directives[directive])
    files.forEach { file ->
      yieldAll(file.directives[directive])
    }
  }
}

private class FlatbuffersPluginEnvironmentConfigurator(
  testServices: TestServices,
) : EnvironmentConfigurator(testServices) {

  private val dataRoot: Path =
    System.getProperty("flatbuffers.tests.dataRoot")
      ?.let { Paths.get(it) }
      ?: error("flatbuffers.tests.dataRoot system property not configured")

  override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
    configuration.setEnabled(true)

    val schemaPaths = resolvePaths(module, FlatbuffersDirectives.FLATBUFFERS_SCHEMA)
    require(schemaPaths.isNotEmpty()) {
      "No FLATBUFFERS_SCHEMA directives found for module ${module.name}"
    }
    schemaPaths.forEach { configuration.addSchemaPath(it.toString()) }

    resolvePaths(module, FlatbuffersDirectives.FLATBUFFERS_INCLUDE).forEach { path ->
      configuration.addIncludePath(path.toString())
    }
    resolvePaths(module, FlatbuffersDirectives.FLATBUFFERS_BFBS).forEach { path ->
      configuration.addBfbsPath(path.toString())
    }

    val options = FlatbuffersPluginOptions.load(configuration)
    check(options.schemaPaths.isNotEmpty()) {
      val moduleValues = module.directives[FlatbuffersDirectives.FLATBUFFERS_SCHEMA]
      val fileValues =
        module.files.associate { file -> file.name to file.directives[FlatbuffersDirectives.FLATBUFFERS_SCHEMA] }
      "Schema paths not configured for module ${module.name}. module=$moduleValues file=$fileValues"
    }
  }

  @OptIn(ExperimentalCompilerApi::class)
  override fun CompilerPluginRegistrar.ExtensionStorage.registerCompilerExtensions(
    module: TestModule,
    configuration: CompilerConfiguration,
  ) {
    FlatbuffersCompilerPluginRegistrar().apply {
      this@registerCompilerExtensions.registerExtensions(configuration)
    }
  }

  private fun resolvePaths(module: TestModule, directive: StringDirective): List<Path> {
    return module
      .collectDirectiveValues(directive)
      .map { value -> resolveDataPath(value) }
      .toList()
  }

  private fun resolveDataPath(relative: String): Path {
    val resolved = dataRoot.resolve(relative).normalize()
    require(Files.exists(resolved)) {
      "Resolved path does not exist: $resolved"
    }
    return resolved
  }
}
