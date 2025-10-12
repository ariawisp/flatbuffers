package dev.flatbuffers.parser

import dev.flatbuffers.ast.TableDeclaration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SchemaParserTest {
  @Test
  fun `parse simple table`() {
    val schema = """
      namespace My.Game;

      table Monster {
        hp:int = 100;
      }
    """.trimIndent()

    val file = SchemaParser.parse("monster.fbs", schema)
    assertEquals("monster.fbs", file.file)
    assertEquals(2, file.statements.size)
    val table = file.statements.last()
    assertTrue(table is TableDeclaration)
    assertEquals("Monster", table.name.value)
    assertEquals(1, table.fields.size)
    assertEquals("hp", table.fields[0].name.value)
    assertEquals("100", (table.fields[0].defaultValue as? dev.flatbuffers.ast.ValueLiteral.IntegerLiteral)?.raw)
  }
}
