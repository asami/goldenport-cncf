# Repository synchronization hygiene follow-up

Date: 2026-10-29

## HYG-SYNC-20261029-E8

- Status: RESOLVED (2026-10-31; originally OPEN on 2026-10-29)
- Priority: P2
- Category: concurrency correctness investigation
- Discovered: 2026-10-29, full validation and independent ordinary repository-sync review.
- Owner: goldenport-cncf managed EventStore maintainers.
- Location: `src/test/scala/org/goldenport/cncf/datastore/sql/ManagedEventObservationIsolationSpec.scala`, E8, concurrent first installation; contract: `docs/spec/managed-event-history.md`, R2–R4.
- Evidence: one full-suite execution observed two successful independent first installers where E8 requires exactly one. Earlier and later full-suite executions passed the same unchanged scenario. The failing observation is retained; a later pass does not establish a correction.
- Risk: a possible create-only installation race or a specification-fixture problem. The root cause is not established. Concurrent installers must never both succeed under the existing contract.
- Boundary: neither this datastore implementation nor its specification fixture is changed by the Phase 99, typed purpose-Git, Help JSON or local-CAR fixture changes. Investigating and altering the installation transaction requires a separately selected datastore repair; no assertion is relaxed by this synchronization.
- Proposed task: reproduce E8 with independent managed hosts, distinguish provider settlement from fixture admission, and repair the evidenced cause while retaining create-only rejection, rollback and no-replay semantics.
- Task / commit: repaired by `3a09ba505c5af3adb26ca4e00faa4105ae970e13` (2026-10-31).

## Resolution — 2026-10-31

The original observation and uncertainty above are retained as the historical
incident. Xerial SQLite JDBC 3.45.2.0 performs a create/delete write-access probe
before opening a plain filename; simultaneous first connections can therefore
open different file instances under one pathname. Managed SQLite now opens
filesystem paths as native file URIs, bypassing that probe while preserving
`:memory:` and explicitly supplied `file:` inputs. No SQL constraint, admission,
transaction, rollback or retry contract is weakened.

The specification also derives fixture parameters from its selected subsystem,
so ambient runtime configuration cannot redirect the database. E8 still requires
one successful initializer, three infrastructure tables and one initial cursor
row; it now covers repeated fresh databases and multiple path selections.

The repair is accepted in the local commit named above; the repair's independent focused review and the integrated full validation are
retained in the task's private execution records. This resolves this installation incident, not the separate existing
provider-size or specification-maintenance items.
