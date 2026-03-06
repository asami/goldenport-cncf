Status: obsolete

This document is retained for historical context.
Canonical CNCF CLI/introspection specifications are in docs/design/:
- docs/design/cncf-cli-spec.md
- docs/design/cncf-selector-resolution-spec.md
- docs/design/cncf-meta-api-spec.md
- docs/design/cncf-introspection-spec.md
- docs/design/cncf-architecture-overview.md

# Design Spec Review (Proposed CNCF Design Files)

Date: 2026-03-06
Decision: **Do not create `docs/design/cncf-*.md` yet** (inconsistencies found)

## Summary

The proposed design specs are mostly aligned with current CLI and selector behavior, but they are **not fully consistent** with the current implementation.

Main blocker:
- `meta.describe` example/semantics in the proposed spec do not match current argument resolution behavior in implementation.

Given the instruction, this review report is produced instead of creating canonical files under `docs/design/`.

## Spec vs implementation mismatches

### 1) `cncf-meta-api-spec.md`: `meta.describe` usage example mismatch

Proposed example:
- `cncf command domain.meta.describe createPerson`

Current implementation:
- `Component.scala` (`_meta_describe_selector`) resolves single argument by prefixing component name (`domain.createPerson`), which is interpreted as `component.service` shape (service target), not necessarily an operation target.
- Service-meta forwarding path (`component.service.meta.describe ...`) now forwards `component.service` as first arg, but `_meta_describe_selector` still treats 2-arg form as `(service, operation)` and prefixes component again.

Impact:
- The proposed canonical example may not produce the documented “operation description” behavior in all cases.

Recommended correction:
- Either adjust spec examples to accepted selector forms that the runtime actually resolves today,
- or update implementation selector composition first, then promote the spec.

### 2) `cncf-architecture-overview.md`: introspection list is incomplete vs runtime

Proposed overview lists:
- `meta.help`, `meta.describe`, `meta.schema`, `meta.openapi`

Current runtime includes more endpoints:
- `meta.components`, `meta.services`, `meta.operations`, `meta.tree`, `meta.version`

Impact:
- Canonical overview would under-specify available runtime API surface.

Recommended correction:
- Expand overview to include all currently exposed `meta.*` endpoints, or explicitly mark subset scope.

### 3) `cncf-selector-resolution-spec.md`: rewrite rule wording is ambiguous at top-level

Proposed rewrite:
- `selector --help -> help selector`

Current behavior is 2-stage:
- Top-level normalization (`CncfRuntime`):
  - `--help|-h -> help`
  - `<command> --help|-h -> <command> help`
- Command-layer normalization (`CommandProtocolHelp`):
  - `<selector> --help|-h -> help <selector>` (after entering `command` protocol)

Impact:
- Proposed wording can be misread as global rewrite for all commands.

Recommended correction:
- Clarify rewrite scope as command-protocol-local selector rewrite.

### 4) CLI examples are not fully normalized across current docs/runtime

Proposed and existing docs use multiple client styles:
- `cncf client call ...`
- `cncf client http get`
- selector-like client examples

Impact:
- Potential confusion when elevated to canonical design docs.

Recommended correction:
- Standardize client command examples before freezing canonical specs.

## Recommended spec corrections

1. `cncf-meta-api-spec.md`
- Rework `meta.describe` examples to match actual selector composition semantics now in code.
- Add explicit note on accepted argument forms for component-level vs service-level describe.

2. `cncf-architecture-overview.md`
- Add complete `meta.*` surface currently implemented (`components/services/operations/tree/version`).

3. `cncf-selector-resolution-spec.md`
- Split rewrite section into:
  - top-level help alias normalization
  - command-selector help normalization

4. All proposed specs
- Add a short “Current implementation baseline” section referencing actual modules:
  - `CncfRuntime.scala`
  - `CliOperation.scala`
  - `cli/help/CommandProtocolHelp.scala`
  - `Component.scala` (default meta operations)
  - `projection/*`

5. Optional before canonization
- Add/extend executable specs for `meta.describe` selector cases to lock behavior before promoting docs to canonical design.

