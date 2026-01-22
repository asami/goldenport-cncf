# Component Distribution Models — Design Exploration

published_at=2026-01-22
status=journal

This document records the design exploration and conclusions
regarding future component distribution models for CNCF.

This is not tied to any specific phase execution and exists
to preserve architectural reasoning for future reference.

---

## Background

As CNCF evolves toward real-world component distribution,
the question of how components should be packaged and delivered
becomes unavoidable.

Early experiments using fat JARs quickly revealed structural issues:

- Artifact size grows excessively
- Dependencies are duplicated across components
- Runtime classpath becomes opaque
- Debugging and explanation become difficult

At the same time, past experience with OSGi highlighted
that overly declarative and dynamic systems impose excessive cognitive
and operational costs, especially when tooling and shared knowledge
are limited.

This journal explores alternative distribution models that balance:

- Simplicity
- Explicit structure
- Runtime control
- Future extensibility

---

## Design Constraints

The following constraints guide the evaluation:

- Component packaging must be explainable in articles and documentation
- Dependency structure should be visible on disk
- Runtime behavior must be deterministic and debuggable
- The model must avoid OSGi-level complexity
- The model should not block future adoption of JPMS

---

## Evaluated Models

### 1. Fat JAR

Summary:
All code and dependencies are bundled into a single JAR.

Evaluation:
- Simple to produce
- Easy to execute
- Quickly becomes unmanageable at scale

Conclusion:
Rejected. This model hides structure and does not scale operationally.

---

### 2. JAR + META-INF Descriptor

Structure:
component.jar
└── META-INF/...

Use Case:
- Lightweight component descriptors
- Entry-point metadata
- Minimal component definitions

Advantages:
- Single-file distribution
- Compatible with standard JVM mechanisms
- Simple loader integration

Limitations:
- Cannot carry component-local dependencies
- Dependency resolution is implicit and opaque
- Not suitable for self-contained components

Conclusion:
Suitable for lightweight components and descriptors only.

---

### 3. WAR/EAR-Style Component Bundle (CAR)

Structure:
component/
  component.jar
  lib/*.jar
  component.conf

Characteristics:
- Explicit separation of component logic and dependencies
- Dependencies are visible and replaceable
- Naturally supports classloader isolation
- Familiar mental model for JVM developers

Advantages:
- Avoids fat JAR duplication
- Enables component-local dependency control
- Easy to explain, inspect, and debug
- Aligns well with CNCF runtime responsibility separation

Conclusion:
Identified as the preferred model for dependency-bearing components.

This model is referred to conceptually as CAR (Component ARchive),
though the name is provisional.

---

### 4. OSGi

Evaluation:
- Powerful dependency and lifecycle model
- High definition and operational complexity
- Limited ecosystem momentum
- Difficult to explain and debug

Conclusion:
Explicitly rejected due to excessive complexity and low practical payoff.

---

### 5. JPMS (Future Option)

Evaluation:
- JVM-standard modularity
- Strong encapsulation and visibility rules
- Potentially valuable for large-scale or secure deployments

Conclusion:
Considered a future, opt-in enhancement, not a baseline requirement.

---

## Final Position

The exploration leads to a dual distribution model:

Lightweight Components:
- JAR + META-INF
- Descriptor-level definition only
- No component-local dependencies

Full Components:
- CAR (WAR/EAR-style bundle)
- component.jar + lib/
- Optional dependency declaration for automated resolution

The choice of format is driven by component complexity,
not by arbitrary standardization.

---

## Relation to Phases

- Phase 2.85:
  Component distribution models are explicitly out of scope.
- Phase 2.9 and later:
  CAR-style bundles and dependency resolution may be introduced.
- Phase 3.x:
  JPMS integration may be explored if justified.

---

## Closing Note

This document intentionally captures design reasoning,
not decisions bound to immediate implementation.

It exists to ensure that future work proceeds with clarity,
without revisiting already-explored trade-offs.
