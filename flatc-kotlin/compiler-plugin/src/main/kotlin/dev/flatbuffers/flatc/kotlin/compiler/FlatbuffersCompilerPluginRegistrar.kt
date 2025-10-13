package dev.flatbuffers.flatc.kotlin.compiler

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.flatc.kotlin.compiler.fir.FlatbuffersFirExtensionRegistrar
import dev.flatbuffers.flatc.kotlin.compiler.ir.FlatbuffersIrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.compiler.plugin.CompilerPluginRegistrar
import org.jetbrains.kotlin.compiler.plugin.ExperimentalCompilerApi
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrarAdapter

@OptIn(ExperimentalCompilerApi::class)
public class FlatbuffersCompilerPluginRegistrar : CompilerPluginRegistrar() {

  override val supportsK2: Boolean
    get() = true

  override fun ExtensionStorage.registerExtensions(configuration: CompilerConfiguration) {
    val options = FlatbuffersPluginOptions.load(configuration)
    if (!options.enabled) return

    val messageCollector = configuration.messageCollector
    val compatContext = CompatContext.getInstance()
    val schemaIndex = SchemaIndex.load(options, messageCollector) ?: return

    FirExtensionRegistrarAdapter.registerExtension(
      FlatbuffersFirExtensionRegistrar(schemaIndex, options, compatContext)
    )

    val lookupTracker = configuration.get(CommonConfigurationKeys.LOOKUP_TRACKER)
    val expectActualTracker =
      configuration.get(
        CommonConfigurationKeys.EXPECT_ACTUAL_TRACKER,
        org.jetbrains.kotlin.incremental.components.ExpectActualTracker.DoNothing,
      )
    IrGenerationExtension.registerExtension(
      FlatbuffersIrGenerationExtension(
        schemaIndex = schemaIndex,
        options = options,
        compatContext = compatContext,
        messageCollector = messageCollector,
        lookupTracker = lookupTracker,
        expectActualTracker = expectActualTracker,
      )
    )
  }
}

internal val CompilerConfiguration.messageCollector: MessageCollector
  get() = get(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, MessageCollector.NONE)
