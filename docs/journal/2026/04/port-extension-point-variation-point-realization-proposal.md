# Port / ExtensionPoint / VariationPoint Realization Proposal

status=draft
created_at=2026-04-04
tag=cncf, component-model, port, extension-point, variation-point, wiring, metamodel

---

# Purpose

This note records the current realization proposal derived from the
follow-up discussion on:

- `Port`
- `ExtensionPoint`
- `VariationPoint`

The earlier note remains as the source idea:

- `port-extension-point-variation-point-design-note.md`

This document is a refinement proposal and must be read as an additive
clarification, not as a replacement of the earlier note.

---

# Design Direction

The earlier note treated `Port` too much like a runtime invocation boundary.
The current conclusion is different.

`Port` should be treated as a **wiring-time architectural concept**.

It is not the canonical runtime execution boundary.

The canonical execution boundary in CNCF remains:

- `OperationCall`

Therefore:

- `Port` is used to support service/interface resolution during wiring
- `ExtensionPoint` is used to bind a resolved interface to a concrete SPI/provider
- `VariationPoint` is used to expose and inject variation settings from outside the Component

This keeps the existing CNCF execution architecture intact while making
pluggable backend binding explicit.

---

# Architectural Position

The recommended layering is:

1. `Component` exposes capabilities
2. `OperationCall` remains the stable execution boundary
3. `Port` participates in wiring/assembly
4. `VariationPoint` exposes current variation and accepts injected variation settings
5. `ExtensionPoint` binds the resolved interface to a provider/SPI
6. injected service/provider is used by `OperationCall`
7. `Engine` continues to apply runtime policy around execution

This means `Port` is not a replacement for `OperationCall`.

---

# Core Interpretation

## Port

`Port` is a wiring-time resolution boundary.

Its role is:

- accept identifying input such as name/capability/service/operation information
- resolve that input into an interface/trait contract
- provide a stable contract used for wiring

In short:

- input side: requirement resolution
- output side: interface contract for wiring

`Port` does not directly represent a concrete implementation.
It resolves the required abstract contract.

## ExtensionPoint

`ExtensionPoint` is the SPI/provider binding boundary.

Its role is:

- accept the resolved interface contract
- determine whether it can provide an implementation
- create or provide the concrete SPI/provider used by the component runtime

This is the place where backend-specific construction belongs.

It is also the canonical place where CNCF supports **adapter realization**.
A resolved abstract service contract is bound here to a concrete backend
adapter/provider implementation.

## VariationPoint

`VariationPoint` is the variation exposure/injection boundary.

Its role is:

- expose the current variation state outside the Component
- accept injected variation settings from configuration or control surfaces
- normalize that variation state for binding

This includes examples such as:

- provider selection
- local / remote mode
- backend engine selection
- fallback preference

The critical point is that `VariationPoint` is configuration-facing.
It is not primarily a runtime invocation API.

---

# UML Metamodel Interpretation

From a UML/metamodel perspective, `Port` should not be modeled as a single
flat runtime interface with all responsibilities attached directly.

Instead, it should be modeled as a central concept with three owned facets:

- API facet
- SPI facet
- Variation facet

Recommended conceptual shape:

```text
Port
 ├─ api : PortApi
 ├─ spi : PortSpi
 └─ variation : PortVariationPoint
```

This means:

- `Port` is the architectural concept
- `PortApi` is the required-side resolution contract
- `PortSpi` is the provided-side binding contract
- `PortVariationPoint` is the configuration/selection facet

This is closer to a structured architectural port definition than to a
minimal runtime callable interface.

---

# Why a Faceted Port Model Fits Better

If all responsibilities are flattened into one type such as:

- `resolve`
- `provide`
- `currentVariation`
- `injectVariation`

then the model mixes:

- requirement-side concerns
- provider-side concerns
- external configuration concerns

That weakens the architecture.

A faceted model preserves clear separation while keeping a single central
concept named `Port`.

This also makes the metamodel easier to expose in documentation and
introspection later.

---

# Proposed Responsibilities by Facet

## PortApi

Responsibilities:

- resolve a named or typed requirement
- return the interface/trait contract used for wiring

Typical meaning:

- `name -> interface`
- `capability -> trait`
- `service/operation selector -> required contract`

## PortSpi

Responsibilities:

- own or reference available extension points
- bind a resolved interface to a concrete provider/SPI
- realize adapters for resolved service contracts
- produce the injected implementation used by the component runtime

