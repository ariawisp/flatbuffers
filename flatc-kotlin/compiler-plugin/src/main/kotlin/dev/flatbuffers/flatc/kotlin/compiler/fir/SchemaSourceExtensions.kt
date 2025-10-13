package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.SourceSpan
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElementKind
import org.jetbrains.kotlin.fir.plugin.PropertyBuildingContext
import org.jetbrains.kotlin.fir.plugin.SimpleFunctionBuildingContext
import org.jetbrains.kotlin.fir.plugin.ClassBuildingContext

/**
 * Applies schema source information to a [ClassBuildingContext] and returns the collapsed doc
 * comment (if any) so callers can attach it after the FIR declaration has been built.
 */
internal fun ClassBuildingContext.withSchemaSource(
  span: SourceSpan?,
  docComment: DocComment?,
  index: SchemaSourceIndex,
  kind: KtSourceElementKind? = null,
): String? {
  val element = index.element(span, kind ?: KtRealSourceElementKind)
  if (element != null) source = element
  return index.doc(docComment)
}

internal fun PropertyBuildingContext.withSchemaSource(
  span: SourceSpan?,
  docComment: DocComment?,
  index: SchemaSourceIndex,
  kind: KtSourceElementKind? = null,
): String? {
  index.element(span, kind ?: KtRealSourceElementKind)?.let { source = it }
  return index.doc(docComment)
}

internal fun SimpleFunctionBuildingContext.withSchemaSource(
  span: SourceSpan?,
  docComment: DocComment?,
  index: SchemaSourceIndex,
  kind: KtSourceElementKind? = null,
): String? {
  index.element(span, kind ?: KtRealSourceElementKind)?.let { source = it }
  return index.doc(docComment)
}
