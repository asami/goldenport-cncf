# Logical UI Producer ABI Boundary Decision

Date: 2026-10-03
Status: design correction / Phase 96 planning input

## Trigger

Phase 96 planning surfaced a concern that Cozy Phase 74 Logical UI classes are distributed from the Scala 2.12 Cozy artifact while CNCF is Scala 3. A binary-sharing solution was initially considered.

## Reconciliation

That concern came from treating Cozy Phase 74 runtime/reference classes as if CNCF should directly link them. This is inconsistent with the established CML architecture. For other CML model elements, Cozy interprets/normalizes the model and generates source/metadata against a CNCF-owned runtime ABI. ComponentFactory discovers/admit those generated definitions. Logical UI should use the same mechanism.

The Scala boundary is therefore: Scala 2.12 Cozy compiler -> generated Scala 3 application source -> Scala 3 CNCF public ABI/runtime.

## Decision

CNCF Phase 96 adds the Logical UI consumer ABI/admission/ComponentFactory route. Cozy gets Phase 76 to generate that ABI from the Phase 74 semantic authority.

The first CNCF artifact should be a hand-written expected-generated-shape fixture. It freezes the consumer contract before Cozy generator work. Cozy then generates the same shape and hands the fixture back to CNCF for runtime acceptance. No cross-built Cozy Logical UI runtime artifact is required.

## Consequence for Phase 96

The ABI seam moves ahead of Display Protocol implementation. Display Projection consumes admitted Logical UI definitions rather than importing Cozy classes or inventing an unrelated configuration model. This keeps Logical UI consistent with the broader CML producer/runtime architecture and removes Scala binary compatibility from the runtime design problem.
