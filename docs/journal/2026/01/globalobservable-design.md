---
title: GlobalObservable DSL Design
---

# Purpose & Placement

GlobalObservable is the lightweight mixin DSL that sits on top of the previously defined `GlobalObservability` entry point. Its goal is to let execution-context-less logic (bootstrappers, wiring code, loaders) emit observability events without requiring each caller to import `GlobalObservability` or reach for SLF4J directly. It reuses the same filters, engine, and backends through GlobalObservability, so the DSL merely supplies convenient syntax while inheriting the centralized configuration from `globalobservability-design.md`.

# Relationship to GlobalObservability

- `GlobalObservable` is pure syntax sugar: trait helpers simply call into `GlobalObservability` behind the scenes.
- `GlobalObservability` remains the JVM-global entry point described earlier. All policy (filters from `runtimeprotocol-config-integration.md`, injected config-aware properties, CLI knobs) is resolved there; the DSL merely offloads metadata derivation to each implementer and forwards events to the global engine.

# Trait Shape & Metadata

- The trait exposes `observe_error`, `observe_warn`, `observe_info`, `observe_debug`, and `observe_trace` helpers (method names mirror CNCF naming conventions). Each helper accepts either a `ScopeContext` or falls back to `ScopeContext.Subsystem`.
- `observe_scope` defaults to the subsystem level; traits can override it to tighten the scope to a `Component` or `Operation` before delegating to `GlobalObservability`.
- Class/package metadata is derived from `this.getClass` so filtering always sees the implementation vocabulary without manual annotation.
- Callers are expected to mix the trait into singleton objects or components that need to log before their `ExecutionContext` exists.

# Usage Patterns

1. **ExecutionContext-less logic**: BSP wiring, CLI bootstrap, component loaders can extend `GlobalObservable`, call `observe_info`, and the DSL automatically uses the shared filtering pipeline described in `runtimeprotocol-config-integration.md`.
2. **Coexistence with ExecutionContext-based observability**: DSL calls resolve to the same `ObservabilityEngine` that context-bound code uses; there is no conflict or duplicate instrumentation.

# Invariants & Constraints

- DSL callers do not call SLF4J directly; all logging routes through `GlobalObservability`.
- Filtering decisions remain centralized; the DSL neither inspects `--log-*` flags nor bypasses the engine’s `VisibilityPolicy`.
- The DSL adds no business semantics—its helpers simply orchestrate metadata capture and delegation to the engine.

# References

- `globalobservability-design.md` (defines the entry point and relationships)
- `runtimeprotocol-config-integration.md` (shows how config feeding respects Stage 1)
- `protocolengine-runtime-config-injection.md` (shows how injected properties reach the engine and why CLI > config > defaults still holds)

# Rationale

Documenting the DSL only after GlobalObservability is settled avoids ambiguity about where policy lives. The entry point must be stable before we can confidently describe syntax sugar on top of it; this document now completes the reference story so DSL users know they are ultimately channeling events through the canonical JVM-global observability stack.
