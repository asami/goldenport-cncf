# Phase 88 S9: bounded canonical EventRecord storage codec

Date: 2026-09-29
Status: S9 bounded producer codec/Create bridge focused/regression-validated on 2026-09-30
Work A: full joint runtime recovery (P2-U05 remains open)
Work B: pure event-fact encoding and Create-only same-store composition

## Initial observed boundary and implementation (2026-09-29; historical)

Existing EventEngine commits EventStore append and DataStore effects separately;
ordinary UnitOfWork still writes data directly. TransactionContext does not
supply the full shared prepare/commit/abort implementation. Record atomicity
alone does not establish active UoW or closing-program enlistment.

Add EventRecordJsonV1 with pure encode, strict decodeC and createSideEffectC.
Retain typed EventId components, nanosecond time, flags/lane/status, attributes,
exact admitted payload types and already-assigned positive sequence.
Payload is a bounded closed tagged algebra: String, Boolean, Int, Long,
Scala BigDecimal, immutable Vector and immutable Map[String, Any].
Preserve numeric type/precision and decimal scale; sort map keys.
Reject unsupported values, unknown fields, duplicate/noncanonical JSON and
representation bounds without text conversion, truncation or regeneration.

The private CommittedDeliveryLookupKey has no admitted durable catalog-bound
representation. Reject an event carrying it; never silently discard it.
The Create bridge additionally requires persistent transactional facts and an
explicit canonical collection. It supplies only a normalized Create value;
the containing S7 plan owns store/target admission and S8 owns native insertion.
A Create collision rejects and rolls back; this is not EventStore's retained
first-record-returning append or committed-delivery deduplication.

## Owned scope and executable specifications

CNCF new source/spec files:

- src/main/scala/org/goldenport/cncf/event/EventRecordJsonV1.scala
- src/test/scala/org/goldenport/cncf/event/EventRecordJsonV1Spec.scala
- docs/spec/event-record-canonical-storage.md
- docs/design/event-record-canonical-storage.md

Twelve executable examples cover typed canonical facts, map order, wire and
payload rejection, explicit bridge admission, an actual internal delivery key,
fifty generated payloads, memory/SQLite joint record insertion and collision
rollback, and SQLite close/reopen. At initial recording these had not executed.
Existing S1-S8 product code and unrelated work remain unchanged.
Cozy remains validation-only. No stage, commit, publication or downstream
CNCF artifact refresh is selected.

Preserved source SHA-256 identities, not accepted executable evidence:

- EventRecordJsonV1.scala: 55518dc0be07a013b6e76483db3a89c491dbbcbc8b68e156fa6347a3d477646e
- EventRecordJsonV1Spec.scala: c080be5baf091b321f0d795ba13ed4b2d5b776bbfe8f1aa4fead3bca4dcca4b3

## Focused attempt 267: rejected receipt and pre-test prerequisite failure

The original authorized instruction occurrence and full consumed-ID ledger
through 266 were preserved. Current continuation extended the exact observed
history to fourteen turns. Registered cncf_command_runner submitted the exact
typed receipt-first request once with scoped escalation and the shared SBT lock.

Evidence ID: SMWF-P2-CNCF-P88-S9-FOCUSED-20260929-267.
Selected command: testOnly EventRecordJsonV1Spec, EventStoreBaselineSpec,
EventEngineLaneSemanticsSpec, EventEnginePrepareSemanticsSpec,
DataStoreCreateSideEffectSpec and WorkflowPreparedDataStoreTransitionSpec
(all fully qualified org.goldenport.cncf packages).

Receipt creation rejected with COMMAND_EXECUTION_RECEIPT_INVALID:
"validation command changed the repository tree; validate the settled tree in
a new attempt". No accepted receipt or retained completion marker was returned.
Do not reconstruct acceptance from the raw logs.

