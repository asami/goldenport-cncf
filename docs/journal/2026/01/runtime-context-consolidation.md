# Design Memo: RuntimeMetadata Removal and Runtime Responsibility Consolidation
status=draft
date=2026-01
scope=cncf-runtime-context
phase=2.8+

## Purpose

This memo records the design decision and rationale for removing `RuntimeMetadata`
and consolidating its responsibilities into `GlobalRuntimeContext`, prior to any
renaming or reduction of `SystemContext`.

The goal is to correct an accumulated structural distortion in the runtime layer
before it becomes permanent.

---

## Background

During Phase 2.x, `RuntimeMetadata` was introduced to support:

- runtime / subsystem identification
- runtime mode
- version reporting
- ping / introspection output

However, subsequent refactoring clarified the execution model:

- `RuntimeContext` now owns *all execution-environment concerns*
- `ScopeContext` represents *hierarchical execution scope*
- `ExecutionContext` represents *execution state*, not environment
- `SystemContext` is effectively a *read-only config snapshot carrier*

As a result, `RuntimeMetadata` no longer represents a first-class concept.

---

## Observations

### 1. RuntimeMetadata is stateless and derivative

`RuntimeMetadata`:

- holds no runtime state
- derives values from `RuntimeConfig` and `SystemContext`
- formats strings for ping/introspection

It does not participate in execution, scoping, or lifecycle.

This makes it a *projection helper*, not a core abstraction.

---

### 2. GlobalRuntimeContext already owns the correct responsibility domain

After recent refactoring, `GlobalRuntimeContext` (via `RuntimeContext`) owns:

- runtime mode
- httpDriver
- observability hooks
- runtime-level execution identity

Extending it to include:

- runtime name
- runtime version
- subsystem name
- subsystem version
- ping formatting

is a natural and responsibility-aligned evolution.

---

### 3. SystemContext is not the right home for runtime identity

`SystemContext` currently acts as:

- an immutable carrier of `configSnapshot`
- a value passed through `ExecutionContext`

It behaves like a cache or snapshot, not a runtime authority.

Storing runtime identity there causes:

- duplication of meaning
- unclear ownership
- dependency inversion (runtime described by a snapshot)

Therefore, SystemContext should *not* be extended further.
Its reduction or renaming should occur **after** RuntimeMetadata is removed.

---

## Decision

### Decision A: Remove RuntimeMetadata

`RuntimeMetadata` will be fully removed.

All responsibilities will be transferred to `GlobalRuntimeContext`.

---

### Decision B: Move runtime introspection to GlobalRuntimeContext

`GlobalRuntimeContext` will become the single authoritative source for:

- runtime identity
- runtime mode
- runtime version
- subsystem identity
- subsystem version
- ping / introspection output

Ping responses will be generated directly from the runtime context,
not via `SystemContext` or metadata adapters.

---

### Decision C: Defer SystemContext restructuring

`SystemContext` will remain unchanged during this step.

It will continue to act as:

- an immutable config snapshot
- an execution-level attachment

Any renaming or shrinking of `SystemContext` will occur *after*
`RuntimeMetadata` has been eliminated.

---

## Intended End State

### Before

```
RuntimeConfig
  → RuntimeMetadata
    → SystemContext (configSnapshot)
      → Ping / introspection
```

### After

```
GlobalRuntimeContext
  → Ping / introspection

SystemContext
  → config snapshot only
```

---

## Non-Goals

- No change to resolver or path semantics
- No change to config key definitions
- No change to subsystem/component boundaries
- No change to execution semantics

This is a *structural correction*, not a feature addition.

---

## Rationale Summary

- Runtime identity belongs to the runtime
- Execution context should not guess its environment
- Snapshots should not define authority
- Removing intermediate abstractions reduces AI and human cognitive load

This change is expected to reduce long-term complexity,
especially under AI-assisted development.

---

## Follow-up (separate memo)

After this change stabilizes:

- Re-evaluate `SystemContext` naming and scope
- Consider renaming to `ConfigSnapshotContext`
- Consider eliminating runtime keys from config snapshots entirely

These steps are intentionally deferred.
