# Phase 61.5 Checklist - Information Downstream and Persisted-State Migration Acceptance

status=planned
phase=[Phase 61.5 - Information Downstream and Persisted-State Migration Acceptance](phase-61.5.md)
predecessor=[Phase 61.4 Checklist](phase-61.4-checklist.md)
successor=[Phase 61.6 Checklist](phase-61.6-checklist.md)

## IC-07: Downstream and Migration Acceptance

Stage Status:
- Current status: OPEN
- Owner: CNCF, Textus Knowledge Editor, Textus SIE, and representative
  application maintainers
- Update rule: Update only from accepted IC-07 evidence; preserve IC-06 public
  projection and managed-input contracts.
- Entry rule: IC-06 is DONE in Phase 61.4.
- Completion rule: Supported downstream and persisted Information flows use
  the canonical generated model without silent incompatibility or data loss.

- [ ] Define supported legacy persisted Information shapes.
- [ ] Implement deterministic migration or explicit incompatibility
  diagnostics.
- [ ] Preserve ids, lifecycle, working/raw data, candidates, bindings,
  publications, conflicts, events, audit, and revision provenance.
- [ ] Add migration preview and rollback-safe failure behavior.
- [x] Complete IC-07A PMR-D design/compatibility review against a frozen
  migration dossier.
- [x] Complete IC-07A PMR-I implementation review against the accepted
  migration dossier and current focused evidence.
- [x] Complete IC-07A PMR-R focused re-review for every byte-changing PMR-I
  correction, when applicable (not required: PMR-I was clean).
- [ ] Validate Textus Knowledge Editor list/detail/edit/lifecycle flows.
- [ ] Validate Textus SIE authority resolution, publication, and
  materialization flows.
- [ ] Validate book, paper, web-resource, Person, Organization, and textual
  work/edition/series/volume profiles.
- [ ] Validate Tag filtering and local Knowledge materialization.
- [ ] Validate Help/API compatibility for development source and packaged CAR
  execution.
- [ ] Run focused downstream suites and representative end-to-end smoke tests.

Evidence:
- IC-07A migration admission: PMR-D, typed PMR-D re-review, and PMR-I PASS;
  migration focused spec 7/0 (`15623-20260901T010636Z`) and physical
  EntityStore focused spec 16/0 (`16405-20260901T010811Z`).
