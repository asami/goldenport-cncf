# Phase 2.8 — Infrastructure Hygiene

status = draft
scope = infrastructure cleanup after Phase 2.6

## Purpose

Clean up infrastructural inconsistencies discovered during Phase 2.6
without introducing new features or semantic changes.

## Initial Scope

- Path alias hard-coding → declarative / logical resolution
- Canonical path normalization

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

## Non-Goals

- No semantic changes
- No new features
