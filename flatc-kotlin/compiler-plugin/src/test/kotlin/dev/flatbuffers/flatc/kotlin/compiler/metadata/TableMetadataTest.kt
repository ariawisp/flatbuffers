package dev.flatbuffers.flatc.kotlin.compiler.metadata

import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersConfigurationKeys
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.junit.jupiter.api.Test

class TableMetadataTest {

  @Test
  fun `required field metadata uses vtable offsets`() {
    val schemaIndex = loadSchema("tests/required_strings.fbs")
    val table = schemaIndex.tableFor("required_strings.Foo")
    assertNotNull(table, "required_strings.Foo table should be present")

    val metadata = requiredFieldMetadata(table)
    assertEquals(
      listOf(
        RequiredFieldMetadata(fieldName = "str_a", vtableOffset = 4),
        RequiredFieldMetadata(fieldName = "str_b", vtableOffset = 6),
      ),
      metadata,
    )
  }

  @Test
  fun `offset array alias naming matches legacy generator`() {
    val schemaIndex = loadSchema("tests/required_strings.fbs")
    val table = schemaIndex.tableFor("required_strings.Foo")
    assertNotNull(table, "required_strings.Foo table should be present")

    val elementClassId = table.classId()
    val aliasClassId = elementClassId.offsetArrayClassId()

    assertEquals("required_strings", aliasClassId.packageFqName.asString())
    assertEquals("FooOffsetArray", aliasClassId.shortClassName.asString())
  }

  private fun loadSchema(vararg schemaPaths: String): SchemaIndex {
    val configuration =
      CompilerConfiguration().apply {
        put(FlatbuffersConfigurationKeys.ENABLED, true)
    val workspaceRoot = Paths.get("..", "..").toAbsolutePath().normalize()
    schemaPaths.forEach { relativePath ->
      addSchemaPath(workspaceRoot.resolve(relativePath).toString())
    }
      }
    val options = FlatbuffersPluginOptions.load(configuration)
    val collector = RecordingMessageCollector()
    val schemaIndex =
      SchemaIndex.load(options, collector)
        ?: error("Failed to load schemas: ${collector.errors}")
    assertEquals(emptyList<String>(), collector.errors, "SchemaIndex emitted errors")
    return schemaIndex
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
