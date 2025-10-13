package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.SourceSpan
import java.util.LinkedHashMap
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.descriptors.Visibilities
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.expressions.builder.buildArgumentList
import org.jetbrains.kotlin.fir.expressions.builder.buildBlock
import org.jetbrains.kotlin.fir.expressions.builder.buildFunctionCall
import org.jetbrains.kotlin.fir.expressions.builder.buildLiteralExpression
import org.jetbrains.kotlin.fir.plugin.PropertyBuildingContext
import org.jetbrains.kotlin.fir.plugin.SimpleFunctionBuildingContext
import org.jetbrains.kotlin.fir.plugin.createMemberFunction
import org.jetbrains.kotlin.fir.plugin.createMemberProperty
import org.jetbrains.kotlin.fir.references.builder.buildResolvedNamedReference
import org.jetbrains.kotlin.fir.resolve.providers.symbolProvider
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.name.CallableId
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.types.ConstantValueKind
import org.jetbrains.kotlin.fir.expressions.buildResolvedArgumentList
import org.jetbrains.kotlin.fir.types.coneType

internal fun FlatbuffersFirDeclarationGenerator.stubFunction(
  owner: FirClassSymbol<*>,
  key: GeneratedDeclarationKey,
  name: Name,
  returnType: ConeKotlinType,
  visibility: Visibility = Visibilities.Public,
  modality: Modality = Modality.FINAL,
  span: SourceSpan? = null,
  docComment: DocComment? = null,
  configure: SimpleFunctionBuildingContext.() -> Unit = {},
): FirSimpleFunction {
  var schemaDoc: String? = null
  val function =
    createMemberFunction(
      owner = owner,
      key = key,
      name = name,
      returnType = returnType,
    ) {
      schemaDoc = withSchemaSource(span, docComment, schemaSourceIndex)
      this.visibility = visibility
      this.modality = modality
      configure()
    }
  function.attachSchemaMetadata(schemaDoc)
  function.replaceBody(todoBlock("${owner.classId.asFqNameString()}.${name.asString()}"))
  return function
}

internal fun FlatbuffersFirDeclarationGenerator.stubProperty(
  owner: FirClassSymbol<*>,
  key: GeneratedDeclarationKey,
  name: Name,
  returnType: ConeKotlinType,
  hasBackingField: Boolean = false,
  isMutable: Boolean = false,
  span: SourceSpan? = null,
  docComment: DocComment? = null,
  configure: PropertyBuildingContext.() -> Unit = {},
): FirProperty {
  var schemaDoc: String? = null
  val property =
    createMemberProperty(
      owner = owner,
      key = key,
      name = name,
      returnType = returnType,
      isVal = !isMutable,
      hasBackingField = hasBackingField,
    ) {
      schemaDoc = withSchemaSource(span, docComment, schemaSourceIndex)
      configure()
    }
  property.attachSchemaMetadata(schemaDoc)
  val target = "${owner.classId.asFqNameString()}.${name.asString()}"
  property.getter?.replaceBody(todoBlock(target))
  property.setter?.replaceBody(todoBlock(target))
  return property
}

internal fun SimpleFunctionBuildingContext.valueParameter(
  name: String,
  type: ConeKotlinType,
  key: GeneratedDeclarationKey,
  hasDefaultValue: Boolean = false,
  isCrossinline: Boolean = false,
  isNoinline: Boolean = false,
  isVararg: Boolean = false,
) {
  valueParameter(
    Name.identifier(name),
    type,
    hasDefaultValue,
    isCrossinline,
    isNoinline,
    isVararg,
    key,
  )
}

internal fun FlatbuffersFirDeclarationGenerator.todoBlock(target: String) =
  buildBlock {
    statements +=
      buildFunctionCall {
        coneTypeOrNull = session.builtinTypes.nothingType.coneType
        val symbol =
          session.symbolProvider
            .getTopLevelFunctionSymbols(
              TODO_CALLABLE_ID.packageName,
              TODO_CALLABLE_ID.callableName,
            )
            .firstOrNull()
            ?: error("kotlin.TODO symbol not found")
        calleeReference =
          buildResolvedNamedReference {
            name = symbol.callableId.callableName
            resolvedSymbol = symbol
          }
        argumentList =
          buildResolvedArgumentList(
            buildArgumentList {
              arguments +=
                buildLiteralExpression(
                  source = null,
                  kind = ConstantValueKind.String,
                  value = "TODO($target)",
                  setType = true,
                )
            },
            LinkedHashMap(),
          )
      }
  }

private val TODO_CALLABLE_ID = CallableId(FqName("kotlin"), Name.identifier("TODO"))
