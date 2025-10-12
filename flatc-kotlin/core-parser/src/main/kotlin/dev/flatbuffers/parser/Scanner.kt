package dev.flatbuffers.parser

import dev.flatbuffers.ast.SourceLocation
import dev.flatbuffers.ast.SourceSpan
import dev.flatbuffers.parser.token.Token
import dev.flatbuffers.parser.token.TokenType

public class Scanner(private val fileName: String, private val source: String) {
  private val tokens = mutableListOf<Token>()
  private var index: Int = 0
  private var line: Int = 1
  private var column: Int = 1

  public fun scanTokens(): List<Token> {
    while (true) {
      skipWhitespaceAndRegularComments()
      if (isAtEnd()) {
        break
      }
      val start = mark()
      val c = advance()
      when (c) {
        '{' -> addToken(TokenType.LEFT_BRACE, start)
        '}' -> addToken(TokenType.RIGHT_BRACE, start)
        '[' -> addToken(TokenType.LEFT_BRACKET, start)
        ']' -> addToken(TokenType.RIGHT_BRACKET, start)
        '(' -> addToken(TokenType.LEFT_PAREN, start)
        ')' -> addToken(TokenType.RIGHT_PAREN, start)
        ':' -> addToken(TokenType.COLON, start)
        ';' -> addToken(TokenType.SEMICOLON, start)
        ',' -> addToken(TokenType.COMMA, start)
        '.' -> addToken(TokenType.DOT, start)
        '=' -> addToken(TokenType.EQUAL, start)
        '+' -> addToken(TokenType.PLUS, start)
        '-' -> addToken(TokenType.MINUS, start)
        '/' -> handleSlash(start)
        '"' -> string(start)
        else -> when {
          c.isDigit() -> number(start, c)
          c.isIdentifierStart() -> identifier(start, c)
          else -> throw LexingException(SourceLocation(fileName, start.line, start.column), "Unexpected character '$c'")
        }
      }
    }
    val eof = SourceLocation(fileName, line, column)
    tokens += Token(TokenType.EOF, "", SourceSpan(eof, eof))
    return tokens
  }

  private fun skipWhitespaceAndRegularComments() {
    while (!isAtEnd()) {
      when (val c = peek()) {
        ' ', '\r', '\t' -> {
          advance()
        }
        '\n' -> {
          advance()
        }
        '/' -> {
          when (peekNext()) {
            '/' -> {
              if (peekAhead(2) == '/') {
                return
              }
              consumeLineComment()
            }
            '*' -> {
              if (peekAhead(2) == '*') {
                return
              }
              consumeBlockComment()
            }
            else -> return
          }
        }
        else -> return
      }
    }
  }

  private fun consumeLineComment() {
    advance() // first '/'
    advance() // second '/'
    while (!isAtEnd() && peek() != '\n') {
      advance()
    }
  }

  private fun consumeBlockComment() {
    val start = mark()
    advance() // '/'
    advance() // '*'
    while (!isAtEnd()) {
      if (peek() == '*' && peekNext() == '/') {
        advance()
        advance()
        return
      }
      advance()
    }
    throw LexingException(SourceLocation(fileName, start.line, start.column), "Unterminated block comment")
  }

  private fun handleSlash(start: Position) {
    when {
      match('/') -> {
        if (!match('/')) {
          // regular comment should have been handled in skip
          throw LexingException(SourceLocation(fileName, start.line, start.column), "Unexpected '//' sequence")
        }
        lineDocComment(start)
      }
      match('*') -> {
        if (!match('*')) {
          throw LexingException(SourceLocation(fileName, start.line, start.column), "Unexpected '/*' sequence")
        }
        blockDocComment(start)
      }
      else -> throw LexingException(SourceLocation(fileName, start.line, start.column), "Unexpected '/' character")
    }
  }

  private fun lineDocComment(start: Position) {
    val contentStartIndex = index
    while (!isAtEnd() && peek() != '\n') {
      advance()
    }
    val raw = source.substring(contentStartIndex, index)
    if (!isAtEnd() && peek() == '\n') {
      advance()
    }
    val text = raw.trimEnd()
    addToken(TokenType.DOC_COMMENT, start, text)
  }

  private fun blockDocComment(start: Position) {
    val content = StringBuilder()
    var first = true
    while (!isAtEnd()) {
      if (peek() == '*' && peekNext() == '/') {
        advance()
        advance()
        val text = content.toString()
        addToken(TokenType.DOC_COMMENT, start, text)
        return
      }
      val ch = advance()
      if (first && ch == '\n') {
        first = false
        continue
      }
      content.append(ch)
      first = false
    }
    throw LexingException(SourceLocation(fileName, start.line, start.column), "Unterminated doc comment")
  }

  private fun string(start: Position) {
    val builder = StringBuilder()
    while (!isAtEnd()) {
      val c = advance()
      when (c) {
        '"' -> {
          addToken(TokenType.STRING, start, builder.toString())
          return
        }
        '\\' -> builder.append(readEscape(start))
        '\n', '\r' -> throw LexingException(SourceLocation(fileName, line, column), "Unterminated string literal")
        else -> builder.append(c)
      }
    }
    throw LexingException(SourceLocation(fileName, start.line, start.column), "Unterminated string literal")
  }

  private fun readEscape(start: Position): Char {
    if (isAtEnd()) {
      throw LexingException(SourceLocation(fileName, start.line, start.column), "Unterminated escape sequence")
    }
    return when (val c = advance()) {
      '"', '\\', '\'', '/' -> c
      'n' -> '\n'
      'r' -> '\r'
      't' -> '\t'
      'b' -> '\b'
      'f' -> '\u000C'
      'x' -> readHexDigits(start, 2)
      'u' -> readHexDigits(start, 4)
      else -> throw LexingException(SourceLocation(fileName, line, column), "Unknown escape sequence '\\$c'")
    }
  }

