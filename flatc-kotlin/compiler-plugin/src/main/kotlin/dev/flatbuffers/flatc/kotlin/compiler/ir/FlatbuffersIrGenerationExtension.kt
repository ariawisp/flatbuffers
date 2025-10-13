package dev.flatbuffers.flatc.kotlin.compiler.ir

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.FlatbuffersIrContext
import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.SchemaIrPopulator
import org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class FlatbuffersIrGenerationExtension(
  private val schemaIndex: SchemaIndex,
  private val options: FlatbuffersPluginOptions,
  private val compatContext: CompatContext,
  private val messageCollector: MessageCollector,
  private val lookupTracker: LookupTracker?,
  private val expectActualTracker: ExpectActualTracker,
) : IrGenerationExtension {
  override fun generate(moduleFragment: IrModuleFragment, pluginContext: IrPluginContext) {
    val context =
      FlatbuffersIrContext(
        pluginContext = pluginContext,
        schemaIndex = schemaIndex,
        options = options,
        compatContext = compatContext,
        messageCollector = messageCollector,
        lookupTracker = lookupTracker,
        expectActualTracker = expectActualTracker,
      )
    SchemaIrPopulator(context).populate(moduleFragment)
  }
}
