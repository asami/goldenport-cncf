# Phase 2.8 — Infrastructure Hygiene

status = draft
scope = infrastructure cleanup after Phase 2.6

## Purpose

Clean up infrastructural inconsistencies discovered during Phase 2.6
without introducing new features or semantic changes.

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

### Phase 2.8 completion condition (related)

This reference item is considered resolved when:

- Component / Service / Operation resolution rules are explicitly defined
- Alias and implicit default expansion order is fixed
- Script DSL and Component DSL share a consistent resolution pipeline
- Error classification (NotFound vs Rejection) is deterministic

At that point, the ignored test can be safely converted into an active assertion.
