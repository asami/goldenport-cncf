# Phase 58.6.1 Checklist - Lifecycle Terminal-Outcome Recovery

status=planned
phase=[Phase 58.6.1 - Lifecycle Terminal-Outcome Recovery](phase-58.6.1.md)
predecessor=[Phase 58.6](phase-58.6.md)
successor=[Phase 58.7](phase-58.7.md)

## RSC-07B: Lifecycle Terminal-Outcome Recovery

Stage Status:
- Current status: PLANNED
- Owner: CNCF runtime, repository, and lifecycle maintainers
- Entry rule: Phase 58.6 core RSC-07 lifecycle is DONE.
- Completion rule: A cancelled loading producer, a provenance-failed waiter, and a waiter observing a completed release/unload/shutdown flight retain their actual separate terminal outcomes with no publication or owner leak.

- [ ] Prove actual waiter admission before the producer cancellation and mismatched evidence complete.
- [ ] Preserve the cancelled producer's no-resource result and remove its owner claim.
- [ ] Preserve the admitted waiter's provenance-mismatch failure without duplicate loading or resource publication.
- [ ] Prove that a waiter cancellation arriving after shared-flight completion preserves the completed release/unload/shutdown terminal result.
- [ ] Verify cancellation/failure metrics and owner state remain truthful and bounded.
- [ ] Verify a later valid retry succeeds after the separated outcomes.
- [ ] Reconcile the RSC07B acceptance-registry nine-row ownership statement.
- [ ] Record focused, affected-accumulator, full-release, and independent-review evidence for this recovery boundary.

Evidence:
- Pending.

Decision Record:
- 2026-08-21: `D-58.6-POST-EXCEPTION-CONVERGENCE` selected `SPLIT_PHASE`, and
  `D-58.6-SPLIT-PROPOSAL` approved this child as the sole owner of the
  unresolved `CB-58.6-02` combination.
- 2026-08-21: `D-58.6-NESTED-SPLIT-001` approved
  `EXTEND_EXISTING_58.6.1`; this child additionally owns `CPB-P58.6-001` and
  `CPB-P58.6-002`, while the ordered dependency chain remains unchanged.
