package dev.flatbuffers.flatc.kotlin.compiler.fir

import org.jetbrains.kotlin.fir.declarations.FirDeclaration
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataKey
import org.jetbrains.kotlin.fir.declarations.FirDeclarationDataRegistry

private object SchemaDocDataKey : FirDeclarationDataKey()

internal var FirDeclaration.flatbuffersSchemaDoc: String? by FirDeclarationDataRegistry.data(SchemaDocDataKey)

internal fun FirDeclaration.attachSchemaMetadata(
  doc: String?,
) {
  if (doc != null) {
    flatbuffersSchemaDoc = doc
  }
}
