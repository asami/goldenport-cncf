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
- **Discovered class**: a class definition that has been found in some repository
  (scala-cli output, sbt target, component.dir, etc.) and is loadable by a ClassLoader.
- **Component repository**: a source of discovered classes and/or component artifacts.
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
4. **No demo-only semantics**: scala-cli support is a general component repository target,
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
- `origin`: repository descriptor (scala-cli / sbt / component-dir / etc.)
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


### ComponentProvider

The Provider is the instantiation mechanism:

- Receives the loaded class or class name + loader, plus an already-prepared Core plan
- Performs reflection / singleton resolution / constructor invocation
- Returns the instantiated Component(s), or a failure

Provider is not responsible for deciding applicability.

Provider guidelines:
- Prefer Scala `object` singleton when present (Foo$ / MODULE$ pattern).
- Else use zero-arg constructor if available.
- Else fail with a clear diagnostic (DbC-style in internal code, Consequence at boundary).


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


Bootstrap Logging
-----------------

Bootstrap logging exists because normal logging may not be initialized during config load.

Rules:
- BootstrapLog is allowed as a temporary diagnostic channel.
- It must be opt-in and clearly marked temporary.
- It must have a documented migration path into Config-driven logging.

Diagnostics MUST include:
- chosen repository targets
- resolved class directories / artifacts
- attempted class names and ClassLoader identity
- instantiation strategy path (singleton / ctor / failed)


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

- `component.dir`: directory-based repository for runtime deployment
- official repository (SimpleModeling.org managed)
- project-specific repository (BoK / project workspace)

The repository layer provides DiscoveredClass values; the factory/provider pipeline remains unchanged.
