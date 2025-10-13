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

  data object TableMemberFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersTableMemberFunction"
  }

  data object TableProperty : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersTableProperty"
  }

  data object TableCompanionFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersTableCompanionFunction"
  }

  data object TableCompanionObject : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersTableCompanionObject"
  }

  data object StructMemberFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersStructMemberFunction"
  }

  data object StructCompanionFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersStructCompanionFunction"
  }

  data object StructCompanionObject : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersStructCompanionObject"
  }

  data object EnumCompanionFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersEnumCompanionFunction"
  }

  data object EnumCompanionObject : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersEnumCompanionObject"
  }

  data object OffsetArrayTypeAlias : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersOffsetArrayTypeAlias"
  }

  data object OffsetArrayConstructorFunction : GeneratedDeclarationKey() {
    override fun toString(): String = "FlatbuffersOffsetArrayConstructorFunction"
  }
}