Read-only investigation of the original raw log/summary found the earlier
failure: development classpath stale at
/Users/asami/src/dev2025/cozy/target/cozy.d/runtime-classpath.txt.
generateInformationCmlModel stopped with generator exit 2 before S9 compilation
or any selected test. Raw summary reports sbt_exit=1, wrapper_exit=1 and
lock=released; no suite/test counts exist. This is unaccepted diagnostic evidence,
not executable acceptance. Repository drift's exact cause/timing is not established.

Raw original summary:
 /tmp/skill.cncf.d/cncf-sbt-68b4b78f187af530474d40d77139a01189567b01a92d22112b367f8da02f4a25-7e8c6808ef9b287cde23c25ed55a7581/62433-20260929T102231Z.summary.json
Raw original log:
 /tmp/skill.cncf.d/cncf-sbt-68b4b78f187af530474d40d77139a01189567b01a92d22112b367f8da02f4a25-7e8c6808ef9b287cde23c25ed55a7581/62433-20260929T102231Z.log

Read-only process inspection after termination found no SBT/receipt helper/
serial-wrapper process. Both the environment-selected TMPDIR shared lock and
/private/tmp alternative were absent. No process was killed, no lock removed,
no consumed attempt reused, and no alternate execution path was attempted.
No regression was selected. Ledger now includes consumed 267.

Execution profile: gpt-5.6-luna / medium; runtime_source=registered;
agent_type=cncf_command_runner; execution_route=registered-custom-role.
Runner: /root/sbt_receipt_9b0ad2ddf4bf1b69bcaf.
Definition: /Users/asami/.codex/agents/cncf_command_runner.toml.

## Continuation and remaining authority gates

Keep P2-U12 unchecked. Once the full observed SM/CNCF/Cozy tree is settled,
select the exact project-owned Cozy cozyExportRuntimeClasspath prerequisite
with its validation-only authority and a fresh attempt. Then run fresh focused
and bounded component/workflow/datastore/unitofwork/event regression attempts;
accept both only on an unchanged complete tree, preserving the ledger/origin.

Full P2-U05 additionally needs admitted durable private delivery identity and
global sequence authority, EventStore append-result semantics, staged data and
closing-program participant admission, and one Consequence-aware native
EventEngine/UoW prepare/commit/abort boundary. No post-commit callback, shadow
store, application lock or new transaction engine substitutes for these gates.
Production Factory/service/Operations/launcher/server/MCP, separate-process
recovery and downstream artifact consumption remain open.

## Accepted continuation: 2026-09-30 (268-271)

The direct continuation preserved the original instruction occurrence and the
full consumed-ID ledger through 267. Native role=user history was re-observed;
the typed renderer retained the exact fifteen-turn suffix. All four attempts
used registered cncf_command_runner, unchanged receipt-first helpers and the
shared serialized SBT wrapper. No transport recovery or fallback was needed.

Cozy prerequisite-268 succeeded with an accepted terminal receipt: the exact
project-owned cozyExportRuntimeClasspath task rebuilt the development generator
classpath. Cozy remained validation-only; no source/Git/publication changes
were selected. Focused-269 then compiled successfully and ran fifty tests:
45 succeeded, five failed, six suites completed, none aborted. Its accepted
terminal failure receipt makes those failures attributable; the prior raw
rejected 267 run remains unaccepted and is not promoted.

### Bounded repair derived from executable failures

Two contract defects and one fixture defect were observed:

- EventId.generate leaves optional timestamp/entropy unresolved while its
  UniversalId base privately issues actual operational parts. Reconstructing
  those absent options issues a different ID. Admit only explicit timestamp
  and entropy; reject missing options without codec-side regeneration.
- SQL's existing generic column decoder interprets JSON-looking text as a
  Record. Wrap the exact JSON body with cncf.event-record.v1: for storage, with
  pure encodeStorage and strict decodeStorageC. Reject bare JSON, wrong/missing
  prefixes and noncanonical bodies. Do not change the shared SQL decoder.
- Replace the invalid one-segment ComponentId namespace in the real typed
  committed-delivery-key fixture with test.event.Event.

