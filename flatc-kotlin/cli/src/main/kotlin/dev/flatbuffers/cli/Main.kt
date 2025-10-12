package dev.flatbuffers.cli

import dev.flatbuffers.semantics.DiagnosticSeverity
import dev.flatbuffers.semantics.SchemaProcessor
import java.nio.file.Path
import kotlin.io.path.Path
import kotlin.system.exitProcess

fun main(args: Array<String>) {
  if (args.isEmpty()) {
    println("Usage: flatc-kotlin <schema.fbs> [-I includePath ...]")
    return
  }

  var entryPath: Path? = null
  val includes = mutableListOf<Path>()
  var index = 0
  while (index < args.size) {
    val arg = args[index]
    when {
      arg == "-I" -> {
        val path = args.getOrNull(index + 1)
        if (path == null) {
          println("Missing path after -I")
          return
        }
        includes.add(Path(path))
        index += 2
      }
      arg.startsWith("-I") -> {
        includes.add(Path(arg.removePrefix("-I")))
        index += 1
      }
      arg.contains('=') && arg.startsWith("--include=") -> {
        includes.add(Path(arg.substringAfter('=')))
        index += 1
      }
      arg.startsWith("-") -> {
        println("Unknown option: $arg")
        return
      }
      else -> {
        entryPath = Path(arg)
        index += 1
      }
    }
  }

  if (entryPath == null) {
    println("Missing schema path")
    return
  }

  val result = SchemaProcessor.loadAndAnalyze(entryPath, includes)
  if (result.diagnostics.isEmpty()) {
    println("Analysis succeeded with no diagnostics.")
  } else {
    result.diagnostics.forEach { diagnostic ->
      val location = diagnostic.span?.let { span -> "${span.start.file}:${span.start.line}" } ?: "<unknown>"
      println("${diagnostic.severity}: $location: ${diagnostic.message}")
    }
  }
  val hasErrors = result.diagnostics.any { it.severity == DiagnosticSeverity.ERROR }
  if (hasErrors) {
    exitProcess(1)
  }
}
