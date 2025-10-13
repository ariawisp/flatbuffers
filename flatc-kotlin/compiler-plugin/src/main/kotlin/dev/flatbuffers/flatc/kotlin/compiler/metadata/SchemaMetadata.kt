package dev.flatbuffers.flatc.kotlin.compiler.metadata

import dev.flatbuffers.semantics.ResolvedEnum
import dev.flatbuffers.semantics.ResolvedField
import dev.flatbuffers.semantics.ResolvedNamedType
import dev.flatbuffers.semantics.ResolvedStruct
import dev.flatbuffers.semantics.ResolvedTable
import dev.flatbuffers.semantics.ResolvedUnion
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name

internal data class RequiredFieldMetadata(
  val fieldName: String,
  val vtableOffset: Int,
)

internal fun requiredFieldMetadata(table: ResolvedTable): List<RequiredFieldMetadata> =
  table.fields.mapIndexedNotNull { index, field ->
    if (field.isRequiredField()) {
      RequiredFieldMetadata(field.name, vtableOffsetFor(index))
    } else {
      null
    }
  }

private fun vtableOffsetFor(fieldIndex: Int): Int = 4 + fieldIndex * 2

internal fun ResolvedField.isRequiredField(): Boolean =
  attributes.any { it.name.equals("required", ignoreCase = true) }

internal fun classIdFrom(
  namespace: String?,
  simpleName: String,
): ClassId {
  val packageFqName = namespace?.takeIf { it.isNotBlank() }?.let(::FqName) ?: FqName.ROOT
  return ClassId(packageFqName, Name.identifier(simpleName))
}

internal fun ClassId.offsetArrayClassId(): ClassId =
  ClassId(packageFqName, Name.identifier("${shortClassName.asString()}OffsetArray"))

internal fun ResolvedTable.classId(): ClassId = classIdFrom(namespace, name)
internal fun ResolvedStruct.classId(): ClassId = classIdFrom(namespace, name)
internal fun ResolvedEnum.classId(): ClassId = classIdFrom(namespace, name)
internal fun ResolvedUnion.classId(): ClassId = classIdFrom(namespace, name)
internal fun ResolvedNamedType.classId(): ClassId? =
  runCatching { ClassId.topLevel(FqName(name)) }.getOrNull()
