# Compiler Plugin Testing Strategy

The Kotlin-only toolchain needs confidence at three levels: FIR synthesis, IR lowerings, and
runtime integration. This document outlines the test matrix that will accompany the plugin.

## 1. FIR Surface Tests

Purpose: verify that FIR stubs faithfully mirror the legacy generated API.

- **Approach:** leverage Kotlin's FIR plugin test harness (`AbstractFirExtensionRegistrarTest`).
- **Inputs:** small curated schemas (e.g., `monster_test.fbs`, `optional_scalars.fbs`, union cases).
- **Assertions:**
  - FIR dump contains expected class/function signatures under the right package.
  - Doc comments & annotations appear on the proper declarations.
  - Source information (`source.kind`, offsets) points to `.fbs` paths.
- **Implementation Notes:**
  - Place golden dumps under `compiler-plugin/src/test/resources/fir/<schema>.txt`.
  - Provide configurator to register our plugin with in-memory `ResolvedSchema`.
  - Adopt Metro's `FirDumpTestGenerated` approach for parametrised coverage.

## 2. IR Body Tests

Purpose: confirm IR lowerings build the same execution as legacy code.

- **Approach:** rely on Kotlin's internal compiler test framework. The bespoke
  `AbstractFlatbuffersIrTest` extends `AbstractFirLightTreeJvmIrTextTest`, registers the plugin via
  CLI options, and inspects the produced `IrModuleFragment` directly.
- **Fixtures:** store schemas and Kotlin drivers under `compiler-plugin/src/test/data/ir/<case>`.
  Each Kotlin file declares its schema dependencies with directives such as
  `// FLATBUFFERS_SCHEMA: ir/sample/sample.fbs`.
- **Assertions:**
  - Generated initialisers (`init` and `reset`) delegate to runtime entry points such as
    `Table.reset`.
  - Scalar accessors route through `lookupField`, emit the schema default literal, and read from the
    appropriate `ReadWriteBuffer.get*` intrinsic.
  - Companion helpers invoke the correct `FlatBufferBuilder.add` overload.
  - Metadata (`FlatbuffersSchemaMetadata`) preserves schema documentation in both raw and rendered
    forms.
- **Debug Aid:** the Gradle test task exposes `flatbuffers.compilerPlugin.{jar,runtimeClasspath}` so
  the packaged plugin and its runtime can be inspected alongside IR dumps when needed.

## 3. Bytecode / Runtime Integration Tests

Purpose: validate real code compiled with the plugin behaves identically to source-generated code.

- **Harness:** Gradle-based integration tests under `compiler-tests/`.
- **Scenario per schema:**
  1. Compile Kotlin project with plugin + runtime.
  2. Load schema via plugin, build runtime objects (tables/builders).
  3. Serialize payload using plugin-generated API.
  4. Deserialize with:
     - (a) Plugin-generated API (roundtrip).
     - (b) Legacy generated sources (shared as baseline) to ensure wire compatibility.
  5. Compare resulting buffers byte-for-byte with C++ `flatc --binary` output from golden data.
- **Real-world Schemas:** include at least one larger schema from the FlatBuffers test suite (e.g.,
  `reflection/reflection.fbs`) and a gRPC example.

## 4. CLI Contract Tests

Purpose: ensure CLI options load schemas, surface diagnostics, and pass data into compiler plugin.

- Test CLI invocation that prints diagnostics for erroneous schema (missing include, duplicate enum).
- Verify `.bfbs` emission matches `flatc` reference (binary comparison).
- Validate CLI exit codes.

## 5. Gradle Plugin Tests (Phase 10+)

- Use Gradle TestKit to assert plugin wiring:
  - Schema directories resolved.
  - Incremental build inputs tracked.
  - Compiler plugin applied to Kotlin compilation tasks.

## Test Utilities

- **Schema Fixtures:** centralised fixture loader in `compiler-plugin-test-util` module to share
  schema parsing & ResolvedSchema caching between FIR/IR/integration tests.
- **Buffer Comparator:** helper to diff byte buffers and explain mismatches (offset, field name).
- **Reflection Validator:** util to dump `.bfbs` fields for debugging.

## Continuous Integration

- Target matrix: JVM 21 (matching toolchain), Kotlin 2.2.20.
- Enable Gradle build scans for failing compiler integration tests to capture dumps.
- Provide reproducible `./gradlew :compiler-plugin:firDump` / `irDump` tasks for developers adding
  new schemas.

Following this plan keeps coverage layered: unit-level FIR/IR validation prevents regressions in
code shape, while integration tests protect wire compatibility and runtime behaviour.
