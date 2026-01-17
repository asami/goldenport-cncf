# Phase 2.8 — Work A1-2  
## Hard-Coded Alias Inventory Plan (Journal)

Date: 2026-01  
Phase: 2.8  
Work Item: A1-2  
Status: planned (journal)

---

## Purpose

This document defines **how to identify and classify hard-coded alias–like
behavior** across the codebase.

The goal is NOT to modify code, remove aliases, or decide solutions.
The goal is to make alias-related assumptions **explicit, enumerable,
and classifiable**, in preparation for Phase 2.8 hygiene work.

This plan exists to ensure that:
- alias discovery is systematic and complete,
- human and AI reviewers apply the same criteria,
- the task has a clear and checkable completion condition.

---

## Scope

### Included

The inventory covers all layers where identifier-like strings may appear:

- CLI entry and argument handling
- Script DSL parsing and tests
- Resolver / runtime infrastructure
- Built-in component handling
- Configuration-related glue code

### Explicitly Excluded

The following are OUT OF SCOPE for this inventory:

- Resolver search strategy (prefix matching, heuristics)
- UX-level shortcuts or interactive abbreviations
- DSL grammar design
- Repository resolution order

These are handled in later work items (A2+ or next phases).

---

## Definition: Alias Candidate

A string or identifier is considered an **alias candidate** if ANY of the
following are true:

1. It is a shortened or abbreviated form of a longer identifier.
2. It is treated specially by code (branching, matching, guarding).
3. It has implicit meaning not derived from structural rules.
4. It appears in tests as a magic literal affecting resolution behavior.
5. It represents user-facing convenience rather than canonical identity.

This definition is intentionally broad.
False positives are acceptable at this stage.

---

## Layers to Inspect

### 1. CLI Layer

Look for:
- string comparisons after argument parsing,
- implicit defaults when parameters are omitted,
- special-cased names that bypass normal resolution.

---

### 2. Resolver / Runtime Layer

Look for:
- hard-coded component / service / operation names,
- branching based on literal strings,
- logic that compensates for upstream ambiguity.

---

### 3. Script DSL and Tests

Look for:
- ignored or fragile tests depending on specific literals,
- shortcut syntax encoded as strings,
- assumptions about implicit names.

---

### 4. Configuration Boundary

Look for:
- alias-like behavior implemented in code instead of configuration,
- mixing of configuration keys and semantic identifiers.

---

## Classification Rules

Each discovered alias candidate MUST be classified into exactly one category.

### Category A — Canonical Name

- A legitimate component / service / operation identifier.
- Fully qualified or structurally complete.
- Required for correct system behavior.

Action: **Keep as-is.**

---

### Category B — Configuration-Driven Alias

- A shorthand or convenience name.
- Should be supplied via normalized configuration.
- Does not belong in code or tests.

Action: **Remove from code; migrate to configuration.**

---

### Category C — Deferred UX Shortcut

- Convenience feature depending on heuristics or interaction.
- Not appropriate for Phase 2.8 hygiene.

Action: **Defer to Next Phase Development Items.**

---

### Category D — Invalid or Legacy Artifact

- No clear semantic justification.
- Historical residue or accidental behavior.

Action: **Remove or explicitly reject.**

---

## Recording Format

Each alias candidate MUST be recorded using the following structure:

- Location:
  - module / file / test
- Literal:
  - exact string
- Observed behavior:
  - how it is treated
- Classification:
  - A / B / C / D
- Notes:
  - context, dependencies, risks

No solution proposals or refactoring plans
should be included at this stage.

---

## Completion Criteria

Work A1-2 is considered complete when:

- All relevant layers have been inspected.
- Every alias candidate has been recorded.
- Each entry has a classification (A–D).
- No unclassified or “maybe” items remain.

At this point:
- implementation work may begin, or
- Work A1 may be declared DONE and handed off to Work A2.

---

## Positioning

This document is a **journal planning artifact**.

It:
- is not authoritative design,
- may be revised as understanding improves,
- exists to enable consistent execution of alias inventory work.

---

## Initial Inventory — Pending Enumeration

- Location: `org/goldenport/cncf/cli/CncfRuntime.scala` (method `_to_request_script`)
  - Literal: `"SCRIPT" "DEFAULT" "RUN"`
  - Observed behavior: script handler prepends `SCRIPT DEFAULT RUN` tokens before calling `parseCommandArgs` whenever the first three incoming arguments are not already the canonical script identifiers, effectively aliasing user inputs to one fixed component/service/operation.
  - Classification: B
  - Notes: alias is hard-coded in the CLI layer; should be surfaced via configuration or declarative mapping for Phase 2.8.

- Location: `org/goldenport/cncf/cli/CncfRuntime.scala` (method `_parse_client_command`)
  - Literal: `"http"`
  - Observed behavior: when the first client argument equals `http`, the parser treats the remaining tokens as HTTP-specific operation/path details instead of default positional arguments, effectively aliasing the `http` keyword to the HTTP client flow.
  - Classification: B
  - Notes: this convenience command is hard-coded and not configurable, so Phase 2.8 hygiene should document or normalize it via configuration.

