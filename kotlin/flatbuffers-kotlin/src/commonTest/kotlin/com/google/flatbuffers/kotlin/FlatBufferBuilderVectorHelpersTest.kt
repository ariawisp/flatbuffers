package com.google.flatbuffers.kotlin

import kotlin.test.Test
import kotlin.test.assertContentEquals

class FlatBufferBuilderVectorHelpersTest {

  @Test
  fun booleanVectorMatchesManualImplementation() {
    val values = booleanArrayOf(true, false, true, false)
    assertVectorMatches(
      helper = { createBooleanVector(values) },
      manual = {
        startVector(1, values.size, 1)
        for (i in values.size - 1 downTo 0) {
          add(values[i])
        }
        endVector<Boolean>()
      },
    )
  }

  @Test
  fun unsignedIntVectorMatchesManualImplementation() {
    val values = uintArrayOf(1u, 2u, 3u, 4u)
    assertVectorMatches(
      helper = { createUIntVector(values) },
      manual = {
        startVector(Int.SIZE_BYTES, values.size, Int.SIZE_BYTES)
        for (i in values.size - 1 downTo 0) {
          add(values[i])
        }
        endVector<UInt>()
      },
    )
  }

  @Test
  fun doubleVectorMatchesManualImplementation() {
    val values = doubleArrayOf(1.0, 2.5, -3.0)
    assertVectorMatches(
      helper = { createDoubleVector(values) },
      manual = {
        startVector(Double.SIZE_BYTES, values.size, Double.SIZE_BYTES)
        for (i in values.size - 1 downTo 0) {
          add(values[i])
        }
        endVector<Double>()
      },
    )
  }

  @Test
  fun offsetVectorMatchesManualImplementation() {
    val strings = arrayOf("alpha", "beta", "gamma")
    assertVectorMatches(
      helper = {
        val offsets = OffsetArray(strings.size) { createString(strings[it]) }
        createOffsetVector(offsets)
      },
      manual = {
        val offsets = Array(strings.size) { createString(strings[it]) }
        startVector(Int.SIZE_BYTES, offsets.size, Int.SIZE_BYTES)
        for (i in offsets.indices.reversed()) {
          add(offsets[i])
        }
        endVector<String>()
      },
    )
  }

  private fun <T> assertVectorMatches(
    helper: FlatBufferBuilder.() -> VectorOffset<T>,
    manual: FlatBufferBuilder.() -> VectorOffset<T>,
  ) {
    val helperBytes = buildVector(helper)
    val manualBytes = buildVector(manual)
    assertContentEquals(manualBytes, helperBytes)
  }

  private fun <T> buildVector(block: FlatBufferBuilder.() -> VectorOffset<T>): ByteArray {
    val builder = FlatBufferBuilder()
    val vector = builder.block()
    builder.finish(Offset<Unit>(vector.value))
    return builder.sizedByteArray()
  }
}
