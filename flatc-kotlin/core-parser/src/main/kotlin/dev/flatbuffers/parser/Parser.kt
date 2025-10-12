package dev.flatbuffers.parser

import dev.flatbuffers.ast.Attribute
import dev.flatbuffers.ast.AttributeArgument
import dev.flatbuffers.ast.AttributeNamedArgument
import dev.flatbuffers.ast.AttributePositionalArgument
import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.EnumDeclaration
import dev.flatbuffers.ast.Enumerator
import dev.flatbuffers.ast.Field
import dev.flatbuffers.ast.FileExtensionDirective
import dev.flatbuffers.ast.FileIdentifierDirective
import dev.flatbuffers.ast.Identifier
import dev.flatbuffers.ast.IncludeStatement
import dev.flatbuffers.ast.NamespaceDeclaration
import dev.flatbuffers.ast.QualifiedName
import dev.flatbuffers.ast.RootTypeDeclaration
import dev.flatbuffers.ast.RpcMethod
import dev.flatbuffers.ast.RpcServiceDeclaration
import dev.flatbuffers.ast.SchemaFile
import dev.flatbuffers.ast.SchemaStatement
import dev.flatbuffers.ast.SourceLocation
import dev.flatbuffers.ast.SourceSpan
import dev.flatbuffers.ast.StructDeclaration
import dev.flatbuffers.ast.TableConstraint
import dev.flatbuffers.ast.TableDeclaration
import dev.flatbuffers.ast.TypeDeclaration
import dev.flatbuffers.ast.TypeRef
import dev.flatbuffers.ast.UnionDeclaration
import dev.flatbuffers.ast.UnionMember
import dev.flatbuffers.ast.ValueLiteral
import dev.flatbuffers.ast.AttributeDeclaration
import dev.flatbuffers.ast.ScalarType
import dev.flatbuffers.parser.token.Token
import dev.flatbuffers.parser.token.TokenType

public class Parser(private val fileName: String, private val tokens: List<Token>) {
  private var current: Int = 0

  public fun parse(): SchemaFile {
    val statements = mutableListOf<SchemaStatement>()
    val fileStart = tokens.first().span.start
    while (!check(TokenType.EOF)) {
      val doc = consumeDocCommentIfAny()
      if (check(TokenType.EOF)) break
      statements += parseTopLevelStatement(doc)
    }
    val eofSpan = previousOrCurrent().span
    return SchemaFile(
      file = fileName,
      statements = statements,
      span = SourceSpan(fileStart, eofSpan.end),
    )
  }

  private fun parseTopLevelStatement(doc: DocComment?): SchemaStatement {
    return when {
      match(TokenType.KW_INCLUDE) -> parseInclude()
      match(TokenType.KW_NAMESPACE) -> parseNamespace(doc)
      match(TokenType.KW_ATTRIBUTE) -> parseAttributeDeclaration(doc)
      match(TokenType.KW_ROOT_TYPE) -> parseRootType(doc)
      match(TokenType.KW_FILE_IDENTIFIER) -> parseFileIdentifier(doc)
      match(TokenType.KW_FILE_EXTENSION) -> parseFileExtension(doc)
      match(TokenType.KW_TABLE) -> parseTable(doc)
      match(TokenType.KW_STRUCT) -> parseStruct(doc)
      match(TokenType.KW_ENUM) -> parseEnum(doc)
      match(TokenType.KW_UNION) -> parseUnion(doc)
      match(TokenType.KW_RPC_SERVICE) -> parseRpcService(doc)
      else -> throw parseError(peek(), "Unexpected token ${peek().type}")
    }
  }

  private fun parseInclude(): IncludeStatement {
    val stringToken = consume(TokenType.STRING, "Expected string literal after include")
    consume(TokenType.SEMICOLON, "Expected ';' after include path")
    val span = SourceSpan(stringToken.span.start, previous().span.end)
    return IncludeStatement(stringToken.lexeme, span)
  }

  private fun parseNamespace(doc: DocComment?): NamespaceDeclaration {
    val name = parseQualifiedName()
    consume(TokenType.SEMICOLON, "Expected ';' after namespace declaration")
    val span = SourceSpan(name.span.start, previous().span.end)
    return NamespaceDeclaration(name, doc, span)
  }

