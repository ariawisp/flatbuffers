package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import dev.flatbuffers.ast.ScalarType
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.ir.ObsoleteDescriptorBasedAPI
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.symbols.IrClassSymbol
import org.jetbrains.kotlin.ir.symbols.IrPropertySymbol
import org.jetbrains.kotlin.ir.symbols.IrSimpleFunctionSymbol
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.util.isNullable
import org.jetbrains.kotlin.ir.util.functions
import org.jetbrains.kotlin.ir.declarations.IrParameterKind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

@OptIn(ObsoleteDescriptorBasedAPI::class, UnsafeDuringIrConstructionAPI::class)
internal class FlatbuffersIrSymbols(
  private val pluginContext: IrPluginContext,
) {
  private val irBuiltIns = pluginContext.irBuiltIns

  val tableClass: IrClassSymbol by lazy { referenceClass(TABLE_CLASS_ID) }
  val readWriteBufferClass: IrClassSymbol by lazy { referenceClass(READ_WRITE_BUFFER_CLASS_ID) }
  val flatBufferBuilderClass: IrClassSymbol by lazy { referenceClass(FLAT_BUFFER_BUILDER_CLASS_ID) }

  val tableReset: IrSimpleFunctionSymbol by lazy {
    tableClass.findFunction("reset") { function ->
      function.regularValueParameters().let { parameters ->
        parameters.size == 2 &&
          parameters[0].type == irBuiltIns.intType &&
          parameters[1].type.classFqName == READ_WRITE_BUFFER_CLASS_ID.asSingleFqName()
      }
    }
  }

  val tableLookupField: IrSimpleFunctionSymbol by lazy {
    tableClass.findFunction("lookupField") { it.regularValueParameters().size == 3 }
  }

  val tableOffset: IrSimpleFunctionSymbol by lazy {
    tableClass.findFunction("offset") { function ->
      function.regularValueParameters().let { parameters ->
        parameters.size == 1 && parameters[0].type == irBuiltIns.intType
      }
    }
  }

  val tableBufferProperty: IrPropertySymbol by lazy { tableProperty("bb") }
  val tableBufferPosProperty: IrPropertySymbol by lazy { tableProperty("bufferPos") }
  val tableVtableStartProperty: IrPropertySymbol by lazy { tableProperty("vtableStart") }
  val tableVtableSizeProperty: IrPropertySymbol by lazy { tableProperty("vtableSize") }
  val tableBufferGetter: IrSimpleFunctionSymbol by lazy {
    tableBufferProperty.owner.getter?.symbol ?: error("Table.bb getter not found")
  }
  val tableBufferPosGetter: IrSimpleFunctionSymbol by lazy {
    tableBufferPosProperty.owner.getter?.symbol ?: error("Table.bufferPos getter not found")
  }

  val readWriteBufferGetInt: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getInt") }
  val readWriteBufferGetShort: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getShort") }
  val readWriteBufferGetByte: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("get", irBuiltIns.byteType) }
  val readWriteBufferGetBoolean: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getBoolean") }
  val readWriteBufferGetLong: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getLong") }
  val readWriteBufferGetFloat: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getFloat") }
  val readWriteBufferGetDouble: IrSimpleFunctionSymbol by lazy { findReadWriteBufferGetter("getDouble") }

  val flatBufferBuilderStartTable: IrSimpleFunctionSymbol by lazy {
    flatBufferBuilderClass.findFunction("startTable") { function ->
      function.regularValueParameters().let { parameters ->
        parameters.size == 1 && parameters[0].type == irBuiltIns.intType
      }
    }
  }

  private val flatBufferBuilderAddFunctions: Map<ScalarType, IrSimpleFunctionSymbol> by lazy {
    val supportedScalars = listOf(
      ScalarType.BOOL,
      ScalarType.BYTE,
      ScalarType.SHORT,
      ScalarType.INT,
      ScalarType.LONG,
      ScalarType.FLOAT,
      ScalarType.DOUBLE,
    )
    val result = mutableMapOf<ScalarType, IrSimpleFunctionSymbol>()
    supportedScalars.forEach { scalar ->
      findFlatBufferBuilderAdd(scalar)?.let { result[scalar] = it }
    }
    result
  }

  private val readWriteBufferGetters: Map<ScalarType, IrSimpleFunctionSymbol> by lazy {
    mapOf(
      ScalarType.BOOL to readWriteBufferGetBoolean,
      ScalarType.BYTE to readWriteBufferGetByte,
      ScalarType.SHORT to readWriteBufferGetShort,
      ScalarType.INT to readWriteBufferGetInt,
      ScalarType.LONG to readWriteBufferGetLong,
      ScalarType.FLOAT to readWriteBufferGetFloat,
      ScalarType.DOUBLE to readWriteBufferGetDouble,
    )
  }

  fun readWriteBufferGetterFor(scalar: ScalarType): IrSimpleFunctionSymbol =
    readWriteBufferGetters[scalar]
      ?: error("ReadWriteBuffer getter not found for scalar type $scalar")

  fun flatBufferBuilderAddFor(scalar: ScalarType): IrSimpleFunctionSymbol =
    flatBufferBuilderAddFunctions[scalar]
      ?: error("FlatBufferBuilder.add overload not found for scalar type $scalar")

  private fun referenceClass(classId: ClassId): IrClassSymbol =
    pluginContext.referenceClass(classId)
      ?: error("Unable to resolve class symbol for ${classId.asSingleFqName()}")

  private fun tableProperty(name: String): IrPropertySymbol =
    tableClass.owner.declarations
      .filterIsInstance<IrProperty>()
      .singleOrNull { it.name.asString() == name }
      ?.symbol
      ?: error("Property $name not found on Table class")

  private fun findReadWriteBufferGetter(name: String, returnType: IrType? = null): IrSimpleFunctionSymbol =
    readWriteBufferClass.findFunction(name) { function ->
      function.regularValueParameters().let { parameters ->
        val matchesParameters = parameters.size == 1 && parameters[0].type == irBuiltIns.intType
        val matchesReturn = returnType?.let { function.returnType == it } ?: true
        matchesParameters && matchesReturn
      }
    }

  private fun findFlatBufferBuilderAdd(scalar: ScalarType): IrSimpleFunctionSymbol? {
    val expectedType = scalarIrType(scalar) ?: return null
    return flatBufferBuilderClass.functions
      .filter { it.owner.name.asString() == "add" && it.owner.regularValueParameters().size == 3 }
      .firstOrNull { function ->
        val parameters = function.owner.regularValueParameters()
        parameters[0].type == irBuiltIns.intType &&
          parameters[1].type == expectedType &&
          parameters[2].type == expectedType &&
          !parameters[2].type.isNullable()
      }
  }

  private fun IrClassSymbol.findFunction(
    name: String,
    predicate: (org.jetbrains.kotlin.ir.declarations.IrSimpleFunction) -> Boolean,
  ): IrSimpleFunctionSymbol =
    functions
      .filter { it.owner.name.asString() == name }
      .singleOrNull { predicate(it.owner) }
      ?: error("Function $name not found on ${owner.name.asString()}")

  private fun IrSimpleFunction.regularValueParameters(): List<IrValueParameter> =
    parameters.filter { parameter ->
      parameter.kind == IrParameterKind.Regular || parameter.kind == IrParameterKind.Context
    }

  private fun scalarIrType(scalar: ScalarType): IrType? =
    when (scalar) {
      ScalarType.BOOL -> irBuiltIns.booleanType
      ScalarType.BYTE -> irBuiltIns.byteType
      ScalarType.UBYTE -> null
      ScalarType.SHORT -> irBuiltIns.shortType
      ScalarType.USHORT -> null
      ScalarType.INT -> irBuiltIns.intType
      ScalarType.UINT -> null
      ScalarType.LONG -> irBuiltIns.longType
      ScalarType.ULONG -> null
      ScalarType.FLOAT -> irBuiltIns.floatType
      ScalarType.DOUBLE -> irBuiltIns.doubleType
    }

  companion object {
    private val RUNTIME_PACKAGE = FqName("com.google.flatbuffers.kotlin")
    private val TABLE_CLASS_ID = ClassId(RUNTIME_PACKAGE, Name.identifier("Table"))
    private val READ_WRITE_BUFFER_CLASS_ID = ClassId(RUNTIME_PACKAGE, Name.identifier("ReadWriteBuffer"))
    private val FLAT_BUFFER_BUILDER_CLASS_ID = ClassId(RUNTIME_PACKAGE, Name.identifier("FlatBufferBuilder"))
  }
}
