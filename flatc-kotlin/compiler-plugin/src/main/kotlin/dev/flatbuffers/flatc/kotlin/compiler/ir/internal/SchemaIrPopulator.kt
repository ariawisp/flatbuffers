package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrConstructor
import org.jetbrains.kotlin.ir.declarations.IrClassConstructor

internal class SchemaIrPopulator(
  private val context: FlatbuffersIrContext,
) {
  fun populate(moduleFragment: IrModuleFragment) {
    moduleFragment.acceptVoid(
      MissingObjectConstructorChecker,
    )
    moduleFragment.transformChildrenVoid(
      SchemaProvenanceIrTransformer(context),
    )
    moduleFragment.transformChildrenVoid(
      TableBodyLowering(context),
    )
    moduleFragment.transformChildrenVoid(
      TableCompanionEndLowering(context),
    )
  }
}
