# Docs-Implementation Consistency Handoff (2026-03-21, Full Sweep)

## Purpose

This handoff summarizes the results of a cross-cutting consistency review between the `docs/` tree and the current implementation, and defines concrete follow-up work for the development thread.

## Scope

- `CONS-P1-01`: mismatch between `admin.system.status` spec and implementation
- `CONS-P1-02`: mismatch between `path-resolution` specification and runtime execution path
- `CONS-P2-01`: mismatch between `config-resolution` project-root detection and legacy implementation
- `CONS-P2-02`: mismatch between `output-format` suffix contract and implementation
- `CONS-P2-03`: mismatch between `meta.*` design docs and implemented API set
- `CONS-P3-01`: self-contradiction in docs language rules

## Findings

### CONS-P1-01

- Severity: `P1`
- Summary:
  - `docs/spec/admin-system-status.md` defines `admin.system.status`, but implemented system operations are only `ping` and `health`.
  - There is no executable operation path corresponding to the current spec text.
- Primary references:
  - `docs/spec/admin-system-status.md:9`
  - `docs/spec/admin-system-status.md:14`
  - `src/main/scala/org/goldenport/cncf/component/Component.scala:807`
  - `src/main/scala/org/goldenport/cncf/component/Component.scala:819`
  - `src/main/scala/org/goldenport/cncf/component/builtin/admin/AdminComponent.scala:71`

### CONS-P1-02

- Severity: `P1`
- Summary:
  - `docs/spec/path-resolution.md` defines a common CanonicalPath resolution contract for CLI/HTTP/Script.
  - Runtime execution uses `CncfRuntime + OperationResolver`; `PathResolution` is not the active runtime path.
  - The specified rule order (R5 -> R2/R3 -> R4 -> R1) also differs from the current implementation flow.
- Primary references:
  - `docs/spec/path-resolution.md:7`
  - `docs/spec/path-resolution.md:107`
  - `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:1112`
  - `src/main/scala/org/goldenport/cncf/subsystem/resolver/OperationResolver.scala:27`
  - `src/main/scala/org/goldenport/cncf/resolver/PathResolution.scala:28`

### CONS-P2-01

- Severity: `P2`
- Summary:
  - `config-resolution` specifies upward project-root detection from `cwd`.
  - Legacy implementation directly reads `cwd/.cncf/config.conf` and does not perform upward root search.
- Primary references:
  - `docs/spec/config-resolution.md:94`
  - `docs/spec/config-resolution.md:99`
  - `src/main/scala/org/goldenport/cncf/config/source/ConfigSource.scala:60`
  - `src/main/scala/org/goldenport/cncf/config/source/ConfigSource.scala:61`
  - `src/main/scala/org/goldenport/cncf/config/source/ConfigSources.scala:22`

### CONS-P2-02

- Severity: `P2`
- Summary:
  - `output-format` specifies suffix-driven format selection as a general contract.
  - Implementation is primarily controlled by `--format` / request `format` property.
  - Suffix interpretation exists only as OpenAPI route special handling.
- Primary references:
  - `docs/spec/output-format.md:49`
  - `docs/spec/output-format.md:58`
  - `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala:1485`
  - `src/main/scala/org/goldenport/cncf/protocol/OperationResponseFormatter.scala:49`
  - `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala:305`

### CONS-P2-03

- Severity: `P2`
- Summary:
  - `docs/design/cncf-meta-api-spec.md` does not list `meta.statemachine`.
  - The runtime exposes `meta.statemachine` as a built-in meta operation.
- Primary references:
  - `docs/design/cncf-meta-api-spec.md:11`
  - `docs/design/cncf-meta-api-spec.md:23`
  - `src/main/scala/org/goldenport/cncf/component/Component.scala:783`
  - `src/main/scala/org/goldenport/cncf/component/Component.scala:786`

### CONS-P3-01

- Severity: `P3`
- Summary:
  - `docs/rules/document-boundary.md` declares English-only docs under `docs/`.
  - `rules` documents themselves include non-English content, creating a self-contradiction.
- Primary references:
  - `docs/rules/document-boundary.md:13`
  - `docs/rules/document-boundary.md:15`
  - `docs/rules/execution-naming.md:5`
  - `docs/rules/execution-naming.md:16`

## Implementation Direction

### For CONS-P1-01

- Option A: implement `admin.system.status` and keep the spec as-is.
- Option B: align the spec to current implementation (`admin.system.health`) under implementation-first policy.
- Choose one path and make operation naming + canonical output contract consistent.

### For CONS-P1-02

- Option A: align runtime routing to the `PathResolution` contract (single canonical entry path).
- Option B: narrow `path-resolution.md` scope to the `PathResolution` module and define runtime behavior separately for `OperationResolver`.
- Remove the current dual state (global normative spec vs separate runtime behavior).

