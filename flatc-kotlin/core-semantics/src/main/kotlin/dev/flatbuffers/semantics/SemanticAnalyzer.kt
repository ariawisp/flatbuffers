package dev.flatbuffers.semantics

import dev.flatbuffers.ast.Attribute
import dev.flatbuffers.ast.AttributeArgument
import dev.flatbuffers.ast.AttributeDeclaration
import dev.flatbuffers.ast.AttributeNamedArgument
import dev.flatbuffers.ast.AttributePositionalArgument
import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.EnumDeclaration
import dev.flatbuffers.ast.Enumerator
import dev.flatbuffers.ast.Field
import dev.flatbuffers.ast.FileExtensionDirective
import dev.flatbuffers.ast.FileIdentifierDirective
import dev.flatbuffers.ast.NamespaceDeclaration
import dev.flatbuffers.ast.QualifiedName
import dev.flatbuffers.ast.RootTypeDeclaration
import dev.flatbuffers.ast.RpcMethod
import dev.flatbuffers.ast.RpcServiceDeclaration
import dev.flatbuffers.ast.SchemaFile
import dev.flatbuffers.ast.SchemaStatement
import dev.flatbuffers.ast.StructDeclaration
import dev.flatbuffers.ast.TableConstraint
import dev.flatbuffers.ast.TableDeclaration
import dev.flatbuffers.ast.TypeDeclaration
import dev.flatbuffers.ast.TypeRef
import dev.flatbuffers.ast.UnionDeclaration
import dev.flatbuffers.ast.UnionMember
import dev.flatbuffers.ast.ValueLiteral
import dev.flatbuffers.ast.SourceSpan
import dev.flatbuffers.ast.Identifier
import dev.flatbuffers.ast.ScalarType

public class SemanticAnalyzer {
  public fun analyze(files: List<SchemaFile>): SemanticAnalysisResult {
    val collector = DiagnosticCollector()
    val registry = DeclarationRegistry(collector)
    files.forEach { registry.registerFile(it) }
    val resolver = DeclarationResolver(registry, collector)
    val declarations = registry.allSymbols().associate { it.fullName to resolver.resolveDeclaration(it.fullName) }
    val roots = registry.rootTypeNames.mapNotNull { resolver.resolveType(it.namespace, it.nameQualified, it.span) }
    val schema = ResolvedSchema(
      files = files,
      declarations = declarations,
      rootTypes = roots,
      fileIdentifier = registry.fileIdentifier,
      fileExtension = registry.fileExtension,
    )
    return SemanticAnalysisResult(schema, collector.diagnostics())
  }
}

private class DiagnosticCollector {
  private val _diagnostics = mutableListOf<Diagnostic>()
  fun report(severity: DiagnosticSeverity, message: String, span: SourceSpan?) {
    _diagnostics += Diagnostic(severity, message, span)
  }
  fun diagnostics(): List<Diagnostic> = _diagnostics
}

private data class Symbol(val fullName: String, val shortName: String, val namespace: String?, val declaration: TypeDeclaration)

private data class RootTypeEntry(val namespace: String?, val nameQualified: QualifiedName, val span: SourceSpan)

private class DeclarationRegistry(private val diagnostics: DiagnosticCollector) {
  private val symbols = linkedMapOf<String, Symbol>()
  private val declarationsByAst = mutableMapOf<TypeDeclaration, Symbol>()
  private val namespaceByFile = mutableMapOf<SchemaFile, String?>()
  private val rootTypes = mutableListOf<RootTypeEntry>()
  private var identifier: String? = null
  private var extension: String? = null

  val fileIdentifier: String? get() = identifier
  val fileExtension: String? get() = extension
  val rootTypeNames: List<RootTypeEntry> get() = rootTypes

  fun allSymbols(): List<Symbol> = symbols.values.toList()

