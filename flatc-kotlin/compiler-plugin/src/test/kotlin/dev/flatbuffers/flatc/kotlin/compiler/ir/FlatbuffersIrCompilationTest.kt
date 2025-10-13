
package dev.flatbuffers.flatc.kotlin.compiler.ir

import com.tschuchort.compiletesting.KotlinCompilation
import com.tschuchort.compiletesting.KotlinCompilation.ExitCode
import com.tschuchort.compiletesting.SourceFile
import dev.flatbuffers.flatc.kotlin.compiler.FLATBUFFERS_PLUGIN_ID
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.assertEquals
import org.junit.jupiter.api.Test

class FlatbuffersIrCompilationTest {
  @Test
  fun `init reset and scalar getter execute`() {
    val schemaDir = Files.createTempDirectory("flatbuffers-schema")
    val schema = schemaDir.resolve("sample.fbs")
    schema.writeText(
      """
      namespace sample;

      /// table docs
      table Sample {
        /// hp docs
        hp:short = 123;
      }

      root_type Sample;
      """
        .trimIndent()
    )

    val kotlinSource = SourceFile.kotlin(
      name = "Usage.kt",
      contents =
        """
        package sample

        import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer

        fun checkDefault(): Int {
          val buffer = ArrayReadWriteBuffer(32)
          val table = Sample()
          val initResult = table.init(0, buffer)
          require(initResult === table)
          val resetResult = table.reset(0, buffer)
          require(resetResult === table)
          return table.hp.toInt()
        }
        """
          .trimIndent()
    )

    val pluginJar = java.io.File("build/libs/compiler-plugin.jar").also {
      check(it.exists()) { "Plugin jar not found: $it" }
    }

    val compilation =
      KotlinCompilation().apply {
        kotlinCompilerVersion = "2.2.20"
        inheritClassPath = true
        sources = listOf(kotlinSource)
        pluginClasspaths = listOf(pluginJar)
        kotlincArguments =
          listOf(
            "-Xuse-k2",
            "-P", "plugin:$FLATBUFFERS_PLUGIN_ID:enabled=true",
            "-P", "plugin:$FLATBUFFERS_PLUGIN_ID:schema=${schema}",
          )
        messageOutputStream = System.out
      }

    val result = compilation.compile()
    assertEquals(ExitCode.OK, result.exitCode, result.messages)

    val checkClass = result.classLoader.loadClass("sample.CheckKt")
    val method = checkClass.getDeclaredMethod("checkDefault")
    val value = method.invoke(null) as Int
    assertEquals(123, value)
  }
}
