# Port / ExtensionPoint / VariationPoint Handoff

status=obsolete
created_at=2026-04-07
tag=cncf, handoff, port, extension-point, variation-point, component-model

---

# Obsolescence Notice

This handoff is now obsolete.

It is superseded by:

- `port-extension-point-variation-point-redesign-2026-04-07.md`

Reason:

- it modeled `Port` as a runtime invocation boundary
- it modeled `VariationPoint` as a route selector only
- it modeled `ExtensionPoint` as a concrete port factory

That structure mixes execution flow and wiring flow.
The current direction treats these concepts as wiring-time architectural
boundaries, while preserving `OperationCall` as the canonical execution
boundary.

---

# Purpose

This note is the handoff summary for the newly added CNCF abstractions:

- `Port`
- `ExtensionPoint`
- `VariationPoint`

It captures the current implementation state, the ownership model, and the
next concrete work items.

---

# Current State

## Implemented in CNCF

The following types now exist in CNCF:

- `org.goldenport.cncf.component.Port`
- `org.goldenport.cncf.component.ExtensionPoint`
- `org.goldenport.cncf.component.VariationPoint`
- `org.goldenport.cncf.component.Component.Binding`

These types are intentionally minimal and synchronous.

## Ownership Model

The intended ownership model is:

- `Component` owns `VariationPoint` and `ExtensionPoint`
- `Port` is the externally visible execution surface
- backend selection and adapter construction remain internal to the component

## Execution Model Placement

The abstractions sit conceptually between:

- `Component`
- `OperationCall`

The current direction is:

1. `Component` resolves binding policy
2. `VariationPoint` chooses the route
3. `ExtensionPoint` materializes the backend-specific port
4. `Port` invokes the backend
5. `OperationCall` remains the engine-facing execution boundary
6. `Engine` applies runtime policy

---

# API Summary

## Port

Purpose:

- represent a typed invocation boundary

Rules:

- must be small
- must not decide routing
- must not expose backend-specific composition details

Current shape:

```scala
trait Port[-In, +Out] {
  def invoke(input: In)(using ExecutionContext): Consequence[Out]
}
```

## ExtensionPoint

Purpose:

- create the concrete port from runtime configuration

Rules:

- must decide whether a backend is supported
- must create the backend-specific port
- must remain internal to the owning component

Current shape:

```scala
trait ExtensionPoint[In, Out] {
  def supports(input: In)(using ExecutionContext): Boolean
  def create(input: In)(using ExecutionContext): Port[In, Out]
}
```

## VariationPoint

Purpose:

- select among alternative execution routes

Rules:

- must support local-first and remote-fallback style routing
- must remain internal to the owning component
- should be driven by a small explicit policy model

Current shape:

```scala
trait VariationPoint[In, Out] {
  def select(input: In)(using ExecutionContext): ExtensionPoint[In, Out]
}
```

## Component.Binding

Purpose:

- store the selected variation policy on the component
- resolve the port from the variation point

Current shape:

```scala
final case class Binding[In, Out](
  variationPoint: VariationPoint[In, Out]
)
```

---

# Practical Interpretation

The current interpretation is:

- `VariationPoint` owns selection policy
- `ExtensionPoint` owns backend binding
- `Port` owns invocation
- `Component` owns the assembly of all three

This is the model to use when wiring AI integrations such as Gemma/Ollama.

---

# What to Do Next

## Immediate Next Step

Connect `Component.Binding` to a real component implementation, starting with
`textus-ai`.

## Follow-Up Steps

1. Teach a component to expose a binding through `Component.withBinding`
2. Replace project-local adapter assembly with framework binding resolution
3. Add `OperationCall` bridge code so execution can invoke the resolved port
4. Add tests for local-first / remote-fallback selection

---

# Open Questions

- Should `VariationPoint` resolve directly to `ExtensionPoint`, or to a policy
  object that later produces the `ExtensionPoint`?
- Should `Port` remain synchronous only, or later support effectful invocation?
- Should `Component.Binding` be a single optional slot or a named registry of
  bindings?

---

# Summary

The new CNCF abstractions are in place as a minimal framework skeleton.

They should be treated as:

- `Port` = invocation boundary
- `ExtensionPoint` = backend materialization boundary
- `VariationPoint` = selection boundary
- `Component` = owner of the binding policy

This is the handoff baseline for further integration work.
