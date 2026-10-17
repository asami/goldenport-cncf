# Phase 103 Checklist — Validation/Test Metadata ABI and Discovery

status=in-progress
Phase: [103](phase-103.md)
Contract input: [ABI proposal](../notes/validation-test-metadata-abi-proposal.md)

Step P103-S1 declaration implementation and Executable Specifications passed
focused validation and independent protected conformance review. Unchecked Phase items
remain open acceptance obligations. S1 local Step acceptance is recorded in
`ac443ed353cc682251e4d17bf4e64a1285955798`. S2 discovery/query validation and
independent review are accepted, with the local Step commit pending; S3 invocation
and consumer delivery remain open.

## Step P103-S1: declaration interpretation

Stage Status:
- Current status: CLOSED (accepted local Step)
- Owner: CNCF Phase 103
- Update rule: Parent updates evidence after focused validation and independent review.
- Closure basis: The Step acceptance entries below and remaining Phase VM ledger.

Source: [public ABI](../../src/main/scala/org/goldenport/cncf/validation/ValidationMetadataV1.scala),
[interpreter](../../src/main/scala/org/goldenport/cncf/validation/ValidationMetadataResolution.scala),
[Executable Specification](../../src/test/scala/org/goldenport/cncf/validation/ValidationMetadataV1Spec.scala).
Contract: [spec](../spec/validation-test-metadata.md),
[design](../design/validation-test-metadata.md).
Progress: [journal](../journal/2026/10/2026-10-18-phase-103-validation-metadata.md).

- [x] Focused declaration specifications and affected execution-contract regressions pass (33 tests, three suites, zero failures).
- [x] Independent focused review accepts the connected Step change (protected conformance review, no findings).
- [x] Parent records Step acceptance and local commit evidence: `ac443ed353cc682251e4d17bf4e64a1285955798`.

No annotation/discovery/invocation support is delivered by S1. Stable scoped
operation identity and revision provenance, completeness and real scenario
discovery are authored in S2; their acceptance and remaining S3/Phase obligations
are tracked below.

## Step P103-S2: real scoped ScalaTest discovery and pure selection

Stage Status:
- Current status: ACCEPTED_PENDING_LOCAL_COMMIT
- Owner: CNCF Phase 103
- Update rule: Parent records focused validation, protected-focused review and acceptance evidence.
- Closure basis: The connected S2 boundary, without closing Phase VM-01..04.

Source: [discovery ABI](../../src/main/scala/org/goldenport/cncf/validation/ValidationDiscoveryV1.scala),
[discovery specification](../../src/test/scala/org/goldenport/cncf/validation/ValidationDiscoveryV1Spec.scala),
[selection specification](../../src/test/scala/org/goldenport/cncf/validation/ValidationDiscoverySelectionSpec.scala).

- [x] Author runtime Java annotations, typed provider admission and S1-backed interpretation of actual registered scenarios.
- [x] Author caller-scoped directory/JAR discovery, provenance, completeness, invalid/unannotated rows and deterministic logical references/selectors.
- [x] Author pure purpose/feature queries, exact supplemental resolution and real passing FunSuite/WordSpec/parameter fixtures.
- [x] Parent focused discovery/selection specifications and S1 regression pass through serialized SBT with successful terminal completion and lock release (36 discovery/selection plus 17 declaration tests, three suites, zero failures or aborts).
- [x] Independent protected-focused Step review covers all connected S2 paths and S1/build edges; fresh independent focused review accepts closure of its sole descendant-symlink completeness defect, with no remaining blocker, Hygiene or Development Candidate findings.
- [ ] Parent records S2 acceptance and exact local Step commit evidence.

S3 remains open: existing typed CNCF execution binding, CML equivalence mapping,
shared complete/partial static fixtures, actual sm-workflow runtime handoff and
cbd-support handoff. The authored in-process ScalaTest selector specification is
not CNCF process invocation evidence. Full Phase validation and review remain open.

## VM-01: Contract

