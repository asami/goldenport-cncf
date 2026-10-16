# Phase 88 S8: Create-only canonical side records

Date: 2026-09-29
Status: bounded Create-only effects focused/regression-validated; full joint runtime remains open
Work A: full joint runtime recovery (P2-U05 remains open)
Work B: append-only side-record admission inside the existing native transaction

## Observed prerequisite

S7 accepts normalized Save/Delete effects. Save is an upsert in memory/SQL:
mapping EventRecord directly to Save would overwrite a previously retained
event identity. Reading absence before preparation is not a commit-time guard.
Add EntityVersionedSideEffect.Create as a closed normalized record value.
Keep Save and Delete unchanged. A duplicate Create is a structured datastore
duplicate failure, never overwrite, successful idempotent append, NotMatched,
or automatic retry. The check and insert must share the existing provider
native transaction with root, successor and all other side effects.

Use memory replacement-state publication and SQL insert, never a second store,
application lock, new SQL backend, callback, general predicate or independent
CRUD call. Collision after other staged changes must roll back the whole plan.
Canonical plan normalization applies existing record/target/limit checks to
Create. The same shared effect algebra also serves ordinary versioned mutation:
handle that route and entity normalization compatibly, not a partial match.

## Frozen ownership and executable acceptance

CNCF source ownership:
- datastore/EntityVersionedMutation.scala
- datastore/EntityConditionalTransition.scala
- datastore/DataStore.scala
- datastore/sql/SqlDataStore.scala
- entity/StandardEntityConditionalPart.scala
- entity/ContentBodyStoragePolicy.scala

Specs: new datastore/DataStoreCreateSideEffectSpec.scala and additions to
workflow/WorkflowPreparedDataStoreTransitionSpec.scala. Preserve existing
acceptance examples and all unrelated dirty work. Paired normative contract:
docs/spec/entity-conflict-and-conditional-transition.md and matching design.
Cozy is validation-only; no publication, artifact refresh, stage or commit.

Acceptance covers memory/SQLite creation, exact identity collision (including
identical content), rollback of earlier effects and both guarded records,
stale/replay failure closure, admission limits/normalization, ordinary versioned
mutation compatibility, prepared Workflow collision after preparation,
independent SQLite writers with different roots sharing one created identity,
resource reopen and generated normalized values.

## Remaining boundary

This is not EventRecord serialization, EventStore append/load/query/replay,
sequence allocation, committed delivery-key retention, active UoW enlistment,
closing-program interpretation or AtomicWorkflowSuspensionEngine. Those must
use admitted canonical data without weakening append-only/order/authority
semantics. Production service/Operations/launcher/server/MCP, separate-process
recovery, artifact consumption and management remain open.

## Implemented boundary (acceptance pending)

The closed Create case is normalized by the existing canonical record validator.
Memory stages replacement maps; SQL checks/inserts on the existing native
connection and primary key. A collision rolls back root, successor and earlier
Create/Save/Delete effects. Ordinary versioned mutation and entity/content-body
normalization handle the same closed case; Save/Delete semantics are unchanged.
SQL schema preparation may precede the record transaction; DDL rollback is not
claimed.

Fourteen new examples cover this boundary: twelve in
DataStoreCreateSideEffectSpec and two prepared Workflow collision examples.
Independent SQLite providers use different guarded roots but compete for one
side-record identity; close/reopen verifies one winner without overwriting the
loser's records. Fifty generated normalized payloads retain exact values.
The initial fixture compared newly constructed Consequence objects; the repair
compares the structured datastore-duplicate taxonomy, retaining provider facets
and all product guarantees. Invalid Create values also reject on the ordinary
versioned-mutation route.

## Historical validation ledger and stopping point (261-264)

All attempts observe the selected SM/CNCF/Cozy repository scope. Cozy remains
validation-only. No command changed the selected publication or commit scope.

- Focused-261: pre-start intake transport lost the locator digest; one typed
  same-runner recovery restored the unchanged request. SBT then stopped at the
  stale Cozy runtime-classpath prerequisite, before Test compilation/execution.
  Its accepted receipt is terminal failure, not executable acceptance.
- Cozy prerequisite-262: cozyExportRuntimeClasspath completed with exits 0/0
  and summary lock=released. Receipt creation rejected observed repository
  drift; no accepted receipt or retained terminal marker exists.
- Focused-263: raw summary reports 55 successful and two failed fixture
  comparisons, six completed suites, exits 1/1 and lock=released. Receipt
  creation again rejected observed tree drift. The fixture was then repaired.
