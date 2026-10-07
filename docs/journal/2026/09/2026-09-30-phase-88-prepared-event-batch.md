# CNCF Phase 88 S10: prepared EventRecord batch

Date: 2026-09-30
Status: bounded P2-U13 accepted; full P2-U05 open

## Boundary and implementation

`WorkflowPreparedDataStoreTransition.withEventRecordsC` admits one nonempty
batch of fully issued persistent transactional EventRecords, one explicit event
collection, distinct canonical Create keys and strictly increasing distinct
positive assigned sequences. It rejects an event collection already used by
the prepared plan. Each record first passes S9's strict canonical V1 codec,
including fully issued EventId timestamp/entropy. Only then does key derivation
create S8's Create-only side effect. The existing S7 plan validates the complete
same-store effect set; the canonical conditional transition performs one
guarded native commit. Preparation publishes nothing; a late identity collision
rolls back the batch, root and successor together. No EventStore append call
is introduced.

This is a composition seam only. The caller still needs an authoritative
global sequence allocator, durable committed-delivery lookup, EventStore
append/query/replay semantics, active UnitOfWork data interception and closing
program enlistment. The method is not AtomicWorkflowSuspensionEngine, a
production binding or a restart/transport Operation.

## Executable evidence

Six new Given/When/Then examples in
`WorkflowPreparedDataStoreTransitionSpec` exercise memory and SQLite:
two-event no-precommit publication and exact readback, invalid/duplicate/
unordered batch rejection, and late collision rollback. Existing EventRecord
codec and canonical Create specs join the focused selection.

- Attempt 272: raw 43/44 tests; one test fixture compared Record instances,
  and the command receipt rejected concurrent repository-tree drift. Not accepted.
- Attempt 273: terminal SBT failure before tests because the Cozy development
  runtime classpath was stale. Not product validation.
- Prerequisite 274: `cozyExportRuntimeClasspath` in Cozy validation-only scope,
  exit 0/0, lock released, receipt accepted.
- Focused 275: 44/44 tests, three suites, exit 0/0, lock released, receipt accepted.
- Regression 276: 1190 succeeded, 143 suites, zero failed/aborted, eight
  existing opt-in MySQL cases canceled, nine placeholders pending; exit 0/0,
  lock released, receipt accepted on the first S10 tree.
- After the codec-first admission correction, focused 277 stopped before tests
  because concurrent Cozy source edits invalidated the prior classpath.
- Prerequisite 278: current Cozy validation-only classpath refresh, exit 0/0,
  lock released, receipt accepted.
- Final focused 279: 44/44 tests, three suites, exit 0/0, lock released,
  receipt accepted.
- Final regression 280: 1190 succeeded, 143 suites, zero failed/aborted,
  eight existing opt-in MySQL cases canceled and nine placeholders pending;
  exit 0/0, lock released, receipt accepted.

Final focused and regression receipts bind the identical complete SM/CNCF/Cozy tree:
`88bbea9f991d101e33b853c506b6904208b4742b0cf4217854d80000ec3a78ea`.
This validates the selected producer source tree before the subsequent Phase,
journal and specification-clarification edits; those documentation edits are
not a new product test receipt.

Accepted receipt locators:

- Focused:
  `/tmp/skill.cncf.d/cncf-command-execution-07e52a9ef45bd5676191216edc18481b0d106065cc8c789dfff93132ddffcde7-4a24d153b432e90e74edad1e9d62ae31/command-execution-sha256-65c360f1dcd90e3148578994ee45af2d7d3535ed4e0b7f8a404cfcbd532673a3`
- Regression:
  `/tmp/skill.cncf.d/cncf-command-execution-c3f8fe1e2f9ece5a414c0cbc60cae4e810ff5fe1601a75ec519b706886921ee3-3b9c9bbb5838d873b0256201dd45aee1/command-execution-sha256-01abfd3f3d77b89c437c1c9cfdce01a75c6354688155978b53b15b269b12d114`

Owned source identities at accepted test tree:

- `WorkflowPreparedDataStoreTransition.scala`:
  `26cd65c07bfbc31b90a2d4fa176eef97e30fe757c75f17a23f3b7e61b520de38`
- `WorkflowPreparedDataStoreTransitionSpec.scala`:
  `cdb4a8d4143f3bd7c9b1f23ae4287e61239e97e7399a41b97c35e5a19d56cbcf`
- `event-record-canonical-storage.md`:
  `f02637212d98d2ce1bc88c7a3015950bd829f422e40fcacc383d176d64e3662b`

No source commit, local publication, downstream artifact refresh or full joint
runtime acceptance occurred. Continue Work B with durable sequence/delivery
admission and active UoW/closing-program composition; preserve P2-U05 open.
