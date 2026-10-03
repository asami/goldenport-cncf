# CAR lint Finding Disposition and Human-in-the-loop direction

Date: 2026-10-04

Current CAR lint operation is producing useful architecture/model-quality findings. The next step is not unconditional AI auto-fix.

## Decision

Treat each lint warning as a Finding that requires an explicit Disposition rather than equating resolution with source modification or warning suppression.

The durable semantic choices include source/model correction, explicit acceptance, deferral, and deterministic configuration. Repeated findings may be grouped into a resolution plan, but the admitted scope remains explicit.

Representation is language/model native:

- CML: model property;
- hand-written Scala: CNCF public annotation ABI;
- generated Scala: projection only when needed; CML remains authority.

The Scala annotation grammar/semantics belong to CNCF ABI. Individual lint rule IDs remain extensible data so adding a lint rule does not require an ABI change.

## Human-in-the-loop

The operational flow is:

`Finding -> Resolution Proposal -> Human Admission -> Work -> Re-lint/Review -> Close`

AI proposes and, after admission, performs the work. The developer approves the semantic decision, not annotation syntax. Bulk and future full-auto operation are explicit policies on the same admission model, not bypasses.

CAR lint itself remains deterministic and independently runnable. Annotation/property interpretation must not depend on AI or workflow runtime.

The meaningful operational KPI is unresolved findings, while raw warning count remains diagnostic information.

## Project allocation

- goldenport-cncf Phase 99: normalized Finding Disposition model and Scala annotation ABI.
- Cozy Phase 77: CML property syntax/mapping and generated projection boundary.
- textus-cbd-support Phase 13: developer-facing AI proposal/admission/resolution/re-lint workflow.
- sm-workflow: use CAR lint resolution as a concrete Candidate-Admission scenario first; add a dedicated Phase only if the existing general workflow/admission machinery proves insufficient.