Canonical datastore keys now use opaque EventId.value, not presentation print.
Twelve S9 examples additionally verify operational ID value, missing-ID rejection,
strict prefix admission and exact stored text in memory/SQLite and after reopen.
The normative spec/design were amended before the source/spec repair. Only the
four already-owned S9 files changed; prior S1-S8 and unrelated work were preserved.
The initial contract's absent-option/raw-JSON description above is historical
and is superseded by this executable-evidence-derived repair.

### Accepted focused and bounded regression

Focused-270: 50/50 successful, six suites, zero failed/aborted/canceled/pending.
Regression-271: 1184 successful, 143 suites, zero failed/aborted, eight existing
opt-in MySQL cases canceled and nine existing placeholders pending.
Both have sbt_exit=0, wrapper_exit=0 and lock=released. Parent verification
accepted exact request/executor/invocation/completion identity.

Both successful receipts bind this same complete SM/CNCF/Cozy tree:
62c05db0630afe3c42b86f9b3cfc45376c30c52c949709330d0f27285f18c8b3.
The four owned S9 identities match both receipts and the pre-documentation
live files:

| CNCF owned path | SHA-256 |
| --- | --- |
| src/main/scala/org/goldenport/cncf/event/EventRecordJsonV1.scala | 0d00350d11703af11d3360d34c65d3e229f438eb485558e4654e90254b0ca906 |
| src/test/scala/org/goldenport/cncf/event/EventRecordJsonV1Spec.scala | 9387d8864f09c36e9797daedf39f821b2970079381387844fb7cca5d410bb9c3 |
| docs/spec/event-record-canonical-storage.md | ea9afb4af9e178a941f78722b94ed7d312cda85a615b8643821032d1b1c0a792 |
| docs/design/event-record-canonical-storage.md | de69dcb5ce78f53f4e070325f7241c8a65566edd441d759f646e85be5b2d340e |

### Immutable receipts and terminal markers

Attempt 268: success; receipt SHA-256 de45822cb74e57fc61e5fc2e38cc2a165d413e3f11f3c400e1b71d7df724767c.
Locator: /tmp/skill.cncf.d/cncf-command-execution-40d815781558e8f8367e273f99f38d1801d0925fd9f44db2fc8b4f716b4a5a18-7b65907a099123972bf33d5a4855b26c/command-execution-sha256-de45822cb74e57fc61e5fc2e38cc2a165d413e3f11f3c400e1b71d7df724767c
Terminal: [cncf-sbt] completed: invocation=SMWF-P2-COZY-P88-S9-CLASSPATH-20260930-268 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-d94457a942947c9e4d1a3152a5fb372500b8778a331379de6590ef72c1e3ca7f-2aa496b71fb669da3f027b4d3439ba33/17284-20260929T160155Z.log summary=/tmp/skill.cncf.d/cncf-sbt-d94457a942947c9e4d1a3152a5fb372500b8778a331379de6590ef72c1e3ca7f-2aa496b71fb669da3f027b4d3439ba33/17284-20260929T160155Z.summary.json

