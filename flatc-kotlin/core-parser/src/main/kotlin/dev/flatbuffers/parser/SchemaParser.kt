package dev.flatbuffers.parser

import dev.flatbuffers.ast.SchemaFile

public object SchemaParser {
  public fun parse(fileName: String, source: String): SchemaFile {
    val tokens = Scanner.scan(fileName, source)
    return Parser(fileName, tokens).parse()
  }
}
