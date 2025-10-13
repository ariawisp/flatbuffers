package dev.flatbuffers.flatc.kotlin.compiler

import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersOption
import dev.flatbuffers.flatc.kotlin.compiler.options.addBfbsPath
import dev.flatbuffers.flatc.kotlin.compiler.options.addIncludePath
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import dev.flatbuffers.flatc.kotlin.compiler.options.setDebug
import dev.flatbuffers.flatc.kotlin.compiler.options.setEnabled
import org.jetbrains.kotlin.compiler.plugin.AbstractCliOption
import org.jetbrains.kotlin.compiler.plugin.CliOptionProcessingException
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.compiler.plugin.CommandLineProcessor
import org.jetbrains.kotlin.config.CompilerConfiguration

internal const val FLATBUFFERS_PLUGIN_ID: String = "dev.flatbuffers.flatc.kotlin.compiler"

@OptIn(ExperimentalCompilerApi::class)
public class FlatbuffersCommandLineProcessor : CommandLineProcessor {

  override val pluginId: String = FLATBUFFERS_PLUGIN_ID

  override val pluginOptions: Collection<AbstractCliOption> =
    FlatbuffersOption.entries.map { it.cliOption }

  override fun processOption(
    option: AbstractCliOption,
    value: String,
    configuration: CompilerConfiguration,
  ) {
    when (FlatbuffersOption.byName[option.optionName]) {
      FlatbuffersOption.ENABLED -> configuration.setEnabled(value.toBooleanStrict())
      FlatbuffersOption.DEBUG -> configuration.setDebug(value.toBooleanStrict())
      FlatbuffersOption.SCHEMA -> configuration.addSchemaPath(value)
      FlatbuffersOption.INCLUDE -> configuration.addIncludePath(value)
      FlatbuffersOption.BFBS -> configuration.addBfbsPath(value)
      null -> throw CliOptionProcessingException("Unknown option: ${option.optionName}")
    }
  }
}