Attempt 269: failure; receipt SHA-256 5fe5935ec3bd6636d7583105829b807f6b897f7094dc418bd58d1395e3526038.
Locator: /tmp/skill.cncf.d/cncf-command-execution-6322b38e3a7e987faba7a74c3d34558c689e6863ea26820b1ba253d3e691c96b-6d14dbd5f8b1be1f7c8ffa9fb1527d0c/command-execution-sha256-5fe5935ec3bd6636d7583105829b807f6b897f7094dc418bd58d1395e3526038
Terminal: [cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S9-FOCUSED-20260930-269 sbt_exit=1 wrapper_exit=1 lock=released log=/tmp/skill.cncf.d/cncf-sbt-b97c8e81c3071b20d00443686dc077fc7db5c4a3205c5873807cbceaeb895d64-ac116788af698603db46efa9c5b172f1/18394-20260929T160327Z.log summary=/tmp/skill.cncf.d/cncf-sbt-b97c8e81c3071b20d00443686dc077fc7db5c4a3205c5873807cbceaeb895d64-ac116788af698603db46efa9c5b172f1/18394-20260929T160327Z.summary.json

Attempt 270: success; receipt SHA-256 7553643d56d40687bdfc61e3a622f55baa46a6f602bffea307fd2685cd252dd9.
Locator: /tmp/skill.cncf.d/cncf-command-execution-b4031b9cbcb6a95ea3a641256b0051e6ea23bcf9a069f20c682089a2d42f396e-6dc3e9e58c6be607d7930acf90e84f91/command-execution-sha256-7553643d56d40687bdfc61e3a622f55baa46a6f602bffea307fd2685cd252dd9
Terminal: [cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S9-FOCUSED-20260930-270 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-acedfae3bf1936ef33feb7ba650d7a7ee01f581ce70edeca5a6679ebac448252-53a8345bbbed8e77c8b3f2b3d8cb1a46/25731-20260929T160844Z.log summary=/tmp/skill.cncf.d/cncf-sbt-acedfae3bf1936ef33feb7ba650d7a7ee01f581ce70edeca5a6679ebac448252-53a8345bbbed8e77c8b3f2b3d8cb1a46/25731-20260929T160844Z.summary.json

Attempt 271: success; receipt SHA-256 ad2fa6cae881bfb647f9750fa3f156aff552eee3e10fe2afaee60fd02580c95f.
Locator: /tmp/skill.cncf.d/cncf-command-execution-f7297ebfc11a70fe7b81b65ebc86b9c8fb8a3c6a72617151366ddef693ebcbe0-a8a641cfb509a1c9e9aea537763c0eff/command-execution-sha256-ad2fa6cae881bfb647f9750fa3f156aff552eee3e10fe2afaee60fd02580c95f
Terminal: [cncf-sbt] completed: invocation=SMWF-P2-CNCF-P88-S9-REGRESSION-20260930-271 sbt_exit=0 wrapper_exit=0 lock=released log=/tmp/skill.cncf.d/cncf-sbt-7ef3d39003304cf36dab9f4d1ff3f0a1856dde18b8cd58cefba7e74d88f9457c-1fb0fbbd63d9b4e5c81ce952ae6fd1bd/27375-20260929T161133Z.log summary=/tmp/skill.cncf.d/cncf-sbt-7ef3d39003304cf36dab9f4d1ff3f0a1856dde18b8cd58cefba7e74d88f9457c-1fb0fbbd63d9b4e5c81ce952ae6fd1bd/27375-20260929T161133Z.summary.json

Execution profile for all four: gpt-5.6-luna / medium;
runtime_source=registered; agent_type=cncf_command_runner;
execution_route=registered-custom-role; runner_health=healthy;
infrastructure_issue=none; role-resolution failure=none.
Deployed regular-file definition: /Users/asami/.codex/agents/cncf_command_runner.toml.

- /root/sbt_receipt_e1f9c55c9020a40babec (268)
- /root/sbt_receipt_2eb60ee7e872c8168254 (269)
- /root/sbt_receipt_2c7df01eced87236f7c8 (270)
- /root/sbt_receipt_c18aaa9511ee7e070d62 (271)

S9 / P2-U12 is now bounded producer-source executable acceptance. Full P2-U05,
Phase 88 and Phase 2 remain in progress. No EventStore sequence allocator,
private delivery-key persistence/deduplication, active UoW data/closing-program
enlistment, AtomicWorkflowSuspensionEngine, production binding/Operations/
launcher/server/MCP or separate-process production proof is claimed.
No downstream CNCF artifact refresh, commit or publication occurred.

Post-validation updates are documentation-only progress/evidence records.
They are not the complete accepted test tree and grant no current-tree
release/commit freshness. A future release must consume the canonical
freshness/validation contract for its then-selected exact tree. The ledger
retains every consumed attempt through 271 and all historical failure evidence.