  fun registerFile(file: SchemaFile) {
    var currentNamespace: String? = null
    file.statements.forEach { statement ->
      when (statement) {
        is NamespaceDeclaration -> currentNamespace = statement.name.segments.joinToString(".") { it.value }
        is TypeDeclaration -> registerDeclaration(statement, currentNamespace)
        is RootTypeDeclaration -> statement.types.forEach { rootTypes += RootTypeEntry(currentNamespace, it, it.span) }
        is FileIdentifierDirective -> {
          if (identifier != null && identifier != statement.identifier) {
            diagnostics.report(DiagnosticSeverity.ERROR, "Multiple file_identifier directives", statement.span)
          }
          identifier = statement.identifier
        }
        is FileExtensionDirective -> {
          if (extension != null && extension != statement.extension) {
            diagnostics.report(DiagnosticSeverity.ERROR, "Multiple file_extension directives", statement.span)
          }
          extension = statement.extension
        }
        is AttributeDeclaration -> Unit
        else -> Unit
      }
    }
    namespaceByFile[file] = currentNamespace
  }

  private fun registerDeclaration(declaration: TypeDeclaration, namespace: String?) {
    val fullName = listOfNotNull(namespace, declaration.name.value).filter { it.isNotEmpty() }.joinToString(".")
    if (symbols.containsKey(fullName)) {
      diagnostics.report(DiagnosticSeverity.ERROR, "Duplicate declaration '$fullName'", declaration.span)
    } else {
      val symbol = Symbol(fullName, declaration.name.value, namespace, declaration)
      symbols[fullName] = symbol
      declarationsByAst[declaration] = symbol
    }
  }

  fun lookup(fullName: String): Symbol? = symbols[fullName]

  fun lookupInNamespace(name: QualifiedName, namespace: String?): Symbol? {
    val qualified = name.fullName
    return symbols[qualified]
      ?: if (!qualified.contains('.')) {
        val nsName = listOfNotNull(namespace, qualified).joinToString(".")
        symbols[nsName]
      } else null
  }
}

