package dev.flatbuffers.flatc.kotlin.compiler.options

import java.nio.file.Path
import java.nio.file.Paths
import org.jetbrains.kotlin.compiler.plugin.CliOption
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal object FlatbuffersConfigurationKeys {
  val ENABLED: CompilerConfigurationKey<Boolean> =
    CompilerConfigurationKey.create("flatbuffers.enabled")
  val DEBUG: CompilerConfigurationKey<Boolean> =
    CompilerConfigurationKey.create("flatbuffers.debug")
  val SCHEMA_PATHS: CompilerConfigurationKey<MutableList<String>> =
    CompilerConfigurationKey.create("flatbuffers.schema")
  val INCLUDE_PATHS: CompilerConfigurationKey<MutableList<String>> =
    CompilerConfigurationKey.create("flatbuffers.include")
  val BFBS_PATHS: CompilerConfigurationKey<MutableList<String>> =
    CompilerConfigurationKey.create("flatbuffers.bfbs")
}

internal enum class FlatbuffersOption(val cliOption: CliOption) {
  ENABLED(
    CliOption(
      optionName = "enabled",
      valueDescription = "<true | false>",
      description = "Enable or disable the FlatBuffers compiler plugin for this compilation.",
      required = false,
      allowMultipleOccurrences = false,
    )
  ),
  DEBUG(
    CliOption(
      optionName = "debug",
      valueDescription = "<true | false>",
      description = "Enable verbose logging for the FlatBuffers compiler plugin.",
      required = false,
      allowMultipleOccurrences = false,
    )
  ),
  SCHEMA(
    CliOption(
      optionName = "schema",
      valueDescription = "<path>",
      description = "Path to a root .fbs schema file. May be specified multiple times.",
      required = false,
      allowMultipleOccurrences = true,
    )
  ),
  INCLUDE(
    CliOption(
      optionName = "include",
      valueDescription = "<path>",
      description = "Additional include directory used when resolving schema imports.",
      required = false,
      allowMultipleOccurrences = true,
    )
  ),
  BFBS(
    CliOption(
      optionName = "bfbs",
      valueDescription = "<path>",
      description = "Path to an existing .bfbs reflection schema that should be loaded instead of parsing .fbs sources.",
      required = false,
      allowMultipleOccurrences = true,
    )
  ),
  ;

  companion object {
    val byName: Map<String, FlatbuffersOption> = entries.associateBy { it.cliOption.optionName }
  }
}

internal data class FlatbuffersPluginOptions(
  val enabled: Boolean,
  val debug: Boolean,
  val schemaPaths: List<Path>,
  val includePaths: List<Path>,
  val bfbsPaths: List<Path>,
) {
  companion object {
    fun load(configuration: CompilerConfiguration): FlatbuffersPluginOptions {
      val enabled = configuration[FlatbuffersConfigurationKeys.ENABLED] ?: true
      val debug = configuration[FlatbuffersConfigurationKeys.DEBUG] ?: false
      return FlatbuffersPluginOptions(
        enabled = enabled,
        debug = debug,
        schemaPaths = configuration[FlatbuffersConfigurationKeys.SCHEMA_PATHS].orEmpty().map(::asPath),
        includePaths = configuration[FlatbuffersConfigurationKeys.INCLUDE_PATHS].orEmpty().map(::asPath),
        bfbsPaths = configuration[FlatbuffersConfigurationKeys.BFBS_PATHS].orEmpty().map(::asPath),
      )
    }

    private fun asPath(raw: String): Path = Paths.get(raw)

    private fun <T> MutableList<T>?.orEmpty(): List<T> = this?.toList() ?: emptyList()
  }
}

internal fun CompilerConfiguration.addSchemaPath(value: String) {
  configurationListFor(FlatbuffersConfigurationKeys.SCHEMA_PATHS).add(value)
}

internal fun CompilerConfiguration.addIncludePath(value: String) {
  configurationListFor(FlatbuffersConfigurationKeys.INCLUDE_PATHS).add(value)
}

internal fun CompilerConfiguration.addBfbsPath(value: String) {
  configurationListFor(FlatbuffersConfigurationKeys.BFBS_PATHS).add(value)
}

private fun <T> CompilerConfiguration.configurationListFor(
  key: CompilerConfigurationKey<MutableList<T>>,
): MutableList<T> {
  val current = this[key]
  if (current != null) {
    return current
  }
  val list = mutableListOf<T>()
  put(key, list)
  return list
}

internal fun CompilerConfiguration.setEnabled(value: Boolean) {
  put(FlatbuffersConfigurationKeys.ENABLED, value)
}

internal fun CompilerConfiguration.setDebug(value: Boolean) {
  put(FlatbuffersConfigurationKeys.DEBUG, value)
}
