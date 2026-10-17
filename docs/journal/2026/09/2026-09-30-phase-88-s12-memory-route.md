# Phase 88 S12: real same-owner memory staging through the admitted route

## Implemented evidence

DataStoreMultiGuardMemoryRouteSpec adds a test-only provider opt-in delegating
to the existing conditionalMultiGuardSameOwner primitive. The call goes through
WorkflowPreparedMultiGuardTransition and its host-admitted DataStoreSpace view;
no production provider or host bootstrap is enabled. The fixture checks:

- three independently revision-guarded updates and two Creates publish together;
- a stale third guard or collision on the second Create leaves all five records
  unchanged, including the first Create staged before that collision;
- each GuardAdmitted, SuccessorPrepared, RootPrepared and BeforePublish failure
  discards all staged changes;
- the existing same-owner restriction rejects a cross-owner plan even after
  the test host route admits its policy, without changing any stored record;
- a second explicit submission of the stale proposal returns NotMatched and
  performs no second update/Create. This is not an automatic retry policy.

Stored-record comparisons read the fixture-owned memory provider directly as
diagnostics. DataStoreSpace has no load method; the spec does not invent a
public readback API or use these reads to authorize Workflow completion or
transient delivery. The positive host view is test assembly, not a real
Subsystem memory bootstrap. No cross-owner atomicity, SQLite independent writer,
post-commit failure classification, public capability or S12 acceptance is claimed.
Production source code and selected-event policy were not changed in this slice.

## Validation

Attempt 300 reached terminal exits 1/1 with lock released: four compile errors
came from the spec calling nonexistent DataStoreSpace.load. No behavior tests
ran in that attempt. The fixture was corrected to diagnostic store reads.
Attempt 301 then passed **65/65 tests in eight suites**, zero failures/aborts,
SBT exit 0, wrapper exit 0 and lock released, with an accepted execution receipt.
The five new examples ran with the existing seven route/host/policy/codec/
conditional-update/selected-record suites. This is focused, not full-suite QA.

Invocation: `SMWF-P2-CNCF-P88-MEMORY-ROUTE-RETEST-20260930-301`.
Receipt:
`/tmp/skill.cncf.d/cncf-command-execution-001ae0fec78af662f54684d2510b4fc47dcb4812fcf9f113247ec773215a69b7-11d91bf54fd3cbf23c1354c6f362f628/command-execution-sha256-40962c79d08d511066c0fc3f65a3eb36654f549f6c0fa2ef412946586217310a`.
Log:
`/tmp/skill.cncf.d/cncf-sbt-c9a16c98056aa7da759eb3777cf4ff94cd10127ac8ac88d947fc34849575dcf6-09091c9dc087c6df98b6e063dea7ff5a/91896-20260930T011326Z.log`.
Agent Use: registered-custom-role cncf_command_runner, gpt-5.6-luna / medium,
`/root/sbt_receipt_577a88bfffa9548cebe4`; actual receipt identity matched.

Subsequent changes are documentation only. No commit, publication or runtime
activation. S12, P2-U15, P2-U16 and P2-U05 remain unchecked. The next contract
must provide admitted same-provider readback of every guarded/Create record,
not just the two-record Transitioned result, before it can authorize success
or delivery. Cross-owner and SQLite acceptance still require their own real
transaction, rollback and independent-writer specs.
