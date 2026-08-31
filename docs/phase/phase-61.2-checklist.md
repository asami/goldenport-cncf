# Phase 61.2 Checklist - InformationSpace Entity Persistence and OCC

status=in-progress
phase=[Phase 61.2 - InformationSpace Entity Persistence and OCC](phase-61.2.md)
predecessor=[Phase 61.1 Checklist](phase-61.1-checklist.md)
successor=[Phase 61.3 Checklist](phase-61.3-checklist.md)

## IC-04: InformationSpace Entity Persistence and OCC

Stage Status:
- Current status: IN PROGRESS — IC-04A accepted; IC-04B and IC-04C remain.
- Owner: CNCF InformationSpace, Entity runtime, and persistence maintainers
- Update rule: Update only from accepted IC-04 evidence; preserve the IC-03
  generated identity contract.
- Entry rule: IC-03 is DONE in Phase 61.1.
- Completion rule: InformationSpace uses the standard Entity
  repository/UnitOfWork/revision path without a parallel persistence kernel.

- [x] Define the component-scoped Information Entity collection identity.
- [x] Register the generated Information Entity descriptor deterministically.
- [x] Bind InformationSpace to the owning Component Entity repository.
- [x] Replace private mutable snapshot authority with repository-backed
  reads/writes while retaining a storage-neutral InformationSpace API.
- [ ] Apply create defaults for id, revision, common attributes, audit, and
  security without admitting managed input.
- [ ] Advance revision on every effective Information mutation.
- [ ] Apply `WriteIfChanged` only where the Information operation contract
  explicitly selects it; preserve the standard default otherwise.
- [ ] Require observed revision for user-visible edit/save paths according to
  Phase 50 policy.
- [ ] Use atomic conditional transition for stale-write rejection.
- [ ] Ensure failed/stale mutations do not partially modify nested state,
  field events, Tags, publication records, or Knowledge projections.
- [ ] Preserve component ownership and isolation for multiple Components.
- [ ] Add in-memory, SQLite, and representative provider OCC specifications.
- [ ] Add concurrent update, replay, restart, and rollback specifications.

Evidence:
- IC-04A: Component-owned `information` collections use the canonical
  `major/minor_component-id/information` identity, StoreOnly memory policy,
  and embedded revisions. InformationSpace delegates persistence reads and
  writes to the standard EntityStore, retaining its snapshot only as a cache.
- IC-04A focused validation: `testOnly
  org.goldenport.cncf.information.*
  org.goldenport.cncf.component.ComponentInformationSpaceSpec` passed 56 tests
  in 12 suites on the Step candidate tree (SBT receipt `phase61.2-ic04-020`).
- IC-04A lightweight independent review: PASS; no current-boundary blockers,
  Hygiene, or Development Candidates.
