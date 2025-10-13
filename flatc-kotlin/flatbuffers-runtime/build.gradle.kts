import java.io.File
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  id("org.jetbrains.kotlin.jvm")
}

java {
  withSourcesJar()
}

val runtimeSourcesDir = layout.buildDirectory.dir("generated/flatbuffers-runtime")

val prepareRuntimeSources by tasks.registering(Sync::class) {
  into(runtimeSourcesDir)
  val commonDir = rootDir.resolve("../kotlin/flatbuffers-kotlin/src/commonMain/kotlin")
  val jvmDir = rootDir.resolve("../kotlin/flatbuffers-kotlin/src/jvmMain/kotlin")

  val byteArrayReplacements = mapOf(
    "internal expect inline fun ByteArray.getUByte(index: Int): UByte" to
      "internal inline fun ByteArray.getUByte(index: Int): UByte = ByteArrayOps.getUByte(this, index)",
    "internal expect inline fun ByteArray.getShort(index: Int): Short" to
      "internal inline fun ByteArray.getShort(index: Int): Short = ByteArrayOps.getShort(this, index)",
    "internal expect inline fun ByteArray.getUShort(index: Int): UShort" to
      "internal inline fun ByteArray.getUShort(index: Int): UShort = ByteArrayOps.getUShort(this, index)",
    "internal expect inline fun ByteArray.getInt(index: Int): Int" to
      "internal inline fun ByteArray.getInt(index: Int): Int = ByteArrayOps.getInt(this, index)",
    "internal expect inline fun ByteArray.getUInt(index: Int): UInt" to
      "internal inline fun ByteArray.getUInt(index: Int): UInt = ByteArrayOps.getUInt(this, index)",
    "internal expect inline fun ByteArray.getLong(index: Int): Long" to
      "internal inline fun ByteArray.getLong(index: Int): Long = ByteArrayOps.getLong(this, index)",
    "internal expect inline fun ByteArray.getULong(index: Int): ULong" to
      "internal inline fun ByteArray.getULong(index: Int): ULong = ByteArrayOps.getULong(this, index)",
    "internal expect inline fun ByteArray.getFloat(index: Int): Float" to
      "internal inline fun ByteArray.getFloat(index: Int): Float = ByteArrayOps.getFloat(this, index)",
    "internal expect inline fun ByteArray.getDouble(index: Int): Double" to
      "internal inline fun ByteArray.getDouble(index: Int): Double = ByteArrayOps.getDouble(this, index)",
    "internal expect inline fun ByteArray.setUByte(index: Int, value: UByte)" to
      "internal inline fun ByteArray.setUByte(index: Int, value: UByte) = ByteArrayOps.setUByte(this, index, value)",
    "internal expect inline fun ByteArray.setShort(index: Int, value: Short)" to
      "internal inline fun ByteArray.setShort(index: Int, value: Short) = ByteArrayOps.setShort(this, index, value)",
    "internal expect inline fun ByteArray.setUShort(index: Int, value: UShort)" to
      "internal inline fun ByteArray.setUShort(index: Int, value: UShort) = ByteArrayOps.setUShort(this, index, value)",
    "internal expect inline fun ByteArray.setInt(index: Int, value: Int)" to
      "internal inline fun ByteArray.setInt(index: Int, value: Int) = ByteArrayOps.setInt(this, index, value)",
    "internal expect inline fun ByteArray.setUInt(index: Int, value: UInt)" to
      "internal inline fun ByteArray.setUInt(index: Int, value: UInt) = ByteArrayOps.setUInt(this, index, value)",
    "internal expect inline fun ByteArray.setLong(index: Int, value: Long)" to
      "internal inline fun ByteArray.setLong(index: Int, value: Long) = ByteArrayOps.setLong(this, index, value)",
    "internal expect inline fun ByteArray.setULong(index: Int, value: ULong)" to
      "internal inline fun ByteArray.setULong(index: Int, value: ULong) = ByteArrayOps.setULong(this, index, value)",
    "internal expect inline fun ByteArray.setFloat(index: Int, value: Float)" to
      "internal inline fun ByteArray.setFloat(index: Int, value: Float) = ByteArrayOps.setFloat(this, index, value)",
    "internal expect inline fun ByteArray.setDouble(index: Int, value: Double)" to
      "internal inline fun ByteArray.setDouble(index: Int, value: Double) = ByteArrayOps.setDouble(this, index, value)"
  )

  from(commonDir) {
    filter { line: String ->
      var result = line
      byteArrayReplacements.forEach { (pattern, replacement) ->
        result = result.replace(pattern, replacement)
      }
      result
    }
  }
  from(jvmDir) {
    exclude("com/google/flatbuffers/kotlin/ByteArray.kt")
    filter { line: String ->
      line.replace("actual ", "")
    }
  }
}

sourceSets.named("main") {
  kotlin.srcDir(runtimeSourcesDir)
  resources.setSrcDirs(emptyList<File>())
}

tasks.named("compileKotlin").configure {
  dependsOn(prepareRuntimeSources)
}

tasks.named("sourcesJar").configure {
  dependsOn(prepareRuntimeSources)
}

tasks.withType<KotlinCompile>().configureEach {
  compilerOptions {
    jvmTarget.set(JvmTarget.JVM_21)
    freeCompilerArgs.add("-Xmulti-platform")
  }
}
