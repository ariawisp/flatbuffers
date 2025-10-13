package dev.flatbuffers.flatc.kotlin.compiler.ir

import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.DocCommentMaterializer
import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.FlatbuffersSchemaMetadata
import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.flatbuffersRenderedDocComment
import org.jetbrains.kotlin.name.Name
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class FlatbuffersDocCommentMaterializerTest {

  @Test
  fun `single line doc renders as compact KDoc`() {
    val rendered = DocCommentMaterializer.render("Hello world")
    assertEquals(
      """
      /**
       * Hello world
       */
      """.trimIndent(),
      rendered,
    )
  }

  @Test
  fun `empty doc renders inline comment`() {
    assertEquals("/**  */", DocCommentMaterializer.render(""))
  }

  @Test
  fun `multiline doc preserves newlines`() {
    val doc = """
      First line
      Second line

      Fourth line
    """.trimIndent()
    val rendered = DocCommentMaterializer.render(doc)
    assertEquals(
      """
      /**
       * First line
       * Second line
       *
       * Fourth line
       */
      """.trimIndent(),
      rendered,
    )
  }

  @Test
  fun `metadata exposes rendered doc comment`() {
    val metadata =
      FlatbuffersSchemaMetadata.Function(
        name = Name.identifier("foo"),
        source = null,
        docString = "Line 1\nLine 2",
      )
    assertEquals(
      """
      /**
       * Line 1
       * Line 2
       */
      """.trimIndent(),
      metadata.flatbuffersRenderedDocComment,
    )
  }

  @Test
  fun `metadata without doc returns null`() {
    val metadata =
      FlatbuffersSchemaMetadata.Function(
        name = Name.identifier("foo"),
        source = null,
        docString = null,
      )
    assertNull(metadata.flatbuffersRenderedDocComment)
  }
}
