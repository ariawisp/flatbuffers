package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import dev.flatbuffers.ast.ScalarType
import dev.flatbuffers.semantics.ResolvedBooleanValue
import dev.flatbuffers.semantics.ResolvedField
import dev.flatbuffers.semantics.ResolvedFloatValue
import dev.flatbuffers.semantics.ResolvedIntegerValue
import dev.flatbuffers.semantics.ResolvedScalarType
import dev.flatbuffers.semantics.ResolvedTable
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.DeprecatedForRemovalCompilerApi
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.ir.UNDEFINED_OFFSET
import org.jetbrains.kotlin.ir.builders.IrBuilderWithScope
import org.jetbrains.kotlin.ir.builders.irBlockBody
import org.jetbrains.kotlin.ir.builders.irBoolean
import org.jetbrains.kotlin.ir.builders.irByte
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irEquals
import org.jetbrains.kotlin.ir.builders.irGet
import org.jetbrains.kotlin.ir.builders.irIfThenElse
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irLong
import org.jetbrains.kotlin.ir.builders.irReturn
import org.jetbrains.kotlin.ir.builders.irShort
import org.jetbrains.kotlin.ir.builders.irTemporary
import org.jetbrains.kotlin.ir.builders.irUnit
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.util.classId
import org.jetbrains.kotlin.ir.util.defaultType
import org.jetbrains.kotlin.ir.util.render
import org.jetbrains.kotlin.ir.visitors.IrElementTransformerVoid
import org.jetbrains.kotlin.ir.visitors.transformChildrenVoid
import org.jetbrains.kotlin.name.ClassId

