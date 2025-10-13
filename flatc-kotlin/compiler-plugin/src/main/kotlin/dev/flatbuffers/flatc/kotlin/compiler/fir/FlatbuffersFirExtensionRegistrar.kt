package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar

internal class FlatbuffersFirExtensionRegistrar(
  private val schemaIndex: SchemaIndex,
  private val options: FlatbuffersPluginOptions,
  private val compatContext: CompatContext,
) : FirExtensionRegistrar() {
  override fun ExtensionRegistrarContext.configurePlugin() {
    +FirDeclarationGenerationExtension.Factory { session ->
      FlatbuffersFirDeclarationGenerator(session, schemaIndex, options, compatContext)
    }
  }
}
