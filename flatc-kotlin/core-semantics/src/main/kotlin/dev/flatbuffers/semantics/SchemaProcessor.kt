package dev.flatbuffers.semantics

import dev.flatbuffers.parser.include.SchemaLoader
import java.nio.file.Path

public object SchemaProcessor {
  public fun loadAndAnalyze(entry: Path, includePaths: List<Path> = emptyList()): SemanticAnalysisResult {
    val loader = SchemaLoader(includePaths)
    val files = loader.load(entry)
    return SemanticAnalyzer().analyze(files)
  }
}
