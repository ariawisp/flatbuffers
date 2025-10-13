package dev.flatbuffers.flatc.kotlin.compiler.schema

import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersConfigurationKeys
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
import org.junit.jupiter.api.Assertions.assertNotNull
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class SchemaIndexTest {

  @Test
  fun `monster schema loads without diagnostics`() {
    val schema = createSchemaFile()
    val configuration = CompilerConfiguration().apply {
      put(FlatbuffersConfigurationKeys.ENABLED, true)
      addSchemaPath(schema.toString())
    }

    val options = FlatbuffersPluginOptions.load(configuration)
    val collector = RecordingMessageCollector()
    val index = SchemaIndex.load(options, collector)

    assertTrue(collector.errors.isEmpty()) { "SchemaIndex emitted errors: ${collector.errors}" }
    assertNotNull(index, "SchemaIndex should load generated schema")
    val schemaIndex = index!!
    println("Index resolved ${schemaIndex.hashCode()}, diagnostics=${collector.errors}")
  }

  private fun createSchemaFile(): Path {
    val dir = createTempDirectory("flatbuffers-plugin-test")
    val file = dir.resolve("test_schema.fbs")
    file.writeText(
      """
      namespace dev.flatbuffers.sample;
      table Simple {
        id:int;
      }
      root_type Simple;
      """
        .trimIndent()
    )
    return file
  }
}

private class RecordingMessageCollector : MessageCollector {
  private val _errors = CopyOnWriteArrayList<String>()
  val errors: List<String> get() = _errors

  override fun clear() {
    _errors.clear()
  }

  override fun report(
    severity: CompilerMessageSeverity,
    message: String,
    location: CompilerMessageSourceLocation?,
  ) {
    if (severity.isError) {
      _errors += message
    }
  }

  override fun hasErrors(): Boolean = _errors.isNotEmpty()
}
