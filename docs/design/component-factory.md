Component Factory and Provider
==============================

Status: draft (frozen for Phase 2.6 Stage 5)

This document defines the canonical architecture for turning discovered classes
(loaded from class files via a ClassLoader) into concrete CNCF Component instances.

It exists to prevent AI-only "verify ok" outcomes that do not satisfy human requirements.
The contract here is the shared baseline for both humans and AI agents.


Terms
-----

- **Component**: a runtime instance that participates in CNCF routing/execution.
- **Discovered class**: a class definition that has been found in some source
  (scala-cli output, sbt target, repository.d, component.d, etc.) and is loadable by a ClassLoader.
- **Packaged source**: a search or active source of discovered classes and/or component artifacts.
- **Factory / Provider**: two-stage instantiation pipeline.


Design Goals
------------

1. **ClassLoader-aware**: instantiation must be correct even when the class is not
   on the system/application classpath.
2. **Clear responsibility split**: selection and policy in Factory, mechanics in Provider.
3. **Explicit failure semantics**: distinguish
   - "not my target" (None)
   - "my target but failed" (Consequence.failure)
   - "my target and succeeded" (Some(NonEmptyVector[Component]))
4. **No demo-only semantics**: scala-cli support is a general packaged-source target,
   not a special "demo mode".
5. **Config integration path**: bootstrap toggles may exist temporarily but must have an
   explicit deprecation path into the Config mechanism.


Core Abstractions
-----------------

### DiscoveredClass

DiscoveredClass is the unit of discovery. It binds the class identity with the loader
that can actually load it.

- `className`: fully qualified class name
- `classLoader`: the ClassLoader that can load the class
- `origin`: packaged-source descriptor (scala-cli / sbt / component-dir / etc.)
- `evidence`: optional evidence for diagnostics (paths, fingerprints)

This is intentionally *not* a Class[_] value. Discovery may prefer names first and
only load when needed.


### ComponentFactory

The Factory is an external interface (policy and eligibility):

- Receives `DiscoveredClass`
- Decides whether it can produce Components from that class
- If not applicable -> returns `Consequence.success(None)`
- If applicable:
  - creates / resolves the appropriate `Component.Core` (or its builder input)
  - delegates to a Provider to instantiate
  - returns `Consequence[Option[NonEmptyVector[Component]]]`

Signature (conceptual):

```
trait ComponentFactory {
  def create(
    p: DiscoveredClass,
    core: ComponentCorePlan
  ): Consequence[Option[NonEmptyVector[Component]]]
}
```

Notes:
- The Factory owns *selection* and *meaning*.
- The Provider owns *mechanics* of instantiation.


### Runtime Component.Factory And BundleFactory

The runtime has two related factory concepts with different construction
scopes.

`Component.Factory` creates one runtime participant. It owns the generated or
handwritten component construction policy for a single participant:

- `createPrimary(params)` creates a primary participant.
- `createComponentlet(params)` creates a componentlet participant.
- `create_Component` creates the concrete `Component`.
- `create_Core` supplies the `Component.Core`, including generated service and
  operation metadata.

Generated Cozy factories such as `FooComponent.Factory` are
`Component.Factory` implementations. They are not mere boilerplate: they carry
the generated component protocol, service/operation definitions, authorization
hooks, projection hooks, and other component-local metadata used by
`ActionCall` and internal DSL helpers.

Factory code should remain a construction and binding layer. In generated CAR
projects the handwritten `impl.ComponentFactory` should normally be a facade
that creates the component participant, supplies `Component.Core`, installs
ports/SPI surfaces, and connects generated service/operation metadata to
handwritten behavior.

Domain/application behavior that grows beyond small action glue belongs in
component-local `*Logic` modules, not in the factory. A `*Logic` module is the
behavior body that may bind configuration, providers, policies, and runtime
adapters. More specific helper names such as `*Store`, `*Client`, `*Strategy`,
`*Policy`, `*Renderer`, and `*Workflow` should be used when the role is clear.
The `Factory` suffix should remain reserved for CNCF/Cozy construction or
adapter concepts.

`*Logic` is not a place to retain a request `ExecutionContext` as long-lived
state. Request context is supplied by the generated action surface through
`ActionCall.Core` and should be consumed via protected internal DSL helpers.
This lets the logic module be behavior/configuration-bound while each operation
call still receives the correct authorization, CallTree, configuration
precedence, sandbox, and runtime effect context.

`Component.BundleFactory` creates a bundle:

```scala
primaryFactory: Component.PrimaryComponentFactory
componentletFactories: Vector[Component.ComponentletFactory]
```

It is the correct abstraction when one packaged component entrypoint needs to
produce a primary participant plus zero or more componentlets. Componentlets are
the semantic reason to use a bundle factory. A single-primary component may use
`Component.Factory` directly; using `SinglePrimaryBundleFactory` is a packaging
convenience, not a replacement for the generated `FooComponent.Factory`.

