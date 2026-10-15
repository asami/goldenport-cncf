# Phase 78 Checklist - Component Capability External Contract

status=closed
phase=[Phase 78](phase-78.md)
development_item=DEV-011

## Current Phase closure — 2026-10-16

Closure binding: `cncf78-phase-closure-20261016-01@1`.
All six declared Steps are locally committed. The independent whole-Phase
review accepted all 25 criteria below and all five Phase completion conditions
without blockers or follow-up findings. The configured full suite passed
4,568 tests in 597 suites with no failed test or aborted suite; 13 canceled,
one ignored and 46 pending tests remain explicitly nonexecuted. The local
Phase release binds these accepted results and the current closed status.

Earlier Step acceptance sections retain their historical pending statements.
See the [current Phase closure](phase-78.md#phase-closure--2026-10-16) for the
current local release boundary and remaining CBD Phase 11/11.1 obligations.
This checklist does not accept transferred CBD Job work or start Phase 14.

## CAP-78-PKG01: Component Usage model and Component-owned Directory/CAR

Stage Status:
- Current status: DONE
- Owner: CNCF package metadata owner
- Update rule: corrected scope replaces the Application-owned package proposal;
  preserve prior Step acceptance and require focused new-boundary evidence.
- Closure basis: Current public Usage acquisition, shared Directory/CAR transport,
  focused Step validation and independent protected Step review are accepted.

- [x] Expose Component-owned Usage metadata from Directory/CAR, separate from Component contracts.
- [x] Keep inductive Usage and deductive Component Capability separate; preserve
      explicit Component ownership, source/derivation evidence and many-to-many
      mappings. Usage observations do not create PROVIDED/REQUIRED contracts.
- [x] Delete old Application types/APIs/fields without aliases or forwarders;
      compile affected callers/generated code so stale typed references fail.
      Update CML/JSON/fixtures together; no dual reader, converter or fallback.
- [x] Prove one normal generated Directory route without requiring a CAR or loose
      JSON; prove multi-Component owner partitioning and external references.
- [x] Prove equivalent semantic results from that Directory and normal CAR,
      with config and separate capability namespaces coexisting.
- [x] Preserve optional absence and actual upstream failures; reuse lower-layer
      guarantees. Complete focused owner review/validation and record actual
      versions without claiming old separate-file proof accepts this change.


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

Historical pre-CAP-78-05 status — recorded before 2026-10-15: the pending
interoperability wording below is retained; see
[current CAP-78-05 acceptance](phase-78.md#cap-78-05-acceptance--2026-10-15).

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
- Current status: DONE
- Owner: CNCF Operation/Workflow/StateMachine contract owners
- Current step: R13 actual typed target adapters and CPT01..32 accepted.
- Update rule: Retain accepted explicit realization binding without turning
  Capability into an executable object, established by focused validation and
  independent protected Step conformance on 2026-10-14; the local Step commit
  binds this acceptance.
- Closure basis: All four criteria below are checked against the actual typed
  adapters, accepted focused validation and independent protected Step conformance.

Accepted contract and executable specifications:
- [R13 actual typed target adapter](../spec/component-capability.md#r13-actual-typed-target-adapter)
- [Adapter specification CPT01..22](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetAdapterSpec.scala)
- [Binding specification CPT23..32](../../src/test/scala/org/goldenport/cncf/component/capability/ComponentCapabilityTargetBindingSpec.scala)

Historical pre-CAP-78-05 status — 2026-10-14: the acceptance and pending
producer/consumer wording below is retained; see
[current CAP-78-05 acceptance](phase-78.md#cap-78-05-acceptance--2026-10-15).

Focused validation passed and independent protected Step conformance accepted
CAP-78-03 on 2026-10-14. The local Step commit binds this acceptance. Canonical
acquisition/discovery is accepted in CAP-78-04; real Cozy producer/CBD consumer proof is
CAP-78-05 with Cozy CAP-64-05 and CBD Phase 11. Separate participant Phase
full validation, review and local release remain open.

- [x] Define realization references to Operation, Workflow, and StateMachine.
- [x] Fail closed for unresolved or incompatible realization targets.
- [x] Keep ordering, retry, compensation, guards, and runtime state outside the
      Capability record.
- [x] Treat Specification and Evidence references as traceability links.

## CAP-78-04: Component/package exposure

Stage Status:
- Current status: DONE
- Owner: CNCF ComponentFactory and packaging owners
- Current step: R14 canonical Directory/CAR acquisition, public native
  provider/catalog, Factory entry and 36 behavioral cases accepted.
- Update rule: Retain acceptance established by focused validation and independent
  protected Step conformance on 2026-10-14; the local Step commit binds this acceptance.
- Closure basis: All three criteria below are checked against the accepted R14
  contract, focused validation and independent protected Step conformance on 2026-10-14.

Historical pre-CAP-78-05 status — 2026-10-14: the acceptance and pending
producer/consumer wording below is retained; see
[current CAP-78-05 acceptance](phase-78.md#cap-78-05-acceptance--2026-10-15).

[R14](../spec/component-capability.md#r14-canonical-package-and-public-native-discovery)
and the [module design](../design/component-capability.md#canonical-package-and-public-discovery-structure)
define the accepted boundary. Focused validation passed and independent protected
Step conformance accepted CAP-78-04 on 2026-10-14; the local Step commit binds
this acceptance. Normal Cozy/CBD proof and participant Phase completion remain open.

- [x] Integrate Capability discovery with ComponentFactory metadata.
- [x] Select and document the canonical package/CAR publication surface.
- [x] Prove no second CML parser or name-derived reconstruction is introduced.

## CAP-78-05: Cross-repository acceptance

Stage Status:
- Current status: DONE
- Owner: CNCF Phase 78 with Cozy Phase 64 and CBD Support Phase 11
- Update rule: Retain the actual normal Cozy producer/canonical CNCF public
  ComponentFactory Directory/CAR/source-free CBD consumer acceptance and distinct
  exact provenance established by focused validation and independent protected
  own Step conformance on 2026-10-15; the local Step commit binds this acceptance.
- Closure basis: All four original criteria below are checked against the actual
  producer/consumer path, separate Component source, producer, generated ABI,
  package/release, schema, native target and CBD revision domains, accepted focused
  validation and independent protected own Step conformance on 2026-10-15.

See [CAP-78-05 acceptance](phase-78.md#cap-78-05-acceptance--2026-10-15).
Participant Phase full review, full validation and local release remain owed;
authorized Cozy main integration and CBD Phase 11 CAP-11-02 catalog/CAP-11-03
queries remain separate mandatory obligations. Phase 78 remains in progress.

- [x] Admit one Cozy-generated Capability projection.
- [x] Expose it through the canonical CNCF Component/package surface.
- [x] Consume it from Textus CBD Support without semantic inference.
- [x] Record focused validation, independent review, and revision/ABI evidence.
