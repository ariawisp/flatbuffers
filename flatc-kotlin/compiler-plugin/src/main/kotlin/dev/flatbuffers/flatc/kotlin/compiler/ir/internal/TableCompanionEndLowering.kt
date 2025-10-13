package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import dev.flatbuffers.flatc.kotlin.compiler.metadata.RequiredFieldMetadata
import dev.flatbuffers.flatc.kotlin.compiler.metadata.classId
import dev.flatbuffers.flatc.kotlin.compiler.metadata.requiredFieldMetadata
import dev.flatbuffers.semantics.ResolvedTable
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irString
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(ObsoleteDescriptorBasedAPI::class, UnsafeDuringIrConstructionAPI::class)
internal class TableCompanionEndLowering(
  private val context: FlatbuffersIrContext,
) : IrElementTransformerVoid() {

  private data class TableInfo(
    val endFunctionName: String,
    val requiredFields: List<RequiredFieldMetadata>,
  )

  private val flatBufferBuilderClassId =
    ClassId(FqName("com.google.flatbuffers.kotlin"), Name.identifier("FlatBufferBuilder"))
  private val endTableSymbol =
    context.pluginContext
      .referenceFunctions(CallableId(flatBufferBuilderClassId, Name.identifier("endTable")))
      .single()
  private val requiredSymbol =
    context.pluginContext.referenceFunctions(
      CallableId(flatBufferBuilderClassId, Name.identifier("required"))
    ).firstOrNull()
      ?: error("FlatBufferBuilder.required symbol not found")

  private val tableInfoByCompanionFqName: Map<String, TableInfo> = buildTableInfoMap()

  override fun visitSimpleFunction(declaration: IrSimpleFunction): IrSimpleFunction {
    super.visitSimpleFunction(declaration)

    val companionClass = declaration.parent as? IrClass ?: return declaration
    if (companionClass.name != Name.identifier("Companion")) return declaration
    if (declaration.origin != IrDeclarationOrigin.GeneratedByPlugin) return declaration
    val tableClass = companionClass.parent as? IrClass ?: return declaration

    val companionFqName = companionClass.fqNameWhenAvailable?.asString() ?: return declaration
    val tableInfo = tableInfoByCompanionFqName[companionFqName] ?: return declaration
    if (declaration.name.asString() != tableInfo.endFunctionName) return declaration
    val regularParameters = declaration.parameters.filter { it.kind == IrParameterKind.Regular }
    if (regularParameters.size != 1) return declaration

    val builderParam = regularParameters.single()
    val tableType = tableClass.defaultType

    val irBuilder =
      DeclarationIrBuilder(
        context.pluginContext,
        declaration.symbol,
        declaration.startOffset,
        declaration.endOffset,
      )
    declaration.body =
      irBuilder.irBlockBody {
        val endCall =
          irCall(endTableSymbol).apply {
            dispatchReceiver = irGet(builderParam)
            if (typeArguments.isNotEmpty()) {
              typeArguments[0] = tableType
            }
          }
        val offsetVar = irTemporary(endCall, nameHint = "o")
        tableInfo.requiredFields.forEach { metadata ->
          +irCall(requiredSymbol).apply {
            dispatchReceiver = irGet(builderParam)
            arguments[0] = irGet(offsetVar)
            arguments[1] = irInt(metadata.vtableOffset)
            if (arguments.size >= 3) {
              arguments[2] = irString(metadata.fieldName)
            }
          }
        }
        +irReturn(irGet(offsetVar))
      }

    return declaration
  }

  private fun buildTableInfoMap(): Map<String, TableInfo> {
    val result = mutableMapOf<String, TableInfo>()
    context.schemaIndex.schemas.forEach { schema ->
      schema.declarations.values.forEach { declaration ->
        val table = declaration as? ResolvedTable ?: return@forEach
        val companionFqName =
          table.classId().asSingleFqName().child(Name.identifier("Companion")).asString()
        result[companionFqName] =
          TableInfo(
            endFunctionName = "end${table.name}",
            requiredFields = requiredFieldMetadata(table),
          )
      }
    }
    return result
  }
}