  private fun parseAttributeDeclaration(doc: DocComment?): AttributeDeclaration {
    val nameToken = when {
      match(TokenType.STRING) -> previous()
      else -> consume(TokenType.IDENTIFIER, "Expected attribute name")
    }
    consume(TokenType.SEMICOLON, "Expected ';' after attribute declaration")
    val identifier = Identifier(nameToken.lexeme, nameToken.span)
    val span = SourceSpan(nameToken.span.start, previous().span.end)
    return AttributeDeclaration(identifier, doc, span)
  }

  private fun parseRootType(doc: DocComment?): RootTypeDeclaration {
    val types = mutableListOf<QualifiedName>()
    do {
      types += parseQualifiedName()
    } while (match(TokenType.COMMA))
    consume(TokenType.SEMICOLON, "Expected ';' after root_type declaration")
    val span = SourceSpan(types.first().span.start, previous().span.end)
    return RootTypeDeclaration(types, doc, span)
  }

  private fun parseFileIdentifier(doc: DocComment?): FileIdentifierDirective {
    val value = consume(TokenType.STRING, "Expected string literal for file_identifier")
    consume(TokenType.SEMICOLON, "Expected ';' after file_identifier")
    val span = SourceSpan(value.span.start, previous().span.end)
    return FileIdentifierDirective(value.lexeme, doc, span)
  }

  private fun parseFileExtension(doc: DocComment?): FileExtensionDirective {
    val value = consume(TokenType.STRING, "Expected string literal for file_extension")
    consume(TokenType.SEMICOLON, "Expected ';' after file_extension")
    val span = SourceSpan(value.span.start, previous().span.end)
    return FileExtensionDirective(value.lexeme, doc, span)
  }

