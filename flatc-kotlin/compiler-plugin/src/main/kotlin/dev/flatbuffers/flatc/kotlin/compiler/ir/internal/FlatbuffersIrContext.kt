package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.incremental.components.ExpectActualTracker
import org.jetbrains.kotlin.incremental.components.LookupTracker

internal class FlatbuffersIrContext(
  val pluginContext: IrPluginContext,
  val schemaIndex: SchemaIndex,
  val options: FlatbuffersPluginOptions,
  val compatContext: CompatContext,
  val messageCollector: MessageCollector,
  val lookupTracker: LookupTracker?,
  val expectActualTracker: ExpectActualTracker,
)
