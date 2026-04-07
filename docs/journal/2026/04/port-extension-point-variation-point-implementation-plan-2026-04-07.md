# Port / ExtensionPoint / VariationPoint Implementation Plan (2026-04-07)

status=draft
created_at=2026-04-07
tag=cncf, implementation-plan, port, extension-point, variation-point, binding

---

# Purpose

This note turns the current redesign into an implementation plan for CNCF.

It should be read after:

- `port-extension-point-variation-point-redesign-2026-04-07.md`
- `port-extension-point-variation-point-realization-proposal.md`

The goal is to move from the currently implemented runtime-shaped skeleton to a
wiring-oriented model without breaking the canonical CNCF execution boundary.

---

# Current Implementation Baseline

Current implementation exists in:

- `src/main/scala/org/goldenport/cncf/component/Port.scala`
- `src/main/scala/org/goldenport/cncf/component/Component.scala`

Current shapes are effectively:

- `Port[-In, +Out] { invoke(...) }`
- `ExtensionPoint[In, Out] { supports(...); create(...) }`
- `VariationPoint[In, Out] { select(...) }`
- `Component.Binding(variationPoint)`

This baseline is now considered transitional.

---

# Implementation Strategy

## Keep

Keep these principles:

- `Component` owns wiring/assembly
- `OperationCall` remains the canonical execution boundary
- `Engine` remains the runtime policy/execution coordinator
- `Component.Port` service registry stays available for injected services

## Replace

Replace these assumptions:

- `Port` is not a runtime invocation boundary
- `ExtensionPoint` should not return a `Port` that is then invoked
- `VariationPoint` should not be only a route selector
- `Component.Binding` should not be only a thin wrapper over `VariationPoint`

---

# Target Model for Phase 1

Phase 1 should introduce a wiring-safe minimum model.

## 1. PortApi

Introduce a new resolution-side abstraction.

Purpose:

- accept a requirement
- resolve it into a service contract used for wiring

Conceptual direction:

```scala
trait PortApi[-Req, S] {
  def resolve(req: Req): Consequence[ServiceContract[S]]
}
```

## 2. ServiceContract

Introduce an explicit service contract type.

Purpose:

- represent the resolved trait/interface used for wiring
- avoid using raw runtime invocation ports as the public abstraction

Conceptual direction:

```scala
final case class ServiceContract[S](
  name: String,
  runtimeClass: Class[? <: S]
)
```

## 3. VariationSelection

Introduce an explicit variation value object.

Purpose:

- normalize the current variation state
- separate variation state from the requirement itself

Conceptual direction:

```scala
final case class VariationSelection(
  provider: Option[String],
  mode: Option[String],
  engine: Option[String]
)
```

## 4. PortVariationPoint

Redefine variation handling as exposure/injection.

Purpose:

- expose current variation state
- accept injected variation state
- normalize variation before SPI binding

Conceptual direction:

```scala
trait PortVariationPoint[-Req] {
  def current(req: Req)(using ExecutionContext): Consequence[VariationSelection]
  def inject(req: Req, selection: VariationSelection)(using ExecutionContext): Consequence[Req]
}
```

## 5. ExtensionPoint

Redefine extension points as adapter realization boundaries.

Purpose:

- inspect a resolved service contract and variation selection
- determine compatibility
- provide a concrete service/provider/adapter implementation

Conceptual direction:

```scala
trait ExtensionPoint[S] {
  def supports(
    contract: ServiceContract[S],
    variation: VariationSelection
  )(using ExecutionContext): Boolean

  def provide(
    contract: ServiceContract[S],
    variation: VariationSelection
  )(using ExecutionContext): Consequence[S]
}
```

## 6. Binding

Redefine `Component.Binding` into a richer binding record.

Minimum Phase 1 purpose:

- hold `PortApi`
- hold `PortVariationPoint`
- hold available extension points
- resolve the injected service/provider

Conceptual direction:

```scala
final case class Binding[Req, S](
  api: PortApi[Req, S],
  variation: PortVariationPoint[Req],
  extensions: Vector[ExtensionPoint[S]]
)
```

With a helper such as:

```scala
  def bind(req: Req)(using ExecutionContext): Consequence[S]
```

This helper performs:

1. resolve contract
2. obtain variation state
3. find supporting extension point
4. provide injected service/provider

---

# Compatibility Strategy

Phase 1 should avoid a wide disruptive refactor.

## Temporary Coexistence

The following coexistence is acceptable during migration:

- existing `Component.Port` service registry remains unchanged
- new binding model resolves concrete services and stores them in `Component.Port`
- old runtime-shaped `Port` trait may remain temporarily but should be marked transitional

## Immediate Deprecation Candidate

The file:

- `src/main/scala/org/goldenport/cncf/component/Port.scala`

should be treated as transitional and scheduled for replacement.

The important rule is:

- new integrations must target the new model
- old model must not be expanded further

---

# Recommended File-Level Steps

## Step 1

Add new component-side model files:

- `ServiceContract.scala`
- `PortApi.scala`
- `PortVariationPoint.scala`
- `VariationSelection.scala`

These can live under:

- `src/main/scala/org/goldenport/cncf/component/`

## Step 2

Refactor `ExtensionPoint` into the adapter/provider shape.

Either:

- replace the current `ExtensionPoint` definition directly

or:

- introduce a new type first and migrate callers before removal

## Step 3

Refactor `Component.Binding`.

- stop resolving by `variationPoint.select(...).create(...).invoke(...)`
- replace with binding-time provider resolution

## Step 4

Keep `Component.Port` as the injected service registry.

That means:

- bound provider/service instances are stored there
- operation logic consumes services from `Component.Port`
- `Binding` remains part of assembly, not execution

## Step 5

Add one focused vertical slice as validation.

Recommended validation target:

- AI / collaborator integration

But the first implementation may remain framework-only until consumer code is ready.

---

# Executable Specification Plan

Before broad integration, add focused specs for the new semantics.

## Spec 1: PortApi resolves service contract

Given:

- a named requirement

When:

- `PortApi.resolve(...)` is called

Then:

- the expected `ServiceContract` is returned

## Spec 2: VariationPoint exposes current variation

Given:

- a component binding with a configured variation state

When:

- `current(...)` is called

Then:

- normalized variation is returned

## Spec 3: VariationPoint injects new variation

Given:

- an initial requirement/config pair

When:

- `inject(...)` is called

Then:

- the requirement/config used for binding reflects the requested variation

## Spec 4: Binding resolves service through ExtensionPoint

Given:

- a resolved service contract
- a variation selection
- multiple extension points

When:

- binding is performed

Then:

- the first compatible extension point provides the injected service

## Spec 5: Binding failure is explicit

Given:

- no compatible extension point

When:

- binding is performed

Then:

- `Consequence.Failure` is returned

## Spec 6: OperationCall remains separate

Given:

- a component with an already injected service in `Component.Port`

When:

- operation logic executes

Then:

- it uses the injected service without treating `Port` as a runtime invocation surface

---

# Non-Goals for Phase 1

Do not do the following in Phase 1:

- redesign `OperationCall`
- redesign `Engine`
- migrate every existing component immediately
- add effect-system abstraction beyond `Consequence`
- add full admin/meta variation API surfaces yet
- remove `Component.Port` registry

---

# Recommended Immediate Next Move

The immediate next move in CNCF should be:

1. add the new types (`ServiceContract`, `PortApi`, `VariationSelection`, `PortVariationPoint`)
2. refactor `Binding` into a real wiring record
3. leave the old runtime-shaped `Port` trait as transitional for one migration step only
4. add executable specs before integrating a real consumer

