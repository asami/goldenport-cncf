# Phase 103 Checklist — Validation/Test Metadata ABI and Discovery

status=planned
Phase: [103](phase-103.md)
Contract input: [ABI proposal](../notes/validation-test-metadata-abi-proposal.md)

All items are PLANNED. No implementation or validation evidence is claimed.

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
