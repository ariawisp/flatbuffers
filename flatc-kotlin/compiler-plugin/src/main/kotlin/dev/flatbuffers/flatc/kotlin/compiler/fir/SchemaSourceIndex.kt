package dev.flatbuffers.flatc.kotlin.compiler.fir

import dev.flatbuffers.ast.DocComment
import dev.flatbuffers.ast.SourceLocation
import dev.flatbuffers.ast.SourceSpan
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.concurrent.ConcurrentHashMap
import org.jetbrains.kotlin.KtLightSourceElement
import org.jetbrains.kotlin.KtRealSourceElementKind
import org.jetbrains.kotlin.KtSourceElement
import org.jetbrains.kotlin.KtSourceElementKind
import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.tree.IElementType
import com.intellij.util.diff.FlyweightCapableTreeStructure

/**
 * Provides access to the original schema text so we can rehydrate [KtSourceElement] instances
 * for FIR declarations synthesized by the plugin.
 *
 * The implementation keeps a lightweight cache keyed by the schema file path to avoid repeatedly
 * reading the same file when multiple declarations originate from it.
 */
internal class SchemaSourceIndex {
  private val cache = ConcurrentHashMap<String, SchemaFileSnapshot>()

  /**
     * Returns a [KtSourceElement] describing the given [span], or `null` when the span is absent
     * or the backing file could not be resolved.
     */
  fun element(
    span: SourceSpan?,
    kind: KtSourceElementKind = KtRealSourceElementKind,
  ): KtSourceElement? {
    if (span == null) return null
    val snapshot = snapshot(span.file) ?: return null
    val startOffset = snapshot.offset(span.start) ?: return null
    val endOffset = snapshot.offset(span.end) ?: return null
    if (startOffset > endOffset) return null
    return snapshot.sourceElement(startOffset, endOffset, kind)
  }

  /**
     * Collapses the schema [DocComment] lines into a single string. The caller decides whether to
     * preserve an empty comment (represented as an empty string) or treat it as absent.
     */
  fun doc(docComment: DocComment?): String? =
    docComment?.lines?.joinToString(separator = "\n")

  private fun snapshot(file: String): SchemaFileSnapshot? {
    cache[file]?.let { return it }
    val snapshot = loadSnapshot(file) ?: return null
    val existing = cache.putIfAbsent(file, snapshot)
    return existing ?: snapshot
  }

  private fun loadSnapshot(file: String): SchemaFileSnapshot? {
    return try {
      val path = Paths.get(file)
      val text = Files.readString(path)
      SchemaFileSnapshot(path, text, computeLineStartOffsets(text))
    } catch (_: IOException) {
      null
    } catch (_: SecurityException) {
      null
    } catch (_: RuntimeException) {
      null
    }
  }

  private data class SchemaFileSnapshot(
    val path: Path,
    val text: String,
    val lineStartOffsets: IntArray,
  ) {
    fun offset(location: SourceLocation): Int? {
      val lineIndex = location.line - 1
      if (lineIndex !in lineStartOffsets.indices) return null
      val columnIndex = location.column - 1
      if (columnIndex < 0) return null
      val base = lineStartOffsets[lineIndex]
      val candidate = base + columnIndex
      return candidate.coerceIn(0, text.length)
    }

    fun sourceElement(
      startOffset: Int,
      endOffset: Int,
      kind: KtSourceElementKind,
    ): KtSourceElement {
      val node = SchemaLighterAstNode(startOffset, endOffset)
      val tree = SchemaLightTreeStructure(node, text)
      return KtLightSourceElement(
        lighterASTNode = node,
        startOffset = startOffset,
        endOffset = endOffset,
        treeStructure = tree,
        kind = kind,
      )
    }
  }

  private class SchemaLighterAstNode(
    private val startOffset: Int,
    private val endOffset: Int,
  ) : LighterASTNode {
    override fun getTokenType(): IElementType? = null

    override fun getStartOffset(): Int = startOffset

    override fun getEndOffset(): Int = endOffset
  }

  private class SchemaLightTreeStructure(
    private val root: LighterASTNode,
    private val text: CharSequence,
  ) : FlyweightCapableTreeStructure<LighterASTNode> {
    override fun getRoot(): LighterASTNode = root

    override fun getParent(node: LighterASTNode): LighterASTNode? = null

    override fun getChildren(parent: LighterASTNode, into: Ref<Array<LighterASTNode>>): Int {
      into.set(LighterASTNode.EMPTY_ARRAY)
      return 0
    }

    override fun disposeChildren(nodes: Array<LighterASTNode>, count: Int) = Unit

    override fun toString(node: LighterASTNode): CharSequence =
      text.subSequence(node.startOffset, node.endOffset.coerceAtMost(text.length))

    override fun getStartOffset(node: LighterASTNode): Int = node.startOffset

    override fun getEndOffset(node: LighterASTNode): Int = node.endOffset
  }

  companion object {
    private fun computeLineStartOffsets(text: String): IntArray {
      if (text.isEmpty()) return intArrayOf(0)
      val offsets = ArrayList<Int>()
      offsets.add(0)
      text.forEachIndexed { index, ch ->
        if (ch == '\n') {
          offsets.add(index + 1)
        }
      }
      return offsets.toIntArray()
    }
  }
}
