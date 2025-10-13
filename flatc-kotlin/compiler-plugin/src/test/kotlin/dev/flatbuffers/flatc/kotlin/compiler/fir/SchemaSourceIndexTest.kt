package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersConfigurationKeys
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.semantics.ResolvedField
import java.nio.file.Path
import kotlin.io.path.readText
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.jupiter.api.Test
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

class SchemaSourceIndexTest {
  @Test
  fun `source offsets and docs align with schema spans`() {
    val schemaFile = writeSchema()
    val schemaIndex = loadSchema(schemaFile)
    val table = assertNotNull(schemaIndex.tableFor("example.DocTable"))
    val tableSpan = assertNotNull(table.span)

    val sourceIndex = SchemaSourceIndex()
    val schemaText = schemaFile.readText()

    val tableElement = assertNotNull(sourceIndex.element(tableSpan))
    val (tableStart, tableEnd) = offsets(tableSpan, schemaText)
    assertEquals(tableStart, tableElement.startOffset)
    assertEquals(tableEnd, tableElement.endOffset)
    assertEquals("""Table doc first line
Second line""", sourceIndex.doc(table.docComment))

    val field = assertNotNull(table.fields.firstOrNull { it.name == "value" })
    assertFieldMetadata(field, sourceIndex, schemaText)
  }

  private fun assertFieldMetadata(field: ResolvedField, index: SchemaSourceIndex, text: String) {
    val element = assertNotNull(index.element(field.span))
    val (start, end) = offsets(field.span, text)
    assertEquals(start, element.startOffset)
    assertEquals(end, element.endOffset)
    assertEquals("""Field docs line one
line two""", index.doc(field.docComment))
  }

  private fun writeSchema(): Path {
    val dir = createTempDirectory("flatbuffers-schema-test")
    val schemaFile = dir.resolve("doc_schema.fbs")
    schemaFile.writeText(
      """
      namespace example;

      /// Table doc first line
      /// Second line
      table DocTable {
        /// Field docs line one
        /// line two
        value:int;
      }

      root_type DocTable;
      """
        .trimIndent()
    )
    return schemaFile
  }

  private fun loadSchema(schemaPath: Path): SchemaIndex {
    val configuration = CompilerConfiguration().apply {
      put(FlatbuffersConfigurationKeys.ENABLED, true)
      addSchemaPath(schemaPath.toString())
    }
    val options = FlatbuffersPluginOptions.load(configuration)
    val collector = RecordingMessageCollector()
    return SchemaIndex.load(options, collector)
      ?: error("Failed to load schema: \${collector.errors}")
  }

  private fun offsets(span: dev.flatbuffers.ast.SourceSpan, text: String): Pair<Int, Int> {
    fun offsetOf(line: Int, column: Int): Int {
      var currentLine = 1
      var index = 0
      while (currentLine < line && index < text.length) {
        if (text[index] == '\n') {
          currentLine += 1
        }
        index += 1
      }
      return index + (column - 1)
    }
    return offsetOf(span.start.line, span.start.column) to offsetOf(span.end.line, span.end.column)
  }
}


private class RecordingMessageCollector : MessageCollector {
  private val _errors = mutableListOf<String>()
  val errors: List<String> get() = _errors

  override fun clear() {
    _errors.clear()
  }

  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ) {
    if (severity.isError) {
      _errors += message
    }
  }

  override fun hasErrors(): Boolean = _errors.isNotEmpty()
}
