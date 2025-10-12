package dev.flatbuffers.parser.include

import dev.flatbuffers.ast.SchemaFile
import dev.flatbuffers.parser.SchemaParser
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.io.path.isRegularFile

public class SchemaLoader(
  roots: List<Path>,
  private val fileSystem: FileSystemReader = DefaultFileSystemReader,
) {
  private val includePaths: List<Path> = (roots + listOf(Paths.get("."))).distinct()
  private val visited = linkedMapOf<Path, SchemaFile>()
  private val visiting = ArrayDeque<Path>()

  public fun load(entry: Path): List<SchemaFile> {
    val normalized = fileSystem.toAbsolutePath(entry)
    processFile(normalized)
    return visited.values.toList()
  }

  private fun processFile(path: Path) {
    if (visited.containsKey(path)) return
    if (visiting.contains(path)) {
      val cycle = (visiting + path).joinToString(" -> ") { it.toString() }
      throw IncludeCycleException("Include cycle detected: $cycle")
    }
    visiting.addLast(path)
    val source = fileSystem.read(path)
    val schema = SchemaParser.parse(path.toString(), source)
    schema.statements.filterIsInstance<dev.flatbuffers.ast.IncludeStatement>().forEach { include ->
      val resolved = resolveInclude(path.parent, include.path)
      processFile(resolved)
    }
    visiting.removeLast()
    visited[path] = schema
  }

  private fun resolveInclude(baseDir: Path?, includePath: String): Path {
    val candidatePaths = buildList {
      if (baseDir != null) {
        add(baseDir.resolve(includePath))
      }
      includePaths.forEach { root ->
        add(root.resolve(includePath))
      }
    }
    return candidatePaths.firstOrNull { fileSystem.exists(it) }
      ?: throw IncludeNotFoundException(includePath, includePaths.map { it.toString() })
  }
}

public interface FileSystemReader {
  public fun read(path: Path): String
  public fun exists(path: Path): Boolean
  public fun toAbsolutePath(path: Path): Path
}

private object DefaultFileSystemReader : FileSystemReader {
  override fun read(path: Path): String = Files.readString(path)
  override fun exists(path: Path): Boolean = Files.exists(path) && path.isRegularFile()
  override fun toAbsolutePath(path: Path): Path = path.toAbsolutePath().normalize()
}

public class IncludeNotFoundException(
  include: String,
  searchPaths: List<String>,
) : RuntimeException(buildString {
  appendLine("Unable to resolve include '$include'.")
  appendLine("Searched paths:")
  searchPaths.forEach { appendLine("  - $it") }
})

public class IncludeCycleException(message: String) : RuntimeException(message)
