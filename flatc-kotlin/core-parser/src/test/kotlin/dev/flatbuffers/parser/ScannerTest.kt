package dev.flatbuffers.parser

import dev.flatbuffers.parser.token.TokenType
import kotlin.test.Test
import kotlin.test.assertEquals

class ScannerTest {
  @Test
  fun `basic table tokens`() {
    val source = """
      table Monster {
        hp:int = 100;
      }
    """.trimIndent()

    val tokens = Scanner.scan("monster.fbs", source)
    val types = tokens.map { it.type }
    assertEquals(
      listOf(
        TokenType.KW_TABLE,
        TokenType.IDENTIFIER,
        TokenType.LEFT_BRACE,
        TokenType.IDENTIFIER,
        TokenType.COLON,
        TokenType.TYPE_INT,
        TokenType.EQUAL,
        TokenType.INTEGER,
        TokenType.SEMICOLON,
        TokenType.RIGHT_BRACE,
        TokenType.EOF,
      ),
      types,
    )
    assertEquals("100", tokens[7].lexeme)
  }

  @Test
  fun `doc comment captured`() {
    val source = """
      /// Monster health points
      table Monster { hp:int; }
    """.trimIndent()

    val tokens = Scanner.scan("monster.fbs", source)
    assertEquals(TokenType.DOC_COMMENT, tokens[0].type)
    assertEquals("Monster health points", tokens[0].lexeme.trim())
  }
}
