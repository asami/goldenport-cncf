---
title: Observability Filter & Scope Hygiene
---

# Inventory Summary

| Type | Package | Purpose | Constructed by | Consumed by |
|------|---------|---------|----------------|-------------|
| `ScopeContext` / `ScopeKind` | `org.goldenport.cncf.context` | Canonical runtime hierarchy (Runtime/Subsystem/Component/etc.) plus observability metadata | `ExecutionContext`, `GlobalRuntimeContext`, DSL helpers | `ObservabilityContext`, `CncfRuntime`, `ObservabilityEngine`, DSLs |
| `ObservabilityEngine` | `org.goldenport.cncf.observability` | Emits trace/info/warn/error records through `LogBackendHolder` | any runtime actor with `ObservabilityContext` | `ObservabilityContext`, existing `ObservationDsl` and new `GlobalObservability` |
| `ObservabilityContext` | `org.goldenport.cncf.context` | Carries trace/correlation IDs for events | `ExecutionContext` | `ObservabilityEngine`, `ScopeContext` |
| `ObservabilityScopeDefaults` | `org.goldenport.cncf.observability.global` | PoC helper providing a single `ScopeContext` for GlobalObservability when no context exists | Static initialization in `ObservabilityScopeDefaults` | `GlobalObservability` entry point |
| `GlobalObservabilityGate` (new) | `org.goldenport.cncf.observability.global` | JVM-global entry gate that currently performs no visibility decisions | `CncfRuntime` bootstrap | `GlobalObservability.observe*` |
| `GlobalObservability` / `GlobalObservable` | `org.goldenport.cncf.observability.global` | JVM-global entry and DSL wrapping `ScopeContext`/`ObservabilityEngine` | `CncfRuntime`, any bootstrapper | Application code needing trace events outside `ExecutionContext` |


# Issues & Ambiguities

1. **Parallel scope hierarchies.** `ScopeContext` is the canonical CNCF runtime hierarchy, while `ObservabilityScopeDefaults` introduces another `ScopeContext.Instance` for the “global” scope. This risks callers referencing the wrong scope instance and undermines the idea that `ScopeContext` is the single authoritative descriptor.
2. **Gate placement.** The only gate that exists is the new `GlobalObservabilityGate` attached to `GlobalObservability`, but the `ObservabilityEngine` (the real producer of log calls) still controls visibility policy. This leaves future flow decisions split between the gate and the engine’s yet-to-be-plumbed `VisibilityPolicy`.
3. **Naming drift.** `VisibilityPolicy` lives adjacent to the engine but currently cannot be referenced from the global gate, so auditors might question where CLI-configured policies belong.


# Canonical Model

1. **Single scope abstraction:** `ScopeContext` (plus `ScopeKind`) remains the canonical scope description for CNCF observability. `ObservabilityScopeDefaults` should be treated as a convenience factory—either renamed to `ObservabilityScopeDefaults` (already done) or collapsed into `GlobalObservability`—but should not introduce a new hierarchy. All public APIs (including `GlobalObservability.observe*`) must accept `ScopeContext` only.
2. **Single visibility policy:** `VisibilityPolicy` represents the global policy surface and should be the only visibility type. If future scope-based filtering is required in `ObservabilityEngine`, the engine should expose the same `VisibilityPolicy` rather than inventing another filter type; the policy’s scope/package/class constraints should stay optional (`Option[Set[String]]`) with AND semantics so defaults remain permissive.
3. **Responsibility split:** `GlobalObservability` is responsible for coordinating with `GlobalObservabilityGate`/`LogBackendHolder` and delegating to `ObservabilityEngine`. It must never duplicate scope logic that already belongs to `ScopeContext` or `ObservabilityContext`.


# Invariants

-- **Public APIs:** only `ScopeContext` (from `org.goldenport.cncf.context`) and `VisibilityPolicy` should appear in public interfaces. `ObservabilityScopeDefaults` can remain internal or be renamed to reinforce its helper status.
- **Internal-only:** the new factory scope object is internal; consumers should never treat it as a root `ScopeContext` substitute except via `GlobalObservability`.
- **GlobalObservability ↔ ScopeContext:** GlobalObservability must continue to accept a `ScopeContext` argument; downstream consumers (like `GlobalObservable` or other bootstrappers) should derive the necessary `ScopeContext` from existing runtime state or fall back to `ScopeContext.Subsystem`. No new scope types should be introduced.


# Migration Guidance

- Reference `globalobservability-design.md` and `globalobservable-design.md` when making future observability changes to ensure the canonical scope/filter model is respected.
- Once the guardrail is accepted, consider folding `ObservabilityScopeDefaults` into `GlobalObservability` so that `ScopeContext` remains the sole scope domain.
- Avoid duplicating visibility logic; if additional surfaces need to reuse `VisibilityPolicy`, expose it through the core `ObservabilityEngine` rather than defining new ad-hoc selectors.
