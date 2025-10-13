package dev.flatbuffers.flatc.kotlin.compiler.schema

import dev.flatbuffers.ast.SourceSpan
import dev.flatbuffers.semantics.DiagnosticSeverity
import dev.flatbuffers.semantics.ResolvedDeclaration
import dev.flatbuffers.semantics.ResolvedSchema
import dev.flatbuffers.semantics.SchemaProcessor
import dev.flatbuffers.flatc.kotlin.compiler.options.FlatbuffersPluginOptions
import java.nio.file.Path
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector

/**
 * Holds the resolved schema data for the current compilation.
 */
internal class SchemaIndex private constructor(
  private val loaded: List<LoadedSchema>,
  private val declarationMap: Map<String, ResolvedDeclaration>,
) {
  val schemas: List<ResolvedSchema> get() = loaded.map { it.analysis.schema }

  fun declarationFor(name: String): ResolvedDeclaration? = declarationMap[name]

  data class LoadedSchema(val entry: Path, val analysis: dev.flatbuffers.semantics.SemanticAnalysisResult)

  companion object {
    fun load(
      options: FlatbuffersPluginOptions,
      messageCollector: MessageCollector,
    ): SchemaIndex? {
      if (options.schemaPaths.isEmpty()) {
        messageCollector.report(
          CompilerMessageSeverity.WARNING,
          "FlatBuffers compiler plugin enabled but no --schema option was provided; skipping generation",
        )
        return null
      }

      val loaded = mutableListOf<LoadedSchema>()
      val declarations = linkedMapOf<String, ResolvedDeclaration>()
      var hasErrors = false

      for (entry in options.schemaPaths) {
        val result = SchemaProcessor.loadAndAnalyze(entry, options.includePaths)
        result.diagnostics.forEach { diagnostic ->
          val severity = when (diagnostic.severity) {
            DiagnosticSeverity.ERROR -> CompilerMessageSeverity.ERROR
            DiagnosticSeverity.WARNING -> CompilerMessageSeverity.WARNING
            DiagnosticSeverity.INFO -> CompilerMessageSeverity.INFO
          }
          val location = diagnostic.span?.let { span ->
            SchemaMessageLocation(span)
          }
          messageCollector.report(severity, diagnostic.message, location)
          if (diagnostic.severity == DiagnosticSeverity.ERROR) {
            hasErrors = true
          }
        }
        if (hasErrors) continue
        loaded += LoadedSchema(entry, result)
        for ((name, declaration) in result.schema.declarations) {
          declarations.putIfAbsent(name, declaration)
        }
      }

      if (hasErrors || loaded.isEmpty()) {
        if (!hasErrors) {
          messageCollector.report(
            CompilerMessageSeverity.WARNING,
            "FlatBuffers compiler plugin did not load any schemas.",
          )
        }
        return null
      }

      return SchemaIndex(loaded, declarations)
    }
  }
}

private class SchemaMessageLocation(private val span: SourceSpan) : CompilerMessageSourceLocation {
  override val path: String
    get() = span.file

  override val line: Int
    get() = span.start.line

  override val column: Int
    get() = span.start.column

  override val lineContent: String?
    get() = null
}
