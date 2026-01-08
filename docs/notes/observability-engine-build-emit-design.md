# ObservabilityEngine Design — Separation of Build and Emit

status = draft
since = 2026-01-08

## Purpose

This document defines a strict separation of responsibilities inside
`ObservabilityEngine` between **semantic construction** and **side-effectful emission**.

The goals are to:

- Preserve deterministic execution logic
- Enable rich observability without polluting control flow
- Support multiple output channels (logs, events, OpenTelemetry, AI/RAG)

---

## Two-Phase Observability Model

Observability in CNCF is explicitly modeled as two independent phases:

1. **Build phase** — semantic meaning construction
2. **Emit phase** — presentation and side effects

These phases must never be conflated.

---

## Build Phase (Semantic, Pure)

### Responsibility

`ObservabilityEngine.build` is responsible for:

- Constructing a **semantic observability record**
- Merging information from execution contexts
- Producing a **pure, immutable Record**

### Characteristics

- Pure function (no side effects)
- No logging
- No I/O
- No persistence
- No branching logic

Conceptually:

```scala
def build(...): Record
```

### Usage

Build is called at canonical completion points:

- Action completion
- Failure capture
- Boundary transitions

Build does not emit; it only constructs meaning.

---

## Emit Phase (Presentation / Side Effects)

### Responsibility

`ObservabilityEngine.emit*` methods are responsible for:

- Sending observability records to output channels
- Applying presentation formatting
- Performing logging or export

### Characteristics

- Side-effectful
- May log to stdout/stderr/SLF4J
- May publish to events or exporters (future)
- Does not participate in logic flow

Conceptually:

```scala
def emitInfo(...): Unit
```

Emit does not construct meaning; it only presents it.

---

## Canonical Rule

**Build is semantic. Emit is side-effectful.**

If an operation must influence control flow, it belongs to logic
or Conclusion handling — not to ObservabilityEngine.emit.

---

## Summary

- `build` constructs semantic meaning
- `emit` outputs or exports
- Mixing build and emit violates the architecture

This separation ensures observability remains explainable,
composable, and safe to extend.

---

See also:
- scope-context-design.md
- observability-record-namespace.md

END OF DOCUMENT