- Location: `org/goldenport/cncf/cli/CncfRuntime.scala` (method `normalizeServerEmulatorArgs`)
  - Literal: `"http://localhost/"`
  - Observed behavior: server-emulator arguments without an explicit URL are expanded into `http://localhost/{component}/{service}/{operation}`, aliasing component/service/operation selectors to a fixed localhost URL.
  - Classification: B
  - Notes: the host/port prefix is baked into the emulator path construction rather than configuration, which ties tests and clients to localhost.

- Location: `org/goldenport/cncf/cli/CncfRuntime.scala` (method `_client_baseurl_from_request`)
  - Literal: `"http://localhost:8080"`
  - Observed behavior: when the `baseurl` property is missing from the client request, the method defaults to `http://localhost:8080`, aliasing the unspecified base URL to a fixed host and port.
  - Classification: B
  - Notes: runtime behavior depends on this literal default instead of a configurable endpoint, so Phase 2.8 should surface it in documentation or configuration.

- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
  - Literal: `"spec-old"`
  - Observed behavior: HTTP paths starting with `spec-old` are rewritten to use the same `spec` path handling, so the legacy prefix aliases directly to the canonical spec component/service/operation without configuration.
  - Classification: B
  - Notes: legacy URLs remain hard-coded in runtime routing, so Phase 2.8 hygiene should document the alias or move it to configuration.

- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
  - Literal: `"current"`
  - Observed behavior: the `current/<operation>` path is treated as an alias for `spec/current/<operation>`, dispatching to the same spec export even though no configuration defines the short form.
  - Classification: B
  - Notes: the resolver hard-codes this shorthand, which should be visible in Phase 2.8 inventory before any change.

- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (method `_resolve_spec_route`)
  - Literal: `"openapi.html"`
  - Observed behavior: `openapi`, `openapi.json`, and `openapi.html` are collapsed onto the single `openapi` operation inside the spec component, effectively aliasing multiple URL variations to the same handler.
  - Classification: B
  - Notes: the resolver maps additional literal strings to `openapi`, so document the alias to prevent confusion during Phase 2.8 rework.

- Location: `org/goldenport/cncf/cli/CncfRuntime.scala` (method `_parse_client_command`)
  - Literal: `"http"`
  - Observed behavior: the client parser treats a literal `http` token specially by parsing an explicit HTTP operation/path pair instead of treating it as a generic selector; the branch enforces HTTP semantics for the following arguments.
  - Classification: C
  - Notes: convenience shortcut for client HTTP requests; behavior is built into CLI parsing rather than resolver configuration.

- Location: `org/goldenport/cncf/dsl/script/ScriptDslSpec.scala` (test `"execute with implicit DEFAULT RUN when args contains only user arguments"`)
  - Literal: `"SCRIPT" "DEFAULT" "RUN"`
  - Observed behavior: the DSL test demonstrates that supplying only user arguments causes the runtime to implicitly assign the script component/service/operation identifiers `SCRIPT`, `DEFAULT`, and `RUN` before invoking the script, making the canonical script selector a fixed alias.
  - Classification: B
  - Notes: the DSL convenience defaults appear directly in the test harness, so Phase 2.8 should treat the trio as an alias for the script selector.

- Location: `org/goldenport/cncf/subsystem/SubsystemFactory.scala` (method `_resolve_http_driver`)
  - Literal: `"real"`
  - Observed behavior: when `cncf.http.driver` is missing or unspecified via system properties, the factory defaults to the literal `"real"`, creating a `UrlConnectionHttpDriver` even though no configuration key supplies that value.
  - Classification: B
  - Notes: the hard-coded `"real"` name effectively aliases the absence of a driver configuration to the URL connection driver, so Phase 2.8 documentation should mention it.

- Location: `org/goldenport/cncf/subsystem/SubsystemFactory.scala` (method `defaultWithScope`)
  - Literal: `"command"`
  - Observed behavior: subsystem creation defaults the runtime mode label to `"command"` when no mode argument is provided, so `PingRuntime.systemContext` and related context metadata embed that literal without configuration.
  - Classification: B
  - Notes: the fallback mode ties runtime metadata to a fixed string, so Phase 2.8 hygiene should surface it as an alias for unspecified modes.

- Location: `org/goldenport/cncf/subsystem/Subsystem.scala` (object `Config`, method `from`)
  - Literal: `"normal"`
  - Observed behavior: when the `cncf.subsystem.mode` configuration entry is absent, the resolver injects the literal `"normal"` before converting it to a `RunMode`, effectively aliasing missing user input to a concrete mode.
  - Classification: B
  - Notes: this default is implemented in code rather than configuration normalization, so Phase 2.8 inventory should capture the alias semantics.