@OptIn(UnsafeDuringIrConstructionAPI::class, DeprecatedForRemovalCompilerApi::class)
internal class TableBodyLowering(
  private val context: FlatbuffersIrContext,
) : IrElementTransformerVoid() {
  private val symbols = context.symbols
  private val supportedScalars =
    setOf(
      ScalarType.BOOL,
      ScalarType.BYTE,
      ScalarType.SHORT,
      ScalarType.INT,
      ScalarType.LONG,
      ScalarType.FLOAT,
      ScalarType.DOUBLE,
    )
  private val scalarFieldCache = mutableMapOf<ClassId, ScalarFieldInfo?>()

  override fun visitClass(declaration: IrClass): IrStatement {
    val tableInfo = tableInfoFor(declaration)
    if (tableInfo != null) {
      val (tableId, table) = tableInfo
      val fieldInfo = scalarFieldInfo(tableId, table)
      if (fieldInfo != null) {
        if (declaration.isCompanion) {
          lowerCompanionAddFunction(declaration, fieldInfo)
        } else {
          lowerInitReset(declaration)
          lowerScalarProperty(declaration, fieldInfo)
        }
      }
    }
    return super.visitClass(declaration)
  }

  private fun tableInfoFor(klass: IrClass): Pair<ClassId, ResolvedTable>? {
    val tableClassId =
      if (klass.isCompanion) {
        (klass.parent as? IrClass)?.classId
      } else {
        klass.classId
      } ?: return null
    val table = context.schemaIndex.tableFor(classId = tableClassId) ?: return null
    return tableClassId to table
  }

  private fun scalarFieldInfo(tableId: ClassId, table: ResolvedTable): ScalarFieldInfo? {
    if (!scalarFieldCache.containsKey(tableId)) {
      scalarFieldCache[tableId] = computeScalarFieldInfo(table)
    }
    return scalarFieldCache[tableId]
  }

  private fun computeScalarFieldInfo(table: ResolvedTable): ScalarFieldInfo? {
    table.fields.withIndex().forEach { (index, field) ->
      val scalarType = (field.type as? ResolvedScalarType)?.scalar ?: return@forEach
      if (scalarType in supportedScalars) {
        return ScalarFieldInfo(field = field, scalarType = scalarType, index = index)
      }
    }
    return null
  }

  private fun lowerInitReset(tableClass: IrClass) {
    val tableType = tableClass.defaultType
    tableClass.declarations.filterIsInstance<IrSimpleFunction>().forEach { function ->
      if (function.origin != IrDeclarationOrigin.GeneratedByPlugin) return@forEach
      when (function.name.asString()) {
        "init",
        "reset",
        -> lowerInitOrReset(function, tableType)
      }
    }
  }

  private fun lowerInitOrReset(function: IrSimpleFunction, tableType: IrType) {
    val receiver = function.dispatchReceiverParameter ?: return
    val parameters = function.regularValueParameters()
    if (parameters.size != 2) return
    val builder =
      DeclarationIrBuilder(context.pluginContext, function.symbol, function.startOffset, function.endOffset)
    function.body =
      builder.irBlockBody {
        val call =
          irCall(symbols.tableReset).apply {
            dispatchReceiver = irGet(receiver)
            typeArguments[0] = tableType
            setRegularValueArgument(0, irGet(parameters[0]))
            setRegularValueArgument(1, irGet(parameters[1]))
          }
        +irReturn(call)
      }
  }

  private fun lowerScalarProperty(
    tableClass: IrClass,
    fieldInfo: ScalarFieldInfo,
  ) {
    val property =
      tableClass.declarations
        .filterIsInstance<IrProperty>()
        .firstOrNull { it.name.asString() == fieldInfo.field.name }
        ?: return
    val getter = property.getter ?: return
    val receiver = getter.dispatchReceiverParameter ?: return
    val builder =
      DeclarationIrBuilder(context.pluginContext, getter.symbol, getter.startOffset, getter.endOffset)
    val runtimeSymbols = symbols
    val runtimeContextSymbols = context.symbols
    getter.body =
      builder.irBlockBody {
        val condition =
          irEquals(
            irCall(runtimeSymbols.tableOffset).apply {
              dispatchReceiver = irGet(receiver)
              setRegularValueArgument(0, irInt(vtableOffsetFor(fieldInfo.index)))
            },
            irInt(0),
          )
        val readExpr =
          irCall(runtimeContextSymbols.readWriteBufferGetterFor(fieldInfo.scalarType)).apply {
            dispatchReceiver =
              irCall(runtimeSymbols.tableBufferGetter).apply {
                dispatchReceiver = irGet(receiver)
              }
            val offsetExpr =
              irCall(runtimeSymbols.tableOffset).apply {
                dispatchReceiver = irGet(receiver)
                setRegularValueArgument(0, irInt(vtableOffsetFor(fieldInfo.index)))
              }
            val bufferPosExpr =
              irCall(runtimeSymbols.tableBufferPosGetter).apply {
                dispatchReceiver = irGet(receiver)
              }
            setRegularValueArgument(0, addInts(offsetExpr, bufferPosExpr))
          }
        +irReturn(
          irIfThenElse(
            getter.returnType,
            condition,
            scalarDefaultExpression(fieldInfo.scalarType, fieldInfo.field),
            readExpr,
          )
        )
      }
  }

  private fun lowerCompanionAddFunction(
    companionClass: IrClass,
    fieldInfo: ScalarFieldInfo,
  ) {
    val addFunctionName = "add${fieldInfo.field.name.replaceFirstChar { it.uppercaseChar() }}"
    val function =
      companionClass.declarations
        .filterIsInstance<IrSimpleFunction>()
        .firstOrNull { it.origin == IrDeclarationOrigin.GeneratedByPlugin && it.name.asString() == addFunctionName }
        ?: return
    val parameters = function.regularValueParameters()
    if (parameters.size != 2) return
    val builder =
      DeclarationIrBuilder(context.pluginContext, function.symbol, function.startOffset, function.endOffset)
    function.body =
      builder.irBlockBody {
        +irCall(symbols.flatBufferBuilderAddFor(fieldInfo.scalarType)).apply {
          dispatchReceiver = irGet(parameters[0])
          setRegularValueArgument(0, irInt(fieldInfo.index))
          setRegularValueArgument(1, irGet(parameters[1]))
          setRegularValueArgument(2, scalarDefaultExpression(fieldInfo.scalarType, fieldInfo.field))
        }
        +irReturn(irUnit())
      }
  }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.scalarDefaultExpression(
    scalar: ScalarType,
    field: ResolvedField,
  ): IrExpression =
    scalarConstant(scalar, defaultValueFor(field, scalar))

  private fun defaultValueFor(field: ResolvedField, scalar: ScalarType): Any =
    when (val default = field.defaultValue) {
      is ResolvedBooleanValue -> default.value
      is ResolvedIntegerValue -> default.value
      is ResolvedFloatValue -> default.value
      null ->
        when (scalar) {
          ScalarType.BOOL -> false
          ScalarType.BYTE,
          ScalarType.SHORT,
          ScalarType.INT,
          ScalarType.LONG,
          -> 0L
          ScalarType.FLOAT,
          ScalarType.DOUBLE,
          -> 0.0
          else -> 0L
        }
      else -> error("Unsupported default value $default for scalar field ${field.name}")
    }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.scalarConstant(
    scalar: ScalarType,
    value: Any,
  ): IrExpression =
    when (scalar) {
      ScalarType.BOOL -> irBoolean((value as? Boolean) ?: ((value as Number).toInt() != 0))
      ScalarType.BYTE -> irByte((value as Number).toByte())
      ScalarType.SHORT -> irShort((value as Number).toShort())
      ScalarType.INT -> irInt((value as Number).toInt())
      ScalarType.LONG -> irLong((value as Number).toLong())
      ScalarType.FLOAT ->
        IrConstImpl.float(
          UNDEFINED_OFFSET,
          UNDEFINED_OFFSET,
          this@TableBodyLowering.context.pluginContext.irBuiltIns.floatType,
          (value as Number).toFloat(),
        )
      ScalarType.DOUBLE ->
        IrConstImpl.double(
          UNDEFINED_OFFSET,
          UNDEFINED_OFFSET,
          this@TableBodyLowering.context.pluginContext.irBuiltIns.doubleType,
          (value as Number).toDouble(),
        )
      else -> error("Unsupported scalar type $scalar")
    }

  private fun org.jetbrains.kotlin.ir.builders.IrBuilderWithScope.addInts(
    lhs: IrExpression,
    rhs: IrExpression,
  ): IrExpression =
    irCall(this@TableBodyLowering.context.pluginContext.irBuiltIns.intPlusSymbol).apply {
      putValueArgument(0, lhs)
      putValueArgument(1, rhs)
    }

  private fun vtableOffsetFor(fieldIndex: Int): Int = 4 + fieldIndex * 2

  private data class ScalarFieldInfo(
    val field: ResolvedField,
    val scalarType: ScalarType,
    val index: Int,
  )
}

private fun IrSimpleFunction.regularValueParameters(): List<IrValueParameter> =
  parameters.filter { parameter ->
    parameter.kind == IrParameterKind.Regular || parameter.kind == IrParameterKind.Context
  }

@OptIn(UnsafeDuringIrConstructionAPI::class)
private fun IrCall.setRegularValueArgument(index: Int, expression: IrExpression) {
  val parameterList =
    this.symbol.owner.parameters.filter { parameter ->
      parameter.kind == IrParameterKind.Regular || parameter.kind == IrParameterKind.Context
    }
  val parameter = parameterList.getOrNull(index)
    ?: error("No regular parameter at index $index for ${this.symbol.owner.render()}")
  this.arguments[parameter.indexInParameters] = expression
}
