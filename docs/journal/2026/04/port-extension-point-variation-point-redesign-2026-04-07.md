# Port / ExtensionPoint / VariationPoint Redesign (2026-04-07)

status=draft
created_at=2026-04-07
tag=cncf, redesign, port, extension-point, variation-point, wiring, adapter

---

# Purpose

This note replaces the runtime-shaped handoff interpretation of:

- `Port`
- `ExtensionPoint`
- `VariationPoint`

The redesign aligns these concepts with the current CNCF component model and
keeps `OperationCall` as the canonical execution boundary.

This note supersedes:

- `port-extension-point-variation-point-handoff.md`

It should be read together with:

- `port-extension-point-variation-point-design-note.md`
- `port-extension-point-variation-point-realization-proposal.md`
- `docs/design/component-model.md`

---

# Redesign Summary

The prior interpretation was:

- `Port` = invocation boundary
- `ExtensionPoint` = backend-specific port factory
- `VariationPoint` = route selector

This redesign replaces that model with:

- `Port` = wiring-time resolution boundary
- `ExtensionPoint` = adapter realization and SPI binding boundary
- `VariationPoint` = external variation exposure/injection boundary

Execution remains on:

- `OperationCall`
- `Engine`

The new abstractions must not introduce a second runtime execution boundary.

---

# Why the Previous Model Was Wrong

## 1. It treated `Port` as a runtime call surface

A runtime-shaped definition such as:

```scala
trait Port[-In, +Out] {
  def invoke(input: In): Consequence[Out]
}
```

turns `Port` into a second execution abstraction beside `OperationCall`.
That is not acceptable in CNCF.

## 2. It reduced `VariationPoint` to selector logic only

A shape such as:

```scala
trait VariationPoint[In, Out] {
  def select(input: In): ExtensionPoint[In, Out]
}
```

misses the more important role of variation:

- exposing the current variation state to the outside
- accepting injected variation settings from outside the component
- normalizing those settings for binding

## 3. It made `ExtensionPoint` only a port factory

A shape such as:

```scala
trait ExtensionPoint[In, Out] {
  def supports(input: In): Boolean
  def create(input: In): Port[In, Out]
}
```

collapses adapter realization and invocation into one line.
It hides the fact that the real product of extension binding is not a `Port`,
but a concrete service/provider/adapter implementation.

---

# Canonical Interpretation

## Port

`Port` is a wiring-time architectural concept.

Its purpose is:

- accept identifying information such as name, capability, service, or operation
- resolve that information into an interface/trait contract
- provide the contract used for wiring

`Port` is therefore not a concrete implementation and not a runtime call API.

In short:

- input side: requirement resolution
- output side: service interface contract

## ExtensionPoint

`ExtensionPoint` is the adapter realization and SPI binding boundary.

Its purpose is:

- accept a resolved abstract service contract
- inspect current variation state if needed
- determine compatibility
- provide a concrete service/provider/adapter implementation

This is the canonical place where adapter mechanisms belong.

## VariationPoint

`VariationPoint` is the external variation exposure/injection boundary.

Its purpose is:

- expose the current variation state outside the component
- accept injected variation settings from configuration or control surfaces
- normalize the variation state used by binding

This includes examples such as:

- provider selection
- local or remote mode
- backend engine selection
- fallback preference

`VariationPoint` is configuration-facing, not invocation-facing.

---

# UML/Metamodel Direction

The recommended metamodel is not a flat `Port` interface with all methods on it.

Instead, `Port` should be understood as a concept with three facets:

```text
Port
 ├─ api : PortApi
 ├─ spi : PortSpi
 └─ variation : PortVariationPoint
```

Meaning:

- `PortApi` = required-side resolution facet
- `PortSpi` = provided-side binding facet
- `PortVariationPoint` = variation exposure/injection facet

This makes `Port` a structured architectural concept rather than a thin
runtime callable interface.

---

# Execution Placement

The correct execution placement is:

1. `PortApi` resolves the required service contract
2. `VariationPoint` exposes and/or accepts variation settings
3. `ExtensionPoint` realizes the adapter/provider for the resolved contract
4. the resolved provider is injected into component/runtime assembly
5. `OperationCall` uses the injected provider during execution
6. `Engine` continues to apply runtime policy

The injected service/provider executes the capability.
`Port` itself does not execute it.

---

# Adapter Support Rule

`ExtensionPoint` must be treated as the canonical home of adapter support.

That means:

- backend-specific adapter construction belongs there
- provider-specific communication logic is hidden behind there
- component and operation logic should only see the abstract service trait

For AI/collaborator integration, this implies:

- `GenerateService` / `ChatService` are abstract contracts
- Gemma/Ollama or remote HTTP adapters are concrete realizations
- those realizations are provided by `ExtensionPoint`

---

# Binding Model

The previous `Component.Binding` concept was too small if it only carried a
variation point.

The effective binding model needs to account for:

- resolved service contract
- variation state
- available extension points
- injected provider/service instance

So the redesign direction is:

- either replace the previous `Component.Binding` shape
- or expand it into a proper binding record/registry

A single optional slot is unlikely to be sufficient once multiple named ports
exist.

---

# Recommended Type Direction

The implementation should move away from runtime-shaped port types and toward
contract-oriented types.

Illustrative examples only:

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

The key point is not the exact syntax.
The key point is the separation of concerns.

---

# First Concrete Target

The first realization should remain intentionally narrow.

Recommended target:

- AI / collaborator integration

Examples:

- `GeneratePortApi`
- `ChatPortApi`
- `GenerateService`
- `ChatService`
- Gemma/Ollama extension points
- remote adapter extension points
- variation state such as provider/mode/engine

This allows validation without forcing an immediate framework-wide rewrite.

---

# Migration Guidance

Projects or branches that implemented the previous handoff model should change
in this order:

1. remove `invoke` from `Port`
2. stop returning `Port` from `ExtensionPoint.create`
3. redefine `VariationPoint` as exposure/injection boundary
4. introduce explicit service contracts / provider traits
5. move adapter construction into `ExtensionPoint`
6. keep runtime execution on the injected provider used by `OperationCall`

This is especially important for AI integration branches such as `textus-ai`.

---

# Obsoleted Interpretation

The following interpretation is now obsolete:

- `Port` = invocation boundary
- `ExtensionPoint` = backend-specific port factory
- `VariationPoint` = route selector

It should not be used as the basis for new implementation work.

---

# Current Recommendation

The current recommendation is:

1. keep `OperationCall` as the canonical execution boundary
2. treat `Port` as a wiring-time resolution concept
3. treat `ExtensionPoint` as the adapter realization boundary
4. treat `VariationPoint` as the external variation exposure/injection boundary
5. validate the design on AI/collaborator integration first
6. only after that promote the model into stable design/API guidance