private class DeclarationResolver(
  private val registry: DeclarationRegistry,
  private val diagnostics: DiagnosticCollector,
) {
  private val declCache = mutableMapOf<String, ResolvedDeclaration>()
  private val resolvingDecls = mutableSetOf<String>()

  fun resolveDeclaration(fullName: String): ResolvedDeclaration {
    declCache[fullName]?.let { return it }
    if (!resolvingDecls.add(fullName)) {
      diagnostics.report(DiagnosticSeverity.ERROR, "Circular declaration reference: $fullName", null)
      return ResolvedUnresolvedType(fullName, null).toPlaceholderDeclaration(fullName)
    }
    val symbol = registry.lookup(fullName)
      ?: return ResolvedUnresolvedType(fullName, null).toPlaceholderDeclaration(fullName)
    val resolved = when (val decl = symbol.declaration) {
      is TableDeclaration -> resolveTable(symbol, decl)
      is StructDeclaration -> resolveStruct(symbol, decl)
      is EnumDeclaration -> resolveEnum(symbol, decl)
      is UnionDeclaration -> resolveUnion(symbol, decl)
      is RpcServiceDeclaration -> resolveService(symbol, decl)
    }
    declCache[fullName] = resolved
    resolvingDecls.remove(fullName)
    return resolved
  }

  fun resolveType(namespace: String?, qualifiedName: QualifiedName, span: SourceSpan): ResolvedType? {
    val symbol = registry.lookupInNamespace(qualifiedName, namespace)
    return if (symbol != null) {
      val decl = resolveDeclaration(symbol.fullName)
      ResolvedNamedType(symbol.fullName, decl, span)
    } else {
      diagnostics.report(DiagnosticSeverity.ERROR, "Unknown type '${qualifiedName.fullName}'", span)
      ResolvedUnresolvedType(qualifiedName.fullName, span)
    }
  }

  private fun resolveTable(symbol: Symbol, declaration: TableDeclaration): ResolvedTable {
    val fields = declaration.fields.map { resolveField(symbol, it) }
    val constraints = declaration.constraints
    val attributes = declaration.attributes.map { resolveAttribute(symbol.namespace, it) }
    return ResolvedTable(
      name = symbol.shortName,
      qualifiedName = symbol.fullName,
      namespace = symbol.namespace,
      fields = fields,
      constraints = constraints,
      attributes = attributes,
      docComment = declaration.docComment,
    )
  }

  private fun resolveStruct(symbol: Symbol, declaration: StructDeclaration): ResolvedStruct {
    val fields = declaration.fields.map { resolveField(symbol, it) }
    val attributes = declaration.attributes.map { resolveAttribute(symbol.namespace, it) }
    return ResolvedStruct(
      name = symbol.shortName,
      qualifiedName = symbol.fullName,
      namespace = symbol.namespace,
      fields = fields,
      attributes = attributes,
      docComment = declaration.docComment,
    )
  }

  private fun resolveEnum(symbol: Symbol, declaration: EnumDeclaration): ResolvedEnum {
    val baseType = resolveScalarType(declaration.baseType, symbol.namespace)
    var nextValue = 0L
    val values = declaration.enumerators.map { enumerator ->
      val value = enumerator.value?.let { resolveValueLiteral(baseType, it) as? ResolvedIntegerValue }?.value ?: nextValue
      nextValue = value + 1
      ResolvedEnumValue(
        name = enumerator.name.value,
        value = value,
        attributes = enumerator.attributes.map { resolveAttribute(symbol.namespace, it) },
        docComment = enumerator.docComment,
        span = enumerator.span,
      )
    }
    return ResolvedEnum(
      name = symbol.shortName,
      qualifiedName = symbol.fullName,
      namespace = symbol.namespace,
      baseType = baseType,
      values = values,
      attributes = declaration.attributes.map { resolveAttribute(symbol.namespace, it) },
      docComment = declaration.docComment,
    )
  }

  private fun resolveUnion(symbol: Symbol, declaration: UnionDeclaration): ResolvedUnion {
    val members = declaration.members.map {
      val type = resolveType(symbol.namespace, (it.type as TypeRef.Named).name, it.span)
        ?: ResolvedUnresolvedType(it.type.span.start.file, it.span)
      ResolvedUnionMember(
        type = type,
        alias = it.alias?.value,
        attributes = it.attributes.map { attr -> resolveAttribute(symbol.namespace, attr) },
        docComment = it.docComment,
        span = it.span,
      )
    }
    return ResolvedUnion(
      name = symbol.shortName,
      qualifiedName = symbol.fullName,
      namespace = symbol.namespace,
      members = members,
      attributes = declaration.attributes.map { resolveAttribute(symbol.namespace, it) },
      docComment = declaration.docComment,
    )
  }

  private fun resolveService(symbol: Symbol, declaration: RpcServiceDeclaration): ResolvedRpcService {
    val methods = declaration.methods.map { method ->
      val requestType = resolveType(symbol.namespace, method.requestType.name, method.span) ?: ResolvedUnresolvedType(method.requestType.name.fullName, method.span)
      val responseType = resolveType(symbol.namespace, method.responseType.name, method.span) ?: ResolvedUnresolvedType(method.responseType.name.fullName, method.span)
      ResolvedRpcMethod(
        name = method.name.value,
        requestType = requestType,
        responseType = responseType,
        attributes = method.attributes.map { resolveAttribute(symbol.namespace, it) },
        streamingRequest = method.streamingRequest,
        streamingResponse = method.streamingResponse,
        docComment = method.docComment,
        span = method.span,
      )
    }
    return ResolvedRpcService(
      name = symbol.shortName,
      qualifiedName = symbol.fullName,
      namespace = symbol.namespace,
      methods = methods,
      attributes = declaration.attributes.map { resolveAttribute(symbol.namespace, it) },
      docComment = declaration.docComment,
    )
  }

  private fun resolveField(symbol: Symbol, field: Field): ResolvedField {
    val type = resolveTypeRef(symbol.namespace, field.type)
    val defaultValue = field.defaultValue?.let { resolveValueLiteral(type, it) }
    val attributes = field.attributes.map { resolveAttribute(symbol.namespace, it) }
    if (type is ResolvedUnresolvedType) {
      diagnostics.report(DiagnosticSeverity.ERROR, "Unresolved type for field '${field.name.value}'", field.span)
    }
    return ResolvedField(
      name = field.name.value,
      type = type,
      defaultValue = defaultValue,
      attributes = attributes,
      docComment = field.docComment,
      span = field.span,
    )
  }

  private fun resolveTypeRef(namespace: String?, type: TypeRef): ResolvedType {
    return when (type) {
      is TypeRef.Scalar -> ResolvedScalarType(type.type, type.span)
      is TypeRef.StringType -> ResolvedStringType
      is TypeRef.Named -> resolveType(namespace, type.name, type.span) ?: ResolvedUnresolvedType(type.name.fullName, type.span)
      is TypeRef.Vector -> {
        val element = resolveTypeRef(namespace, type.elementType)
        ResolvedVectorType(element, type.span)
      }
      is TypeRef.Array -> {
        val element = resolveTypeRef(namespace, type.elementType)
        ResolvedArrayType(element, type.length, type.span)
      }
    }
  }

  private fun resolveScalarType(baseType: TypeRef, namespace: String?): ResolvedScalarType {
    val type = resolveTypeRef(namespace, baseType)
    return when (type) {
      is ResolvedScalarType -> type
      else -> {
        diagnostics.report(DiagnosticSeverity.ERROR, "Enum base type must be scalar", baseType.span)
        ResolvedScalarType(ScalarType.INT, baseType.span)
      }
    }
  }

  private fun resolveValueLiteral(expectedType: ResolvedType, literal: ValueLiteral): ResolvedValue? {
    return when (literal) {
      is ValueLiteral.BooleanLiteral -> ResolvedBooleanValue(literal.value, literal.span)
      is ValueLiteral.IntegerLiteral -> ResolvedIntegerValue(literal.value, literal.span)
      is ValueLiteral.FloatLiteral -> ResolvedFloatValue(literal.value, literal.span)
      is ValueLiteral.StringLiteral -> ResolvedStringValue(literal.value, literal.span)
      is ValueLiteral.IdentifierLiteral -> {
        val name = literal.identifier
        val enumSymbol = registry.lookup(name.fullName)
        val resolvedDecl = enumSymbol?.let { resolveDeclaration(it.fullName) }
        if (resolvedDecl is ResolvedEnum) {
          val value = resolvedDecl.values.firstOrNull { it.name == name.segments.last().value }
          if (value != null) {
            ResolvedEnumConstantValue(resolvedDecl, value, literal.span)
          } else {
            diagnostics.report(DiagnosticSeverity.ERROR, "Unknown enum value '${name.fullName}'", literal.span)
            null
          }
        } else {
          diagnostics.report(DiagnosticSeverity.ERROR, "Identifier '${name.fullName}' is not an enum value", literal.span)
          null
        }
      }
      is ValueLiteral.ArrayLiteral -> {
        val elementType = if (expectedType is ResolvedVectorType) expectedType.elementType else expectedType
        literal.elements.map { resolveValueLiteral(elementType, it) }
        null
      }
      is ValueLiteral.StructLiteral -> null
    }
  }

  private fun resolveAttribute(namespace: String?, attribute: Attribute): ResolvedAttribute {
    val fullName = attribute.name.fullName
    val arguments = attribute.arguments.map { resolveAttributeArgument(namespace, it) }
    return ResolvedAttribute(attribute.name.segments.last().value, fullName, arguments, attribute.span)
  }

  private fun resolveAttributeArgument(namespace: String?, argument: AttributeArgument): ResolvedAttributeArgument = when (argument) {
    is AttributeNamedArgument -> {
      val value = resolveValueLiteral(ResolvedScalarType(ScalarType.INT, argument.value.span), argument.value)
        ?: ResolvedIntegerValue(0, argument.value.span)
      ResolvedNamedAttributeArgument(argument.name.value, value, argument.span)
    }
    is AttributePositionalArgument -> {
      val value = resolveValueLiteral(ResolvedScalarType(ScalarType.INT, argument.value.span), argument.value)
        ?: ResolvedIntegerValue(0, argument.value.span)
      ResolvedPositionalAttributeArgument(value, argument.span)
    }
  }

  private fun ResolvedUnresolvedType.toPlaceholderDeclaration(fullName: String): ResolvedDeclaration {
    return ResolvedEnum(fullName.substringAfterLast('.'), fullName, null, ResolvedScalarType(ScalarType.INT, span), emptyList(), emptyList(), null)
  }
}
