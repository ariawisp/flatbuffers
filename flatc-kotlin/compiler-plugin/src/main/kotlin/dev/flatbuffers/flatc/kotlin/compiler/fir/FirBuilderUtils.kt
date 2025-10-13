package dev.flatbuffers.flatc.kotlin.compiler.fir

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

internal fun FlatbuffersFirDeclarationGenerator.stubFunction(
  owner: FirClassSymbol<*>,
  key: GeneratedDeclarationKey,
  name: Name,
  returnType: ConeKotlinType,
  visibility: Visibility = Visibilities.Public,
  modality: Modality = Modality.FINAL,
  configure: SimpleFunctionBuildingContext.() -> Unit = {},
): FirSimpleFunction {
  val function =
    createMemberFunction(
      owner = owner,
      key = key,
      name = name,
      returnType = returnType,
    ) {
      this.visibility = visibility
      this.modality = modality
      configure()
    }
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
  configure: PropertyBuildingContext.() -> Unit = {},
): FirProperty {
  val property =
    createMemberProperty(
      owner = owner,
      key = key,
      name = name,
      returnType = returnType,
      isVal = !isMutable,
      hasBackingField = hasBackingField,
    ) {
      configure()
    }
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
          buildArgumentList {
            arguments +=
              buildLiteralExpression(
                source = null,
                kind = ConstantValueKind.String,
                value = "TODO($target)",
                setType = true,
              )
          }
      }
  }

private val TODO_CALLABLE_ID = CallableId(FqName("kotlin"), Name.identifier("TODO"))
