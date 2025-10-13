package dev.flatbuffers.flatc.kotlin.compiler.ir

import dev.flatbuffers.flatc.kotlin.compiler.ir.internal.FlatbuffersSchemaMetadata
import dev.flatbuffers.flatc.kotlin.compiler.testing.AbstractFlatbuffersIrTest
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrDeclarationOrigin
import org.jetbrains.kotlin.ir.declarations.IrMetadataSourceOwner
import org.jetbrains.kotlin.ir.declarations.IrModuleFragment
import org.jetbrains.kotlin.ir.declarations.IrProperty
import org.jetbrains.kotlin.ir.declarations.IrSimpleFunction
import org.jetbrains.kotlin.ir.symbols.UnsafeDuringIrConstructionAPI
import org.jetbrains.kotlin.ir.util.companionObject
import org.jetbrains.kotlin.ir.util.dumpKotlinLike
import org.jetbrains.kotlin.ir.util.fqNameWhenAvailable
import org.jetbrains.kotlin.test.backend.ir.IrBackendInput
import org.jetbrains.kotlin.test.builders.TestConfigurationBuilder
import org.jetbrains.kotlin.test.builders.configureIrHandlersStep
import org.jetbrains.kotlin.test.model.BackendKinds
import org.jetbrains.kotlin.test.model.TestModule
import org.jetbrains.kotlin.test.model.BackendInputHandler
import org.jetbrains.kotlin.test.services.TestServices

class FlatbuffersSampleIrTest : AbstractFlatbuffersIrTest() {
  private val dataRoot: Path =
    System.getProperty("flatbuffers.tests.dataRoot")
      ?.let(Paths::get)
      ?: error("flatbuffers.tests.dataRoot system property not configured")

  override fun configure(builder: TestConfigurationBuilder) {
    super.configure(builder)
    builder.configureIrHandlersStep {
      useHandlers(::SampleIrAssertionsHandler)
    }
  }

  @Test
  fun sampleTableLowering() {
    val testFile = dataRoot.resolve("ir/sample/sample.kt")
    runTest(testFile.toString())
  }
}

@OptIn(UnsafeDuringIrConstructionAPI::class)
private class SampleIrAssertionsHandler(
  testServices: TestServices,
) :
  BackendInputHandler<IrBackendInput>(
    testServices,
    BackendKinds.IrBackend,
    failureDisablesNextSteps = false,
    doNotRunIfThereWerePreviousFailures = false,
  ) {

  override fun processModule(module: TestModule, info: IrBackendInput) {
    if (module.name != "main") return

    val irModule = info.irModuleFragment
    val sampleClass = irModule.findGeneratedClass("sample.Sample")
    val classMetadata =
      (sampleClass as IrMetadataSourceOwner).metadata as? FlatbuffersSchemaMetadata.Class
        ?: error("Sample class metadata missing")
    assertEquals("table docs", classMetadata.docString?.trim())

    assertFunctionDelegatesToReset(sampleClass.findGeneratedFunction("init"))
    assertFunctionDelegatesToReset(sampleClass.findGeneratedFunction("reset"))

    val hpProperty = sampleClass.findGeneratedProperty("hp")
    val hpGetter = hpProperty.getter ?: error("hp getter missing")
    assertScalarGetterShape(hpGetter)

    val companion =
      sampleClass.companionObject() ?: error("Sample companion object not generated")
    assertCompanionAddCallsBuilder(companion.findGeneratedFunction("addHp"))

    val propertyMetadata =
      (hpProperty as IrMetadataSourceOwner).metadata as? FlatbuffersSchemaMetadata.Property
        ?: error("hp metadata missing")
    assertEquals("hp docs", propertyMetadata.docString?.trim())
    assertTrue(
      propertyMetadata.renderedDocComment.orEmpty().contains("hp docs"),
      "Rendered doc comment should contain schema docs",
    )
  }

  override fun processAfterAllModules(someAssertionWasFailed: Boolean) = Unit

  private fun IrModuleFragment.findGeneratedClass(fqName: String): IrClass {
    return files
      .flatMap { it.declarations }
      .filterIsInstance<IrClass>()
      .firstOrNull { it.fqNameWhenAvailable?.asString() == fqName && it.origin == IrDeclarationOrigin.GeneratedByPlugin }
      ?: error("Generated class $fqName not found")
  }

  private fun IrClass.findGeneratedFunction(name: String): IrSimpleFunction {
    return declarations
      .filterIsInstance<IrSimpleFunction>()
      .firstOrNull { it.name.asString() == name && it.origin == IrDeclarationOrigin.GeneratedByPlugin }
      ?: error("Function $name not found on ${fqNameWhenAvailable}")
  }

  private fun IrClass.findGeneratedProperty(name: String): IrProperty {
    return declarations
      .filterIsInstance<IrProperty>()
      .firstOrNull { it.name.asString() == name && it.origin == IrDeclarationOrigin.GeneratedByPlugin }
      ?: error("Property $name not found on ${fqNameWhenAvailable}")
  }

  private fun assertFunctionDelegatesToReset(function: IrSimpleFunction) {
    val dump = function.dumpKotlinLike()
    assertContains(
      dump,
      "com/google/flatbuffers/kotlin/Table.reset",
      message = "${function.name} should delegate to Table.reset, dump:\n$dump",
    )
  }

  private fun assertScalarGetterShape(getter: IrSimpleFunction) {
    val dump = getter.dumpKotlinLike()
    assertContains(
      dump,
      "lookupField",
      message = "Getter ${getter.name} should invoke lookupField; dump:\n$dump",
    )
    assertContains(
      dump,
      "ReadWriteBuffer.getShort",
      message = "Getter ${getter.name} should read via ReadWriteBuffer.getShort; dump:\n$dump",
    )
    assertContains(
      dump,
      "123.toShort()",
      message = "Getter ${getter.name} should include default literal 123; dump:\n$dump",
    )
  }

  private fun assertCompanionAddCallsBuilder(function: IrSimpleFunction) {
    val dump = function.dumpKotlinLike()
    assertContains(
      dump,
      "FlatBufferBuilder.add",
      message = "Companion builder should call FlatBufferBuilder.add; dump:\n$dump",
    )
  }
}