  private fun readHexDigits(start: Position, count: Int): Char {
    if (index + count > source.length) {
      throw LexingException(SourceLocation(fileName, start.line, start.column), "Incomplete hex escape")
    }
    var value = 0
    repeat(count) {
      val c = advance()
      val digit = c.digitToIntOrNull(16)
        ?: throw LexingException(SourceLocation(fileName, line, column), "Invalid hex digit '$c'")
      value = (value shl 4) or digit
    }
    return value.toChar()
  }

  private fun number(start: Position, first: Char) {
    if (first == '0' && (peek() == 'x' || peek() == 'X')) {
      advance()
      while (peek().isHexDigit()) {
        advance()
      }
      addToken(TokenType.INTEGER, start, source.substring(start.index, index))
      return
    }

    while (peek().isDigit()) {
      advance()
    }

    var isFloat = false
    if (peek() == '.' && peekNext().isDigit()) {
      isFloat = true
      advance()
      while (peek().isDigit()) {
        advance()
      }
    }

    if (peek() == 'e' || peek() == 'E') {
      isFloat = true
      advance()
      if (peek() == '+' || peek() == '-') {
        advance()
      }
      if (!peek().isDigit()) {
        throw LexingException(SourceLocation(fileName, line, column), "Malformed exponent")
      }
      while (peek().isDigit()) {
        advance()
      }
    }

    val lexeme = source.substring(start.index, index)
    addToken(if (isFloat) TokenType.FLOAT else TokenType.INTEGER, start, lexeme)
  }

  private fun identifier(start: Position, first: Char) {
    while (peek().isIdentifierPart()) {
      advance()
    }
    val text = source.substring(start.index, index)
    val type = keywords[text] ?: TokenType.IDENTIFIER
    addToken(type, start, text)
  }

  private fun addToken(type: TokenType, start: Position, lexeme: String = source.substring(start.index, index)) {
    tokens += Token(type, lexeme, SourceSpan(start.toLocation(), currentLocation()))
  }

  private fun addToken(type: TokenType, start: Position) {
    addToken(type, start, source.substring(start.index, index))
  }

  private fun currentLocation(): SourceLocation = SourceLocation(fileName, line, column)

  private fun Position.toLocation(): SourceLocation = SourceLocation(fileName, line, column)

  private fun mark(): Position = Position(index, line, column)

  private fun advance(): Char {
    if (isAtEnd()) {
      throw LexingException(currentLocation(), "Unexpected end of file")
    }
    val c = source[index]
    index++
    if (c == '\n') {
      line++
      column = 1
    } else {
      column++
    }
    return c
  }

  private fun match(expected: Char): Boolean {
    if (isAtEnd()) return false
    if (source[index] != expected) return false
    advance()
    return true
  }

  private fun peek(): Char = if (isAtEnd()) '\u0000' else source[index]

  private fun peekNext(): Char = if (index + 1 >= source.length) '\u0000' else source[index + 1]

  private fun peekAhead(offset: Int): Char {
    val i = index + offset
    return if (i >= source.length) '\u0000' else source[i]
  }

  private fun isAtEnd(): Boolean = index >= source.length

  private data class Position(val index: Int, val line: Int, val column: Int)

  private fun Char.isIdentifierStart(): Boolean = this == '_' || isLetter()

  private fun Char.isIdentifierPart(): Boolean = isIdentifierStart() || isDigit()

  private fun Char.isHexDigit(): Boolean = this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

  public companion object {
    private val keywords: Map<String, TokenType> = mapOf(
      "namespace" to TokenType.KW_NAMESPACE,
      "include" to TokenType.KW_INCLUDE,
      "attribute" to TokenType.KW_ATTRIBUTE,
      "table" to TokenType.KW_TABLE,
      "struct" to TokenType.KW_STRUCT,
      "enum" to TokenType.KW_ENUM,
      "union" to TokenType.KW_UNION,
      "root_type" to TokenType.KW_ROOT_TYPE,
      "file_identifier" to TokenType.KW_FILE_IDENTIFIER,
      "file_extension" to TokenType.KW_FILE_EXTENSION,
      "force_align" to TokenType.KW_FORCE_ALIGN,
      "force_defaults" to TokenType.KW_FORCE_DEFAULTS,
      "mutable" to TokenType.KW_MUTABLE,
      "rpc_service" to TokenType.KW_RPC_SERVICE,
      "oneway" to TokenType.KW_ONEWAY,
      "bool" to TokenType.TYPE_BOOL,
      "byte" to TokenType.TYPE_BYTE,
      "ubyte" to TokenType.TYPE_UBYTE,
      "short" to TokenType.TYPE_SHORT,
      "ushort" to TokenType.TYPE_USHORT,
      "int" to TokenType.TYPE_INT,
      "uint" to TokenType.TYPE_UINT,
      "long" to TokenType.TYPE_LONG,
      "ulong" to TokenType.TYPE_ULONG,
      "float" to TokenType.TYPE_FLOAT,
      "double" to TokenType.TYPE_DOUBLE,
      "string" to TokenType.TYPE_STRING,
    )
    public fun scan(fileName: String, source: String): List<Token> =
      Scanner(fileName, source).scanTokens()
  }
}

private fun Char.isLetter(): Boolean = this in 'a'..'z' || this in 'A'..'Z'
