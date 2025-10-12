package dev.flatbuffers.ast

/**
 * Represents a physical Kotlin source file path used when producing schema metadata.
 */
public data class SourceLocation(
  val file: String,
  val line: Int,
  val column: Int,
)

public data class SourceSpan(
  val start: SourceLocation,
  val end: SourceLocation,
) {
  init {
    require(start.file == end.file) { "Span must reference a single file" }
  }

  public val file: String get() = start.file
}

public data class DocComment(
  val lines: List<String>,
  val span: SourceSpan,
) {
  init {
    require(lines.isNotEmpty()) { "DocComment must have at least one line" }
  }
}

public data class Identifier(
  val value: String,
  val span: SourceSpan,
)

public data class QualifiedName(
  val segments: List<Identifier>,
  val span: SourceSpan,
) {
  init {
    require(segments.isNotEmpty()) { "QualifiedName must contain at least one identifier" }
  }

  public val fullName: String = segments.joinToString(".") { it.value }
}

public sealed interface SchemaStatement {
  public val span: SourceSpan
  public val docComment: DocComment?
}

public data class SchemaFile(
  val file: String,
  val statements: List<SchemaStatement>,
  val span: SourceSpan,
)

public data class IncludeStatement(
  val path: String,
  override val span: SourceSpan,
) : SchemaStatement {
  override val docComment: DocComment? get() = null
}

public data class NamespaceDeclaration(
  val name: QualifiedName,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : SchemaStatement

public data class AttributeDeclaration(
  val name: Identifier,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : SchemaStatement

public data class RootTypeDeclaration(
  val types: List<QualifiedName>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : SchemaStatement {
  init {
    require(types.isNotEmpty()) { "root_type declaration must specify at least one type" }
  }
}

public data class FileIdentifierDirective(
  val identifier: String,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : SchemaStatement {
  init {
    require(identifier.length == 4) { "file_identifier must be exactly 4 characters" }
  }
}

public data class FileExtensionDirective(
  val extension: String,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : SchemaStatement {
  init {
    require(extension.isNotBlank()) { "file_extension cannot be blank" }
  }
}

public sealed interface TypeDeclaration : SchemaStatement {
  public val name: Identifier
  public val attributes: List<Attribute>
}

public enum class TableConstraint {
  MUTABLE,
  FORCE_ALIGN,
  FORCE_DEFAULTS,
}

public data class TableDeclaration(
  override val name: Identifier,
  val fields: List<Field>,
  val constraints: Set<TableConstraint>,
  override val attributes: List<Attribute>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : TypeDeclaration

public data class StructDeclaration(
  override val name: Identifier,
  val fields: List<Field>,
  override val attributes: List<Attribute>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : TypeDeclaration

public data class EnumDeclaration(
  override val name: Identifier,
  val baseType: TypeRef,
  val enumerators: List<Enumerator>,
  override val attributes: List<Attribute>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : TypeDeclaration {
  init {
    require(enumerators.isNotEmpty()) { "Enum must define at least one enumerator" }
  }
}

public data class UnionDeclaration(
  override val name: Identifier,
  val members: List<UnionMember>,
  override val attributes: List<Attribute>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : TypeDeclaration {
  init {
    require(members.isNotEmpty()) { "Union must declare at least one member" }
  }
}

public data class RpcServiceDeclaration(
  override val name: Identifier,
  val methods: List<RpcMethod>,
  override val attributes: List<Attribute>,
  override val docComment: DocComment?,
  override val span: SourceSpan,
) : TypeDeclaration

public data class Field(
  val name: Identifier,
  val type: TypeRef,
  val defaultValue: ValueLiteral?,
  val attributes: List<Attribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class Enumerator(
  val name: Identifier,
  val value: ValueLiteral?,
  val attributes: List<Attribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class UnionMember(
  val type: TypeRef.Named,
  val alias: Identifier?,
  val attributes: List<Attribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class RpcMethod(
  val name: Identifier,
  val requestType: TypeRef.Named,
  val responseType: TypeRef.Named,
  val attributes: List<Attribute>,
  val streamingRequest: Boolean,
  val streamingResponse: Boolean,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public sealed interface AttributeArgument {
  public val span: SourceSpan
}

public data class AttributeNamedArgument(
  val name: Identifier,
  val value: ValueLiteral,
  override val span: SourceSpan,
) : AttributeArgument

public data class AttributePositionalArgument(
  val value: ValueLiteral,
  override val span: SourceSpan,
) : AttributeArgument

public data class Attribute(
  val name: QualifiedName,
  val arguments: List<AttributeArgument>,
  val span: SourceSpan,
)

public enum class ScalarType(val keyword: String) {
  BOOL("bool"),
  BYTE("byte"),
  UBYTE("ubyte"),
  SHORT("short"),
  USHORT("ushort"),
  INT("int"),
  UINT("uint"),
  LONG("long"),
  ULONG("ulong"),
  FLOAT("float"),
  DOUBLE("double");

  companion object {
    private val byKeyword = entries.associateBy { it.keyword }
    fun fromKeyword(keyword: String): ScalarType? = byKeyword[keyword]
  }
}

public sealed interface TypeRef {
  public val span: SourceSpan

  public data class Scalar(
    val type: ScalarType,
    override val span: SourceSpan,
  ) : TypeRef

  public data class StringType(
    override val span: SourceSpan,
  ) : TypeRef

  public data class Named(
    val name: QualifiedName,
    override val span: SourceSpan,
  ) : TypeRef

  public data class Vector(
    val elementType: TypeRef,
    override val span: SourceSpan,
  ) : TypeRef

  public data class Array(
    val elementType: TypeRef,
    val length: Int,
    override val span: SourceSpan,
  ) : TypeRef
}

public sealed interface ValueLiteral {
  public val span: SourceSpan

  public data class BooleanLiteral(
    val value: Boolean,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class IntegerLiteral(
    val value: Long,
    val raw: String,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class FloatLiteral(
    val value: Double,
    val raw: String,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class StringLiteral(
    val value: String,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class IdentifierLiteral(
    val identifier: QualifiedName,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class ArrayLiteral(
    val elements: List<ValueLiteral>,
    override val span: SourceSpan,
  ) : ValueLiteral

  public data class StructLiteral(
    val fieldValues: List<ValueLiteral>,
    override val span: SourceSpan,
  ) : ValueLiteral
}
