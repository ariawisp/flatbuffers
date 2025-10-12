package dev.flatbuffers.parser

import dev.flatbuffers.ast.SourceLocation

public class LexingException(
  public val location: SourceLocation,
  message: String,
) : RuntimeException("${location.file}:${location.line}:${location.column}: $message")
