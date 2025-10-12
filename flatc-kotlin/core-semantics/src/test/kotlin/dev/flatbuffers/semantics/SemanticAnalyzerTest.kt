package dev.flatbuffers.semantics

import dev.flatbuffers.parser.SchemaParser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SemanticAnalyzerTest {
  @Test
  fun `analyzes simple schema`() {
    val schemaText = """
      namespace Example;
      table Monster {
        hp:int = 100;
      }
      root_type Monster;
    """.trimIndent()

    val schemaFile = SchemaParser.parse("monster.fbs", schemaText)
    val result = SemanticAnalyzer().analyze(listOf(schemaFile))
    assertTrue(result.diagnostics.isEmpty(), "Diagnostics: ${result.diagnostics}")
    val table = result.schema.declarations["Example.Monster"]
    assertTrue(table is ResolvedTable)
    assertEquals("Monster", table.name)
    assertEquals(1, table.fields.size)
    val field = table.fields.first()
    assertEquals("hp", field.name)
    val defaultValue = field.defaultValue as ResolvedIntegerValue
    assertEquals(100L, defaultValue.value)
    assertEquals(1, result.schema.rootTypes.size)
  }
}
