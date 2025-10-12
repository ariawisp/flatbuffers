package dev.flatbuffers.parser.include

import dev.flatbuffers.ast.SchemaFile
import kotlin.io.path.Path
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class SchemaLoaderTest {
  private class FakeFileSystem(private val files: Map<String, String>) : FileSystemReader {
    override fun read(path: java.nio.file.Path): String =
      files[path.normalize().toString()] ?: error("No file for $path")

    override fun exists(path: java.nio.file.Path): Boolean = files.containsKey(path.normalize().toString())

    override fun toAbsolutePath(path: java.nio.file.Path): java.nio.file.Path =
      Path(path.toString()).normalize()
  }

  @Test
  fun `loads entry file and include`() {
    val fs = FakeFileSystem(
      mapOf(
        "/schema/main.fbs" to "include \"other.fbs\"; table Main { id:int; }",
        "/schema/other.fbs" to "table Other { value:int; }",
      )
    )
    val loader = SchemaLoader(listOf(Path("/schema")), fs)
    val schemas = loader.load(Path("/schema/main.fbs"))
    assertEquals(2, schemas.size)
    assertEquals("/schema/other.fbs", schemas[0].file)
    assertEquals("/schema/main.fbs", schemas[1].file)
  }

  @Test
  fun `throws on missing include`() {
    val fs = FakeFileSystem(mapOf("/main.fbs" to "include \"missing.fbs\";"))
    val loader = SchemaLoader(emptyList(), fs)
    assertFailsWith<IncludeNotFoundException> {
      loader.load(Path("/main.fbs"))
    }
  }

  @Test
  fun `detects include cycles`() {
    val fs = FakeFileSystem(
      mapOf(
        "/a.fbs" to "include \"b.fbs\";",
        "/b.fbs" to "include \"a.fbs\";",
      )
    )
    val loader = SchemaLoader(emptyList(), fs)
    assertFailsWith<IncludeCycleException> {
      loader.load(Path("/a.fbs"))
    }
  }
}
