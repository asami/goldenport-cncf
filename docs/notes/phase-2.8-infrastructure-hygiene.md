# Phase 2.8 — Infrastructure Hygiene

status = draft
scope = infrastructure cleanup after Phase 2.6

## Purpose

Clean up infrastructural inconsistencies discovered during Phase 2.6
without introducing new features or semantic changes.

- Clarify ownership and terminology for configuration vs core Config.
- Record Phase 2.8 decisions for configuration migration (CNCF → core) without semantic change.

## Initial Scope

- Path alias hard-coding → declarative / logical resolution
- Canonical path normalization
- Purpose-aware string rendering (DisplayIntent / Printable)

## CLI Hygiene

- Reorganize CLI structure (HelloWorld CLI positioning)
- File layout cleanup related to CLI
- Clarify and normalize CLI options, especially handling of meta-parameters

## Subsystem Hygiene

- Reorganize HelloWorld subsystem structure
- Clarify placement and role of built-in Components
- Explicitly include AdminComponent placement as an infrastructure concern

## OpenAPI Projection Hygiene

- In Phase 2.6, `/spec/current/openapi(.json)` is confirmed to execute correctly via the standard Component / Subsystem execution path.
- The current OpenAPI output intentionally includes only minimal domain APIs (e.g., admin/system/ping).
- The following items are known, accepted hygiene gaps and are explicitly deferred to Phase 2.8:
  1. Definition of OpenAPI projection scope:
     - domain APIs only
     - inclusion/exclusion of admin APIs
     - treatment of spec/meta APIs
  2. Policy for including Spec APIs themselves in OpenAPI output (self-description / "OpenAPI of OpenAPI").
  3. Handling of recursive or self-referential OpenAPI generation.
  4. Relationship between API visibility (public / internal / meta) and OpenAPI output.
- These are not bugs or regressions; they are unresolved policy decisions.
- Phase 2.6 does not attempt to resolve these items.

## CLI Exit Policy Hygiene

- Current:
  - There are code paths that call `sys.exit(exitCode)` directly during command execution.
  - In REPL / sbt / test / server-emulator / embedded runs, a forced process exit can be disruptive.
- Policy (Phase 2.8):
  - Only the CLI adapter layer handles process exit.
  - Default behavior is return (exit code is returned as a value).
  - `--force-exit` triggers `sys.exit`.
  - Core / Runtime / Subsystem layers must not call `exit`.
- Positioning:
  - This is CLI/adapter hygiene, not an execution model change.
  - It does not affect Phase 2.6 completion.

## Purpose-Aware String Rendering (Candidate)

Phase 2.8 also considers introducing a structured vocabulary for
purpose-aware string rendering.

The intent is to replace ad-hoc `toString` usage with an explicit,
context-aware rendering model that distinguishes output purposes
such as logging, interactive display, debugging, and embedding.

This candidate introduces the following core concepts:

- **DisplayIntent**: an explicit representation of output intent
  (print, display, show, embed, literal)
- **Printable**: an optional interface for values that can render
  themselves according to an explicit output intent

This item is a documentation-level design anchor only.
No runtime wiring or semantic changes are introduced in Phase 2.8.

See also:
- docs/notes/purpose-aware-string-rendering.md

## Non-Goals

- No semantic changes
- No new features

## Pending Item: curl-Compatible Client Parameter Specification

The curl-compatible client parameter specification discussed during client feature development
is formally deferred to **Phase 2.8 (Infrastructure Hygiene)**.

### Scope

- Normalize client CLI parameters based on curl conventions (e.g. `-X`, `-d`, headers, baseurl).
- Treat `-d @file` inputs as **Bag** at Request construction time.
- Align Request property/argument structure with future RestIngress behavior.
- Do NOT introduce ad-hoc shortcuts or emulator-only semantics.

### Rationale

- The specification affects CLI ingress, Request construction, and ClientComponent contracts.
- Premature fixation would risk inconsistency with RestIngress and HTTP semantics.
- Deferring allows validation against real client/server round-trip behavior.

### Status

- Identified
- Deferred
- Not implemented

This item MUST be revisited before Phase 2.8 completion.

## Deferred Development Items from Phase 2.6 / Stage 5

The following items were identified during Phase 2.6 demo completion and were
explicitly deferred to Phase 2.8 for resolution. Phase 2.8 includes design work
and implementation necessary to close these items.

