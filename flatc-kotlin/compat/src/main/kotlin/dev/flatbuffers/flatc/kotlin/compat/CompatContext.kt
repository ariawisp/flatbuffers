package dev.flatbuffers.flatc.kotlin.compat

import java.io.FileNotFoundException
import java.util.ServiceLoader
import org.jetbrains.kotlin.GeneratedDeclarationKey
import org.jetbrains.kotlin.KtFakeSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.descriptors.Modality
import org.jetbrains.kotlin.descriptors.Visibility
import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationStatus
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.FirTypeParameter
import org.jetbrains.kotlin.fir.extensions.ExperimentalTopLevelDeclarationsGenerationApi
import org.jetbrains.kotlin.fir.extensions.FirExtension
import org.jetbrains.kotlin.fir.plugin.SimpleFunctionBuildingContext
import org.jetbrains.kotlin.fir.symbols.FirBasedSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirCallableSymbol
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.fir.types.ConeKotlinType
import org.jetbrains.kotlin.ir.builders.Scope
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.types.IrType
import org.jetbrains.kotlin.ir.types.IrTypeSystemContext
import org.jetbrains.kotlin.name.CallableId

public interface CompatContext {
  public companion object {
    private val lazyInstance: CompatContext by lazy { create() }

    public fun getInstance(): CompatContext = lazyInstance

    private fun loadFactories(): Sequence<Factory> {
      return ServiceLoader.load(Factory::class.java, Factory::class.java.classLoader).asSequence()
    }

    private fun resolveFactory(
      factories: Sequence<Factory> = loadFactories(),
      testVersion: String? = null,
    ): Factory {
      val targetFactory =
        factories
          .mapNotNull { factory ->
            try {
              FactoryData(factory.currentVersion, factory)
            } catch (_: Throwable) {
              null
            }
          }
          .filter { (version, factory) -> (testVersion ?: version) >= factory.minVersion }
          .maxByOrNull { (_, factory) -> factory.minVersion }
          ?.factory
          ?: error(
            """
              Unrecognized Kotlin version!

              Available factories for: ${
                factories.joinToString(separator = "\n") { it.minVersion }
              }
              Detected version(s): ${factories.map { it.currentVersion }.distinct().joinToString(separator = "\n")}
            """
              .trimIndent()
          )
      return targetFactory
    }

    private fun create(): CompatContext = resolveFactory().create()
  }

  public interface Factory {
    public val minVersion: String

    public val currentVersion: String
      get() = loadCompilerVersion()

    public fun create(): CompatContext

    public companion object {
      private const val COMPILER_VERSION_FILE = "META-INF/compiler.version"

      internal fun loadCompilerVersion(): String {
        val inputStream =
          FirExtension::class.java.classLoader!!.getResourceAsStream(COMPILER_VERSION_FILE)
            ?: throw FileNotFoundException("'$COMPILER_VERSION_FILE' not found in the classpath")
        return inputStream.bufferedReader().use { it.readText() }
      }
    }
  }

  public fun FirBasedSymbol<*>.getContainingClassSymbol(): FirClassLikeSymbol<*>?

  public fun FirCallableSymbol<*>.getContainingSymbol(session: FirSession): FirBasedSymbol<*>?

  public fun FirDeclaration.getContainingClassSymbol(): FirClassLikeSymbol<*>?

  @ExperimentalTopLevelDeclarationsGenerationApi
  public fun FirExtension.createTopLevelFunction(
    key: GeneratedDeclarationKey,
    callableId: CallableId,
    returnType: ConeKotlinType,
    containingFileName: String? = null,
    config: SimpleFunctionBuildingContext.() -> Unit = {},
  ): FirSimpleFunction

  @ExperimentalTopLevelDeclarationsGenerationApi
  public fun FirExtension.createTopLevelFunction(
    key: GeneratedDeclarationKey,
    callableId: CallableId,
    returnTypeProvider: (List<FirTypeParameter>) -> ConeKotlinType,
    containingFileName: String? = null,
    config: SimpleFunctionBuildingContext.() -> Unit = {},
  ): FirSimpleFunction

  public fun KtSourceElement.fakeElement(
    newKind: KtFakeSourceElementKind,
    startOffset: Int = -1,
    endOffset: Int = -1,
  ): KtSourceElement

  public fun FirDeclarationStatus.copy(
    visibility: Visibility? = this.visibility,
    modality: Modality? = this.modality,
    isExpect: Boolean = this.isExpect,
    isActual: Boolean = this.isActual,
    isOverride: Boolean = this.isOverride,
    isOperator: Boolean = this.isOperator,
    isInfix: Boolean = this.isInfix,
    isInline: Boolean = this.isInline,
    isValue: Boolean = this.isValue,
    isTailRec: Boolean = this.isTailRec,
    isExternal: Boolean = this.isExternal,
    isConst: Boolean = this.isConst,
    isLateInit: Boolean = this.isLateInit,
    isInner: Boolean = this.isInner,
    isCompanion: Boolean = this.isCompanion,
    isData: Boolean = this.isData,
    isSuspend: Boolean = this.isSuspend,
    isStatic: Boolean = this.isStatic,
    isFromSealedClass: Boolean = this.isFromSealedClass,
    isFromEnumClass: Boolean = this.isFromEnumClass,
    isFun: Boolean = this.isFun,
    hasStableParameterNames: Boolean = this.hasStableParameterNames,
  ): FirDeclarationStatus

  public fun IrClass.addFakeOverrides(typeSystem: IrTypeSystemContext)

  public fun Scope.createTemporaryVariableDeclarationCompat(
    irType: IrType,
    nameHint: String? = null,
    isMutable: Boolean = false,
    origin: IrDeclarationOrigin = IrDeclarationOrigin.IR_TEMPORARY_VARIABLE,
    startOffset: Int,
    endOffset: Int,
  ): IrVariable
}

private data class FactoryData(val version: String, val factory: CompatContext.Factory)
