package dev.flatbuffers.flatc.kotlin.compiler.testing

import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.javaConstructor
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.directives.CodegenTestDirectives.IGNORE_DEXING
import org.jetbrains.kotlin.test.directives.ConfigurationDirectives.WITH_STDLIB
import org.jetbrains.kotlin.test.directives.FirDiagnosticsDirectives.DISABLE_GENERATED_FIR_TAGS
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.FULL_JDK
import org.jetbrains.kotlin.test.directives.JvmEnvironmentConfigurationDirectives.JVM_TARGET
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.runners.codegen.AbstractFirLightTreeBlackBoxCodegenTest
import org.jetbrains.kotlin.test.services.KotlinStandardLibrariesPathProvider

@Suppress("UNCHECKED_CAST")
private val NoIrCompilationErrorsHandler: KClass<BackendInputHandler<IrBackendInput>> =
  sequenceOf(
      "NoIrCompilationErrorsHandler",
      "NoFir2IrCompilationErrorsHandler",
    )
    .mapNotNull { className ->
      try {
        Class.forName("org.jetbrains.kotlin.test.backend.handlers.$className")
      } catch (_: ClassNotFoundException) {
        null
      }
    }
    .firstOrNull()
    ?.kotlin as? KClass<BackendInputHandler<IrBackendInput>>
    ?: error("Could not find NoIrCompilationErrorsHandler for the current kotlin version")

abstract class AbstractFlatbuffersIrTest : AbstractFirLightTreeBlackBoxCodegenTest() {
  override fun createKotlinStandardLibrariesPathProvider(): KotlinStandardLibrariesPathProvider {
    return ClasspathBasedStandardLibrariesPathProvider
  }

  override fun configure(builder: TestConfigurationBuilder) {
    super.configure(builder)

    with(builder) {
      configureFlatbuffersPlugin()

      defaultDirectives {
        JVM_TARGET.with(JvmTarget.JVM_11)
        +FULL_JDK
        +WITH_STDLIB
        +IGNORE_DEXING
        +DISABLE_GENERATED_FIR_TAGS
      }

      configureIrHandlersStep {
        useHandlers(
          { NoIrCompilationErrorsHandler.primaryConstructor!!.javaConstructor!!.newInstance(it) }
        )
      }
    }
  }
}
