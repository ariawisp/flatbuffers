# FIR Stub Generation Plan

This document sketches the FIR surface that the FlatBuffers compiler plugin must synthesize from a
`ResolvedSchema`. It is the contract that the IR lowerings and the runtime helpers will build upon.

## Session Inputs

- `ResolvedSchema` produced by `SchemaProcessor`.
  - Collected before FIR extensions run (held in the upcoming `SchemaIndex` runtime cache).
  - Includes doc comments, `SourceSpan`s, defaults, attributes, and resolved references.
- `FlatbufferPluginOptions`
  - Root `.fbs` entry point(s).
  - Search paths (`-I`), optionally precompiled `.bfbs`.
  - Flags (emit reflection schema, generate builders only, etc.) that can influence emitted surface.
- `CompatContext`
  - Compatibility facade (K 2.2.20 vs K ≥ 2.3) shared with IR. Handles APIs that moved between
    releases (e.g., `createTopLevelFunction`, fake source element creation).

## Source Mapping

Goal: generated FIR nodes expose `.source` that points back to the `.fbs` definition so IDEs and
compiler diagnostics can navigate into schemas.

Implementation:

- `SchemaSourceIndex` is a session-scoped cache that lazily loads schema files, precomputes
  line-start offsets, and produces `KtLightSourceElement` instances from a `SourceSpan`. Multiple
  declarations from the same file share the cached text.
- Builder helpers (`withSchemaSource` + the `stubFunction`/`stubProperty` wrappers) thread spans and
  doc comments through every generated declaration. When a span is missing we intentionally keep the
  plugin-generated fake source so synthetic helpers remain obvious.
- Doc strings are flattened into a single `String` and stored on the FIR node via an extension
  property (`FirDeclaration.flatbuffersSchemaDoc`) registered with `FirDeclarationDataRegistry`.
  IR and tooling can later recover them even though FIR has no built-in doc slot.

If a declaration has neither span nor doc comment we leave its source untouched and the doc payload
`null`, matching previous behaviour.

## Generated Declarations

The FIR plugin emits Kotlin declarations that mirror the current codegen surface. All output lives in
the schema namespace (`namespace foo.bar;` → package `foo.bar`). For clarity we denote runtime types
from `com.google.flatbuffers.kotlin` in monospace.

### Tables

- `class <Name> : Table`
  - Primary constructor private, parameter-less (mirrors generated source).
  - `fun init(i: Int, buffer: ReadWriteBuffer): <Name>` delegates to `reset`.
  - `fun reset(i: Int, buffer: ReadWriteBuffer): <Name>` generated only once (IR will provide body).
  - Field members (for each schema `Field`):
    - **Scalar** (bool, integer, float): `val foo: <KotlinType> get() = lookupField(vt, default) { bb.getX(it + bufferPos) }`.
    - **String**: `val foo: String?`, plus `fun fooAsBuffer(): ReadBuffer`.
    - **Struct**: `val foo: Bar?` (allocates scratch instance), `fun foo(obj: Bar): Bar?`.
    - **Table**: same pattern but `obj.init(indirect(...))`.
    - **Union**: `val fooType: UnionType`, `fun foo(obj: Table): Table?`.
    - **Vector of scalars**: `fun foo(j: Int): T`, `val fooLength: Int`, `fun fooAsBuffer(): ReadBuffer`.
    - **Vector of structs**: overload with target object parameter.
    - **Vector of tables**: overloads + `fooByKey(...)` helper when key defined.
    - **Vector of strings**: `fun foo(j: Int): String?`, `fooLength`, `fooAsBuffer`.
    - **Vector of unions**: `fun fooType(j: Int): Enum`, `fun foo(obj: Table, j: Int): Table?`.
    - **Sorted vector**: `fooByKey` overloads for strings/structs/enums per legacy generator.
    - **Required fields**: property accessor identical; metadata recorded for IR `required`.
    - **Doc comments**: attach to property/overloads.
  - `override fun keysCompare(o1: Offset<*>, o2: Offset<*>, buffer: ReadWriteBuffer)` emitted when table has `key`.
  - Companion members (all MIR-style):
    - `fun validateVersion()` returns `VERSION_2_0_8`.
    - `fun asRoot(buffer: ReadWriteBuffer): <Name>` and overload with target instance.
    - `fun <Name>BufferHasIdentifier(buffer: ReadWriteBuffer): Boolean` if table is root.
    - `fun start<Name>(builder: FlatBufferBuilder)`.
    - `fun add<Field>(builder: FlatBufferBuilder, value: <Type>)` for every field.
    - `fun create<Field>Vector(builder, array)` + `fun start<Field>Vector(builder, numElems)` for vectors.
    - `fun add<Field>(builder, Offset<*>)`, `add<Field>(builder, VectorOffset<*>)`, `add<Field>(builder, UnionOffset)` as appropriate.
    - `fun end<Name>(builder): Offset<<Name>>` including `builder.required` calls for fields marked `required`.
    - `fun finish<Name>Buffer(builder, offset)` and size-prefixed variant when root type matches table.
    - `fun lookupByKey(obj: <Name>?, vectorLocation: Int, key: <KeyType>, bb: ReadWriteBuffer)` for sorted vectors.
  - File-level helpers: `typealias <Name>OffsetArray`, inline constructor function.
  - Source mapping: class uses table span; field accessors/companion functions map to their definition spans.

