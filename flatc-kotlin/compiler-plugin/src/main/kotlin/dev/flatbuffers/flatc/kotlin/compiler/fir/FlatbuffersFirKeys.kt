package dev.flatbuffers.flatc.kotlin.compiler.fir

import org.jetbrains.kotlin.GeneratedDeclarationKey

internal object FlatbuffersFirKeys {
  data object TableClass : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersTableClass"
  }

  data object StructClass : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersStructClass"
  }

  data object EnumClass : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersEnumClass"
  }
}