- Focused-264: raw summary reports 57/57 successful, six completed suites,
  no failed/aborted/canceled/pending examples, exits 0/0 and lock=released.
  Receipt creation again rejected observed tree drift. This is an observed
  passing run, NOT immutable executable acceptance.

The three rejected attempts report COMMAND_EXECUTION_RECEIPT_INVALID:
"validation command changed the repository tree; validate the settled tree in
a new attempt". No receipt or terminal marker is reconstructed from raw logs.
Read-only ownership checks found no remaining SBT/helper/wrapper process and
the exact shared lock absent after each rejected attempt. Comparing currently
dirty file hashes with the accepted failure-261 capture found an unrelated
CozyVideoStoryboardNarrationSpec change and our deliberate fixture repair;
this does not identify the exact cause/timing of each command's drift.

Stop repeated validation against the moving observed tree. Keep S8/P2-U11
unchecked. No regression was selected or run. Next, once the selected observed
tree is settled, allocate a fresh focused attempt followed by the bounded
component/workflow/datastore/unitofwork/event regression. Preserve the full
consumed-ID ledger through 264 and original authority. Do not omit Cozy from
the observation boundary merely to make a receipt pass.

Accepted failure-261 receipt:
`/tmp/skill.cncf.d/cncf-command-execution-559449271c43f291647ef9bd901b9c8a70b37132d693c65364ce3dbd59286869-8248d80736bb4dc044d6b721236ae7de/command-execution-sha256-9d54d35019120dca970132609c70e6990ebfc3d49e94a5564879c55ef7af7ca8`
SHA-256: `9d54d35019120dca970132609c70e6990ebfc3d49e94a5564879c55ef7af7ca8`.
Failure tree: `9940493ed0e935b4254baad41b8de4b8edb1c33925552cc70616514fca36509d`.
Terminal classification: sbt_exit=1, wrapper_exit=1, lock=released.

Raw unaccepted summary locators:

- 262: `/tmp/skill.cncf.d/cncf-sbt-bb0c87e390a58ea764e0080121acd80371d44541d169b3c147909bf625d5aefa-81dddfe75faa723baadb05420944433d/33863-20260929T093127Z.summary.json`
- 263: `/tmp/skill.cncf.d/cncf-sbt-c36742c431334b8b1049c8ccdc379f729457f63a913f3fea79244d41a7f59018-de8d3a06facfad0732bac8d8048189ff/35641-20260929T093413Z.summary.json`
- 264: `/tmp/skill.cncf.d/cncf-sbt-1a5a2512cb756491131a1cbb078472e720bbf228011ef0a3e8d7bf26298a29d2-02b68369187060b300d778359f5023d4/37514-20260929T093732Z.summary.json`

Execution profile: gpt-5.6-luna / medium; runtime_source=registered;
agent_type=cncf_command_runner. IDs, in command order:

- /root/sbt_receipt_d051ba0eb0475b3b890f (261; same-runner pre-start recovery)
- /root/sbt_receipt_02ae3b6c1e9133b05b49 (262)
- /root/sbt_receipt_a6ad86a5998e7f584196 (263)
- /root/sbt_receipt_206a0063ca28049a960e (264)

## Preserved implementation identities (not an accepted test tree)

SHA-256 values below identify the preserved eight owned source files. They do
not substitute for a successful immutable command receipt.

| CNCF source (under src/) | SHA-256 |
| --- | --- |
| main/scala/org/goldenport/cncf/datastore/EntityVersionedMutation.scala | d01099ddc4bfddbb7036ca9ec3c3517c21be49dab0c5927863e2dca1ef34a65c |
| main/scala/org/goldenport/cncf/datastore/EntityConditionalTransition.scala | e04accfae855e29f711f664cb4e1786769e89440c3180852d173f48c0b025c5b |
| main/scala/org/goldenport/cncf/datastore/DataStore.scala | 770c4c790bfe356ac863d49215b5494798045f0956b62fccb1c7c85c440d7fe5 |
| main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala | 5069de36f9a2b1eaa37a11e9719b5f02776e07c45ac12152a7bb2078d9f938ad |
| main/scala/org/goldenport/cncf/entity/StandardEntityConditionalPart.scala | 79142b8820648e62d1d8f1964b3feee8c55d7acd5ae93a3ac040c8a1e8d57aac |
| main/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicy.scala | f0d05744b85417d8ae32161dc94e8cc6e790b1b553f55c62d0f6b21431fa1195 |
| test/scala/org/goldenport/cncf/datastore/DataStoreCreateSideEffectSpec.scala | f93a5ffd46fa4434a2cd356e76201800f48e9ff44f8031c5ef97a0b9d1199ee2 |
| test/scala/org/goldenport/cncf/workflow/WorkflowPreparedDataStoreTransitionSpec.scala | 981b603ea1c59094df3440c64736643394eb6292c64f01d7b15f6446de4b4677 |

