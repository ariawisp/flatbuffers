package dev.flatbuffers.parser.token

import dev.flatbuffers.ast.SourceLocation
import dev.flatbuffers.ast.SourceSpan

public data class Token(
  val type: TokenType,
  val lexeme: String,
  val span: SourceSpan,
)

public enum class TokenType {
  // Structural
  LEFT_BRACE,
  RIGHT_BRACE,
  LEFT_BRACKET,
  RIGHT_BRACKET,
  LEFT_PAREN,
  RIGHT_PAREN,
  COLON,
  SEMICOLON,
  COMMA,
  DOT,
  EQUAL,
  PLUS,
  MINUS,

  // Literals
  IDENTIFIER,
  STRING,
  INTEGER,
  FLOAT,

  // Keywords
  KW_NAMESPACE,
  KW_INCLUDE,
  KW_ATTRIBUTE,
  KW_TABLE,
  KW_STRUCT,
  KW_ENUM,
  KW_UNION,
  KW_ROOT_TYPE,
  KW_FILE_IDENTIFIER,
  KW_FILE_EXTENSION,
  KW_FORCE_ALIGN,
  KW_FORCE_DEFAULTS,
  KW_MUTABLE,
  KW_RPC_SERVICE,
  KW_ONEWAY,

  // Types
  TYPE_BOOL,
  TYPE_BYTE,
  TYPE_UBYTE,
  TYPE_SHORT,
  TYPE_USHORT,
  TYPE_INT,
  TYPE_UINT,
  TYPE_LONG,
  TYPE_ULONG,
  TYPE_FLOAT,
  TYPE_DOUBLE,
  TYPE_STRING,

  DOC_COMMENT,

  // End of file
  EOF,
}

public fun span(start: SourceLocation, end: SourceLocation): SourceSpan = SourceSpan(start, end)
