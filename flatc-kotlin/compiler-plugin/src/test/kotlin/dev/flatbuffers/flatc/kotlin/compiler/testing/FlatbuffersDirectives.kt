package dev.flatbuffers.flatc.kotlin.compiler.testing

import org.jetbrains.kotlin.test.directives.model.SimpleDirectivesContainer

object FlatbuffersDirectives : SimpleDirectivesContainer() {
  val SCHEMA by stringDirective("Relative path (from src/test/data) to a schema file to load.")
  val INCLUDE by stringDirective("Relative include directory for resolving schema imports.")
  val BFBS by stringDirective("Relative path (from src/test/data) to a precompiled .bfbs schema.")
}
