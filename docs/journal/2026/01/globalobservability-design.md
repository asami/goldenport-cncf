---
title: GlobalObservability Design
---

# Motivation

Phase 2.85 introduces `GlobalObservable` as an internal DSL, but the DSL must build on a well-defined *entry point* that CNCF services and components can reach even when no `ExecutionContext` is available. This journal captures the canonical `GlobalObservability` design before any DSL semantics are introduced, ensuring the public narrative stays consistent: GlobalObservability orchestrates JVM-global observability, reuses the same engine/filter/backend as `ExecutionContext`, and remains an *entry point* rather than a logger or business API.

# Purpose & Role

- **Purpose:** Provide a JVM-global observability entry that can be called from bootstrap code, static helpers, or CLI-driven logic that has not yet created an `ExecutionContext`.
- **Role:** Share the existing `ObservabilityEngine`, `GlobalObservabilityGate`, and `LogBackend` so every path ultimately relies on the same tracing/level/visibility policy plumbing as scoped executions.
- **Responsibilities:**
  1. Initialize once per JVM (typically during CLI bootstrap) and retain the canonical `ObservabilityRoot`.
  2. Delegate every call to `ObservabilityEngine` / `ObservabilityRoot` so that instrumentation flows through the same execution-centric pipeline.
  3. Accept caller metadata: both `ScopeContext` (Subsystem / Component / Operation vocabulary) and implementation metadata (package/class) so filtering can consider both CNCF and runtime implementation axes.

# Relationships

- **GlobalObservability ⇒ ObservabilityRoot:** The entry point exposes `observabilityOf(scope, clazz)` helpers that access the shared `ObservabilityRoot` instance. The `ObservabilityRoot` represents the first link in the chain, capturing runtime defaults, log backend selection, and filter wiring.
- **ObservabilityRoot ⇒ ObservabilityEngine:** The root forwards events (trace/log) to `ObservabilityEngine`, which houses the shared log/filter/backends and provides the APIs used throughout the codebase.
- **Visibility policy:** GlobalObservability relies on the engine’s visibility policy (eventually driven by CLI/config) to apply scope + implementation metadata before delegating to the backend.
- **LogBackend:** GlobalObservability never instantiates its own backend; it consults the engine’s configured `LogBackend` (Console, SLF4J, etc.) so delegation remains consistent.
- **Entry Point Clarification:** GlobalObservability is *not* a logger; it is an entry point that locates the shared instrumentation stack and emits events via `ObservabilityEngine`. Consumers still call `trace`, `info`, etc., but GlobalObservability only supplies the `ObservabilityContext`.

# Filtering Vocabularies

- **CNCF Vocabulary:** ScopeContext carries `ScopeKind` (Subsystem/Component/Operation) and canonical identifiers; GlobalObservability exposes filters that can include or exclude subsystems/components/operations.
- **Implementation Vocabulary:** Callers can supply the current `Class` or package string to the entry point; the same filters that apply to scoped runtimes also inspect this metadata.
- **Combined semantics:** Both axes are combined with AND semantics, meaning a log entry is emitted only if it passes both the CNCF scope filter and the implementation filter. This enforces consistent filtering irrespective of invocation context.

# CLI & Config Hooks

Runtime CLI/config knobs are the single point for reconfiguring GlobalObservability:

- `--log-level`: adjusts the root log level used by the shared engine.
- `--log-component` / `--log-operation`: tune the CNCF ScopeContext filter, narrowing which subsystems/operations are emitted.
- `--log-package` / `--log-class`: tune the implementation filter, allowing backend/package-level focus.

All decisions remain centralized inside GlobalObservability / ObservabilityEngine; call sites merely provide metadata and never inspect CLI flags themselves.

# What It Is Not

- Not a replacement for slf4j: backend still determines the actual logging API.
- Not a per-class logger: it does not hold a `Logger` instance per class; it resolves context metadata and forwards events to the engine.
- Not CNCF business logic: it should never contain domain behavior; it merely establishes instrumentation context for other layers.

# Why Document First

The GlobalObservability entry point is the prerequisite conceptual anchor for the forthcoming `GlobalObservable` DSL. Without this foundation, the DSL would feel ad hoc; by documenting GlobalObservability first we make explicit how the JVM-global entry relates to the engine, filters, backends, and CLI configuration. Only once this relationship is stable should we extend the API with the DSL helpers that syntax-sugar the same pipeline.