- ComponentDefinition / DSL definition formalization
  - Clarify contract between DSL-based definitions and class-based Components.
  - Decide instantiation and lifecycle rules.

- Multiple Component Repository priority and override rules
  - Define deterministic resolution order across repositories.
  - Specify override and shadowing behavior.

- Bootstrap log persistence and operational integration
  - Define persistence strategy.
  - Integrate with runtime logging / observability pipeline.

- Full integration contract: config → initialize → runtime
  - Define a single, end-to-end initialization contract.
  - Align configuration loading, Component.initialize, and runtime execution.

These items constitute the core scope of Phase 2.8.

## Phase 2.6 → Phase 2.8 Deferred Item Tracking

| Item | Source | Status | Rationale |
| --- | --- | --- | --- |
| Semantic configuration / propagation (Subsystem.Config, Component.Config, runtime helpers) | Phase 2.6 Stage 3/5 deferred list + canonical configuration model | **DONE** | Builders now exist beside their owners, and the `Configuration Propagation Model` section of `configuration-model.md` captures the propagation path, so the semantic layer is established. |
| Configuration ownership realignment (core vs CNCF) | Phase 2.6 Stage 5 deferred list | **DONE** | CNCF now consumes `org.goldenport.configuration.*` and relies on core’s resolution artifacts, as documented in the Phase 2.8 Design Record and reiterated in `configuration-model.md#configuration-propagation-model`. |
| Canonical documentation consolidation | Phase 2.6 Stage 6 deferred note | **DONE** | The propagation semantics are merged into `configuration-model.md` and linked from the consolidated/design notes, so a single canonical reference now exists. |
| Config → initialize → runtime integration | Phase 2.6 Stage 5 deferred list | **PARTIAL** | Semantic builders and documentation exist, but the single end-to-end contract is still recorded as Phase 2.8 scope. |
| Path / alias resolution hygiene | Phase 2.6 Stage 3 note + Stage 6 deferred steps | **OPEN** | Alias normalization requirements remain in Phase 2.8 docs and no implementation or decision update has been recorded. |
| Component / service / operation canonical construction | Phase 2.6 Stage 6 deferred steps | **OPEN** | The script DSL alias/spec rules remain deferred (ScriptDslSpec is intentionally ignored) and no refinement is documented. |
| ComponentDefinition / DSL formalization | Phase 2.6 Stage 5 deferred list | **OPEN** | Design note still marks the item as “must be revisited before Phase 2.8 completion.” |
| Component repository priority rules | Phase 2.6 Stage 5 deferred list | **OPEN** | Deterministic repository ordering remains unsettled in Phase 2.8 scope. |
| Bootstrap log persistence / ops integration | Phase 2.6 Stage 5 deferred list | **OPEN** | Persistence/operational integration is explicitly deferred with no recorded completion in Phase 2.8 doc. |
| OpenAPI / representation expansion policy | Phase 2.6 Stage 6 deferred steps | **OPEN** | Advanced OpenAPI schema/representation work is marked as deferred to Phase 2.8+. |

### Status Summary

- Semantic Configuration / Propagation, Configuration ownership realignment, and Canonical documentation consolidation are DONE within Phase 2.8 and documented via `configuration-model.md#configuration-propagation-model`.
- Phase 2.8 remains **OPEN** because the remaining hygiene items in the table are still marked PARTIAL or OPEN and have not been resolved or re-deferred.

## Reference: Script DSL operation resolution status

### Background

The following test case in `ScriptDslSpec` is currently marked as `ignore`:

- reject unknown script component

This is **intentional** and does **not** indicate an implementation bug.

The test depends on a precise and finalized construction rule for:

- component
- service
- operation
- aliases
- implicit defaults (SCRIPT / DEFAULT / RUN)

These rules are not yet fully specified in Phase 2.6.

---

### Why this is deferred to Phase 2.8

At the moment, the runtime cannot reliably distinguish between:

- NotFound (component/service/operation does not exist)
- Rejection (request was syntactically valid but semantically rejected)
- BadRequest (invalid request structure)

This ambiguity exists because the **operation name resolution pipeline** is not fully defined.

In particular, the following questions are still open:

- How implicit defaults are applied when arguments are omitted
- How aliases are expanded and at which stage
- How Script DSL shortcuts map onto canonical Component DSL identifiers
- At which point a request should be judged as "not found"

