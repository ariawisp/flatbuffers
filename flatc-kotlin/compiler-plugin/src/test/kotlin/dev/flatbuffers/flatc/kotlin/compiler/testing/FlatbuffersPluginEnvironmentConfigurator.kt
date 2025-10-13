package dev.flatbuffers.flatc.kotlin.compiler.testing

import dev.flatbuffers.flatc.kotlin.compiler.FlatbuffersCommandLineProcessor
import dev.flatbuffers.flatc.kotlin.compiler.FlatbuffersCompilerPluginRegistrar
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersOption
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
  private val processor = FlatbuffersCommandLineProcessor()

  fun apply(configuration: CompilerConfiguration, directives: RegisteredDirectives) {
    processor.processOption(FlatbuffersOption.ENABLED.cliOption, "true", configuration)

    directives[FlatbuffersDirectives.SCHEMA].forEach { value ->
      processPathOption(configuration, FlatbuffersOption.SCHEMA, value)
    }
    directives[FlatbuffersDirectives.INCLUDE].forEach { value ->
      processPathOption(configuration, FlatbuffersOption.INCLUDE, value)
    }
    directives[FlatbuffersDirectives.BFBS].forEach { value ->
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
    processor.processOption(option.cliOption, resolved.toString(), configuration)
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
    cliBridge.apply(configuration, module.directives)
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
