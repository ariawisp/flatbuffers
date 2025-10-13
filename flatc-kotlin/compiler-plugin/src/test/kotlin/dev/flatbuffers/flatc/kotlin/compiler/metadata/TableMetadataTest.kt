package dev.flatbuffers.flatc.kotlin.compiler.metadata

import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersConfigurationKeys
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import dev.flatbuffers.flatc.kotlin.compiler.options.addSchemaPath
import dev.flatbuffers.flatc.kotlin.compiler.schema.SchemaIndex
import dev.flatbuffers.semantics.ResolvedEnum
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.io.path.createTempDirectory
import kotlin.io.path.writeText
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

  @Test
  fun `enum array alias naming matches legacy generator`() {
    val schemaIndex = loadSchema(createEnumSchema().toString())
    val enumDeclaration = schemaIndex.declarationFor("enum_test.Simple") as? ResolvedEnum
    val resolvedEnum = assertNotNull(enumDeclaration, "enum_test.Simple enum should be present")

    val aliasClassId = resolvedEnum.classId().enumArrayClassId()

    assertEquals("enum_test", aliasClassId.packageFqName.asString())
    assertEquals("SimpleArray", aliasClassId.shortClassName.asString())
  }

  private fun loadSchema(vararg schemaPaths: String): SchemaIndex {
    val configuration =
      CompilerConfiguration().apply {
        put(FlatbuffersConfigurationKeys.ENABLED, true)
        val workspaceRoot = Paths.get("..", "..").toAbsolutePath().normalize()
        schemaPaths.forEach { pathString ->
          val path = Paths.get(pathString)
          val absolutePath = if (path.isAbsolute) path else workspaceRoot.resolve(path)
          addSchemaPath(absolutePath.toString())
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

private fun createEnumSchema(): Path {
  val dir = createTempDirectory("flatbuffers-enum-test")
  val file = dir.resolve("enum_schema.fbs")
  file.writeText(
    """
    namespace enum_test;
    enum Simple:ubyte { Foo = 1, Bar = 2 }
    table Holder { value: Simple; }
    root_type Holder;
    """
      .trimIndent()
  )
  return file
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
