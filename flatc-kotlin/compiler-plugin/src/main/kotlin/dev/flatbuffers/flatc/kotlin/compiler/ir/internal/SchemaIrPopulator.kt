package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.util.isObject
import org.jetbrains.kotlin.ir.util.primaryConstructor
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid

internal class SchemaIrPopulator(
  private val context: FlatbuffersIrContext,
) {
  fun populate(moduleFragment: IrModuleFragment) {
    moduleFragment.transformChildrenVoid(MissingObjectConstructorDetector)
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

private object MissingObjectConstructorDetector : IrElementTransformerVoid() {
  override fun visitClass(declaration: IrClass): IrStatement {
    if (declaration.isObject && declaration.primaryConstructor == null) {
      println("[flatbuffers] Missing primary constructor on object ${declaration.render()}")
    }
    return super.visitClass(declaration)
  }
}
