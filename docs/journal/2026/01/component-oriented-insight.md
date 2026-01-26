# Journal: Component-Oriented Design Insight (CNCF / Cozy / SmartDox)

## Date
2026-01-26

## Context
While examining how to integrate Cozy (model compiler) and SmartDox
(document engine) into CNCF, the discussion converged on using
fat jars with ClassLoader isolation and wrapping them as CNCF components.

This journal entry records the architectural insight gained,
not a finalized design or specification.

---

## Key Insight

The current architecture strongly demonstrates the core advantage of
component-oriented design with a component framework:

    Differences are absorbed at component boundaries,
    while usage and semantics remain unified.

This applies simultaneously to:
- Scala 2 vs Scala 3
- Heavy vs lightweight dependencies
- Code generation vs execution
- Documentation vs runtime behavior

---

## Practical Realization

- Cozy (Scala 2) can be packaged as a fat jar, internally including SmartDox.
- The fat jar is loaded via a dedicated ClassLoader (parent = null).
- CNCF (Scala 3) wraps this as logical components:
  - Cozy component (compile / generate)
  - SmartDox component (render / doc)
- CNCF itself remains lightweight and stable.

Scala 2 / Scala 3 coexistence is not a problem when:
- JVM is shared
- Scala 2 runtime is enclosed in the fat jar
- ClassLoader boundaries are explicit

---

## Why This Works Well with CNCF

1. Heavy dependencies are treated as a responsibility, not a failure.
2. CNCF provides governance:
   - lifecycle
   - execution model
   - operation semantics
   - doc / help surface
3. Internal complexity is hidden behind stable component interfaces.
4. Generated artifacts (code, docs) are immediately executable.
5. Both humans and AI interact with a uniform component surface.

This confirms that CNCF functions best as a **component framework**,
not merely as an application or execution framework.

---

## Relation to Documentation Strategy

- doc / help are first-class operations.
- SmartDox becomes an execution-time document engine, not a build-time tool.
- Model → Code → Execution → Documentation form a single continuous pipeline.

This aligns naturally with:
- Literate model-driven development
- AI-assisted generation
- Executable documentation

---

## Process Note

To preserve clarity and rigor, the following discipline will be enforced:

- notes:
  - exploratory thinking
  - conceptual alignment
  - architectural intuition
- design:
  - intentional structure
  - explicit decisions
  - named components and boundaries
- spec:
  - normative definitions
  - contracts and guarantees
  - implementation-independent rules

This journal entry intentionally remains at the notes level.

---

## Status

- Architectural direction: confirmed
- Component-oriented value: clearly demonstrated
- Next step: promote selected ideas to design documents when stabilized
