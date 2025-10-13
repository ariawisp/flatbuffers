package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.ast.ScalarType
import dev.flatbuffers.semantics.ResolvedEnum
import dev.flatbuffers.semantics.ResolvedStruct
import dev.flatbuffers.semantics.ResolvedTable
import dev.flatbuffers.semantics.ResolvedUnion
import org.jetbrains.kotlin.GeneratedDeclarationKey
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
import java.util.Locale

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
  private val offsetArrayClassId = ClassId(runtimePackage, Name.identifier("OffsetArray"))
  private val stringOffsetArrayClassId = ClassId(runtimePackage, Name.identifier("StringOffsetArray"))
  private val unionOffsetArrayClassId = ClassId(runtimePackage, Name.identifier("UnionOffsetArray"))

  private val readWriteBufferType by lazy { readWriteBufferClassId.toType() }
  private val readBufferType by lazy { readBufferClassId.toType() }
  private val flatBufferBuilderType by lazy { flatBufferBuilderClassId.toType() }
  private val unionOffsetType by lazy { unionOffsetClassId.toType() }

  private val tablesByClassId: Map<ClassId, ResolvedTable>
  private val structsByClassId: Map<ClassId, ResolvedStruct>
  private val enumsByClassId: Map<ClassId, ResolvedEnum>
  private val structFieldsByClassId: Map<ClassId, List<TableFieldModel>>
  private val tableFieldsByClassId: Map<ClassId, List<TableFieldModel>>
  private val rootTableClassIds: Set<ClassId>
  private val tableKeyFieldByClassId: Map<ClassId, TableFieldModel?>
  private val tableRequiredFieldsByClassId: Map<ClassId, List<TableFieldModel>>
  private val tablePropertyCache = mutableMapOf<ClassId, Map<Name, PropertySpec>>()
  private val tableFunctionCache = mutableMapOf<ClassId, Map<Name, List<FunctionSpec>>>()
  private val tableCompanionFunctionCache = mutableMapOf<ClassId, Map<Name, List<FunctionSpec>>>()

  private data class ParameterSpec(
    val name: String,
    val type: ConeKotlinType,
    val key: GeneratedDeclarationKey,
    val hasDefaultValue: Boolean = false,
    val isVararg: Boolean = false,
  )

  private data class FunctionSpec(
    val name: Name,
    val returnType: ConeKotlinType,
    val parameters: List<ParameterSpec>,
    val key: GeneratedDeclarationKey,
  )

  private data class PropertySpec(
    val name: Name,
    val returnType: ConeKotlinType,
    val key: GeneratedDeclarationKey,
    val isMutable: Boolean = false,
  )

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
    rootTableClassIds = schemaIndex.rootTables.mapTo(linkedSetOf()) { it.classId() }
    tableKeyFieldByClassId = tableFieldsByClassId.mapValues { (_, fields) -> fields.firstOrNull { it.isKey } }
    tableRequiredFieldsByClassId = tableFieldsByClassId.mapValues { (_, fields) -> fields.filter(TableFieldModel::isRequired) }
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
    tablesByClassId[classId]?.let { table ->
      val properties = tablePropertySpecs(table)
      val functions = tableFunctionSpecs(table)
      return buildSet {
        add(initName)
        add(resetName)
        addAll(properties.keys)
        addAll(functions.keys)
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
        val companionFunctions = tableCompanionFunctionSpecs(table)
        return buildSet {
          add(validateVersionName)
          add(asRootName)
          add(startName)
          add(endName)
          addAll(companionFunctions.keys)
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
    tablesByClassId[classId]?.let { table ->
      val propertySpec = tablePropertySpecs(table)[callableId.callableName] ?: return emptyList()
      return listOf(createProperty(owner, propertySpec))
    }
    structFieldsByClassId[classId] ?: run {
      if (classId.shortClassName == companionName) {
        return emptyList()
      }
      return emptyList()
    }
    return emptyList()
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
  private fun ResolvedUnion.classId(): ClassId = classIdFrom(namespace, name)

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
      else ->
        tableFunctionSpecs(table)[callableId.callableName]
          ?.map { createFunction(owner, it) }
          ?: emptyList()
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
      else ->
        tableCompanionFunctionSpecs(table)[callableId.callableName]
          ?.map { createFunction(owner, it) }
          ?: emptyList()
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

  private fun createFunction(
    owner: FirClassSymbol<*>,
    spec: FunctionSpec,
  ): FirNamedFunctionSymbol {
    val function =
      stubFunction(owner, spec.key, spec.name, spec.returnType) {
        spec.parameters.forEach { parameter ->
          valueParameter(
            parameter.name,
            parameter.type,
            parameter.key,
            hasDefaultValue = parameter.hasDefaultValue,
            isVararg = parameter.isVararg,
          )
        }
      }
    return function.symbol
  }

  private fun createProperty(
    owner: FirClassSymbol<*>,
    spec: PropertySpec,
  ): FirPropertySymbol {
    val property =
      stubProperty(
        owner,
        spec.key,
        spec.name,
        spec.returnType,
        isMutable = spec.isMutable,
      )
    return property.symbol
  }

  private fun tablePropertySpecs(table: ResolvedTable): Map<Name, PropertySpec> =
    tablePropertyCache.getOrPut(table.classId()) {
      val fields = tableFieldsByClassId[table.classId()].orEmpty()
      buildMap {
        fields.forEach { field ->
          when (val kind = field.kind) {
            is FieldKind.Scalar -> putIfAbsent(field.name, PropertySpec(field.name, scalarType(kind.scalar), FlatbuffersFirKeys.TableProperty))
            FieldKind.StringType -> {
              val type = StandardClassIds.String.toType(nullable = !field.isRequired())
              putIfAbsent(field.name, PropertySpec(field.name, type, FlatbuffersFirKeys.TableProperty))
            }
            is FieldKind.Struct -> {
              val structType = kind.struct.classId().toType(nullable = true)
              putIfAbsent(field.name, PropertySpec(field.name, structType, FlatbuffersFirKeys.TableProperty))
            }
            is FieldKind.Table -> {
              val referencedType = kind.table.classId().toType(nullable = true)
              putIfAbsent(field.name, PropertySpec(field.name, referencedType, FlatbuffersFirKeys.TableProperty))
            }
            is FieldKind.Union -> {
              val typePropertyName = Name.identifier("${field.name.asString()}Type")
              val unionType = kind.union.classId().toType()
              putIfAbsent(typePropertyName, PropertySpec(typePropertyName, unionType, FlatbuffersFirKeys.TableProperty))
            }
            is FieldKind.Vector -> {
              val lengthName = field.name.lengthName()
              putIfAbsent(lengthName, PropertySpec(lengthName, StandardClassIds.Int.toType(), FlatbuffersFirKeys.TableProperty))
            }
            else -> Unit
          }
        }
      }
    }

  private fun tableFunctionSpecs(table: ResolvedTable): Map<Name, List<FunctionSpec>> =
    tableFunctionCache.getOrPut(table.classId()) {
      val fields = tableFieldsByClassId[table.classId()].orEmpty()
      val accumulator = linkedMapOf<Name, MutableList<FunctionSpec>>()
      fields.forEach { field ->
        when (val kind = field.kind) {
          FieldKind.StringType -> {
            val name = Name.identifier("${field.name.asString()}AsBuffer")
            accumulator.getOrPut(name) { mutableListOf() } +=
              FunctionSpec(
                name = name,
                returnType = readBufferType,
                parameters = emptyList(),
                key = FlatbuffersFirKeys.TableMemberFunction,
              )
          }
          is FieldKind.Struct -> {
            val structType = kind.struct.classId().toType(nullable = true)
            val structParameterType = kind.struct.classId().toType(nullable = false)
            accumulator.getOrPut(field.name) { mutableListOf() } +=
              FunctionSpec(
                name = field.name,
                returnType = structType,
                parameters =
                  listOf(
                    ParameterSpec(
                      name = "obj",
                      type = structParameterType,
                      key = FlatbuffersFirKeys.TableMemberFunction,
                    ),
                  ),
                key = FlatbuffersFirKeys.TableMemberFunction,
              )
          }
          is FieldKind.Table -> {
            val referencedType = kind.table.classId().toType(nullable = true)
            val parameterType = kind.table.classId().toType(nullable = false)
            accumulator.getOrPut(field.name) { mutableListOf() } +=
              FunctionSpec(
                name = field.name,
                returnType = referencedType,
                parameters =
                  listOf(
                    ParameterSpec(
                      name = "obj",
                      type = parameterType,
                      key = FlatbuffersFirKeys.TableMemberFunction,
                    ),
                  ),
                key = FlatbuffersFirKeys.TableMemberFunction,
              )
            tableKeyParameterType(kind.table)?.let { keyType ->
              val keyParam =
                ParameterSpec(
                  name = "key",
                  type = keyType,
                  key = FlatbuffersFirKeys.TableMemberFunction,
                )
              accumulator.addFunction(
                FunctionSpec(
                  name = field.name.withSuffix("ByKey"),
                  returnType = referencedType,
                  parameters = listOf(keyParam),
                  key = FlatbuffersFirKeys.TableMemberFunction,
                )
              )
              accumulator.addFunction(
                FunctionSpec(
                  name = field.name.withSuffix("ByKey"),
                  returnType = referencedType,
                  parameters = listOf(
                    ParameterSpec(
                      name = "obj",
                      type = parameterType,
                      key = FlatbuffersFirKeys.TableMemberFunction,
                    ),
                    keyParam,
                  ),
                  key = FlatbuffersFirKeys.TableMemberFunction,
                )
              )
            }
          }
          is FieldKind.Union -> {
            val unionFunctionName = field.name
            val unionReturnType = tableClassId.toType(nullable = true)
            accumulator.getOrPut(unionFunctionName) { mutableListOf() } +=
              FunctionSpec(
                name = unionFunctionName,
                returnType = unionReturnType,
                parameters =
                  listOf(
                    ParameterSpec(
                      name = "obj",
                      type = tableType,
                      key = FlatbuffersFirKeys.TableMemberFunction,
                    ),
                  ),
                key = FlatbuffersFirKeys.TableMemberFunction,
              )
          }
          is FieldKind.Vector -> addVectorFunctionSpecs(accumulator, field, kind.elementKind)
          else -> Unit
        }
      }
      accumulator.mapValues { (_, value) -> value.toList() }
    }

  private fun tableCompanionFunctionSpecs(table: ResolvedTable): Map<Name, List<FunctionSpec>> =
    tableCompanionFunctionCache.getOrPut(table.classId()) {
      val unitType = StandardClassIds.Unit.toType()
      val tableConeType = table.classId().toType()
      val nullableTableType = table.classId().toType(nullable = true)
      val result = linkedMapOf<Name, MutableList<FunctionSpec>>()

      tableFieldsByClassId[table.classId()].orEmpty().forEach { field ->
        field.addFunctionParameterType()?.let { parameterType ->
          val builderParam = builderParameter()
          val valueParam =
            ParameterSpec(
              name = field.name.asString(),
              type = parameterType,
              key = FlatbuffersFirKeys.TableCompanionFunction,
            )
          result.addFunction(
            FunctionSpec(
              name = field.name.withPrefix("add"),
              returnType = unitType,
              parameters = listOf(builderParam, valueParam),
              key = FlatbuffersFirKeys.TableCompanionFunction,
            )
          )
        }

        if (field.kind is FieldKind.Vector) {
          addCompanionVectorFunctions(result, field)
        }
      }

      tableKeyParameterType(table)?.let { keyType ->
        val objParam =
          ParameterSpec(
            name = "obj",
            type = nullableTableType,
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        val vectorLocationParam =
          ParameterSpec(
            name = "vectorLocation",
            type = StandardClassIds.Int.toType(),
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        val keyParam =
          ParameterSpec(
            name = "key",
            type = keyType,
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        val bufferParam =
          ParameterSpec(
            name = "bb",
            type = readWriteBufferType,
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        result.addFunction(
          FunctionSpec(
            name = Name.identifier("lookupByKey"),
            returnType = nullableTableType,
            parameters = listOf(objParam, vectorLocationParam, keyParam, bufferParam),
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        )
      }

      if (table.classId() in rootTableClassIds) {
        val builderParam = builderParameter()
        val offsetType = offsetClassId.toType(table.classId().toType())
        val offsetParam =
          ParameterSpec(
            name = "offset",
            type = offsetType,
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        result.addFunction(
          FunctionSpec(
            name = finishFunctionName(table),
            returnType = unitType,
            parameters = listOf(builderParam, offsetParam),
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        )
        result.addFunction(
          FunctionSpec(
            name = finishSizePrefixedFunctionName(table),
            returnType = unitType,
            parameters = listOf(builderParam, offsetParam),
            key = FlatbuffersFirKeys.TableCompanionFunction,
          )
        )
      }

      result.mapValues { (_, value) -> value.toList() }
    }

  private fun TableFieldModel.propertyType(): ConeKotlinType? =
    when (val kind = kind) {
      is FieldKind.Scalar -> scalarType(kind.scalar)
      FieldKind.StringType -> StandardClassIds.String.toType(nullable = !isRequired())
      is FieldKind.Struct -> kind.struct.classId().toType(nullable = true)
      is FieldKind.Table -> kind.table.classId().toType(nullable = true)
      else -> null
    }

  private fun TableFieldModel.isRequired(): Boolean =
    field.attributes.any { it.name.equals("required", ignoreCase = true) }

  private fun addVectorFunctionSpecs(
    accumulator: MutableMap<Name, MutableList<FunctionSpec>>,
    field: TableFieldModel,
    elementKind: FieldKind,
  ) {
    val indexParam =
      ParameterSpec(
        name = "j",
        type = StandardClassIds.Int.toType(),
        key = FlatbuffersFirKeys.TableMemberFunction,
      )
    when (elementKind) {
      is FieldKind.Scalar -> {
        val elementType = scalarType(elementKind.scalar)
        accumulator.addFunction(FunctionSpec(field.name, elementType, listOf(indexParam), FlatbuffersFirKeys.TableMemberFunction))
        val asBufferName = field.name.asBufferName()
        accumulator.addFunction(FunctionSpec(asBufferName, readBufferType, emptyList(), FlatbuffersFirKeys.TableMemberFunction))
      }
      FieldKind.StringType -> {
        val elementType = StandardClassIds.String.toType(nullable = true)
        accumulator.addFunction(FunctionSpec(field.name, elementType, listOf(indexParam), FlatbuffersFirKeys.TableMemberFunction))
        val asBufferName = field.name.asBufferName()
        accumulator.addFunction(FunctionSpec(asBufferName, readBufferType, emptyList(), FlatbuffersFirKeys.TableMemberFunction))
      }
      is FieldKind.Struct -> {
        val structType = elementKind.struct.classId().toType(nullable = true)
        val structParamType = elementKind.struct.classId().toType(nullable = false)
        val objParam =
          ParameterSpec(
            name = "obj",
            type = structParamType,
            key = FlatbuffersFirKeys.TableMemberFunction,
          )
        accumulator.addFunction(FunctionSpec(field.name, structType, listOf(indexParam), FlatbuffersFirKeys.TableMemberFunction))
        accumulator.addFunction(FunctionSpec(field.name, structType, listOf(objParam, indexParam), FlatbuffersFirKeys.TableMemberFunction))
      }
      is FieldKind.Table -> {
        val tableReturnType = elementKind.table.classId().toType(nullable = true)
        val tableParamType = elementKind.table.classId().toType(nullable = false)
        val objParam =
          ParameterSpec(
            name = "obj",
            type = tableParamType,
            key = FlatbuffersFirKeys.TableMemberFunction,
          )
        accumulator.addFunction(FunctionSpec(field.name, tableReturnType, listOf(indexParam), FlatbuffersFirKeys.TableMemberFunction))
        accumulator.addFunction(FunctionSpec(field.name, tableReturnType, listOf(objParam, indexParam), FlatbuffersFirKeys.TableMemberFunction))
      }
      is FieldKind.Union -> {
        val typeName = field.name.typeName()
        val unionEnumType = elementKind.union.classId().toType()
        accumulator.addFunction(FunctionSpec(typeName, unionEnumType, listOf(indexParam), FlatbuffersFirKeys.TableMemberFunction))
        val objParam =
          ParameterSpec(
            name = "obj",
            type = tableType,
            key = FlatbuffersFirKeys.TableMemberFunction,
          )
        val unionReturnType = tableClassId.toType(nullable = true)
        accumulator.addFunction(FunctionSpec(field.name, unionReturnType, listOf(objParam, indexParam), FlatbuffersFirKeys.TableMemberFunction))
      }
      else -> Unit
    }
  }

  private fun MutableMap<Name, MutableList<FunctionSpec>>.addFunction(spec: FunctionSpec) {
    getOrPut(spec.name) { mutableListOf() } += spec
  }

  private fun TableFieldModel.addFunctionParameterType(): ConeKotlinType? =
    when (val kind = kind) {
      is FieldKind.Scalar -> scalarType(kind.scalar)
      FieldKind.StringType -> offsetClassId.toType(StandardClassIds.String.toType())
      is FieldKind.Struct -> offsetClassId.toType(kind.struct.classId().toType())
      is FieldKind.Table -> offsetClassId.toType(kind.table.classId().toType())
      is FieldKind.Union -> unionOffsetType
      is FieldKind.Vector -> vectorOffsetType(kind.elementKind)
      else -> null
    }

  private fun addCompanionVectorFunctions(
    accumulator: MutableMap<Name, MutableList<FunctionSpec>>,
    field: TableFieldModel,
  ) {
    val vectorKind = field.kind as? FieldKind.Vector ?: return
    val vectorOffsetType = vectorOffsetType(vectorKind.elementKind) ?: return
    val vectorArrayType = vectorArrayParameterType(vectorKind.elementKind) ?: return

    val builderParam = builderParameter()
    val vectorParam =
      ParameterSpec(
        name = "vector",
        type = vectorArrayType,
        key = FlatbuffersFirKeys.TableCompanionFunction,
      )
    accumulator.addFunction(
      FunctionSpec(
        name = field.name.withPrefix("create", "Vector"),
        returnType = vectorOffsetType,
        parameters = listOf(builderParam, vectorParam),
        key = FlatbuffersFirKeys.TableCompanionFunction,
      )
    )

    val numElemsParam =
      ParameterSpec(
        name = "numElems",
        type = StandardClassIds.Int.toType(),
        key = FlatbuffersFirKeys.TableCompanionFunction,
      )
    accumulator.addFunction(
      FunctionSpec(
        name = field.name.withPrefix("start", "Vector"),
        returnType = StandardClassIds.Unit.toType(),
        parameters = listOf(builderParam, numElemsParam),
        key = FlatbuffersFirKeys.TableCompanionFunction,
      )
    )
  }

  private fun builderParameter(): ParameterSpec =
    ParameterSpec("builder", flatBufferBuilderType, FlatbuffersFirKeys.TableCompanionFunction)

  private fun vectorOffsetType(kind: FieldKind): ConeKotlinType? =
    vectorElementType(kind)?.let { vectorOffsetClassId.toType(it) }

  private fun vectorElementType(kind: FieldKind): ConeKotlinType? =
    when (kind) {
      is FieldKind.Scalar -> scalarType(kind.scalar)
      FieldKind.StringType -> StandardClassIds.String.toType()
      is FieldKind.Struct -> kind.struct.classId().toType()
      is FieldKind.Table -> kind.table.classId().toType()
      is FieldKind.Union -> unionOffsetType
      else -> null
    }

  private fun vectorArrayParameterType(kind: FieldKind): ConeKotlinType? =
    when (kind) {
      is FieldKind.Scalar -> scalarArrayType(kind.scalar)
      FieldKind.StringType -> stringOffsetArrayClassId.toType()
      is FieldKind.Struct -> offsetArrayClassId.toType(kind.struct.classId().toType())
      is FieldKind.Table -> offsetArrayClassId.toType(kind.table.classId().toType())
      is FieldKind.Union -> unionOffsetArrayClassId.toType()
      else -> null
    }

  private fun scalarArrayType(scalar: ScalarType): ConeKotlinType? {
    val elementClassId = scalarClassId(scalar)
    val primitiveArray = StandardClassIds.primitiveArrayTypeByElementType[elementClassId]
    val unsignedArray = StandardClassIds.unsignedArrayTypeByElementType[elementClassId]
    val arrayClassId = primitiveArray ?: unsignedArray
    return arrayClassId?.toType() ?: StandardClassIds.Array.toType(scalarType(scalar))
  }

  private fun scalarClassId(scalar: ScalarType): ClassId =
    when (scalar) {
      ScalarType.BOOL -> StandardClassIds.Boolean
      ScalarType.BYTE -> StandardClassIds.Byte
      ScalarType.UBYTE -> StandardClassIds.UByte
      ScalarType.SHORT -> StandardClassIds.Short
      ScalarType.USHORT -> StandardClassIds.UShort
      ScalarType.INT -> StandardClassIds.Int
      ScalarType.UINT -> StandardClassIds.UInt
      ScalarType.LONG -> StandardClassIds.Long
      ScalarType.ULONG -> StandardClassIds.ULong
      ScalarType.FLOAT -> StandardClassIds.Float
      ScalarType.DOUBLE -> StandardClassIds.Double
    }

  private fun tableKeyParameterType(table: ResolvedTable): ConeKotlinType? {
    val keyField = tableKeyFieldByClassId[table.classId()] ?: return null
    return when (val kind = keyField.kind) {
      is FieldKind.Scalar -> scalarType(kind.scalar)
      FieldKind.StringType -> StandardClassIds.String.toType()
      else -> keyField.propertyType()
    }
  }

  private fun finishFunctionName(table: ResolvedTable): Name =
    Name.identifier("finish${table.name.capitalizeAscii()}Buffer")

  private fun finishSizePrefixedFunctionName(table: ResolvedTable): Name =
    Name.identifier("finishSizePrefixed${table.name.capitalizeAscii()}Buffer")

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

  private fun String.capitalizeAscii(): String =
    replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.ROOT) else it.toString() }

  private fun Name.withPrefix(prefix: String, suffix: String = ""): Name =
    Name.identifier(prefix + asString().capitalizeAscii() + suffix)

  private fun Name.lengthName(): Name = Name.identifier("${asString()}Length")
  private fun Name.asBufferName(): Name = Name.identifier("${asString()}AsBuffer")
  private fun Name.typeName(): Name = Name.identifier("${asString()}Type")
  private fun Name.withSuffix(suffix: String): Name = Name.identifier("${asString()}$suffix")

  private fun classIdFrom(namespace: String?, simpleName: String): ClassId {
    val packageFqName = namespace?.takeIf { it.isNotBlank() }?.let(::FqName) ?: FqName.ROOT
    return ClassId(packageFqName, Name.identifier(simpleName))
  }
}
