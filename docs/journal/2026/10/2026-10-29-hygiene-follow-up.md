# Repository synchronization hygiene follow-up

Date: 2026-10-29

## HYG-SYNC-20261029-E8

- Status: OPEN
- Priority: P2
- Category: concurrency correctness investigation
- Discovered: 2026-10-29, full validation and independent ordinary repository-sync review.
- Owner: goldenport-cncf managed EventStore maintainers.
- Location: `src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventObservationIsolationSpec.scala`, E8, concurrent first installation; contract: `docs/spec/managed-event-history.md`, R2–R4.
- Evidence: one full-suite execution observed two successful independent first installers where E8 requires exactly one. Earlier and later full-suite executions passed the same unchanged scenario. The failing observation is retained; a later pass does not establish a correction.
- Risk: a possible create-only installation race or a specification-fixture problem. The root cause is not established. Concurrent installers must never both succeed under the existing contract.
- Boundary: neither this datastore implementation nor its specification fixture is changed by the Phase 99, typed purpose-Git, Help JSON or local-CAR fixture changes. Investigating and altering the installation transaction requires a separately selected datastore repair; no assertion is relaxed by this synchronization.
- Proposed task: reproduce E8 with independent managed hosts, distinguish provider settlement from fixture admission, and repair the evidenced cause while retaining create-only rejection, rollback and no-replay semantics.
- Task / commit: not scheduled.
