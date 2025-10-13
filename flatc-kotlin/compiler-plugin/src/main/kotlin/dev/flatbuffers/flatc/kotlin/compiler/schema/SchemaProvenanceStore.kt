package dev.flatbuffers.flatc.kotlin.compiler.schema

import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.SourceSpan
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId

/**
 * Tracks the schema provenance for generated Kotlin declarations so the IR pass can recover source
 * spans and documentation without recomputing the mapping logic.
 */
internal class SchemaProvenanceStore {
  private val classEntries = ConcurrentHashMap<ClassId, SchemaProvenance>()
  private val callableEntries = ConcurrentHashMap<CallableKey, SchemaProvenance>()

  fun recordClass(
    classId: ClassId,
    span: SourceSpan?,
    docComment: DocComment?,
  ) {
    classEntries[classId] = SchemaProvenance(span, docComment)
  }

  fun recordFunction(
    callableId: CallableId,
    span: SourceSpan?,
    docComment: DocComment?,
  ) {
    recordCallable(callableId, SchemaCallableKind.FUNCTION, span, docComment)
  }

  fun recordProperty(
    callableId: CallableId,
    span: SourceSpan?,
    docComment: DocComment?,
  ) {
    recordCallable(callableId, SchemaCallableKind.PROPERTY, span, docComment)
  }

  fun classProvenance(classId: ClassId): SchemaProvenance? = classEntries[classId]

  fun functionProvenance(callableId: CallableId): SchemaProvenance? =
    callableEntries[CallableKey(callableId, SchemaCallableKind.FUNCTION)]

  fun propertyProvenance(callableId: CallableId): SchemaProvenance? =
    callableEntries[CallableKey(callableId, SchemaCallableKind.PROPERTY)]

  private fun recordCallable(
    callableId: CallableId,
    kind: SchemaCallableKind,
    span: SourceSpan?,
    docComment: DocComment?,
  ) {
    callableEntries[CallableKey(callableId, kind)] = SchemaProvenance(span, docComment)
  }
}

internal data class SchemaProvenance(
  val span: SourceSpan?,
  val docComment: DocComment?,
)

internal enum class SchemaCallableKind {
  FUNCTION,
  PROPERTY,
}

private data class CallableKey(
  val callableId: CallableId,
  val kind: SchemaCallableKind,
)
