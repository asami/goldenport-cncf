# Phase 61.1 Checklist - Generated Information Type Adoption

status=planned
phase=[Phase 61.1 - Generated Information Type Adoption](phase-61.1.md)
predecessor=[Phase 61 Checklist](phase-61-checklist.md)
successor=[Phase 61.2 Checklist](phase-61.2-checklist.md)

## IC-03: Generated Type Adoption

Stage Status:
- Current status: OPEN
- Owner: CNCF Information model maintainers
- Update rule: Update only from accepted IC-03 evidence; do not reopen IC-02.
- Entry rule: IC-02 is DONE in Phase 61.
- Completion rule: Runtime source uses the generated Entity/value/powertype
  family with only explicitly bounded compatibility adapters remaining.

- [ ] Introduce the canonical generated Information import/facade policy.
- [ ] Replace the handwritten Information case class in InformationSpace
  snapshots and method signatures.
- [ ] Replace duplicated ValidationIssue, IdentityBinding,
  ResolutionCandidate, PublicationStatus, Conflict, FieldEvent, snapshot, and
  count representations where defined by CML.
- [ ] Preserve required helper construction and safe `ValueReader` behavior
  through generated builders/codecs or explicit non-model utilities.
- [ ] Preserve `InformationId` compatibility while using canonical `EntityId`.
- [ ] Preserve deterministic field names and external wire aliases.
- [ ] Add explicit compatibility decoding for admitted legacy payloads.
- [ ] Reject ambiguous payloads that could select different handwritten and
  generated interpretations.
- [ ] Mark temporary root aliases/adapters with removal criteria.
- [ ] Add compile-time and runtime class-identity specifications.

Evidence:
- Pending.
