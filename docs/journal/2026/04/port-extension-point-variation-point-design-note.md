# Port, ExtensionPoint, and VariationPoint Design Note

status=draft
created_at=2026-04-04
tag=cncf, component-model, port, extension-point, variation-point, adapter

---

# Overview

This note proposes a CNCF-side abstraction set for extension-aware execution:

- `Port`: a concrete invocation boundary
- `ExtensionPoint`: a binding/factory boundary that creates ports
- `VariationPoint`: a selection boundary that chooses among alternative ports

The goal is to make pluggable execution explicit before binding AI components
and other collaborator-style integrations to specific backends.

The current CNCF documentation already defines:

- `Component`
- `Componentlet`
- `OperationCall`
- `Engine`

It also mentions `ExtensionPoint` in the component/subsystem packaging roadmap.
This note fills in the missing execution-side structure needed to support
Gemma/Ollama-style integrations cleanly.

---

# Current State

## Existing CNCF Model

The current component model defines:

- `Component` as runtime container
- `Componentlet` as domain logic
- `OperationCall` as execution boundary
- `Engine` as execution coordinator

This gives a strong execution model, but the extension layer is still implicit
in many integrations.

## Adapter Need From `textus-ai`

The `textus-ai` work surfaced a concrete need:

- the AI capability surface should stay stable
- provider selection should be configurable
- Gemma should be usable as a local default backend
- the implementation should allow local Ollama first, with remote fallback
- the adapter must stay outside business logic and operation logic

That need suggests CNCF should provide a framework-level way to express:

- backend-specific binding
- backend selection policy
- execution routing
- adapter materialization from runtime configuration

## Existing Gaps

The current model does not yet make these concepts explicit at the framework
level:

- invocation port
- backend binding point
- variation/routing among multiple backends

As a result, integrations often end up encoding backend choice directly in
operation logic or in project-local adapters.

---

# Proposed Model

## Port

`Port` is the smallest invocation boundary.

Responsibilities:

- accept a typed input
- produce a typed output
- isolate execution mechanics from callers

Examples:

- `GeneratePort`
- `ChatPort`

Port is the executable surface used by components and engines.

### Suggested Shape

```scala
trait Port[-In, +Out] {
  def invoke(input: In): Out
}
```

Port should remain intentionally small.
It is a call boundary, not a routing policy.

## ExtensionPoint

`ExtensionPoint` is the factory/binding boundary.

Responsibilities:

- inspect configuration or runtime context
- decide whether a backend is supported
- create the appropriate `Port`

Examples:

- `GemmaPortExtensionPoint`
- `GemmaChatPortExtensionPoint`

### Suggested Shape

```scala
trait ExtensionPoint[In, Out] {
  def supports(input: In): Boolean
  def create(input: In): Port[In, Out]
}
```

ExtensionPoint is the place where CNCF binds a capability to a concrete
implementation.

### Adapter Interpretation

In practical AI integration terms, an `ExtensionPoint` is the CNCF-level hook
that materializes an adapter.

For example:

- `GemmaOllamaExtensionPoint` can create a Gemma-specific port
- `RemoteGemmaExtensionPoint` can create a remote HTTP-backed port

The adapter is not the component itself.
It is the object produced by the `ExtensionPoint` and consumed by the `Port`.

## VariationPoint

`VariationPoint` is the selection boundary among multiple candidate ports or
extension points.

Responsibilities:

- select between local and remote execution
- select among providers
- apply fallback policy
- keep routing decisions explicit

Examples:

- `gemma -> ollama`
- `gemma -> remote http backend`
- `local first, remote fallback`

### Suggested Shape

```scala
trait VariationPoint[In, Out] {
  def select(input: In): Port[In, Out]
}
```

VariationPoint may delegate to multiple `ExtensionPoint` instances.

## Practical Adapter Staging

The adapter realization can be staged as follows:

### Stage 1: Explicit Invocation Port

Introduce `Port` as the stable invocation surface.

This gives every backend the same minimal contract:

- typed input
- typed output
- no direct component coupling

### Stage 2: Backend Binding Point

Introduce `ExtensionPoint` as the binding hook.

This lets CNCF resolve:

- which backend is available
- which backend matches the current configuration
- which backend should be instantiated

### Stage 3: Routing and Fallback

Introduce `VariationPoint` as the selection policy.

This makes it possible to express:

- local-first execution
- remote fallback
- provider-based routing
- policy-based selection

### Stage 4: Adapter Construction

Use the selected `ExtensionPoint` to construct the concrete adapter or port.

Example flow:

1. `AI` operation requests `generate`
2. `VariationPoint` selects `gemma + local + ollama`
3. `ExtensionPoint` creates `GemmaOllamaGeneratePort`
4. `Port` invokes Ollama
5. `Engine` applies runtime policy

---

# Layering

Recommended layering:

1. `Component` exposes the capability surface
2. `VariationPoint` chooses the execution route
3. `ExtensionPoint` creates the backend binding
4. `Port` performs invocation
5. `Engine` schedules and coordinates execution

This preserves the current CNCF separation of concerns while making backend
selection explicit.

---

# Resolution Flow

## Canonical Flow

1. Component receives an operation request
2. VariationPoint reads provider/mode/engine configuration
3. VariationPoint selects the best candidate
4. ExtensionPoint creates the concrete Port
5. Port executes the request
6. Engine manages the execution policy around the call

## Gemma Example

For a Gemma integration, the canonical path is:

1. `AI` operation requests text generation or chat
2. `VariationPoint` sees `provider=gemma`, `mode=local`, `engine=ollama`
3. `ExtensionPoint` creates `GemmaOllamaGeneratePort` or `GemmaOllamaChatPort`
4. `Port` invokes Ollama
5. `Engine` applies timeout, concurrency, tracing, and other policies

This is the concrete adapter story the `textus-ai` work needs:

- Gemma is not embedded in operation logic
- Ollama is the default execution backend
- Port is the runtime call boundary
- ExtensionPoint owns backend construction
- VariationPoint owns policy and selection

---

# Why This Fits CNCF

This model aligns with the current CNCF architecture because:

- `Component` remains the runtime container
- `OperationCall` remains the stable execution boundary
- `Port` captures the actual invocation surface
- `ExtensionPoint` captures backend binding
- `VariationPoint` captures routing and fallback

This avoids pushing backend-specific branching into component logic.

It also keeps CNCF compatible with both:

- generated components that need stable runtime bindings
- hand-authored components that need custom backend resolution

---

# Impact on AI Integration

For AI components, this model provides a clean split:

- capability lives in the component model
- execution binding lives in the extension point
- backend selection lives in the variation point
- inference backend stays outside the JVM component logic

This is a better fit for AI than embedding model behavior directly into the
operation implementation.

The recommended default deployment path remains:

CNCF (Scala/JVM) + Docker + Ollama + Gemma

---

# Open Questions

- Should `VariationPoint` be a first-class framework type, or a composition
  pattern over `ExtensionPoint`?
- Should `Port` be synchronous by default, or support an effect type?
- Should `ExtensionPoint` cache constructed ports, or create them per request?
- Should fallback be defined in `VariationPolicy`, with `VariationPoint`
  handling only selection?

---

# Conclusion

The recommended CNCF extension model is:

- `Port` for invocation
- `ExtensionPoint` for backend binding
- `VariationPoint` for selection and fallback

This gives AI and collaborator integrations a stable abstraction stack that
can support Gemma, Ollama, remote backends, and future execution modes without
changing the public component surface.
