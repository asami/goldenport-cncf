# Architecture Review Work Instructions (R4)
Date: 2026-03-21
Owner: Development Thread
Source: Architecture Review (2026-03-21)
Status: Open

## Objective

Address the architecture review findings with implementation-first fixes, while preserving current runtime behavior outside the specified change scope.

Priority order:

1. P1: `--path-resolution` execution-path breakage and parser split
2. P2: config-resolution contract mismatch and introspection doc mismatch
3. P3: CLI runtime-flag documentation drift

---

## Task 1 (P1): Fix `--path-resolution` on real CLI execution path

### Problem

`run command --path-resolution ...` fails with `path-resolution failed: selector is required` on the real CLI route, even though parser-level tests pass.

### Required Change

- Make `CncfRuntime().run(Array("command", ...))` and `CncfRuntime.parseCommandArgs(...)` use the same selector normalization/parsing contract.
- Eliminate behavior drift caused by duplicate `parseCommandArgs` implementations in `object CncfRuntime` and `class CncfRuntime`.
- Keep existing non-flag command behavior (`admin.system.ping` etc.) unchanged.

### Scope

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`
- Any directly related helper in the same package, only if required.

### Acceptance Criteria

- `sbt "run command admin.system.ping"` succeeds (baseline preserved).
- `sbt "run command --path-resolution admin.component"` succeeds.
- `sbt "run command --path-resolution admin/component"` succeeds or is explicitly rejected by contract with updated spec/tests; behavior must be consistent across parser test and run path.
- No regression on existing command parsing tests.

### Required Tests

- Add/update executable specs for real run-path behavior (not only parse helper behavior).
- At minimum run:
  - `sbt "testOnly org.goldenport.cncf.cli.CommandExecuteComponentSpec"`

---

## Task 2 (P2): Align legacy `config-resolution.md` with implementation-first policy

### Problem

`docs/spec/config-resolution.md` specifies upward project-root discovery (`.cncf`/`.git`), while legacy CNCF implementation uses `project(cwd)` fixed to `cwd/.cncf/config.conf`.

### Required Change

Choose one canonical direction and apply it consistently:

- Recommended for current policy: update document contract to match actual legacy implementation and clearly mark legacy/deprecated boundaries.

### Scope

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/spec/config-resolution.md`
- Optional: legacy tests under `src/test/scala/org/goldenport/cncf/config/**` if clarifying behavior.

### Acceptance Criteria

- `config-resolution.md` no longer claims behavior not implemented in legacy CNCF resolver.
- Legacy/deprecated note remains explicit.
- If behavior is documented, at least one executable check exists (remove `pending` where practical, or add concrete TODO note with reason and owner/date).

---

## Task 3 (P2): Fix introspection doc mismatch (`meta.*` surface)

### Problem

`cncf-architecture-overview.md` endpoint list is missing operations that are implemented and covered elsewhere (`meta.mcp`, `meta.statemachine`).

### Required Change

- Update architecture overview introspection endpoint list to match runtime and `cncf-meta-api-spec.md`.
- Do not alter runtime behavior in this task.

### Scope

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/cncf-architecture-overview.md`
- Cross-check with:
  - `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/cncf-meta-api-spec.md`

### Acceptance Criteria

- Endpoint list in overview is consistent with current runtime surface.
- No contradictions between overview and meta API spec.

---

## Task 4 (P3): Update CLI spec runtime-flag section

### Problem

`cncf-cli-spec.md` runtime-flag section is outdated (documents only `--json/--debug/--no-exit`, while runtime parser also handles `--format` and `--path-resolution`).

### Required Change

- Update documented runtime flags to current parser behavior.
- Clarify whether `--path-resolution` is command-mode only and feature-flag semantics.

### Scope

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/design/cncf-cli-spec.md`
- Cross-check with:
  - `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`

### Acceptance Criteria

- CLI spec runtime flag list matches implemented behavior.
- Feature-flag semantics are explicit and non-ambiguous.

---

## Verification Checklist

Run after implementation:

- `sbt compile`
- `sbt "testOnly org.goldenport.cncf.cli.CommandExecuteComponentSpec"`
- `sbt "run command admin.system.ping"`
- `sbt "run command --path-resolution admin.component"`
- `sbt "run command --path-resolution admin/component"` (or documented intentional failure if contract forbids it)

---

## Constraints

- Follow repository rules order: rules -> spec -> design -> code.
- Keep docs in English.
- Do not introduce speculative redesign beyond these findings.
- Preserve behavior outside defined scope.

---

## Definition of Done

- All four tasks completed or explicitly deferred with rationale.
- P1 run-path defect resolved and reproducible via CLI command.
- Docs/spec consistency restored for the targeted areas.
- Verification commands and results attached in the development thread.
