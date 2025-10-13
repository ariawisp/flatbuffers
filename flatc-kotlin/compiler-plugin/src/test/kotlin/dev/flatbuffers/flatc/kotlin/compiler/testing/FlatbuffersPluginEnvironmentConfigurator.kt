package dev.flatbuffers.flatc.kotlin.compiler.testing

import dev.flatbuffers.flatc.kotlin.compiler.FlatbuffersCompilerPluginRegistrar
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersOption
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
import org.jetbrains.kotlin.test.directives.model.RegisteredDirectives
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.services.EnvironmentConfigurator
import org.jetbrains.kotlin.test.services.TestServices

fun TestConfigurationBuilder.configureFlatbuffersPlugin() {
  useDirectives(FlatbuffersDirectives)
  useConfigurators(::FlatbuffersPluginEnvironmentConfigurator)
}

private class FlatbuffersCommandLineBridge(
  private val dataRoot: Path,
) {
  fun apply(configuration: CompilerConfiguration, module: TestModule) {
    configuration.setEnabled(true)

    val schemaValues = module.collectValues(FlatbuffersDirectives.FLATBUFFERS_SCHEMA).toList()
    if (schemaValues.isEmpty()) {
      error("No FLATBUFFERS_SCHEMA directives found for module ${module.name}")
    }
    schemaValues.forEach { value ->
      processPathOption(configuration, FlatbuffersOption.SCHEMA, value)
    }
    module.collectValues(FlatbuffersDirectives.FLATBUFFERS_INCLUDE).forEach { value ->
      processPathOption(configuration, FlatbuffersOption.INCLUDE, value)
    }
    module.collectValues(FlatbuffersDirectives.FLATBUFFERS_BFBS).forEach { value ->
      processPathOption(configuration, FlatbuffersOption.BFBS, value)
    }
  }

  private fun processPathOption(
    configuration: CompilerConfiguration,
    option: FlatbuffersOption,
    relative: String,
  ) {
    val resolved = dataRoot.resolve(relative).normalize()
    require(Files.exists(resolved)) {
      "Resolved path for ${option.cliOption.optionName} does not exist: $resolved"
    }
    when (option) {
      FlatbuffersOption.SCHEMA -> configuration.addSchemaPath(resolved.toString())
      FlatbuffersOption.INCLUDE -> configuration.addIncludePath(resolved.toString())
      FlatbuffersOption.BFBS -> configuration.addBfbsPath(resolved.toString())
      FlatbuffersOption.ENABLED,
      FlatbuffersOption.DEBUG,
      -> {}
    }
  }
}

private fun TestModule.collectValues(directive: org.jetbrains.kotlin.test.directives.model.StringDirective): Sequence<String> {
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

  private val cliBridge = FlatbuffersCommandLineBridge(dataRoot)

  override fun configureCompilerConfiguration(configuration: CompilerConfiguration, module: TestModule) {
    cliBridge.apply(configuration, module)
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
}