  private fun parseTable(doc: DocComment?): TableDeclaration {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected table name")
    val rawAttributes = parseAttributeList()
    val constraints = extractTableConstraints(rawAttributes)
    val attributes = rawAttributes
    consume(TokenType.LEFT_BRACE, "Expected '{' to start table body")
    val fields = mutableListOf<Field>()
    while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
      val fieldDoc = consumeDocCommentIfAny()
      if (check(TokenType.RIGHT_BRACE)) break
      fields += parseField(fieldDoc)
    }
    consume(TokenType.RIGHT_BRACE, "Expected '}' to close table body")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return TableDeclaration(
      name = Identifier(nameTok.lexeme, nameTok.span),
      fields = fields,
      constraints = constraints,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun extractTableConstraints(attributes: List<Attribute>): Set<TableConstraint> {
    val result = mutableSetOf<TableConstraint>()
    attributes.forEach { attr ->
      when (attr.name.fullName) {
        "mutable" -> result += TableConstraint.MUTABLE
        "force_align" -> result += TableConstraint.FORCE_ALIGN
        "force_defaults" -> result += TableConstraint.FORCE_DEFAULTS
      }
    }
    return result
  }

  private fun parseField(doc: DocComment?): Field {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected field name")
    consume(TokenType.COLON, "Expected ':' after field name")
    val type = parseTypeRef()
    val defaultValue = if (match(TokenType.EQUAL)) parseValueLiteral() else null
    val attributes = parseAttributeList()
    consume(TokenType.SEMICOLON, "Expected ';' after field definition")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return Field(
      name = Identifier(nameTok.lexeme, nameTok.span),
      type = type,
      defaultValue = defaultValue,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseStruct(doc: DocComment?): StructDeclaration {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected struct name")
    val attributes = parseAttributeList()
    consume(TokenType.LEFT_BRACE, "Expected '{' to start struct body")
    val fields = mutableListOf<Field>()
    while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
      val fieldDoc = consumeDocCommentIfAny()
      if (check(TokenType.RIGHT_BRACE)) break
      fields += parseField(fieldDoc)
    }
    consume(TokenType.RIGHT_BRACE, "Expected '}' to close struct body")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return StructDeclaration(
      name = Identifier(nameTok.lexeme, nameTok.span),
      fields = fields,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseEnum(doc: DocComment?): EnumDeclaration {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected enum name")
    consume(TokenType.COLON, "Expected ':' after enum name")
    val baseType = parseTypeRef()
    val attributes = parseAttributeList()
    consume(TokenType.LEFT_BRACE, "Expected '{' to start enum body")
    val enumerators = mutableListOf<Enumerator>()
    while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
      val enumDoc = consumeDocCommentIfAny()
      if (check(TokenType.RIGHT_BRACE)) break
      enumerators += parseEnumerator(enumDoc)
      if (!match(TokenType.COMMA)) break
    }
    while (match(TokenType.COMMA)) { /* allow trailing commas */ }
    consume(TokenType.RIGHT_BRACE, "Expected '}' to close enum body")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return EnumDeclaration(
      name = Identifier(nameTok.lexeme, nameTok.span),
      baseType = baseType,
      enumerators = enumerators,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseEnumerator(doc: DocComment?): Enumerator {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected enum value name")
    val value = if (match(TokenType.EQUAL)) parseValueLiteral() else null
    val attributes = parseAttributeList()
    val endSpan = when {
      attributes.isNotEmpty() -> attributes.last().span.end
      value != null -> value.span.end
      else -> previous().span.end
    }
    val span = SourceSpan(nameTok.span.start, endSpan)
    return Enumerator(
      name = Identifier(nameTok.lexeme, nameTok.span),
      value = value,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseUnion(doc: DocComment?): UnionDeclaration {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected union name")
    val attributes = parseAttributeList()
    consume(TokenType.LEFT_BRACE, "Expected '{' to start union body")
    val members = mutableListOf<UnionMember>()
    while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
      val memberDoc = consumeDocCommentIfAny()
      if (check(TokenType.RIGHT_BRACE)) break
      members += parseUnionMember(memberDoc)
      if (!match(TokenType.COMMA)) break
    }
    while (match(TokenType.COMMA)) {}
    consume(TokenType.RIGHT_BRACE, "Expected '}' to close union body")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return UnionDeclaration(
      name = Identifier(nameTok.lexeme, nameTok.span),
      members = members,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseUnionMember(doc: DocComment?): UnionMember {
    val typeName = parseQualifiedName()
    val alias = if (match(TokenType.COLON)) {
      val tok = consume(TokenType.IDENTIFIER, "Expected alias identifier")
      Identifier(tok.lexeme, tok.span)
    } else null
    val attributes = parseAttributeList()
    val spanEnd = if (attributes.isNotEmpty()) attributes.last().span.end else previous().span.end
    return UnionMember(
      type = TypeRef.Named(typeName, typeName.span),
      alias = alias,
      attributes = attributes,
      docComment = doc,
      span = SourceSpan(typeName.span.start, spanEnd),
    )
  }

  private fun parseRpcService(doc: DocComment?): RpcServiceDeclaration {
    val nameTok = consume(TokenType.IDENTIFIER, "Expected rpc_service name")
    val attributes = parseAttributeList()
    consume(TokenType.LEFT_BRACE, "Expected '{' to start rpc_service body")
    val methods = mutableListOf<RpcMethod>()
    while (!check(TokenType.RIGHT_BRACE) && !check(TokenType.EOF)) {
      val methodDoc = consumeDocCommentIfAny()
      if (check(TokenType.RIGHT_BRACE)) break
      methods += parseRpcMethod(methodDoc)
    }
    consume(TokenType.RIGHT_BRACE, "Expected '}' to close rpc_service body")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return RpcServiceDeclaration(
      name = Identifier(nameTok.lexeme, nameTok.span),
      methods = methods,
      attributes = attributes,
      docComment = doc,
      span = span,
    )
  }

  private fun parseRpcMethod(doc: DocComment?): RpcMethod {
    val oneway = match(TokenType.KW_ONEWAY)
    val nameTok = consume(TokenType.IDENTIFIER, "Expected RPC method name")
    consume(TokenType.LEFT_PAREN, "Expected '(' after method name")
    val request = parseTypeRefNamed()
    val streamingRequest = false
    consume(TokenType.RIGHT_PAREN, "Expected ')' after request type")
    consume(TokenType.COLON, "Expected ':' between request and response")
    val responseType = parseTypeRefNamed()
    val streamingResponse = if (oneway) true else false
    val attributes = parseAttributeList()
    consume(TokenType.SEMICOLON, "Expected ';' after RPC method")
    val span = SourceSpan(nameTok.span.start, previous().span.end)
    return RpcMethod(
      name = Identifier(nameTok.lexeme, nameTok.span),
      requestType = request,
      responseType = responseType,
      attributes = attributes,
      streamingRequest = streamingRequest,
      streamingResponse = streamingResponse,
      docComment = doc,
      span = span,
    )
  }

  private fun parseTypeRefNamed(): TypeRef.Named {
    val name = parseQualifiedName()
    return TypeRef.Named(name, name.span)
  }

  private fun parseTypeRef(): TypeRef {
    if (match(TokenType.LEFT_BRACKET)) {
      val startToken = previous()
      val elementType = parseTypeRef()
      val arrayLength = if (match(TokenType.COLON)) {
        val lenTok = consume(TokenType.INTEGER, "Expected array length")
        lenTok.lexeme.toInt()
      } else null
      val endToken = consume(TokenType.RIGHT_BRACKET, "Expected ']' after vector or array type")
      val span = SourceSpan(startToken.span.start, endToken.span.end)
      return if (arrayLength != null) {
        TypeRef.Array(elementType, arrayLength, span)
      } else {
        TypeRef.Vector(elementType, span)
      }
    }

    if (match(TokenType.TYPE_STRING)) {
      return TypeRef.StringType(previous().span)
    }

    val scalar = scalarTypes[peek().type]
    if (scalar != null) {
      advance()
      val tok = previous()
      return TypeRef.Scalar(scalar, tok.span)
    }

    val name = parseQualifiedName()
    return TypeRef.Named(name, name.span)
  }

  private fun parseQualifiedName(): QualifiedName {
    val first = consume(TokenType.IDENTIFIER, "Expected identifier")
    val segments = mutableListOf<Identifier>()
    segments += Identifier(first.lexeme, first.span)
    var lastSpan = first.span
    while (match(TokenType.DOT)) {
      val part = consume(TokenType.IDENTIFIER, "Expected identifier after '.'")
      segments += Identifier(part.lexeme, part.span)
      lastSpan = part.span
    }
    return QualifiedName(segments, SourceSpan(first.span.start, lastSpan.end))
  }

  private fun parseAttributeList(): List<Attribute> {
    if (!match(TokenType.LEFT_PAREN)) return emptyList()
    val attributes = mutableListOf<Attribute>()
    if (!check(TokenType.RIGHT_PAREN)) {
      do {
        attributes += parseAttribute()
      } while (match(TokenType.COMMA))
    }
    consume(TokenType.RIGHT_PAREN, "Expected ')' to close attribute list")
    return attributes
  }

  private fun parseAttribute(): Attribute {
    val name = parseQualifiedName()
    val arguments = mutableListOf<AttributeArgument>()
    when {
      match(TokenType.COLON) || match(TokenType.EQUAL) -> {
        val value = parseValueLiteral()
        arguments += AttributePositionalArgument(value, value.span)
      }
      match(TokenType.LEFT_PAREN) -> {
        if (!check(TokenType.RIGHT_PAREN)) {
          do {
            val argStartToken = peek()
            if (check(TokenType.IDENTIFIER) && peekNext().type == TokenType.EQUAL) {
              val identTok = advance()
              consume(TokenType.EQUAL, "Expected '=' in named attribute argument")
              val value = parseValueLiteral()
              arguments += AttributeNamedArgument(Identifier(identTok.lexeme, identTok.span), value, SourceSpan(identTok.span.start, value.span.end))
            } else {
              val value = parseValueLiteral()
              arguments += AttributePositionalArgument(value, value.span)
            }
          } while (match(TokenType.COMMA))
        }
        consume(TokenType.RIGHT_PAREN, "Expected ')' after attribute arguments")
      }
    }
    val spanEnd = if (arguments.isNotEmpty()) arguments.last().span.end else name.span.end
    return Attribute(name, arguments, SourceSpan(name.span.start, spanEnd))
  }

  private fun parseValueLiteral(): ValueLiteral {
    if (match(TokenType.MINUS)) {
      val minusToken = previous()
      val literal = parseValueLiteral()
      return when (literal) {
        is ValueLiteral.IntegerLiteral -> literal.copy(value = -literal.value, span = SourceSpan(minusToken.span.start, literal.span.end), raw = "-${literal.raw}")
        is ValueLiteral.FloatLiteral -> literal.copy(value = -literal.value, span = SourceSpan(minusToken.span.start, literal.span.end), raw = "-${literal.raw}")
        else -> throw parseError(previous(), "Unary '-' only valid on numeric literals")
      }
    }

    val token = advance()
    return when (token.type) {
      TokenType.INTEGER -> ValueLiteral.IntegerLiteral(token.lexeme.toLongWithRadix(), token.lexeme, token.span)
      TokenType.FLOAT -> ValueLiteral.FloatLiteral(token.lexeme.toDouble(), token.lexeme, token.span)
      TokenType.STRING -> ValueLiteral.StringLiteral(token.lexeme, token.span)
      TokenType.IDENTIFIER -> when (token.lexeme) {
        "true" -> ValueLiteral.BooleanLiteral(true, token.span)
        "false" -> ValueLiteral.BooleanLiteral(false, token.span)
        else -> {
          var name = QualifiedName(listOf(Identifier(token.lexeme, token.span)), token.span)
          while (match(TokenType.DOT)) {
            val part = consume(TokenType.IDENTIFIER, "Expected identifier after '.' in literal")
            val segments = name.segments + Identifier(part.lexeme, part.span)
            name = QualifiedName(segments, SourceSpan(name.span.start, part.span.end))
          }
          ValueLiteral.IdentifierLiteral(name, name.span)
        }
      }
      TokenType.LEFT_BRACKET -> {
        val elements = mutableListOf<ValueLiteral>()
        if (!check(TokenType.RIGHT_BRACKET)) {
          do {
            elements += parseValueLiteral()
          } while (match(TokenType.COMMA))
        }
        val endToken = consume(TokenType.RIGHT_BRACKET, "Expected ']' after array literal")
        ValueLiteral.ArrayLiteral(elements, SourceSpan(token.span.start, endToken.span.end))
      }
      TokenType.LEFT_BRACE -> {
        val elements = mutableListOf<ValueLiteral>()
        if (!check(TokenType.RIGHT_BRACE)) {
          do {
            elements += parseValueLiteral()
          } while (match(TokenType.COMMA))
        }
        val endToken = consume(TokenType.RIGHT_BRACE, "Expected '}' after struct literal")
        ValueLiteral.StructLiteral(elements, SourceSpan(token.span.start, endToken.span.end))
      }
      else -> throw parseError(token, "Unexpected token in literal")
    }
  }

  private fun consumeDocCommentIfAny(): DocComment? {
    if (!check(TokenType.DOC_COMMENT)) return null
    val first = peek()
    val lines = mutableListOf<String>()
    var lastSpan = first.span
    while (check(TokenType.DOC_COMMENT)) {
      val tok = advance()
      lastSpan = tok.span
      val cleaned = tok.lexeme.lines().map { it.trim().trimStart('*').trim() }
      lines += cleaned
    }
    if (lines.isEmpty()) {
      lines += ""
    }
    return DocComment(lines, SourceSpan(first.span.start, lastSpan.end))
  }

  private fun match(type: TokenType): Boolean {
    if (check(type)) {
      advance()
      return true
    }
    return false
  }

  private fun match(vararg types: TokenType): Boolean {
    types.forEach { type ->
      if (check(type)) {
        advance()
        return true
      }
    }
    return false
  }

  private fun check(type: TokenType): Boolean {
    if (isAtEnd()) return type == TokenType.EOF
    return peek().type == type
  }

  private fun advance(): Token {
    if (!isAtEnd()) current++
    return previous()
  }

  private fun isAtEnd(): Boolean = peek().type == TokenType.EOF

  private fun peek(): Token = tokens[current]

  private fun peekNext(): Token = tokens[minOf(current + 1, tokens.lastIndex)]

  private fun previous(): Token = tokens[current - 1]

  private fun previousOrCurrent(): Token = if (current == 0) tokens[0] else previous()

  private fun consume(type: TokenType, message: String): Token {
    if (check(type)) return advance()
    throw parseError(peek(), message)
  }

  private fun parseError(token: Token, message: String): ParseException = ParseException(token, message)

  public companion object {
    private val scalarTypes: Map<TokenType, ScalarType> = mapOf(
      TokenType.TYPE_BOOL to ScalarType.BOOL,
      TokenType.TYPE_BYTE to ScalarType.BYTE,
      TokenType.TYPE_UBYTE to ScalarType.UBYTE,
      TokenType.TYPE_SHORT to ScalarType.SHORT,
      TokenType.TYPE_USHORT to ScalarType.USHORT,
      TokenType.TYPE_INT to ScalarType.INT,
      TokenType.TYPE_UINT to ScalarType.UINT,
      TokenType.TYPE_LONG to ScalarType.LONG,
      TokenType.TYPE_ULONG to ScalarType.ULONG,
      TokenType.TYPE_FLOAT to ScalarType.FLOAT,
      TokenType.TYPE_DOUBLE to ScalarType.DOUBLE,
    )
  }
}

public class ParseException(public val token: Token, message: String) : RuntimeException(message)

private fun String.toLongWithRadix(): Long {
  return if (startsWith("0x", ignoreCase = true)) {
    substring(2).toLong(16)
  } else {
    toLong()
  }
}
