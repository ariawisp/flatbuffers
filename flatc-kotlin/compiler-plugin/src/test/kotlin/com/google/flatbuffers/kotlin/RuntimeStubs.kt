package com.google.flatbuffers.kotlin

/**
 * Minimal runtime stubs used solely for compiler plugin tests. These mirror the signatures that
 * the plugin references when generating IR so the Kotlin compiler can resolve symbols without
 * depending on the real runtime artifacts.
 */
public open class ReadWriteBuffer(public val capacity: Int = 0) {
  public open val limit: Int get() = capacity

  public operator fun get(index: Int): Byte = 0

  public fun getBoolean(index: Int): Boolean = false

  public fun getByte(index: Int): Byte = 0

  public fun getShort(index: Int): Short = 0

  public fun getInt(index: Int): Int = 0

  public fun getLong(index: Int): Long = 0L

  public fun getFloat(index: Int): Float = 0f

  public fun getDouble(index: Int): Double = 0.0

  public fun getString(offset: Int, length: Int): String = ""

  public fun slice(start: Int, length: Int): ReadBuffer = ReadBuffer()

  public fun clear() {}

  public fun moveWrittenDataToEnd(minCapacity: Int): Int = capacity.coerceAtLeast(minCapacity)

  public operator fun set(index: Int, value: Short) {}

  public operator fun set(index: Int, value: Int) {}
}

public typealias ReadBuffer = ReadWriteBuffer

public val emptyBuffer: ReadWriteBuffer = ReadWriteBuffer()

public class ArrayReadWriteBuffer(capacity: Int) : ReadWriteBuffer(capacity)

public open class Table {
  public var bufferPos: Int = 0
  public var bb: ReadWriteBuffer = emptyBuffer
  public var vtableStart: Int = 0
  public var vtableSize: Int = 0

  public fun offset(vtableOffset: Int): Int = 0

  public fun vector(offset: Int): Int = offset

  public fun vectorLength(offset: Int): Int = 0

  public fun vectorAsBuffer(buffer: ReadWriteBuffer, vectorOffset: Int, elemSize: Int): ReadBuffer =
    buffer

  public fun union(t: Table, offset: Int): Table = t

  public inline fun <reified T> lookupField(
    index: Int,
    default: T,
    crossinline found: (Int) -> T,
  ): T = if (index != 0) found(index) else default

  public inline fun <reified T : Table> reset(): T = reset(0, emptyBuffer)

  public inline fun <reified T : Table> reset(pos: Int, reuseBuffer: ReadWriteBuffer): T {
    bb = reuseBuffer
    bufferPos = pos
    return this as T
  }

  public companion object {
    public fun offset(vtableOffset: Int, offset: Offset<*>, bb: ReadWriteBuffer): Int = vtableOffset

    public fun indirect(offset: Int, bb: ReadWriteBuffer): Int = offset

    public fun string(offset: Int, bb: ReadWriteBuffer): String = ""

    public fun union(t: Table, offset: Int, bb: ReadWriteBuffer): Table = t

    public fun hasIdentifier(bb: ReadWriteBuffer?, ident: String): Boolean = false
  }
}

public class Offset<T>(public val value: Int)

public class FlatBufferBuilder {
  public fun startTable(requiredFields: Int) {}

  public fun add(index: Int, value: Boolean, default: Boolean) {}

  public fun add(index: Int, value: Byte, default: Byte) {}

  public fun add(index: Int, value: Short, default: Short) {}

  public fun add(index: Int, value: Int, default: Int) {}

  public fun add(index: Int, value: Long, default: Long) {}

  public fun add(index: Int, value: Float, default: Float) {}

  public fun add(index: Int, value: Double, default: Double) {}
}