Use these rules:

- For a single component with no componentlets, a `Component.Factory`
  implementation is sufficient and natural.
- For a component package that publishes componentlets, expose a
  `BundleFactory` and delegate participant construction to
  `PrimaryComponentFactory` / `ComponentletFactory` implementations.
- If a generated `FooComponent.Factory` exists, prefer extending or delegating
  to it. Do not bypass it with a raw `SinglePrimaryBundleFactory` unless the
  generated protocol/core behavior is deliberately reimplemented.
- `SinglePrimaryBundleFactory` reduces boilerplate for bundle publication with
  no componentlets, but it still introduces the bundle entrypoint and
  ServiceLoader shape. It does not make the development model identical to a
  plain `Component.Factory`.

### ServiceLoader Declaration Policy

Java `ServiceLoader` metadata is not part of the ordinary CAR development
path. A normal CAR should be discoverable through CAR metadata, class scanning,
the `impl.ComponentFactory` naming convention, and the runtime
`Component.Factory` / `Component.BundleFactory` type contract. Most component
developers should not need to know about
`META-INF/services/org.goldenport.cncf.component.Component$BundleFactory`.

Use ServiceLoader metadata only as an explicit advanced loading policy:

- when the factory is intentionally placed outside CNCF's normal discovery
  paths;
- when startup discovery cost needs to be reduced by naming the factory
  directly;
- when the project intentionally wants a strict explicit factory binding.

The first case can make ServiceLoader declaration necessary. The latter two
are valid operational choices, but they increase development surface area and
are not the recommended default. If ServiceLoader metadata is present, it must
point at an implementation of the declared service type. A
`Component$BundleFactory` service declaration must therefore name a real
`Component.BundleFactory`, not a plain `Component.Factory`.


### ComponentProvider

The Provider is the instantiation mechanism:

- Receives the loaded class or class name + loader, plus an already-prepared Core plan
- Performs reflection / singleton resolution / constructor invocation
- Returns the instantiated Component(s), or a failure

Provider is not responsible for deciding applicability.

Provider guidelines:
- Prefer Scala `object` singleton when present (Foo$ / MODULE$ pattern).
- Else use a JVM-visible zero-argument constructor if available.
- Scala default constructor parameters are not a substitute for the zero-argument
  reflection contract. If a handwritten factory adds constructor parameters,
  keep an explicit no-arg auxiliary constructor or an equivalent no-arg factory
  entrypoint for CAR/dev-dir discovery.
- Else fail with a clear diagnostic (DbC-style in internal code, Consequence at boundary).

Cozy-generated `impl.ComponentFactory` classes are expected to satisfy the
zero-argument construction path. Handwritten extensions should preserve that
property unless the component deliberately uses an explicit advanced loading
policy.

**Component generation scope**: For Phase 2.8 the Provider only handles concrete `Component` classes discovered on the classpath or from packaged/component sources. The previous `ComponentDefinition` / `GeneratedComponent` path has been removed, so the runtime no longer interprets DSL-based definitions. Every discovered artifact must resolve to an instantiable `Component` class, including script-generated classes or classes loaded from packaged search or active directories such as `repository.d` and `component.d`. Future automated component generation is expected to emit such concrete classes so the Provider can apply the documented reflection/constructor logic without additional semantic layers.


### ComponentFactoryGroup

A group composes multiple factories:

- Input: Seq[ComponentFactory]
- Output: a single evaluation pipeline
- Evaluation semantics:
  - Iterate factories in order (stable, deterministic)
  - The first factory that returns Some(...) wins
  - A failure from an applicable factory is a failure of the overall pipeline

Signature (conceptual):

```
final class ComponentFactoryGroup(factories: Seq[ComponentFactory]) {
  def createAll(p: Seq[DiscoveredClass]): Consequence[Vector[Component]]
}
```

Collection policy:
- Input: Seq
- Output: Vector (or NonEmptyVector when non-empty is guaranteed)
- Prefer: Vector > Chain > List (unless a specific structure is required)


Result Semantics
----------------

The canonical return type at the discovery boundary is:

```
Consequence[Option[NonEmptyVector[Component]]]
```

Meaning:
- `Consequence.success(None)`:
  - the factory declares the class is *not* applicable
  - this is normal control flow, not an error
- `Consequence.success(Some(components))`:
  - the factory is applicable and produced one or more components
- `Consequence.failure(err)`:
  - the factory is applicable but could not instantiate
  - this must carry diagnostic evidence and must not be silently ignored

Rules:
- `Some(empty)` is forbidden (use None for "not applicable").
- If multiple components are produced, it must be `NonEmptyVector`.
- If exactly one is produced, it is still represented as `NonEmptyVector` for uniformity.


Component.Core Creation
-----------------------

Instantiation requires a valid Core. The Factory is responsible for obtaining it.

