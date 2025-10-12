# IR Lowering Plan

IR generation populates executable bodies for all FIR stubs emitted by the FlatBuffers plugin. The
goal is to preserve the API surface produced by legacy source generation while delegating the heavy
lifting to the runtime in `com.google.flatbuffers.kotlin`.

## Guiding Principles

- **No textual Kotlin output.** All codegen occurs via compiler IR transforms.
- **Runtime-first design.** Every synthesized body funnels through runtime helpers or inline
  intrinsics (e.g., `lookupField`). If a pattern cannot be expressed succinctly, we add a helper to
  the runtime (see new vector helpers from phase 8.1).
- **Schema-driven.** The lowering consumes `ResolvedSchema` so it knows field offsets, defaults,
  keys, and doc comments.
- **Consistent sources.** The IR plugin reuses the same `CompatContext` helpers to attach
  `KtSourceElement`s derived from schema spans.

## Extension Architecture

1. `FlatbuffersIrGenerationExtension` (registered alongside FIR registrar)
   - Instantiated with `SchemaIndex`, options, `CompatContext`, `MessageCollector`.
   - Creates an `IrFlatbuffersContext` (mirrors Metro's `IrMetroContext`) with symbol lookups,
     schema index, runtime references (`com.google.flatbuffers.kotlin.Table` etc.).
2. Transformers:
   - `SchemaIrPopulator` walks module fragments, detecting synthetic declarations we own via
     `GeneratedDeclarationKey`.
   - For each declaration kind (table class, struct class, enum companion, etc.) delegate to
     specialized populator.
3. Helpers to translate `Resolved...` models into IR (factory functions to construct constants,
   loops, etc.).

## Body Recipes

### Table Instance API

- **`init` function**
  - `this.bb = buffer`
  - `this.bufferPos = i`
  - `this.vtableStart = bufferPos - bb.getInt(bufferPos)`
  - `this.vtableSize = bb.getShort(vtableStart).toInt()`
  - Return `this`.
- **Scalar property getter** (`val hp: Short`)
  - IR constructs lambda for `lookupField`.
  - Emit:
    ```
    return lookupField(
      /*vtableOffset*/ 8,
      /*default*/ 100,
    ) { idx ->
      bb.getShort(idx + bufferPos)
    }
    ```
  - Leverage inline function in runtime (IR builds `IrFunctionExpression`).
- **Optional table/struct field** (`fun enemy(obj: Monster)` / property `enemy`)
  - Use `lookupField(offset, null) { obj.init(indirect(idx + bufferPos), bb) }`.
  - For property variant the temporary object is allocated (`Monster()` call) via FIR stub; IR just
    uses existing parameter.
- **Vector element accessor** (`fun inventory(j: Int)` / `val inventoryLength`)
  - Element: `lookupField(offset, default) { bb.get...(vector(it) + j * elemSize) }`
  - Length: `lookupField(offset, 0) { vectorLength(it) }`
  - `AsBuffer`: `lookupField(offset, emptyBuffer) { vectorAsBuffer(bb, offsetConst, elemSize) }`
- **Union accessors** (`fun anyUnique(obj: Table)`):
  - `lookupField(vtableOffset, null) { union(obj, it + bufferPos) }`
- **Nested flatbuffer convenience**:
  - Compose `indirect` and `vector(...)` using runtime methods.
- **`keysCompare` override**:
  - Compose call to `Table.offset` & `Table.compareStrings` using runtime functions; fetch key offset
    constant from schema metadata.

### Table Companion API

- `validateVersion` → return `VERSION_2_0_8`.
- `asRoot(buffer, obj)`:
  - Equivalent to `obj.init(buffer.getInt(buffer.limit) + buffer.limit, buffer)`.
- `start<Name>` → `builder.startTable(fieldCount)`.
- `add<Field>`:
  - Scalars: `builder.add(slotIndex, value, default)`.
  - Structs: `builder.addStruct(slotIndex, offset.value, default)`.
  - Unions: `builder.add(slotIndex, unionOffset, 0)`.
  - Strings: inlines `builder.add(name)` + `builder.slot(slotIndex)` (current generator pattern).
- `create<Field>Vector`:
  - Use new runtime helpers:
    - Scalars → `builder.createIntVector(array)` etc.
    - Offsets → `builder.createOffsetVector(offsetArray)`.
    - Strings → `builder.createOffsetVector`.
    - Bytes → existing `createByteVector`.
- `start<Field>Vector` → direct call to `builder.startVector(elemSize, count, alignment)`.
- `end<Name>` → `builder.endTable()` with required field checks afterwards.
- `finish<Name>Buffer` / size-prefixed variants → `builder.finish`.
- `lookupByKey`:
  - Emit binary-search loop identical to legacy code:
    - Compute `span = bb.getInt(vectorLocation - 4)`.
    - Loop maintaining `start`, `span`, `middle`.
    - Convert key argument:
      - `String` key → `key.encodeToByteArray()`.
      - `UShort`/`ULong` keys → direct `bb.get...` comparisons.
    - Use runtime `indirect`, `offset`, `compareStrings`.

### Structs

- `init` / getters identical to legacy generator (direct buffer reads).
- Companion `create<Name>`:
  - Use builder operations (`prep`, `pad`, `put`).
  - Keep original reverse-write order.

### Enums

- Value class has no body.
- Companion constants: IR assigns `@JvmField val Foo = <Name>(value)`.
- `names` array built as `arrayOf("...")`.
- `name()` returns `names[e.value.toInt()]`.

### Unions

- Value class for type discriminant identical to enums.
- Additional helpers (`typealias`, `UnionOffsetArray`) are present in FIR; no IR work needed
  beyond `names` array.

### Services

- FIR exposes abstract functions.
- IR may inject default implementations that throw `NotImplementedError` until RPC runtime exists,
  or leave abstract (TBD based on later design).

### Required Field Checks

- Insert `builder.required(o, fieldIndex, "fieldName")` immediately after `endTable` for each field
  flagged `required`.
- Field index derived from schema (vtable offset / generated code uses immediate integer).

## Runtime Touchpoints

The lowering assumes the following runtime APIs (already present or added in phase 8.1):

- `FlatBufferBuilder.createBooleanVector` / `createUIntVector` / … / `createOffsetVector`.
- `Table.lookupField`, `vector`, `vectorLength`, `vectorAsBuffer`, `union`.
- `Table.offset`, `Table.compareStrings`, `Table.indirect`.
- `FlatBufferBuilder.startVector`, `endVector`, `add`, `addStruct`, `required`, `finish`.
- `FlatBufferBuilder.createString`, `createByteVector`.
- `OffsetArray`, `StringOffsetArray`, etc.

If further helpers are required during implementation we will extend the runtime before wiring IR.

## Implementation Steps

1. Build symbol table (`IrSymbols`) resolving runtime classes, methods, constants.
2. Implement `IrFlatbuffersContext` + utilities:
   - Create wrappers for constants (`irInt(field.vtableOffset)` etc.).
   - Helpers to build `lookupField` call with lambda.
   - Utilities for buffer math (add offsets, multiply indices).
3. Transformer per declaration type populates bodies:
   - Locates target `IrFunction`/`IrProperty`.
   - Empties stub body.
   - Constructs new body via `IrBuilderWithScope`.
4. Add unit tests comparing emitted IR to invocation of legacy generated code (Phase 8.4).

With this plan the IR plugin can faithfully recreate the behaviour of the current source generator.
