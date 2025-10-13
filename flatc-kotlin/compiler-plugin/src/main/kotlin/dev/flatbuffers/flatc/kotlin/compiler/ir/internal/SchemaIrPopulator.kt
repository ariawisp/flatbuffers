package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import org.jetbrains.kotlin.ir.declarations.IrModuleFragment

internal class SchemaIrPopulator(
  private val context: FlatbuffersIrContext,
) {
  fun populate(moduleFragment: IrModuleFragment) {
    // IR lowerings will be implemented in a later phase. For now, this method
    // exists as a staging point to wire the context through the extension.
    // Walking the module tree eagerly ensures we touch every file, which makes
    // it straightforward to add transformers in follow-up changes.
    moduleFragment.files.forEach { _ ->
      // no-op placeholder
    }
  }
}