Until these are formally defined, asserting `NotFound` behavior would be incorrect.

---

### Design intent

The current `ignore` serves as a **semantic freeze marker**, not as a temporary workaround.

It indicates:
- The test scenario is valid
- The expected behavior is understood
- The resolution rules are intentionally postponed

Once the operation resolution mechanism is finalized, this test should be re-enabled.

---

## Design Record: Operation Resolution vs Configuration (Phase 2.8)

This section records clarified design decisions derived from document review
during Phase 2.8. These decisions are considered **locked for this phase**
and are intended to prevent accidental semantic drift.

### Resolver Core vs Configuration

- Operation resolution logic (Component / Service / Operation lookup) is
  **independent of configuration source resolution**.
- Configuration resolution is responsible only for:
  - discovery (HOME / PROJECT / CWD)
  - source ordering
  - merging
  - normalization into a semantic configuration model
- Resolver semantics (alias, prefix matching, default rules) must not depend
  on raw configuration paths, files, or DSL fragments.

This separation is consistent with:
- `config-resolution.md` (resolution ≠ semantics)
- `configuration-model.md` (no raw config exposure to domain logic)
- `docs/design/configuration-model.md#configuration-propagation-model` (semantic propagation from system → subsystem → component)

### Design Record: Core-owned Configuration Mechanism (Phase 2.8)

#### Decision

The generic configuration mechanism (`Config`, `ConfigValue`, `ResolvedConfig`,
and deterministic resolution pipeline) is **owned by goldenport-core**, not CNCF.

CNCF consumes configuration as a resolved, schema-free container and must not
own or redefine the underlying resolution or merge semantics.

#### Rationale

- The configuration mechanism is:
  - runtime-agnostic
  - subsystem-agnostic
  - CLI / server / embedded compatible
- It carries **no domain or CNCF-specific semantics**.
- Multiple systems (CNCF, SIE, CLI tools, future runtimes) rely on the same
  deterministic resolution behavior.

Therefore, the configuration mechanism belongs to the core infrastructure layer.

#### Boundary Definition

- **core** is responsible for:
  - configuration discovery
  - source ordering and merging
  - traceability
  - production of `ResolvedConfig`
- **cncf** is responsible for:
  - consuming `ResolvedConfig`
  - building **semantic / normalized configuration models**
  - applying configuration-derived metadata (e.g. aliases, deployment hints)

This preserves the principle:
> resolution ≠ semantics

#### Implications

- The current `org.goldenport.cncf.config` implementation is transitional.
- The implementation will be migrated to **goldenport-core** without semantic change.
- CNCF will depend on core’s configuration artifacts and must not fork or duplicate them.

#### Non-Goals (Phase 2.8)

- No semantic changes to configuration behavior
- No DSL introduction
- No resolver logic embedded in configuration

This decision is **locked for Phase 2.8** to prevent later architectural drift.

### Prefix Matching (Resolver Core)

- The resolver core uses **prefix matching** as its primary search strategy.
- Prefix matching applies uniformly to:
  - component
  - service
  - operation
- Resolution proceeds deterministically:
  - unique match → Resolved
  - no match → NotFound
  - multiple matches → Ambiguous (explicit candidate list)
- Prefix matching is part of the canonical resolver and is **always enabled**.
- This mechanism replaces earlier notions of “incremental matching”.

### Terminology Clarification: Config vs Configuration (Phase 2.8)

To avoid semantic ambiguity, the term **Config** is reserved for
**core-internal, concrete configuration** required for operating the core
runtime itself (e.g. locale, timezone, logging, encoding).

The generic, schema-free, property-based configuration mechanism used for
applications, subsystems, and components is formally named
**configuration**.

#### Definitions

- **Config**
  - Owned by core
  - Concrete and strongly typed
  - Required for core runtime behavior
  - Implemented under `org.goldenport.config`

- **configuration**
  - Generic and schema-free
  - Resolution-focused (discovery, ordering, merging, traceability)
  - Semantics applied only after resolution
  - Implemented under `org.goldenport.configuration`

#### Implications

- The current `org.goldenport.cncf.config` implementation represents
  **configuration**, not core `Config`.
- This implementation will be migrated to core as
  `org.goldenport.configuration` without semantic change.
- CNCF must not own or fork configuration resolution logic.
- CNCF is responsible only for building **semantic / normalized configuration
  models** from resolved configuration.

