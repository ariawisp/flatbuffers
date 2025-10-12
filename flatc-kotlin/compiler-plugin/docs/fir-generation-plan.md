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

Plan:

1. Introduce `FbsSourceFile` & `FbsSourceElement`
   - Wrap schema `Path` + `SourceSpan`.
   - Implements/embeds `KtSourceFile`/`KtSourceElement`, returning `null` PSI but stable offsets.
   - Compatible with `CompatContext.fakeElement` by returning an underlying `KtSourceElement`
     derived from `org.jetbrains.kotlin.psi.KtPsiSourceElement`.
2. Extend `CompatContext` with utilities:
   - `fun fromSpan(span: SourceSpan, kind: KtFakeSourceElementKind = PluginGenerated): KtSourceElement`
     constructing a fake element anchored to `.fbs`.
   - `fun FirDeclaration.withSchemaSource(span: SourceSpan?, fallbackKind: KtFakeSourceElementKind)`
     convenience to attach the element when span is present.
3. Store a per-session cache (`SchemaSourceIndex`) mapping `(file path → KtSourceFile)` to reuse
   open handles per schema file and support highlight ranges.

If span is `null`, fall back to `PluginGenerated`. IR lowering must mirror the same mapping.

## Generated Declarations

The FIR plugin emits Kotlin declarations that mirror the current codegen surface. All output lives in
the schema namespace (`namespace foo.bar;` → package `foo.bar`). For clarity we denote runtime types
from `com.google.flatbuffers.kotlin` in monospace.

### Tables

- `class <Name> : Table`
  - Primary constructor private, parameter-less.
  - Public `fun init(offset: Int, buffer: ReadWriteBuffer): <Name>`
  - One property per field:
    - Scalars → `val` with `lookupField(...) { bb.getX(...) }`.
    - Structs/tables → overload pair `val foo` + `fun foo(obj: Type): Type?`.
    - Vectors → `fun foo(index: Int): T`, `val fooLength`, `fun fooAsBuffer()`.
    - For vector of tables/strings/enums include `fooByKey(...)` helpers when key defined.
  - `override fun keysCompare(...)` when table has key.
  - `companion object`
    - `fun validateVersion()`
    - `fun asRoot(buffer, obj)`
    - `fun start<Name>(builder)`
    - `fun add<Field>(builder, value)`
    - Vector helpers (`create<Field>Vector`, `start<Field>Vector`)
    - `fun end<Name>(builder): Offset<<Name>>`
    - `fun finish<Name>Buffer(...)`, `finishSizePrefixed...`
    - `fun lookupByKey(...)` when applicable.
  - Top-level `typealias <Name>OffsetArray = OffsetArray<<Name>>` plus inline factory.
  - Source: class spans table definition, companion members map to field spans where possible.

### Structs

- `class <Name> : Struct`
  - `fun init(offset: Int, buffer: ReadWriteBuffer): <Name>`
  - Field accessors read directly from `bb`.
  - `companion object` exposes `create<Name>(builder, ...)`.
  - Inline vector alias same as tables.

### Enums

- `@JvmInline value class <Name>(val value: <base Kotlin type>)`
  - `companion object`
    - `val <Case>` for each enumerator.
    - `val names: Array<String>`
    - `fun name(e: <Name>): String`
  - `typealias <Name>Array` to corresponding primitive array.

### Unions

- `@JvmInline value class <Union>E` for type discriminant.
  - Cases as constants.
  - `fun name`.
- `typealias <Union>Union = UnionOffset`
  - Additional helpers (e.g., for vector of unions) as needed.

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
- Need to validate whether Kotlin FIR allows multiple fake source kinds pointing to non-Kotlin
  files; might require custom `VirtualFile` integration.

This plan should be kept in sync with runtime and IR plans as we iterate.
