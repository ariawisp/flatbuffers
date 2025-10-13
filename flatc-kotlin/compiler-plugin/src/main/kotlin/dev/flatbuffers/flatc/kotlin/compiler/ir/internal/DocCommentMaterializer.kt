package dev.flatbuffers.flatc.kotlin.compiler.ir.internal

private const val DOC_PREFIX = "/**"
private const val DOC_LINE_PREFIX = " *"
private const val DOC_SUFFIX = " */"

/**
 * Converts a flattened schema doc string into the Kotlin KDoc form that downstream emitters expect.
 */
internal object DocCommentMaterializer {
  fun render(doc: String): String {
    if (doc.isEmpty()) return "$DOC_PREFIX $DOC_SUFFIX"
    val builder = StringBuilder()
    builder.appendLine(DOC_PREFIX)
    doc.splitToSequence('\n').forEach { line ->
      builder.append(DOC_LINE_PREFIX)
      if (line.isNotEmpty()) {
        builder.append(' ').append(line)
      }
      builder.appendLine()
    }
    builder.append(DOC_SUFFIX)
    return builder.toString()
  }
}
