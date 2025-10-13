package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.flatc.kotlin.compat.CompatContext
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.semantics.ResolvedEnum
import dev.flatbuffers.semantics.ResolvedStruct
import dev.flatbuffers.semantics.ResolvedTable
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.plugin.createTopLevelClass
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.toFirResolvedTypeRef
import org.jetbrains.kotlin.fir.types.constructClassLikeType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

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

  private val tablesByClassId: Map<ClassId, ResolvedTable>
  private val structsByClassId: Map<ClassId, ResolvedStruct>
  private val enumsByClassId: Map<ClassId, ResolvedEnum>

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
  }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun getTopLevelClassIds(): Set<ClassId> =
    buildSet {
      addAll(tablesByClassId.keys)
      addAll(structsByClassId.keys)
      addAll(enumsByClassId.keys)
    }

  @ExperimentalTopLevelDeclarationsGenerationApi
  override fun generateTopLevelClassLikeDeclaration(classId: ClassId): FirClassLikeSymbol<*>? {
    tablesByClassId[classId]?.let { return generateTableClass(classId) }
    structsByClassId[classId]?.let { return generateStructClass(classId) }
    enumsByClassId[classId]?.let { return generateEnumClass(classId) }
    return null
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

  private fun classIdFrom(namespace: String?, simpleName: String): ClassId {
    val packageFqName = namespace?.takeIf { it.isNotBlank() }?.let(::FqName) ?: FqName.ROOT
    return ClassId(packageFqName, Name.identifier(simpleName))
  }
}
