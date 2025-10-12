package dev.flatbuffers.semantics

import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.SchemaFile
import dev.flatbuffers.ast.SourceSpan
import dev.flatbuffers.ast.TableConstraint

public enum class DiagnosticSeverity { ERROR, WARNING, INFO }

public data class Diagnostic(
  val severity: DiagnosticSeverity,
  val message: String,
  val span: SourceSpan?,
)

public data class SemanticAnalysisResult(
  val schema: ResolvedSchema,
  val diagnostics: List<Diagnostic>,
)

public data class ResolvedSchema(
  val files: List<SchemaFile>,
  val declarations: Map<String, ResolvedDeclaration>,
  val rootTypes: List<ResolvedType>,
  val fileIdentifier: String?,
  val fileExtension: String?,
)

public sealed interface ResolvedDeclaration {
  public val name: String
  public val qualifiedName: String
  public val docComment: DocComment?
  public val attributes: List<ResolvedAttribute>
}

public data class ResolvedTable(
  override val name: String,
  override val qualifiedName: String,
  val namespace: String?,
  val fields: List<ResolvedField>,
  val constraints: Set<TableConstraint>,
  override val attributes: List<ResolvedAttribute>,
  override val docComment: DocComment?,
) : ResolvedDeclaration

public data class ResolvedStruct(
  override val name: String,
  override val qualifiedName: String,
  val namespace: String?,
  val fields: List<ResolvedField>,
  override val attributes: List<ResolvedAttribute>,
  override val docComment: DocComment?,
) : ResolvedDeclaration

public data class ResolvedEnum(
  override val name: String,
  override val qualifiedName: String,
  val namespace: String?,
  val baseType: ResolvedScalarType,
  val values: List<ResolvedEnumValue>,
  override val attributes: List<ResolvedAttribute>,
  override val docComment: DocComment?,
) : ResolvedDeclaration

public data class ResolvedUnion(
  override val name: String,
  override val qualifiedName: String,
  val namespace: String?,
  val members: List<ResolvedUnionMember>,
  override val attributes: List<ResolvedAttribute>,
  override val docComment: DocComment?,
) : ResolvedDeclaration

public data class ResolvedRpcService(
  override val name: String,
  override val qualifiedName: String,
  val namespace: String?,
  val methods: List<ResolvedRpcMethod>,
  override val attributes: List<ResolvedAttribute>,
  override val docComment: DocComment?,
) : ResolvedDeclaration

public data class ResolvedField(
  val name: String,
  val type: ResolvedType,
  val defaultValue: ResolvedValue?,
  val attributes: List<ResolvedAttribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class ResolvedEnumValue(
  val name: String,
  val value: Long,
  val attributes: List<ResolvedAttribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class ResolvedUnionMember(
  val type: ResolvedType,
  val alias: String?,
  val attributes: List<ResolvedAttribute>,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class ResolvedRpcMethod(
  val name: String,
  val requestType: ResolvedType,
  val responseType: ResolvedType,
  val attributes: List<ResolvedAttribute>,
  val streamingRequest: Boolean,
  val streamingResponse: Boolean,
  val docComment: DocComment?,
  val span: SourceSpan,
)

public data class ResolvedAttribute(
  val name: String,
  val fullName: String,
  val arguments: List<ResolvedAttributeArgument>,
  val span: SourceSpan,
)

public sealed interface ResolvedAttributeArgument {
  public val span: SourceSpan
}

public data class ResolvedNamedAttributeArgument(
  val name: String,
  val value: ResolvedValue,
  override val span: SourceSpan,
) : ResolvedAttributeArgument

public data class ResolvedPositionalAttributeArgument(
  val value: ResolvedValue,
  override val span: SourceSpan,
) : ResolvedAttributeArgument

public sealed interface ResolvedValue {
  public val span: SourceSpan
}

public data class ResolvedBooleanValue(
  val value: Boolean,
  override val span: SourceSpan,
) : ResolvedValue

public data class ResolvedIntegerValue(
  val value: Long,
  override val span: SourceSpan,
) : ResolvedValue

public data class ResolvedFloatValue(
  val value: Double,
  override val span: SourceSpan,
) : ResolvedValue

public data class ResolvedStringValue(
  val value: String,
  override val span: SourceSpan,
) : ResolvedValue

public data class ResolvedEnumConstantValue(
  val enum: ResolvedEnum,
  val value: ResolvedEnumValue,
  override val span: SourceSpan,
) : ResolvedValue

public sealed interface ResolvedType {
  public val span: SourceSpan?
}

public data class ResolvedScalarType(
  val scalar: dev.flatbuffers.ast.ScalarType,
  override val span: SourceSpan?,
) : ResolvedType

public object ResolvedStringType : ResolvedType {
  override val span: SourceSpan? = null
}

public data class ResolvedNamedType(
  val name: String,
  val declaration: ResolvedDeclaration?,
  override val span: SourceSpan?,
) : ResolvedType

public data class ResolvedVectorType(
  val elementType: ResolvedType,
  override val span: SourceSpan?,
) : ResolvedType

public data class ResolvedArrayType(
  val elementType: ResolvedType,
  val length: Int,
  override val span: SourceSpan?,
) : ResolvedType

public data class ResolvedUnresolvedType(
  val name: String,
  override val span: SourceSpan?,
) : ResolvedType