Typical meaning:

- `interface -> concrete provider`
- `abstract service contract -> concrete adapter`

## PortVariationPoint

Responsibilities:

- expose current variation state
- accept variation setting injection from external sources
- normalize variation state for SPI binding

Typical meaning:

- `configuration/environment/control input -> normalized variation selection`

---

# Runtime Consequence

This model preserves the CNCF execution boundary.

Execution still happens through:

- `OperationCall`
- `Engine`

The new concepts only make wiring explicit.

The runtime sequence should be interpreted as:

1. startup/bootstrap determines the required service contracts through `PortApi`
2. `VariationPoint` exposes or accepts variation settings
3. `PortSpi` / `ExtensionPoint` bind the resolved contracts to concrete providers
4. injected providers are stored in the component/runtime assembly
5. `OperationCall` uses the injected provider during execution
6. `Engine` applies runtime policy as usual

This avoids introducing a second execution boundary.

---

# Recommended Type Direction

A runtime-shaped `Port[-In, +Out] { def invoke(...) }` is not the best fit for
this clarified design.

The preferred direction is to model `Port` as a definition/contract object.

Conceptual examples:

```scala
final case class ServiceContract[S](
  name: String,
  runtimeClass: Class[? <: S]
)
```

```scala
trait PortApi[-Req, S] {
  def resolve(req: Req): Consequence[ServiceContract[S]]
}
```

```scala
trait ExtensionPoint[S] {
  def supports(contract: ServiceContract[S], variation: VariationSelection): Boolean
  def provide(contract: ServiceContract[S], variation: VariationSelection): Consequence[S]
}
```

```scala
trait PortVariationPoint[-Req] {
  def current(req: Req): Consequence[VariationSelection]
  def inject(req: Req, selection: VariationSelection): Consequence[Req]
}
```

```scala
final case class PortDefinition[Req, S](
  name: String,
  api: PortApi[Req, S],
  spi: Vector[ExtensionPoint[S]],
  variation: PortVariationPoint[Req]
)
```

These examples are illustrative only.
The exact API can evolve.
The important point is the role split.

---

# Fit with Existing CNCF Patterns

This proposal aligns with existing CNCF provider/bootstrap patterns.

Relevant precedents already exist:

- `ComponentFactory`
- `CollaboratorFactory`
- `CollectionTransitionRuleProvider`
- `CollectionStateMachinePlannerProvider`

These show that CNCF already uses:

- optional provider surfaces on components
- factory-time bootstrap wiring
- registry/provider style assembly

The new proposal should extend that style rather than introduce a competing
runtime model.

In practice:

- `Port` belongs to wiring-time assembly
- `ExtensionPoint` follows existing provider/factory patterns
- `VariationPoint` becomes the configuration/introspection-facing control point

---

# First Realization Target

The first target should be narrow and concrete.

Recommended first target:

- AI / collaborator integration

Examples:

- `GeneratePort`
- `ChatPort`
- `GenerateService`
- `ChatService`
- `GemmaOllama...ExtensionPoint`
- remote HTTP-backed extension points
- variation settings such as provider/mode/engine

This gives a useful vertical slice without forcing the whole framework to
adopt the abstraction everywhere at once.

---

# Non-Goals

This proposal does not mean:

- replacing `OperationCall`
- replacing `Engine`
- exposing raw backend providers directly to domain logic
- allowing business logic to branch on backend implementation
- introducing a second execution model beside the current CNCF runtime path

It also does not require every existing subsystem to be rewritten around
`Port` immediately.

---

# Open Questions

- Should the canonical top-level name be `Port` or `PortDefinition`?
- Should `PortVariationPoint` only expose/inject variation, or also normalize defaults?
- Should `ExtensionPoint` create providers per request or cache them at bootstrap time?
- Which introspection surface should expose current variation state first?
  - component metadata
  - meta API
  - admin API
- How should variation injection be prioritized among:
  - static config
  - environment
  - CLI override
  - runtime admin override

---

# Current Recommendation

The current recommendation is:

1. treat `Port` as a wiring-time architectural concept
2. model it as a concept with three facets:
   - API
   - SPI
   - VariationPoint
3. keep execution semantics on `OperationCall` / `Engine`
4. realize the first implementation in AI/collaborator integration only
5. promote this to a design document only after one concrete vertical slice is validated

