# Retained completion rollback and native isolation

Date: 2026-09-30
Status: bounded executable evidence; not Phase acceptance

Continued sm-workflow Phase 2 / admitted CNCF Phase 88 under the existing
simplified parent execution recovery. The cncf-goal-phase contract remains
active; no skill-only runner/receipt gate was reintroduced.

Extended only ManagedEventObservationIsolationSpec's test provider with the
existing EntityRetainedEventDataStore opt-in. Production sources are unchanged.
Three Given/When/Then scenarios use the actual borrowed managed SQLite resource,
captured policy-bearing EventStore, original admitted host and real lease:

- Failure at GuardAdmitted, SuccessorPrepared, RootPrepared and BeforePublish:
  one attempt, zero native commits/readbacks; a fresh admitted scope sees the
  original Workflow pair, cursor 2/2, both retained envelopes and indexes.
- Actual lease revocation at RootPrepared rolls back the pair. Revocation after
  native postcommit readback withholds Verified although the pair is committed.
  Neither case retries. Cursor/envelope/index fields remain unchanged in both.
- Latch-controlled independent immediate zero-timeout JDBC cursor writer after
  observation validation/Workflow staging receives exact SQLITE_BUSY (5).
  One commit and one complete seven-record readback verify the Workflow pair,
  unchanged cursor and two retained envelope/index pairs. An explicit subsequent
  same-value cursor write succeeds, proving lock release without changing the
  cursor evidence or retrying completion.

Fresh raw SQL reads compare every field, including the physical id column.
No alias/fresh event or index is created. This is storage verification only:
no application/UoW completion or transient delivery/dispatch is authorized.

Focused-353 compiled but failed the three new scenarios: the inspection
expectation omitted raw SQL's physical id field. Existing 233 tests passed.
Corrected test expectations to explicitly include the physical id, without
filtering stored fields or changing production. Both final summary/log artifacts
were read before the next serial invocation.

Focused-354 passed 236/236 in 26 suites, with zero failed/aborted/canceled/ignored/
pending; SBT/wrapper 0/0, lock=released. Final summary/log invocation, cwd, argv
and all new scenario outcomes were read. No source edits after final validation.
Shared serial wrapper, scoped permissions and normal caches retained.
No commit/publish/push, skill/agent edits, full Phase review or typed acceptance.

Next: retained-specific malformed/failed/throwing readback, authority and
absence/key-free cases. Transient-only/empty completion remains unimplemented.
Common host/runtime routing must choose branches deterministically from captured
EventStore selection; public skills and AI must not choose them. Application/UoW
dispatch, production assembly/CLI/MCP/restart/management remain open.
S12/P2-U15/P2-U16/P2-U05 remain unchecked; the Phase goal stays active.

> Local execution archive: `~/backup/journal/2026/11/cloud-native-component-framework/cncf-journal-cleanup-20261116/a-46519ec7-ea0d-401b-ac84-b5c5c87060f6/docs/journal/2026/09/2026-09-30-phase-88-retained-completion-isolation.md.gz`