### For CONS-P2-01

- If legacy resolver behavior is kept, update the spec text to match actual implementation.
- If upward root search is required, implement a root finder for `ConfigSource.project` and add matching specs.

### For CONS-P2-02

- If suffix contract is retained, extend request parsing and format selection to handle suffixes consistently.
- If property-driven behavior is retained, revise spec to state suffix support is limited to specific surfaces.

### For CONS-P2-03

- Add `meta.statemachine` to `cncf-meta-api-spec.md` and document selector/output behavior.

### For CONS-P3-01

- Either revise the Language Rule in `document-boundary.md` to match actual policy, or translate non-English rules docs to restore consistency.

## Acceptance Criteria

- [x] `CONS-P1-01`: `admin.system.status` presence/absence is consistent between spec and implementation.
- [x] `CONS-P1-02`: path-resolution scope and runtime routing are consistent.
- [ ] `CONS-P2-01`: project-root detection behavior is consistent between spec and legacy implementation.
- [x] `CONS-P2-02`: output-format selection contract (suffix/property) is consistent between spec and implementation.
- [x] `CONS-P2-03`: `meta.statemachine` is reflected in design docs.
- [x] `CONS-P3-01`: docs language rules are no longer self-contradictory.

## Suggested Spec/Test Work

- `AdminSystemStatusOperationSpec` (proposed), or extension of existing admin operation specs
- specs that define and verify `PathResolution` vs `OperationResolver` responsibility boundaries
- legacy project-root behavior specs for `ConfigResolver`
- output-format contract specs for suffix/property behavior

## Validation Commands

```bash
rg -n "admin\\.system\\.status|meta\\.statemachine|Path Resolution Specification|Configuration Resolution|Output Format Specification" docs src/main/scala src/test/scala
sbt "testOnly org.goldenport.cncf.spec.PathResolutionSpec org.goldenport.cncf.cli.CommandExecuteComponentSpec org.goldenport.cncf.config.ConfigResolverSpec"
sbt compile
```

## Review Validation Snapshot (2026-03-21)

- `sbt "testOnly org.goldenport.cncf.spec.PathResolutionSpec org.goldenport.cncf.cli.CommandExecuteComponentSpec org.goldenport.cncf.config.ConfigResolverSpec"`
  - Result: 37 tests passed, 2 pending (`ConfigResolverSpec`)
- `sbt "testOnly org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec org.goldenport.cncf.component.ComponentFactoryLegacyPlanConsistencySpec"`
  - Result: 2 tests passed

## Execution Update (2026-03-21)

Implemented in this repository:

- `CONS-P1-01`
  - Added first-class `system.status` default operation.
  - Added minimum schema fields: `status`, `timestamp`, `uptime`.
  - Added job metrics projection when `JobEngine.metrics` is available.
  - Added executable specs:
    - `ComponentDefaultServiceSpec`
    - `AdminSystemPingExecutionSpec`

- `CONS-P1-02` (staged CLI adoption)
  - Added command-only feature flag:
    - `--path-resolution`
    - `--cncf.path-resolution.command true|false`
  - Inserted `PathResolution` before selector lookup in CLI command parsing path.
  - Kept `OperationResolver` as post-resolution lookup layer.
  - Added executable spec in `CommandExecuteComponentSpec`.

- `CONS-P2-02`
  - Implemented selector suffix format handling (`.json`, `.yaml`, `.text`).
  - Established deterministic precedence:
    1. explicit request format property (`--format` / request property)
    2. selector suffix
    3. mode default format
  - Added executable specs in `CommandExecuteComponentSpec`.

- `CONS-P2-03`
  - Updated design doc `docs/design/cncf-meta-api-spec.md` to include `meta.statemachine`.

- `CONS-P3-01`
  - Added explicit I18N exception rule in `docs/rules/document-boundary.md`.
  - Converted Japanese rule content to English:
    - `docs/rules/execution-naming.md`
    - `docs/rules/collection-idiom.md`

Pending / follow-up:

- `CONS-P2-01` remains open in this repository.
  - Canonical implementation target is `org.goldenport.configuration.*` (core-owned).
  - This repository already consumes `ConfigurationSources.standard(...)` from core.
  - Upward project-root detection behavior must be finalized/verified in core and then referenced here.

Validation executed:

- `sbt compile` -> success
- `sbt "testOnly org.goldenport.cncf.component.ComponentDefaultServiceSpec org.goldenport.cncf.component.AdminSystemPingExecutionSpec org.goldenport.cncf.cli.CommandExecuteComponentSpec org.goldenport.cncf.spec.PathResolutionSpec"` -> all passed
- `sbt "testOnly org.goldenport.cncf.component.* org.goldenport.cncf.cli.* org.goldenport.cncf.subsystem.resolver.* org.goldenport.cncf.config.*"` -> all passed (with existing pending tests)
