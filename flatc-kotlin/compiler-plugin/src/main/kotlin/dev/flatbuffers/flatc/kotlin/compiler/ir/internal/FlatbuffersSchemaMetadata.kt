package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

import com.intellij.psi.PsiElement
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.ir.declarations.MetadataSource
import org.jetbrains.kotlin.name.Name

/**
 * Metadata wrapper that stores the schema-derived source element and flattened documentation string
 * for generated declarations. The Kotlin backend persists these metadata nodes through to the
 * renderer/emitter stage, letting us re-materialise doc comments or feed tooling with provenance
 * data.
 */
internal sealed class FlatbuffersSchemaMetadata(
  override val name: Name?,
  override val source: KtSourceElement?,
  val docString: String?,
  val renderedDocComment: String? = docString?.let(DocCommentMaterializer::render),
) : MetadataSource {
  class Class(
    name: Name?,
    source: KtSourceElement?,
    docString: String?,
  ) : FlatbuffersSchemaMetadata(name, source, docString), MetadataSource.Class {
    override var serializedIr: ByteArray? = null
  }

  class Function(
    name: Name?,
    source: KtSourceElement?,
    docString: String?,
  ) : FlatbuffersSchemaMetadata(name, source, docString), MetadataSource.Function

  class Property(
    name: Name?,
    source: KtSourceElement?,
    docString: String?,
    override val isConst: Boolean = false,
    override val psi: PsiElement? = null,
  ) : FlatbuffersSchemaMetadata(name, source, docString), MetadataSource.Property
}

internal val MetadataSource.flatbuffersSchemaDoc: String?
  get() = (this as? FlatbuffersSchemaMetadata)?.docString

internal val MetadataSource.flatbuffersRenderedDocComment: String?
  get() = (this as? FlatbuffersSchemaMetadata)?.renderedDocComment
