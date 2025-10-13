package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaProvenance
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclaration
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrDeclarationWithName
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.callableId
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.Name

internal class SchemaProvenanceIrTransformer(
  private val context: FlatbuffersIrContext,
) : IrElementTransformerVoid() {

  override fun visitClass(declaration: IrClass): IrClass {
    super.visitClass(declaration)
    if (declaration.origin != IrDeclarationOrigin.GeneratedByPlugin) return declaration
    val classId = declaration.classId ?: return declaration
    val provenance = context.schemaIndex.provenanceStore.classProvenance(classId) ?: return declaration
    applyProvenance(declaration, provenance) { name, source, doc ->
      FlatbuffersSchemaMetadata.Class(name, source, doc)
    }
    return declaration
  }

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrSimpleFunction {
    super.visitSimpleFunction(declaration)
    if (declaration.origin != IrDeclarationOrigin.GeneratedByPlugin) return declaration
    val provenance =
      context.schemaIndex.provenanceStore.functionProvenance(declaration.callableId)
        ?: return declaration
    applyProvenance(declaration, provenance) { name, source, doc ->
      FlatbuffersSchemaMetadata.Function(name, source, doc)
    }
    return declaration
  }

  override fun visitProperty(declaration: IrProperty): IrProperty {
    super.visitProperty(declaration)
    if (declaration.origin != IrDeclarationOrigin.GeneratedByPlugin) return declaration
    val provenance =
      context.schemaIndex.provenanceStore.propertyProvenance(declaration.callableId)
        ?: return declaration
    applyProvenance(declaration, provenance) { name, source, doc ->
      FlatbuffersSchemaMetadata.Property(name, source, doc, declaration.isConst)
    }
    return declaration
  }

  private fun applyProvenance(
    declaration: IrDeclaration,
    provenance: SchemaProvenance,
    metadataFactory: (Name?, KtSourceElement?, String?) -> FlatbuffersSchemaMetadata,
  ) {
    val sourceElement = provenance.span?.let { context.schemaSourceIndex.element(it) }
    if (sourceElement != null) {
      declaration.startOffset = sourceElement.startOffset
      declaration.endOffset = sourceElement.endOffset
    }
    val docString = context.schemaSourceIndex.doc(provenance.docComment)
    if (sourceElement == null && docString == null) return
    val namedDeclaration = declaration as? IrDeclarationWithName
    val metadata = metadataFactory(namedDeclaration?.name, sourceElement, docString)
    (declaration as IrMetadataSourceOwner).metadata = metadata
  }
}
