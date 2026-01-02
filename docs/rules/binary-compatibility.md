Binary Compatibility Rules (core / cncf)
=======================================

This document defines practical rules for maintaining binary compatibility
in the core and cncf modules.

The goal is NOT perfect binary compatibility at all costs.
The goal is to preserve stability at module boundaries while allowing
internal evolution.

----------------------------------------------------------------------
1. Basic Policy
----------------------------------------------------------------------

- Binary compatibility is a *design goal*, not an absolute requirement.
- Public APIs at module boundaries SHOULD preserve binary compatibility.
- Internal implementation details MAY break binary compatibility when
  introducing significantly better mechanisms.
- Any such breaking change MUST be contained within the module.

In short:

  Binary compatibility may be broken internally,
  but must be preserved externally.

----------------------------------------------------------------------
2. Module Boundary Awareness
----------------------------------------------------------------------

The following elements are considered module boundaries and should be
designed with binary compatibility in mind:

- core
  - Protocol
  - ProtocolHandler
  - Request / OperationRequest
  - ActionCall
  - ExecutionContext and its sub-contexts

- cncf
  - Component
  - Service / Receptor (or equivalents)
  - Scenario / Executable Spec entry points

Types that are not exposed through these boundaries are considered
internal and may evolve freely.

----------------------------------------------------------------------
3. Construction Rules
----------------------------------------------------------------------

### 3.1 Prefer named empty instances

For types that can represent an empty state, always provide a named empty:

  object X {
    val empty: X
  }

Do NOT require callers to construct empty values manually
(e.g. Vector.empty).

This improves:
- API stability
- readability
- future extensibility

### 3.2 Provide zero-argument apply for empty-constructible types

For empty-constructible public types, provide:

  object X {
    def apply(): X = empty
  }

Reasons:
- Binary compatibility with existing compiled code
- Java interoperability
- Scenario / DSL friendliness

Relying only on default constructor arguments is NOT sufficient
for binary compatibility.

### 3.3 Collection / Group idiom reference

The Collection / Group idiom is defined in `docs/idioms/collection-group-idiom.md`.
Named `empty` and zero-arg `apply()` are established idiom patterns there.
Use the idiom as a design guide for aggregation types.

----------------------------------------------------------------------
4. Case Class Usage
----------------------------------------------------------------------

- Public case classes SHOULD be treated as value objects,
  not as exposed data structures.
- Adding, removing, or reordering fields of a public case class
  is considered a binary breaking change.
- Prefer companion factories and methods over exposing structure.

Avoid exposing public case classes whose fields are part of the API
unless the structure itself is intentionally stable.

----------------------------------------------------------------------
5. Trait and Abstract Class Evolution
----------------------------------------------------------------------

- Public traits and abstract classes MUST be append-only.
- Removing or changing existing abstract members is forbidden.
- New behavior MUST be added using default implementations.

Example:

  trait X {
    def a: A
    def b: B = defaultB
  }

----------------------------------------------------------------------
6. Internal Mechanisms
----------------------------------------------------------------------

The following are considered internal mechanisms and MAY break
binary compatibility when necessary:

- Execution engines
- Dispatchers, schedulers, executors
- Internal DSLs
- Observability and tracing internals
- Performance optimizations

Such changes must NOT leak through public APIs.

----------------------------------------------------------------------
7. Scenario and Spec as Stability Anchors
----------------------------------------------------------------------

Executable Specs and Scenarios define the effective public surface.

- If a Scenario compiles and passes, the public API is considered stable.
- If a Scenario breaks due to internal refactoring, the refactoring
  has violated the module boundary.

Scenarios are the primary guardrail for API stability.

----------------------------------------------------------------------
8. Summary
----------------------------------------------------------------------

- Preserve binary compatibility at module boundaries.
- Allow internal evolution without fear.
- Use named empty and zero-arg apply as standard patterns.
- Treat Scenarios as executable contracts.
- Prefer simple rules and daily habits over heavy tooling.

These rules are designed to keep core and cncf evolvable,
stable, and understandable over time.
