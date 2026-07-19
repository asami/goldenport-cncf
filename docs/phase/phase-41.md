# Phase 41 - Persistent View Source Completeness

Stage Status:
- Current status: CLOSED
- Current step: Complete
- Owner: CNCF aggregate/view read-side bootstrap.

status = closed

## 1. Purpose

Ensure an aggregate-backed View reads the complete canonical persistent entity
set when a process-local resident set contains only a partial subset.

## 2. Scope

- Prefer datastore search and count results for persistent View sources.
- Use resident source entities only when persistent access is unavailable.
- Keep aggregate/view registration and query contracts unchanged.
- Add a regression where two persisted entities coexist with one resident
  entity and both search and count return the complete persisted set.

## 3. Boundaries

- This phase does not introduce a persistent materialized View store.
- It does not change entity persistence, View projection, or authorization.
- Resident-only and nonpersistent component behavior remains supported through
  the existing fallback.

## 4. Completion Evidence

- The aggregate/view bootstrap specification covers partial resident state
  against a complete persistent datastore and keeps an empty persistent result
  authoritative over stale resident state.
- Focused aggregate/view and runtime-plan validation passed 10 tests.
- The full CNCF suite passed 2,032 tests with no failures.