## Accepted continuation: focused-265 and regression-266

The next direct user continuation retained the original instruction occurrence
and full consumed-ID ledger through 264. It changed no S8 source or command
scope. The typed history renderer selected the exact thirteen-turn suffix.
Registered custom runners executed through the unchanged receipt-first helper
and shared serialized SBT wrapper; no transport recovery or fallback was needed.

Focused-265 passed 57/57 tests in six suites, with zero failed/aborted/canceled/
pending. Regression-266 ran the same selected component/workflow/datastore/
unitofwork/event scope as S7: 142 completed suites, 1172 successful tests,
zero failed/aborted, eight existing opt-in MySQL examples canceled and nine
existing placeholders pending. Both wrappers exited 0/0 with lock=released.
Parent receipt verification accepted exact invocation/executor/terminal identities.

Both accepted receipts bind the same complete SM/CNCF/Cozy tree:
`4209d7ce20f0f35ba1a9a3608c4a36d971e7eef9cb8d8b83f546d4721a2ba27b`.
All eight owned source SHA-256 values match between receipts and the preserved
implementation identities above. Earlier raw/rejected 262-264 evidence remains
historical and is not promoted into accepted validation.

Focused receipt:
`/tmp/skill.cncf.d/cncf-command-execution-286acd5d5f94c5514d6205c31193f0a090cbd451152623ed594efe65c013ae9f-f4974a495012fddb203fbd68b75c0625/command-execution-sha256-0e653819b3238f6380a7af273a67f46e43d652ff13944df94a1b8d99b9cbfac6`
SHA-256: `0e653819b3238f6380a7af273a67f46e43d652ff13944df94a1b8d99b9cbfac6`.

Regression receipt:
`/tmp/skill.cncf.d/cncf-command-execution-9eb945330d70930c03d614e5f0bccd6255e659623e9d3da0fc22421d87fb51b8-728fdb04fff14e60229e69df93f4de93/command-execution-sha256-69811fea68b57e417299da835f34cc8712ebde6a27d4026107dff353eb5e1666`
SHA-256: `69811fea68b57e417299da835f34cc8712ebde6a27d4026107dff353eb5e1666`.

Terminal markers:

```text
[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S8-FOCUSED-20260929-265 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-58ac133034f06cddd9f3d3c21ef44ce4780a4d01fa173cdb6c442bfd1c0bc133-74d1b5f2283ea184b8aa7fa1a6d86292/49402-20260929T095411Z.log summary=/tmp/skill.cncf.d/cncf-sbt-58ac133034f06cddd9f3d3c21ef44ce4780a4d01fa173cdb6c442bfd1c0bc133-74d1b5f2283ea184b8aa7fa1a6d86292/49402-20260929T095411Z.summary.json
[cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S8-REGRESSION-20260929-266 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-888d23929e76c2fac63c9ebf12d4bb75682e5e21250b3ea2afe813795bc00ad8-88fdf223902b8f385a532d779c1655b3/50949-20260929T095646Z.log summary=/tmp/skill.cncf.d/cncf-sbt-888d23929e76c2fac63c9ebf12d4bb75682e5e21250b3ea2afe813795bc00ad8-88fdf223902b8f385a532d779c1655b3/50949-20260929T095646Z.summary.json
```

Execution profile: gpt-5.6-luna / medium; runtime_source=registered;
agent_type=cncf_command_runner; execution_route=registered-custom-role;
runner_health=healthy; infrastructure_issue=none; role-resolution failure=none.

- /root/sbt_receipt_aa3663446b6b4c2aaca5 (265)
- /root/sbt_receipt_3ca9f32413b3751d75b7 (266)

S8 / P2-U11 is now bounded producer-source executable acceptance. Phase 88 and
Phase 2 remain in progress. P2-U05 stays unchecked: no EventRecord codec,
sequence allocator, EventStore adapter, active UoW/closing-program enlistment,
AtomicWorkflowSuspensionEngine or production claim/service/Operation has been
provided. No downstream artifact refresh, separate-process runtime proof,
launcher/server/MCP proof, commit or publication is claimed.

Post-validation changes are documentation-only progress/evidence records.
They are not the accepted complete test tree; no current-tree release/commit
freshness authorization is inferred. Before a later release, apply the canonical
freshness/validation contract for the then-selected exact tree.
