package dev.flatbuffers.flatc.kotlin.compiler.testing

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

import org.jetbrains.kotlin.test.directives.model.DirectiveApplicability

object FlatbuffersDirectives : SimpleDirectivesContainer() {
  val FLATBUFFERS_SCHEMA by stringDirective(
    description = "Relative path (from src/test/data) to a schema file to load.",
    applicability = DirectiveApplicability.Any,
  )
  val FLATBUFFERS_INCLUDE by stringDirective(
    description = "Relative include directory for resolving schema imports.",
    applicability = DirectiveApplicability.Any,
  )
  val FLATBUFFERS_BFBS by stringDirective(
    description = "Relative path (from src/test/data) to a precompiled .bfbs schema.",
    applicability = DirectiveApplicability.Any,
  )
}