- [ ] Define versioned public/normalized metadata for SMOKE, FOCUSED, ADMISSION,
      FULL and HEAVY, extensible feature IDs, expected duration, duration class
      and binding to existing execution requirements.
- [ ] Fix absent versus explicitly empty operation declaration semantics;
      purposes override class defaults when present, features form a union,
      operation duration/class override defaults. Specify invalid duration and
      incompatible execution requirements as typed interpretation errors.
- [ ] Specify stable component/specification/operation identity, provenance and
      revision references, including parameterized/scenario-level execution for
      the supported test framework. Feature additions are data, not ABI changes.
- [ ] Define Cozy-owned CML property mapping to the same model without making
      generated Scala a second source of truth. Promote spec/design normally.

- [ ] Freeze scoped feature references/mappings, discovery COMPLETE/PARTIAL/UNKNOWN
      semantics, unannotated-operation visibility and source/operation revisions.
      Keep explicit target feature populations separate from test-observed tags.
- [ ] Specify expected-duration unit and operation measurement scope, distinct
      from sm-workflow observation intervals and aggregate query duration.

## VM-02: ABI and discovery

- [ ] Provide public Scala class/specification and operation/scenario metadata
      declarations and deterministic resolved discovery over admitted artifacts.
- [ ] Make normalized metadata and operation identities usable by runtime and
      static consumers without their own annotation-text parsing or merge logic.
- [ ] Bind discovered operations to existing typed execution paths; metadata
      does not supply arbitrary executable/argv. An unannotated operation is
      distinguishable from invalid metadata and is not silently assigned a purpose.
- [ ] Retain declared source facts separately from runtime observations; no pass/
      fail, last-duration, Candidate state or convergence counters in annotations.

## VM-03: Executable Specification acceptance

- [ ] Given class defaults and operation overrides, discovery produces the
      specified purposes, union features and duration/class/requirement values.
- [ ] One operation can belong to several purposes without duplicating code;
      HEAVY-only override does not accidentally inherit ADMISSION/FULL membership.
- [ ] Same-named scenarios in different components retain distinct stable IDs;
      supported parameterized/scenario execution is discoverable and invocable.
- [ ] Absent/empty declarations, malformed duration and unsupported required ABI
      semantics have explicit outcomes; no silent fallback or fuzzy interpretation.
- [ ] Repeated deterministic discovery yields the same normalized declarations;
      adding a feature ID needs no ABI revision, and metadata changes are visible
      as ordinary architecture deltas without hashes or copied-file certificates.
- [ ] Equivalent Scala declarations and CML-mapping fixtures yield equivalent
      normalized metadata; no Cozy parser implementation is required in CNCF.
- [ ] Exercise an actual sm-workflow consumer fixture for project purpose queries
      and FOCUSED plus selected feature union, with operation-level binding.

- [ ] Share complete/partial inventory fixtures, including unannotated operations
      and a sole ADMISSION declaration removed from baseline to Candidate. Static
      consumers see 1 -> 0 only within complete comparable scope; runtime status
      remains Unknown without evidence, and no tested-feature-only denominator
      is fabricated. Hand off to sm-workflow and cbd-support Phase 14.

## VM-04: Handoff and closure

- [ ] Deliver ABI version/API usage, supported declaration/merge examples,
      operation identities, invalid-input cases and discovery fixtures to
      sm-workflow Phase 5 and the Mac mini cbd-support workstream.
- [ ] Static-consumer fixtures expose purpose/feature coverage and metadata
      changes; actual duration remains referenced runtime evidence. CNCF does
      not compute KPI scores or depend on the Mac mini application being done.
- [ ] Record focused/required producer validation and independent review for the
      connected change; record runtime consumer acceptance without declaring its
      entire phase complete. Do not repeat unchanged validation per helper.
- [ ] Update producer spec/design, phase evidence and consumer handoff references;
      close only after required implementation/review/validation is complete.


- [ ] Consumer handoff demonstrates the same stable typed operation binding for
      metadata-selected and explicitly referenced supplemental operations, without
      modifying purpose membership or weakening execution requirements. The Slice
      plan/file lifecycle remains sm-workflow-owned.
