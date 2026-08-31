# Phase 61.1 Checklist - Generated Information Type Adoption

status=closed
phase=[Phase 61.1 - Generated Information Type Adoption](phase-61.1.md)
predecessor=[Phase 61 Checklist](phase-61-checklist.md)
successor=[Phase 61.2 Checklist](phase-61.2-checklist.md)

## IC-03: Generated Type Adoption

Stage Status:
- Current status: DONE
- Owner: CNCF Information model maintainers
- Update rule: Update only from accepted IC-03 evidence; do not reopen IC-02.
- Entry rule: IC-02 is DONE in Phase 61.
- Completion rule: Runtime source uses the generated Entity/value/powertype
  family with only explicitly bounded compatibility adapters remaining.

- [x] Introduce the canonical generated Information import/facade policy.
- [x] Replace the handwritten Information case class in InformationSpace
  snapshots and method signatures.
- [x] Replace duplicated ValidationIssue, IdentityBinding,
  ResolutionCandidate, PublicationStatus, Conflict, FieldEvent, snapshot, and
  count representations where defined by CML.
- [x] Preserve required helper construction and safe `ValueReader` behavior
  through generated builders/codecs or explicit non-model utilities.
- [x] Preserve `InformationId` compatibility while using canonical `EntityId`.
- [x] Preserve deterministic field names and external wire aliases.
- [x] Add explicit compatibility decoding for admitted legacy payloads.
- [x] Reject ambiguous payloads that could select different handwritten and
  generated interpretations.
- [x] Mark temporary root aliases/adapters with removal criteria.
- [x] Add compile-time and runtime class-identity specifications.

Evidence:
- Step commit: `8dae7b5cd4134b84e29210c51e388c159de86fe6`.
- Focused repair validation: `phase61.1-repair1-validation-004` passed 9
  suites / 374 tests with the serialized SBT lock released.
- Mandatory Phase full review found `CPB-61.1-001` (creation provenance) and
  `CPB-61.1-002` (E1/E2 metadata binding). Cycle 1 resolved both; the accepted
  focused closure review found no Current Phase Blocker.
- The final full suite and distinct release commit close IC-03 under
  `phase61.1-clb-ic03-20260831`. Phase 61.2 remains planned and unstarted.
