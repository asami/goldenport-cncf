# Prepared native transaction isolation, rollback and revocation

Date: 2026-09-30
Status: bounded executable evidence; no Phase/application acceptance

Previous turn progressed with focused-349 prepared-publication authority evidence.
This turn extends only ManagedEventObservationIsolationSpec on the admitted CNCF
Phase 88 dependency for the same active sm-workflow Phase 2 goal. Retain the
named cncf-goal-phase contract and simplified parent execution recovery override;
no new goal/Phase, skill/agent change or approval/history loop.

The test provider borrows the actual SystemNode-managed SQLite resource and live
lease. It implements the same admitted native observation, prepared write and
complete readback seams, using production native methods; failure/checkpoint/
revocation hooks are test-only. Host EventStore construction installs the fixed
policy. Existing pure selected-observation specs remain compatible and do not
gain preparation provenance merely because the provider has write capability.

Three new Given/When/Then scenarios:
1. Failure at GuardAdmitted, SuccessorPrepared, RootPrepared and BeforePublish:
   one write invocation, zero native commit hooks and zero readback snapshots.
   Fresh post-scope inspection proves open Workflow/Continuation, original
   cursor sequence 2 and absent new event/index. BeforePublish is after all
   cursor/event/index staging; this exercises prepared-route rollback rather
   than borrowing evidence from the legacy route.
2. Actual lease revoke at RootPrepared also rolls back (one invocation, zero
   commits/readbacks). Revoke after complete native readback instead leaves one
   committed transaction and one readback, but suppresses Verified. Fresh
   inspection distinguishes committed Workflow/Continuation, cursor sequence 3
   and event/index from the rollback case. Neither case automatically retries.
   Storage uncertainty never creates an application/dispatch result here.
3. A separate JDBC writer, zero busy timeout and immediate transaction admission,
   tries to mutate the cursor at RootPrepared, AFTER frozen observation
   validation and Workflow staging. Latches make the race deterministic; it
   receives exact SQLITE_BUSY error code 5. The prepared transaction commits
   once and verifies nine actual records: Workflow pair, cursor, new event/index
   and two retained event/index pairs. An explicit later writer update succeeds
   with one affected row, proving lock release; this is not publication retry.

Focused-350 passed 232/232 in 26 suites, zero failures/aborts/canceled/ignored/
pending; SBT/wrapper 0/0, lock=released. Same session was observed throughout its
full-source compile and tests; no second SBT started. Persistent summary and
scenario log read and match invocation/cwd/unchanged 26-suite argv. No source
edits after final validation. Shared serial wrapper, scoped first-attempt
platform access and normal caches retained. No commit/publish/typed acceptance.
Invocation: SMWF-CNCF-PREPARED-ISOLATION-DIRECT-20260930-350.
Cwd: /Users/asami/src/dev2025/cloud-native-component-framework.
Log: /tmp/skill.cncf.d/cncf-sbt-41838f95b52e5713f15238e0c108a9298d95ec0f00104e15c681846652e92088-023e9c7cda948f7e60a122f6d7cb64a4/49034-20260930T115407Z.log
Validated ManagedEventObservationIsolationSpec.scala SHA-256:
c0cd6f3c21a5eb5f51f474e8db882094a8c0c91f346345b0c532dc8532432f80.
Production preparation/publication sources unchanged from focused-347.

Next implementation: all-retained durable completion must validate the frozen
cursor/event/index observations within the SAME Workflow write transaction,
without cursor update/increment or dummy additional guard. Transient-only/empty
completion must perform no cursor work. Keep the legacy multi-guard plan's
nonempty guarded-update contract unchanged; use explicit internal typed native
composition, not another preflight transaction, private snapshot reconstruction
or direct Workflow SQLite. Preserve exact retained/new readback and no automatic
retry on stale/rollback/uncertainty. These and actual mixed application delivery,
UoW enlistment, production runtime/launcher/MCP/restart/management remain open.
This bounded SQLite evidence is not WAL/non-SQLite general assurance, crash
recovery, every cleanup/commit-exception proof, full review or Phase completion.
S12/P2-U15/P2-U16/P2-U05 remain open; goal remains active.
