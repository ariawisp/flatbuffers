package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.ast.ScalarType
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.semantics.ResolvedArrayType
import dev.flatbuffers.semantics.ResolvedField
import dev.flatbuffers.semantics.ResolvedNamedType
import dev.flatbuffers.semantics.ResolvedScalarType
import dev.flatbuffers.semantics.ResolvedStringType
import dev.flatbuffers.semantics.ResolvedTable
import dev.flatbuffers.semantics.ResolvedStruct
import dev.flatbuffers.semantics.ResolvedType
import dev.flatbuffers.semantics.ResolvedUnion
import dev.flatbuffers.semantics.ResolvedUnresolvedType
import dev.flatbuffers.semantics.ResolvedVectorType
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal data class TableFieldModel(
  val index: Int,
  val name: Name,
  val field: ResolvedField,
  val kind: FieldKind,
)

internal sealed interface FieldKind {
  data class Scalar(val scalar: ScalarType) : FieldKind
  data object StringType : FieldKind
  data class Struct(val struct: ResolvedStruct) : FieldKind
  data class Table(val table: ResolvedTable) : FieldKind
  data class Union(val union: ResolvedUnion) : FieldKind
  data class Vector(val elementKind: FieldKind) : FieldKind
  data class Array(val elementKind: FieldKind, val length: Int) : FieldKind
  data object Unknown : FieldKind
}

internal fun ResolvedTable.toFieldModels(schemaIndex: SchemaIndex): List<TableFieldModel> =
  fields.mapIndexed { index, field ->
    TableFieldModel(
      index = index,
      name = Name.identifier(field.name),
      field = field,
      kind = field.type.toFieldKind(schemaIndex),
    )
  }

private fun ResolvedType.toFieldKind(schemaIndex: SchemaIndex): FieldKind =
  when (this) {
    is ResolvedScalarType -> FieldKind.Scalar(scalar)
    ResolvedStringType -> FieldKind.StringType
    is ResolvedNamedType -> toNamedKind(schemaIndex)
    is ResolvedVectorType -> FieldKind.Vector(elementType.toFieldKind(schemaIndex))
    is ResolvedArrayType -> FieldKind.Array(elementType.toFieldKind(schemaIndex), length)
    is ResolvedUnresolvedType -> FieldKind.Unknown
  }

private fun ResolvedNamedType.toNamedKind(schemaIndex: SchemaIndex): FieldKind {
  val declaration = declaration
  if (declaration is ResolvedTable) return FieldKind.Table(declaration)
  if (declaration is ResolvedStruct) return FieldKind.Struct(declaration)
  if (declaration is ResolvedUnion) return FieldKind.Union(declaration)
  val classId = classId() ?: return FieldKind.Unknown
  schemaIndex.tableFor(classId)?.let { return FieldKind.Table(it) }
  schemaIndex.structFor(classId)?.let { return FieldKind.Struct(it) }
  schemaIndex.unionFor(classId)?.let { return FieldKind.Union(it) }
  return FieldKind.Unknown
}

private fun ResolvedNamedType.classId(): ClassId? =
  runCatching { ClassId.topLevel(FqName(name)) }.getOrNull()
