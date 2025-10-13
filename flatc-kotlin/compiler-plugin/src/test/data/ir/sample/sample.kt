// MODULE: main
// FLATBUFFERS_SCHEMA: ir/sample/sample.fbs
// IGNORE_FIR_DIAGNOSTICS
// FILE: sample.kt

package sample

import com.google.flatbuffers.kotlin.ArrayReadWriteBuffer
import com.google.flatbuffers.kotlin.FlatBufferBuilder

fun useSample(buffer: ArrayReadWriteBuffer): Short {
  val table = Sample()
  table.init(0, buffer)
  table.reset(0, buffer)
  return table.hp
}

fun addHp(builder: FlatBufferBuilder, hp: Short) {
  Sample.addHp(builder, hp)
}
