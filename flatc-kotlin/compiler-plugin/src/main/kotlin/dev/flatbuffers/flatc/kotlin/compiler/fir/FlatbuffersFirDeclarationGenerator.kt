package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.ast.ScalarType
import dev.flatbuffers.semantics.ResolvedEnum
import dev.flatbuffers.semantics.ResolvedStruct
import dev.flatbuffers.semantics.ResolvedTable
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.extensions.DeclarationGenerationContext
import org.jetbrains.kotlin.fir.extensions.NestedClassGenerationContext
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.plugin.createCompanionObject
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirNamedFunctionSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol
import org.jetbrains.kotlin.fir.resolve.defaultType
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.fir.types.ConeKotlinTypeProjectionOut
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.name.StandardClassIds

internal class FlatbuffersFirDeclarationGenerator(
  session: FirSession,
  private val schemaIndex: SchemaIndex,
  @Suppress("UNUSED_PARAMETER") private val options: FlatbuffersPluginOptions,
  @Suppress("UNUSED_PARAMETER") private val compatContext: CompatContext,
) : FirDeclarationGenerationExtension(session) {

  private val tableClassId = ClassId(FqName("com.google.flatbuffers.kotlin"), Name.identifier("Table"))
  private val structClassId = ClassId(FqName("com.google.flatbuffers.kotlin"), Name.identifier("Struct"))

  private val tableType by lazy { tableClassId.constructClassLikeType(emptyArray(), false) }
  private val structType by lazy { structClassId.constructClassLikeType(emptyArray(), false) }

  private val runtimePackage = FqName("com.google.flatbuffers.kotlin")
  private val readWriteBufferClassId = ClassId(runtimePackage, Name.identifier("ReadWriteBuffer"))
  private val readBufferClassId = ClassId(runtimePackage, Name.identifier("ReadBuffer"))
  private val flatBufferBuilderClassId = ClassId(runtimePackage, Name.identifier("FlatBufferBuilder"))
  private val offsetClassId = ClassId(runtimePackage, Name.identifier("Offset"))
  private val vectorOffsetClassId = ClassId(runtimePackage, Name.identifier("VectorOffset"))
  private val unionOffsetClassId = ClassId(runtimePackage, Name.identifier("UnionOffset"))

  private val readWriteBufferType by lazy { readWriteBufferClassId.toType() }
  private val readBufferType by lazy { readBufferClassId.toType() }
  private val flatBufferBuilderType by lazy { flatBufferBuilderClassId.toType() }
  private val unionOffsetType by lazy { unionOffsetClassId.toType() }

  private val tablesByClassId: Map<ClassId, ResolvedTable>
  private val structsByClassId: Map<ClassId, ResolvedStruct>
  private val enumsByClassId: Map<ClassId, ResolvedEnum>
  private val structFieldsByClassId: Map<ClassId, List<TableFieldModel>>
  private val tableFieldsByClassId: Map<ClassId, List<TableFieldModel>>

  init {
    val tableMap = linkedMapOf<ClassId, ResolvedTable>()
    val structMap = linkedMapOf<ClassId, ResolvedStruct>()
    val enumMap = linkedMapOf<ClassId, ResolvedEnum>()
    schemaIndex.schemas.forEach { schema ->
      schema.declarations.values.forEach { declaration ->
        when (declaration) {
          is ResolvedTable -> tableMap.putIfAbsent(declaration.classId(), declaration)
          is ResolvedStruct -> structMap.putIfAbsent(declaration.classId(), declaration)
          is ResolvedEnum -> enumMap.putIfAbsent(declaration.classId(), declaration)
          else -> Unit
        }
      }
    }
    tablesByClassId = tableMap
    structsByClassId = structMap
    enumsByClassId = enumMap
    structFieldsByClassId = structMap.mapValues { (_, struct) -> struct.toFieldModels(schemaIndex) }
    tableFieldsByClassId = tableMap.mapValues { (_, table) -> table.toFieldModels(schemaIndex) }
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> =
    buildSet {
      addAll(tablesByClassId.keys)
      addAll(structsByClassId.keys)
      addAll(enumsByClassId.keys)
    }

  private val companionName = Name.identifier("Companion")
  private val initName = Name.identifier("init")
  private val resetName = Name.identifier("reset")
  private val validateVersionName = Name.identifier("validateVersion")
  private val asRootName = Name.identifier("asRoot")

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    tablesByClassId[classId]?.let { return generateTableClass(classId) }
    structsByClassId[classId]?.let { return generateStructClass(classId) }
    enumsByClassId[classId]?.let { return generateEnumClass(classId) }
    return null
  }

  override fun getNestedClassifiersNames(
    classSymbol: FirClassSymbol<*>,
    context: NestedClassGenerationContext,
  ): Set<Name> {
    val classId = classSymbol.classId
    return when {
      tablesByClassId.containsKey(classId) -> setOf(companionName)
      structsByClassId.containsKey(classId) -> setOf(companionName)
      enumsByClassId.containsKey(classId) -> setOf(companionName)
      else -> emptySet()
    }
  }

  override fun generateNestedClassLikeDeclaration(
    owner: FirClassSymbol<*>,
    name: Name,
    context: NestedClassGenerationContext,
  ): FirClassLikeSymbol<*>? {
    if (name != companionName) return null
    val classId = owner.classId
    return when {
      tablesByClassId.containsKey(classId) ->
        createCompanionObject(owner, FlatbuffersFirKeys.TableCompanionObject).symbol
      structsByClassId.containsKey(classId) ->
        createCompanionObject(owner, FlatbuffersFirKeys.StructCompanionObject).symbol
      enumsByClassId.containsKey(classId) ->
        createCompanionObject(owner, FlatbuffersFirKeys.EnumCompanionObject).symbol
      else -> null
    }
  }

  override fun getCallableNamesForClass(
    classSymbol: FirClassSymbol<*>,
    context: DeclarationGenerationContext.Member,
  ): Set<Name> {
    val classId = classSymbol.classId
    tablesByClassId[classId]?.let {
      return buildSet {
        add(initName)
        add(resetName)
        tableFieldsByClassId[classId]?.forEach { field -> add(field.name) }
      }
    }
    structsByClassId[classId]?.let {
      return buildSet {
        add(initName)
        structFieldsByClassId[classId]?.forEach { field -> add(field.name) }
      }
    }
    if (classId.shortClassName == companionName) {
      val outerClassId = classId.outerClassId ?: return emptySet()
      tablesByClassId[outerClassId]?.let { table ->
        val startName = startFunctionName(table)
        val endName = endFunctionName(table)
        return buildSet {
          add(validateVersionName)
          add(asRootName)
          add(startName)
          add(endName)
        }
      }
      structsByClassId[outerClassId]?.let { struct ->
        val createName = createStructFunctionName(struct)
        return buildSet {
          add(createName)
        }
      }
    }
    return emptySet()
  }

  override fun generateFunctions(
    callableId: CallableId,
    context: DeclarationGenerationContext.Member?,
  ): List<FirNamedFunctionSymbol> {
    val owner = context?.owner ?: return emptyList()
    val classId = owner.classId
    tablesByClassId[classId]?.let { table ->
      return generateTableFunction(owner, table, callableId)
    }
    structsByClassId[classId]?.let { struct ->
      return generateStructFunction(owner, struct, callableId)
    }
    if (classId.shortClassName == companionName) {
      val outerClassId = classId.outerClassId ?: return emptyList()
      tablesByClassId[outerClassId]?.let { table ->
        return generateTableCompanionFunction(owner, table, callableId)
      }
      structsByClassId[outerClassId]?.let { struct ->
        return generateStructCompanionFunction(owner, struct, callableId)
      }
    }
    return emptyList()
  }

  override fun generateProperties(
    callableId: CallableId,
    context: DeclarationGenerationContext.Member?,
  ): List<FirPropertySymbol> {
    val owner = context?.owner ?: return emptyList()
    val classId = owner.classId
    val fieldModels =
      tableFieldsByClassId[classId]
        ?: structFieldsByClassId[classId]
        ?: run {
          if (classId.shortClassName == companionName) {
            return emptyList()
          }
          return emptyList()
        }
    val targetField = fieldModels.firstOrNull { it.name == callableId.callableName } ?: return emptyList()
    val propertyType = targetField.propertyType() ?: return emptyList()
    val property =
      stubProperty(owner, FlatbuffersFirKeys.TableProperty, callableId.callableName, propertyType)
    return listOf(property.symbol)
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  private fun generateTableClass(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, FlatbuffersFirKeys.TableClass).apply {
      replaceSuperTypeRefs(superTypeRefs + tableType.toFirResolvedTypeRef())
    }.symbol
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  private fun generateStructClass(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, FlatbuffersFirKeys.StructClass).apply {
      replaceSuperTypeRefs(superTypeRefs + structType.toFirResolvedTypeRef())
    }.symbol
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  private fun generateEnumClass(classId: ClassId): FirClassLikeSymbol<*> {
    return createTopLevelClass(classId, FlatbuffersFirKeys.EnumClass).symbol
  }

  private fun ResolvedTable.classId(): ClassId = classIdFrom(namespace, name)
  private fun ResolvedStruct.classId(): ClassId = classIdFrom(namespace, name)
  private fun ResolvedEnum.classId(): ClassId = classIdFrom(namespace, name)

  private fun generateTableFunction(
    owner: FirClassSymbol<*>,
    table: ResolvedTable,
    callableId: CallableId,
  ): List<FirNamedFunctionSymbol> =
    when (callableId.callableName) {
      initName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.TableMemberFunction, initName, owner.defaultType()) {
            valueParameter("i", StandardClassIds.Int.toType(), FlatbuffersFirKeys.TableMemberFunction)
            valueParameter("buffer", readWriteBufferType, FlatbuffersFirKeys.TableMemberFunction)
          }.symbol
        )
      resetName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.TableMemberFunction, resetName, owner.defaultType()) {
            valueParameter("i", StandardClassIds.Int.toType(), FlatbuffersFirKeys.TableMemberFunction)
            valueParameter("buffer", readWriteBufferType, FlatbuffersFirKeys.TableMemberFunction)
          }.symbol
        )
      else -> emptyList()
    }

  private fun generateTableCompanionFunction(
    owner: FirClassSymbol<*>,
    table: ResolvedTable,
    callableId: CallableId,
  ): List<FirNamedFunctionSymbol> {
    val tableType = table.classId().toType()
    val startName = startFunctionName(table)
    val endName = endFunctionName(table)
    return when (callableId.callableName) {
      validateVersionName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.TableCompanionFunction, validateVersionName, StandardClassIds.Int.toType())
            .symbol
        )
      asRootName -> {
        val primary =
          stubFunction(owner, FlatbuffersFirKeys.TableCompanionFunction, asRootName, tableType) {
            valueParameter("buffer", readWriteBufferType, FlatbuffersFirKeys.TableCompanionFunction)
          }
        val reuse =
          stubFunction(owner, FlatbuffersFirKeys.TableCompanionFunction, asRootName, tableType) {
            valueParameter("buffer", readWriteBufferType, FlatbuffersFirKeys.TableCompanionFunction)
            valueParameter("obj", tableType, FlatbuffersFirKeys.TableCompanionFunction)
          }
        listOf(primary.symbol, reuse.symbol)
      }
      startName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.TableCompanionFunction, startName, StandardClassIds.Unit.toType()) {
            valueParameter("builder", flatBufferBuilderType, FlatbuffersFirKeys.TableCompanionFunction)
          }.symbol
        )
      endName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.TableCompanionFunction, endName, offsetClassId.toType(tableType)) {
            valueParameter("builder", flatBufferBuilderType, FlatbuffersFirKeys.TableCompanionFunction)
          }.symbol
        )
      else -> emptyList()
    }
  }

  private fun generateStructFunction(
    owner: FirClassSymbol<*>,
    struct: ResolvedStruct,
    callableId: CallableId,
  ): List<FirNamedFunctionSymbol> =
    when (callableId.callableName) {
      initName ->
        listOf(
          stubFunction(owner, FlatbuffersFirKeys.StructMemberFunction, initName, owner.defaultType()) {
            valueParameter("i", StandardClassIds.Int.toType(), FlatbuffersFirKeys.StructMemberFunction)
            valueParameter("buffer", readWriteBufferType, FlatbuffersFirKeys.StructMemberFunction)
          }.symbol
        )
      else -> emptyList()
    }

  private fun generateStructCompanionFunction(
    owner: FirClassSymbol<*>,
    struct: ResolvedStruct,
    callableId: CallableId,
  ): List<FirNamedFunctionSymbol> {
    val createName = createStructFunctionName(struct)
    return when (callableId.callableName) {
      createName -> {
        val structType = struct.classId().toType()
        val parameters = structFieldsByClassId[struct.classId()].orEmpty()
        val function =
          stubFunction(owner, FlatbuffersFirKeys.StructCompanionFunction, createName, offsetClassId.toType(structType)) {
            valueParameter("builder", flatBufferBuilderType, FlatbuffersFirKeys.StructCompanionFunction)
            parameters.forEach { field ->
              val parameterType = field.propertyType() ?: return@forEach
              valueParameter(field.name.asString(), parameterType, FlatbuffersFirKeys.StructCompanionFunction)
            }
          }
        listOf(function.symbol)
      }
      else -> emptyList()
    }
  }

  private fun TableFieldModel.propertyType(): ConeKotlinType? =
    when (val kind = kind) {
      is FieldKind.Scalar -> scalarType(kind.scalar)
      FieldKind.StringType -> StandardClassIds.String.toType(nullable = true)
      is FieldKind.Struct -> kind.struct.classId().toType(nullable = true)
      is FieldKind.Table -> kind.table.classId().toType(nullable = true)
      else -> null
    }

  private fun scalarType(scalar: ScalarType): ConeKotlinType =
    when (scalar) {
      ScalarType.BOOL -> StandardClassIds.Boolean.toType()
      ScalarType.BYTE -> StandardClassIds.Byte.toType()
      ScalarType.UBYTE -> StandardClassIds.UByte.toType()
      ScalarType.SHORT -> StandardClassIds.Short.toType()
      ScalarType.USHORT -> StandardClassIds.UShort.toType()
      ScalarType.INT -> StandardClassIds.Int.toType()
      ScalarType.UINT -> StandardClassIds.UInt.toType()
      ScalarType.LONG -> StandardClassIds.Long.toType()
      ScalarType.ULONG -> StandardClassIds.ULong.toType()
      ScalarType.FLOAT -> StandardClassIds.Float.toType()
      ScalarType.DOUBLE -> StandardClassIds.Double.toType()
    }

  private fun startFunctionName(table: ResolvedTable): Name = Name.identifier("start${table.name}")

  private fun endFunctionName(table: ResolvedTable): Name = Name.identifier("end${table.name}")

  private fun createStructFunctionName(struct: ResolvedStruct): Name = Name.identifier("create${struct.name}")

  private fun ClassId.toType(
    vararg arguments: ConeKotlinType,
    nullable: Boolean = false,
  ): ConeKotlinType =
    constructClassLikeType(
      arguments.map { ConeKotlinTypeProjectionOut(it) }.toTypedArray(),
      nullable,
    )

  private fun classIdFrom(namespace: String?, simpleName: String): ClassId {
    val packageFqName = namespace?.takeIf { it.isNotBlank() }?.let(::FqName) ?: FqName.ROOT
    return ClassId(packageFqName, Name.identifier(simpleName))
  }
}