### Structs

- `class <Name> : Struct`
  - `fun init(i: Int, buffer: ReadWriteBuffer): <Name>` assigns `bufferPos` and returns `this`.
  - Accessors: `val foo: <Type> get() = bb.getX(bufferPos + offset)` for each scalar/enum field.
  - Nested structs: `fun foo(obj: Nested): Nested = obj.init(bufferPos + offset, bb)`.
  - Companion functions:
    - `fun create<Name>(builder: FlatBufferBuilder, field1: T, ...) : Offset<<Name>>` matching legacy ordering (reverse).
    - `fun create<Name>(builder, fields...)` handles struct alignment (`prep`, `pad`, `put`).
  - Emit `typealias <Name>OffsetArray` + constructor helper similar to tables.
  - Doc comments on struct and fields transfer to the generated accessors.

### Enums

- `@JvmInline value class <Name>(val value: <base Kotlin type>)`
  - `companion object`:
    - `val <CASE>` constant for each enumerator.
    - `val names: Array<String> = arrayOf("CASE_0", ...)`.
    - `fun name(e: <Name>): String = names[e.value.toInt()]`.
    - `fun valueOf(name: String): <Name>?` (optional convenience; consider parity).
  - Emit `typealias <Name>Array = <PrimitiveArray>` for vector helpers.
  - Attach doc comments for enum and individual constants when present.

### Unions

- `@JvmInline value class <Union>E` for type discriminant.
  - Cases as constants.
  - `fun name`.
- `typealias <Union>Union = UnionOffset`
  - Additional helpers (e.g., for vector of unions) as needed.
  - Optional `val values: UByteArray` if parity requires.

### RPC Services

- `interface <Service>`
  - Methods mirroring RPC signatures, returning `Offset`/`VectorOffset`.
  - When streaming flags set, expose coroutine-based variants.
  - For plugin V1 we may postpone bodies to IR stage; FIR stub provides signatures + docstrings.

### File Identifier / Extension

- For file-identifiers generate package-level functions in synthetic file:
  - `fun <Root>BufferHasIdentifier(buffer: ReadWriteBuffer): Boolean`
  - Provided by companion but also top-level alias if we decide to match legacy API.

### Diagnostics & Attributes

- FIR should attach doc comments via `FirDeclaration.replaceSource` using `DocComment.span`.
- Attributes propagate to FIR annotation stubs where meaningful (e.g., `deprecated`).
- Unknown attributes recorded for reflection writer but ignored in FIR.

## Generation Flow

1. `FlatbuffersCompilerPluginRegistrar` registers:
   - `FlatbuffersFirExtensionRegistrar(schemaIndex, options, compatContext)`
   - `FlatbuffersIrGenerationExtension(schemaIndex, options, compatContext)` *(phase 3)*.
2. `FlatbuffersFirExtensionRegistrar`
   - Installs a single `FirDeclarationGenerationExtension` that:
     - Announces top-level class IDs via `getTopLevelClassIds` for tables/structs/enums/services.
     - Generates companion members within `generateClassMembers`.
   - Keeps lightweight caches so FIR calls remain side-effect free.
3. `SchemaIndex`
   - Loaded during command-line processing.
   - Exposes lookups by fully-qualified name for FIR and IR stages.

## Outstanding Questions

- Do we emit builder helper functions eagerly, or reserve some for IR to avoid duplication?
  - Current plan: everything that is stateless and already a simple `FlatBufferBuilder` call stays
    in FIR (e.g., `startMonster`). Stateful logic (binary search in `lookupByKey`) built in IR.
- How to surface required-field diagnostics? Probably part of IR (since builder required fields are
  runtime).
- Remaining follow-up: teach IR lowering / renderer to read `flatbuffersSchemaDoc` and materialise
  real doc comments in generated Kotlin.

This plan should be kept in sync with runtime and IR plans as we iterate.