This terminology distinction is **mandatory** and **locked for Phase 2.8**.

### Single Operation Optimization (CLI / Script Only)

This phase introduces a **limited UX optimization** for CLI and Script usage
when exactly one non-builtin operation exists.

This optimization is intentionally conservative and MUST NOT affect
application-level inputs or resolver core semantics.

#### Scope

- Applies only to CLI / Script entry points.
- Does NOT apply to HTTP or application payload resolution.
- Executed only after canonical resolver lookup results in NotFound.

#### Resolution Rules

- Prefix matching is explicitly **disabled** for this optimization.
- Alias expansion is NOT applied.
- Implicit context resolution is NOT applied.
- The selector MUST be a fully qualified operation name (FQN):
  `component.service.operation`
- Operation name-only inputs (e.g. `ping`, `find`, `test.html`) are
  explicitly excluded to avoid collisions with built-in or shell-level commands.

### Single Operation Optimization — Finalized Semantics (Phase 2.8)

This section records the finalized semantics confirmed by
`OperationResolverSpec` and test completion.

#### Positioning

- Single Operation Optimization is a **CLI / Script–only UX optimization**.
- It is **not part of the canonical resolver core semantics**.
- It is evaluated **only as a fallback** when canonical resolution
  results in `NotFound`.

#### Rules

- The optimization applies **only after** canonical resolution fails.
- **Prefix matching is NOT applied**.
- **0-dot selectors are NOT eligible**.
  - Inputs such as `ping`, `find`, or file-like strings (e.g. `test.html`)
    must not be captured by this optimization.
- The selector MUST be a fully qualified operation name (FQN):
  `component.service.operation`.
- Resolution uses **exact match only**.
- The candidate set is limited to **non-builtin components**.
- Resolution succeeds **only if exactly one operation exists globally**.
- Otherwise, the result remains `NotFound` or `Ambiguous`.

#### Rationale

This design prevents accidental capture of:
- shell-level commands
- built-in admin operations
- application payload strings

while still allowing concise CLI / Script usage when the target
operation is globally unique and explicitly specified.

#### Explicit Non-Goals

- No implicit defaults
- No alias expansion
- No heuristic or incremental matching
- No application-level input relaxation

These constraints are intentional and locked for Phase 2.8.

#### Semantics

- Candidate evaluation uses **exact match only**.
- Target scope is limited to **non-builtin components**.
- Resolution succeeds only if the candidate set is globally unique:
  - component = 1
  - service = 1
  - operation = 1
- Otherwise, the result MUST remain NotFound or Ambiguous.

#### Rationale

This optimization is designed to reduce verbosity for users who already
have precise knowledge of the target operation, while preserving strict,
deterministic resolver semantics.

Potential conflicts with application-level payload strings
(e.g. `admin.default.ping`) are intentionally out of scope for Phase 2.8
and should be handled by higher-level command or script structures
(e.g. `SCRIPT.DEFAULT.RUN`).

### Alias Handling (Configuration-Driven)

- Aliases are **not resolver logic** but **configuration-provided metadata**.
- Alias expansion is:
  - deterministic
  - based on exact match only
  - applied before resolver lookup
- Alias definitions must be supplied via the normalized configuration model.
- Resolver implementations must treat aliases as immutable input data and
  must not embed alias definitions in code.

Alias implementation is deferred until full configuration support is in place.

### Explicitly Deferred Items

The following items are intentionally deferred and must not be implemented
before the above rules are completed:

- Implicit context-based resolution (component / service defaults)
- Interactive or REPL-style input abbreviation
- Non-deterministic or heuristic resolution shortcuts

These items depend on runtime context or UX policy and are out of scope for
the resolver core in Phase 2.8.

### Rationale

The above decisions ensure that:

- Resolver behavior is deterministic and testable
- Configuration changes do not introduce semantic branching
- CLI / Script / HTTP surfaces can share a single canonical resolver
- Future UX-oriented extensions can be layered without rewriting core logic

---

### Phase 2.8 completion condition (related)

This reference item is considered resolved when:

- Component / Service / Operation resolution rules are explicitly defined
- Alias and implicit default expansion order is fixed
- Script DSL and Component DSL share a consistent resolution pipeline
- Error classification (NotFound vs Rejection) is deterministic

At that point, the ignored test can be safely converted into an active assertion.
