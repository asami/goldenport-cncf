# Phase 78 Checklist - Component Capability External Contract

status=in-progress
phase=[Phase 78](phase-78.md)
development_item=DEV-011

## CAP-78-01: Semantic contract

Stage Status:
- Current status: CLOSED
- Owner: CNCF Component contract owner
- Update rule: Close only after identity, meaning, versioning, and separation
  from executable and authorization concerns are accepted in design/spec.
- Closure basis: All four checklist items below are checked against the
  accepted design/spec and independent Step review.

Contract documents:
- [Component Capability design](../design/component-capability.md)
- [Component Capability Specification](../spec/component-capability.md)

- [x] Define stable qualified Capability identity and version semantics.
- [x] Define provided and required Capability declarations.
- [x] Separate Capability from Operation, Workflow, StateMachine,
      Availability, Authorization, Permission, and Guard.
- [x] Define compatibility across changed realizations.

Acceptance evidence (2026-10-05):
- Specification rules R1-R10 and expected examples E1-E10 were accepted by
  independent protected Step review at GPT-6.1 Sol high, with no blockers,
  Hygiene, or Development Candidates.
- Static document checks passed: four owned documents, 20 checks, 30 local
  links/anchors, and `git diff --check`.
- The semantic contract is closed. Projection/admission, realization binding,
  package discovery, and real producer/consumer proof remain owned by
  CAP-78-02 through CAP-78-05.

## CAP-78-02: External projection and admission

Stage Status:
- Current status: CLOSED
- Owner: CNCF Component ABI owner
- Update rule: Close only after projection and fail-closed admission have
  executable specification coverage.
- Closure basis: All four items below are checked against the R11/R12
  implementation, passing CPJ/CPA specifications and independent Step review.

Acceptance evidence (2026-10-05):
- Independent protected Step review at GPT-6.1 Sol high passed the complete
  16-file Step and 42-path contract boundary, with no blockers, Hygiene or
  Development Candidates.
- Codec CPJ-01..09 passed 9/9 and admission CPA-01..16 passed 16/16 through
  the serial SBT runner. Main and Test compilation passed with the codec run.
- Static source/document checks and `git diff --check` passed. Participant
  Phase release validation and review remain separate obligations.

Accepted contract and executable specifications:
- [R11 concrete v1 contract](../spec/component-capability.md#r11-concrete-v1-format)
- [R12 metadata admission](../spec/component-capability.md#r12-executable-metadata-admission)
- [Codec specification CPJ-01..09](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityProjectionCodecSpec.scala)
- [Admission specification CPA-01..16](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityAdmissionSpec.scala)

This Step's runtime-independent discovery proof concerns external metadata
decoding, selected-scope admission and readout with no runtime Component.
The hand-authored fixture and explicit target facts are conformance input.
Actual contract adapters/provenance are CAP-78-03; canonical ComponentFactory
and package/CAR source/discovery are CAP-78-04; real Cozy-generated/CBD
interoperability is CAP-78-05. These boxes do not close those later obligations.

- [x] Define a versioned runtime-independent Component Capability projection.
- [x] Preserve component/package/source identity and ABI provenance.
- [x] Reject missing, duplicate, ambiguous, malformed, and incompatible
      declarations.
- [x] Prove discovery without loading Component runtime.

## CAP-78-03: Realization mapping

Stage Status:
- Current status: OPEN
- Owner: CNCF Operation/Workflow contract owners
- Update rule: Close only after explicit realization references bind admitted
  contracts without turning Capability into an executable object.

- [ ] Define realization references to Operation, Workflow, and StateMachine.
- [ ] Fail closed for unresolved or incompatible realization targets.
- [ ] Keep ordering, retry, compensation, guards, and runtime state outside the
      Capability record.
- [ ] Treat Specification and Evidence references as traceability links.

## CAP-78-04: Component/package exposure

Stage Status:
- Current status: OPEN
- Owner: CNCF ComponentFactory and packaging owners
- Update rule: Close only after ComponentFactory and the canonical package/CAR
  surface expose one admitted contract.

- [ ] Integrate Capability discovery with ComponentFactory metadata.
- [ ] Select and document the canonical package/CAR publication surface.
- [ ] Prove no second CML parser or name-derived reconstruction is introduced.

## CAP-78-05: Cross-repository acceptance

Stage Status:
- Current status: OPEN
- Owner: CNCF Phase 78 with Cozy Phase 64 and CBD Support Phase 11
- Update rule: Close only after one real producer/consumer path and exact
  provenance are recorded; downstream Phase closure remains external.

- [ ] Admit one Cozy-generated Capability projection.
- [ ] Expose it through the canonical CNCF Component/package surface.
- [ ] Consume it from Textus CBD Support without semantic inference.
- [ ] Record focused validation, independent review, and revision/ABI evidence.