Core creation must be policy-driven and later configurable:

- Default Core for simple components is allowed, but must be clearly identified
  as a policy decision (not a hidden default).
- If the class requires a different Core, the Factory must supply it or fail.

The Factory must not instantiate Components with "fake" Core values that will be
corrected later. Quick hacks are forbidden.

Note: Providers instantiate concrete Component classes and assign a component
ScopeContext that inherits driver/observability from the parent scope. Driver
resolution traverses the ScopeContext parent chain up to GlobalRuntimeContext, and
SystemContext/ApplicationContext injection no longer exists in the initialization path.


Bootstrap Logging
-----------------

Bootstrap logging exists because normal logging may not be initialized during config load.

Rules:
- BootstrapLog is allowed as a temporary diagnostic channel.
- It must be opt-in and clearly marked temporary.
- It must have a documented migration path into Config-driven logging.

Diagnostics MUST include:
- chosen packaged-source targets
- resolved class directories / artifacts
- attempted class names and ClassLoader identity
- instantiation strategy path (singleton / ctor / failed)


Initialization Parameter Bootstrap
----------------------------------

A factory declares initialization-time values with
`initializationParameterDeclarations: Vector[ComponentParameterKey[?]]`.
Declaration is factory-owned because it must be available before
component-domain initialization. It is distinct from operation-time
`ComponentConfigurationKey` access.

When user-defined logical names form a configuration family, the factory may
also declare `initializationParameterPathRoutes`. A route is a schema template,
not a wildcard lookup: it fixes one owned prefix, one bounded dynamic segment,
and a finite typed leaf vocabulary. CNCF examines only the admitted five
initialization layers, discovers concrete segment identities under that route,
and expands ordinary canonical `ComponentParameterKey` declarations before
the existing decoder/resolver path runs.

Dynamic schema registration precedes typed resolution:

```text
fixed admitted layers
  -> declared route discovery
  -> bounded canonical concrete-key expansion
  -> existing exact-key typed resolution
  -> immutable ComponentInitializationParameters
```

The registration step remains CNCF-private. Component code receives neither
the source maps nor an arbitrary path API; it can resolve only the exact route,
segment, and leaf identities retained by its immutable snapshot. Each concrete
leaf keeps normal fixed-layer precedence and provenance. Compatibility aliases
participate only during CNCF-owned candidate selection and never become public
snapshot identities.

The canonical construction methods are `createPrimaryC` and
`createComponentletC`; bundle factories use `createC`. CNCF allocates the
component and core, establishes final participant identity, resolves the five
fixed layers, then supplies the immutable typed snapshot through
`ComponentInit.initializationParameters`. `create_Component` and `create_Core`
must not inspect raw subsystem configuration to consume declared
initialization values.

Component-specific projection or combination validation belongs in the
consequence-aware `initialize_component_c` hook. A failure remains a
`Consequence.Failure(Conclusion)` through `ComponentFactory.bootstrapC` and
`Subsystem.addC`, preventing installation. Existing non-`C` methods are
compatibility wrappers; new framework/runtime code uses their `C`
counterparts. Factories with no declarations receive
`ComponentInitializationParameters.empty`, and special components use the
same snapshot rather than a parallel configuration route.

Descriptor-based subsystem startup binds the effective subsystem descriptor
before repository factory construction. Each repository construction context
contains exactly one component binding's instance metadata, so a factory can
resolve required initialization parameters during discovery without borrowing
another instance's values. Actual assembly discovery uses the consequence-aware
route and preserves a factory initialization `Conclusion`; exploratory
component-name inference may omit an unconstructable candidate but cannot turn
an actual assembly failure into component absence. The selected repository
participant is materialized again for its binding before subsystem admission.


Verify vs Validate Policy (AI-Human Alignment)
----------------------------------------------

- **Verification**: "Implementation matches the internal design contract (this document)."
- **Validation**: "Behavior satisfies human requirements and expected user workflow."

AI agents MUST report these distinctly:
- "Verified against the design spec" does not imply "Validated against the requirement."
- If validation fails (requirement mismatch), report it explicitly as requirement drift.


Configuration Integration
-------------------------

Environment variables used for bootstrap are temporary and must be moved into Config:

- If an env var is introduced (e.g., CNCF_*), it must be marked `temporary` in code and docs.
- The planned Config key must be documented alongside it.


Repository Targets
------------------

Immediate targets (Phase 2.6 / Stage 5):

- `scala-cli`: uses `.scala-build` outputs (default)
- `sbt`: uses `target/scala-*/classes` outputs

Future targets (directional):

- active packaged component directories such as `component.d`
- packaged search repositories such as `repository.d`
- official repository (SimpleModeling.org managed)
- project-specific repository (BoK / project workspace)

The packaged-source layer provides DiscoveredClass values; the factory/provider pipeline remains unchanged.
