package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class SchemaIrPopulator(
  private val context: FlatbuffersIrContext,
) {
  fun populate(moduleFragment: IrModuleFragment) {
    moduleFragment.transformChildrenVoid(
      SchemaProvenanceIrTransformer(context),
    )
    moduleFragment.transformChildrenVoid(
      TableCompanionEndLowering(context),
    )
  }
}
