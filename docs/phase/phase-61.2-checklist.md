# Phase 61.2 Checklist - InformationSpace Entity Persistence and OCC

status=in-progress
phase=[Phase 61.2 - InformationSpace Entity Persistence and OCC](phase-61.2.md)
predecessor=[Phase 61.1 Checklist](phase-61.1-checklist.md)
successor=[Phase 61.3 Checklist](phase-61.3-checklist.md)

## IC-04: InformationSpace Entity Persistence and OCC

Stage Status:
- Current status: IN PROGRESS — IC-04A, IC-04B, and IC-04C accepted; the
  mandatory full Phase review is pending.
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
- [x] Apply create defaults for id, revision, common attributes, audit, and
  security without admitting managed input.
- [x] Advance revision on every effective Information mutation.
- [x] Apply `WriteIfChanged` only where the Information operation contract
  explicitly selects it; preserve the standard default otherwise.
- [x] Require observed revision for user-visible edit/save paths according to
  Phase 50 policy.
- [x] Use atomic conditional transition for stale-write rejection.
- [x] Ensure failed/stale mutations do not partially modify nested state,
  field events, Tags, publication records, or Knowledge projections.
- [x] Preserve component ownership and isolation for multiple Components.
- [x] Add in-memory, SQLite, and representative provider OCC specifications.
- [x] Add concurrent update, replay, restart, and rollback specifications.

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
- IC-04B: `InformationSpace.updateInformationObserved` keeps adapter-observed
  revision outside the Information domain model and delegates to the standard
  EntityStore `WriteIfChanged + ObservedRequired` update policy. A stale retry
  fails without changing the persisted or cached root.
- IC-04B focused validation: `testOnly
  org.goldenport.cncf.information.InformationSpaceEntityPersistenceSpec` passed
  5 tests in 1 suite (SBT receipt `84942-20260831T040356Z`).
- IC-04B focused independent re-review: PASS; CB-01 closed; no
  current-boundary blockers. `HYG-INFORMATIONSPACE-SIZE` remains a separate,
  pre-existing, nonblocking source-size item.
- IC-04C focused validation: `sbt --batch "testOnly
  org.goldenport.cncf.information.InformationSpaceEntityPersistenceSpec
  org.goldenport.cncf.information.InformationSpaceSpec
  org.goldenport.cncf.information.InformationSpaceDeterminismSpec
  org.goldenport.cncf.datastore.EntityRevisionProviderParitySpec"`
  passed 25 tests in 4 suites covering E6-E9 (SBT receipt
  `19886-20260831T052913Z`).
- IC-04C lightweight independent Step review: PASS; no current-boundary
  blockers or Development Candidates. `HYG-61.1-RR1-002` remains the separate,
  pre-existing, nonblocking InformationSpace source-size item.
