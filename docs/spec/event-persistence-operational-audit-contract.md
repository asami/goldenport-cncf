# Event Persistence Operational Audit Contract

status=normative
phase=69.7
step=JM69-09
slice=C7
initial_authoring_status=authored_test_pending
validation_status=selected-validation-passed
review_status=independent-complete-Step-review-pending

Companion to [Job operations](job-operations-contract.md), [Job lifecycle audit](job-lifecycle-operational-audit-contract.md) and [Event persistence design](../design/event-persistence-operational-audit.md).

## Normative rules

### E1 — Fixed observation

Only private[cncf] `EventPersistenceOperationalAudit.observe(expectedName: String, expectedKind: String, result: Consequence[Vector[EventRecord]])(using ExecutionContext): Unit` observes a normally returned acknowledgement. Catch observation NonFatal only. No source, provider, engine, store, Task, security, payload or attributes getters, clock/ID generation, parser, thread, cache or metric calls. Do not read arbitrary DomainEvent or mutate original result/records.

### E2 — Native source order

Keep DefaultEventBus authorization, captured name/kind, subscription selectors/priority/registration order and persistent-before-dispatch. Only persistent Some(ctx) evaluates original emit once, observes its exact normally returned result under that ctx, then applies original result.map(_ => ()). Context-free and nonpersistent branches, public interfaces, invocation counts and dispatch failure mapping remain unchanged. No domain emit/selector/dispatch catch.

### E3 — Original identity admission

Success reports succeeded with event-identity-available Boolean. Identity requires exactly one nonnull EventRecord, nonnull captured name/kind matching original record, Stored status, NonTransactional lane, persistent=true, nonnull createdAt, nonnegative sequence and original nonnull EventId.value nonempty/no ISO control/at most512 UTF8 bytes inclusive. Use exact value/sequence/createdAt; never generate, parse, normalize or guess. Other Success receipts retain Success with identity=false and no ID/sequence/lane/time atoms. Failure reports failed/identity=false without conclusion or exception text, no rollback/no-effect claim. Null/unrecognizable observation may suppress. Native final EventId empty canonical value is unreachable; keep defensive guard and use actual explicit epoch/entropy fixtures for512/513/control/null, never reflection or seams.

### E4 — Closed atoms and privacy

Only event-identity-available Boolean; when true, event-id, event-sequence Long, event-lane=non-transactional and occurred-at=original createdAt.toString. No Event name/kind/type, payload/attributes, Job/Task/attempt/Operation/Workflow/definition, CallTree, recovery, subject/tenant/credential/provider/path/business selector or failure text. No inference from arbitrary attributes; original authorized canonical sources and C4-C6 owners retain their fields.

### E5 — Fixed standard transport

One internal eventPersistence(succeeded: Boolean, atoms: Vector[(String, String | Int | Long | Boolean)]) calls existing unchanged transport with event.persistence.audit.v1, audit.kind=event-persistence, operation=persist, outcome=succeeded/failed and record-kind=acknowledgement. Existing three Job frames preserve bytes/order/guards/behavior. Original trace/correlation/scope guards512/512/128 suppress unsafe context; unsafe optional span is omitted. Explicit primitive JSON equals Record.dataAuto atoms, body<=4096 and full line<=8192 UTF8 bytes. No truncation/retry/fallback, generic emitter, logger replacement or ownership move.

### E6 — Point-in-time response

Acknowledgement is a source response, not final delivery success, durable Event/Workflow recovery, commit/authenticity/atomicity certificate or all17 acceptance. Store Failure may follow committed effects; handler Failure/throw may follow acknowledgement. Preserve source/dispatch ordering and original conclusion/throw object. No observation for authorization refusal, source throw before normal return, context-free publish or persistent=false. No global context/output fallback.

### E7 — Genuine fixtures

Private[cncf] Event test support owns synchronized prefix-filtered recording/throwing backends, allEvents for prior Job frames, strict UTF8 parser, counters and real EventStore delegate fault modes. Reuse existing private[cncf] JobProducer support for context/backend restoration. Native Event fixtures use EventEngine.noop/EventStore.inMemory/DefaultEventBus; native Job integration lives in job package and uses unchanged private[job] JobLifecycleOperationalAuditFixtures.withNative(useBus=true). Test/parallelExecution=false remains configured; no production seam or fixture visibility widening.

### E8 — Executable specification

Exactly EA1..EA6, EI1..EI6 and EB1..EB2 use AnyWordSpec/GivenWhenThen, adjacent actual Given/When/Then and should. Each has afterWord metadata spec:event-persistence-operational-audit/example/rules/phase:69.7/slice:C7. Exactly EA1 and EI1 use ordinary shrinking Prop.forAll, seed69709602L, minimum24, checked.passed and checked.succeeded>=24. Preserve existing EventBus/prior Job leaves; no printed snapshots.

### E9 — Truthful documents

This normative companion, design and dated journal were authored before production source, with initial authored_test_pending and no invented execution counts. Append current C7 only; preserve C6 receipts/history. The fourteen leaves below have passed selected parent execution; independent complete-Step review remains pending. Phase69.7/JM69-09 remain IN_PROGRESS; JM69-10 OPEN and original completion gates unchecked.

### E10 — Exact ownership and remaining acceptance

Only the frozen fifteen paths. All17 integrated security/retention/hostile operational proof, original CPBs/C1-C6 M2/HYG/DVL, real JM69-10 two-process/CBD, Phase69.4-69.6 ancestry/deferred audit, independent complete-Step review/local commits, aggregate full/downstream validation, sole epoch1 Phase review and final local release remain required. No EventEngine/Store/Reception/Job/core/config/build/generator/dependency/other repository/Phase87/93/94/97/shared management edits. Worker executes no validation/scripts/runtime/self-review/commit/delegation; parent owns verification, selected18/ordered110 validation and transitions.

## Seventeen-facet responsibility matrix

| Facet | C7 responsibility and original owner |
|---|---|
| Job | No C7 field; genuine cncf.job.id remains canonical, C4-C6 native Job references unchanged. |
| Task | No getter/field; C5 native Task and C6 actual root Action references remain. |
| attempt | No field; native retry/attempt and C5 flags unchanged. |
| Event | Exact admitted original EventId/sequence/lane/time after normal source return; no name/type/payload. |
| Operation | No field; C6 actual ActionId and authorized resolved selector retained, no definition identity invention. |
| Workflow | No field; C6 original instance/definition association and canonical history retained. |
| definition | No field; C6 actual JCL snapshot ID/presence and authorized key/source retained. |
| trace | Exact original guarded context traceId.value. |
| span | Exact valid optional context spanId.value; invalid omitted. |
| CallTree | No traversal/field; C4-C5 presence and authorized canonical consumers retained. |
| payload | No traversal/formatting; canonical Event/Job/input/result remains original. |
| recovery | No certificate/field; native/C5 recovery flags and real JM10 acceptance remain. |
| subject | Omitted without security/principal reads. |
| tenant | Omitted without tenant/authorization reads. |
| credential | Omitted including private failure/exception/nested data. |
| provider | Omitted without lookup/invocation/name/error reads. |
| path | Omitted including paths/URLs/business selectors and failure text. |

## Executable leaf map

All leaves initially had authored_test_pending status: source authoring was not execution evidence. Each source anchor below now has passed-selected status; every leaf retains independent-complete-Step-review-pending. Actual REP-004 completed18 suites/142 succeeded/0 failed/0 pending; ACC-001 completed110 suites/896 succeeded/0 failed with1 existing SqliteDataStoreSpec pending. These root Test/testOnly selections are documented in the [canonical C7 journal](../journal/2026/10/2026-10-04-phase-69.7-event-persistence-audit.md), not full repository or Step/Phase acceptance. Exactly EA1 and EI1 executed ordinary shrinking Prop.forAll with seed69709602L/minimum24, passing the actual checked.passed and checked.succeeded>=24 assertions; no printed property iteration count is claimed.

| Leaf | Source | Rules | Given / When / Then behavior |
|---|---|---|---|
| EA1 | [Event audit spec:28](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L28), passed-selected; complete-Step review pending | E1,E3,E4,E5 | Original safe receipts / shrinking entropy0..96 / exact closed primitive JSON=Record, unchanged references/counters. |
| EA2 | [Event audit spec:63](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L63), passed-selected; complete-Step review pending | E1,E3,E4 | Native512/513/control/null and receipt variants / observe / only admissible single identity exposed, Success preserved. |
| EA3 | [Event audit spec:122](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L122), passed-selected; complete-Step review pending | E1,E3,E4 | Seventeen hostile markers and formatting trap / success/failure / no markers/traversal/private failure or rollback fiction. |
| EA4 | [Event audit spec:170](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L170), passed-selected; complete-Step review pending | E3,E5 | Measured legal native context512/513 and real quote/backslash EventId512 / required-context refusal, optional-span omission and inclusive admission / exact original primitive JSON=Record, unchanged references/counters and bounded successful body/line. |
| EA5 | [Event audit spec:267](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L267), passed-selected; complete-Step review pending | E4,E5,E7 | File and structured consumer / same multibyte receipt / strict bytes/atoms agree, owned finally cleanup. |
| EA6 | [Event audit spec:301](../../src/test/scala/org/goldenport/cncf/event/EventPersistenceOperationalAuditSpec.scala#L301), passed-selected; complete-Step review pending | E1,E5,E7 | Counted context and original holder / Nop/refusal/throw / no effects/retry and exact restoration. |
| EI1 | [Native integration spec:24](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L24), passed-selected; complete-Step review pending | E1,E2,E3,E5,E6,E7 | Independent native graphs / shrinking repeated publications1..3 / full canonical records/results/handler/context/counters equal and exact receipt frames. |
| EI2 | [Native integration spec:51](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L51), passed-selected; complete-Step review pending | E2,E3,E4,E6,E7 | Native before/after-append Failure / persistent publish / original failure, truthful different committed effects and no dispatch/read/retry. |
| EI3 | [Native integration spec:97](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L97), passed-selected; complete-Step review pending | E1,E2,E6,E7 | Native handler failure/throw or store throw / publish / original faults/order, successful frame only after normal store return. |
| EI4 | [Native integration spec:157](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L157), passed-selected; complete-Step review pending | E1,E2,E4,E6,E7 | Genuine hostile event / independent native paths / exact private canonical objects retained without traversal or fabricated associations. |
| EI5 | [Native integration spec:190](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L190), passed-selected; complete-Step review pending | E2,E3,E4,E5,E6,E7 | Actual native Sync Nil Jobs / submit through shared bus / exact submitted/running/succeeded receipts/context, full facts/counters equal, C5 bus identity still unavailable. |
| EI6 | [Native integration spec:233](../../src/test/scala/org/goldenport/cncf/job/EventPersistenceOperationalAuditIntegrationSpec.scala#L233), passed-selected; complete-Step review pending | E1,E2,E5,E6,E7 | Native counted graphs / Nop/refusal/throw / identical source success/receipts/results/counters and exact restoration. |
| EB1 | [EventBus spec:300](../../src/test/scala/org/goldenport/cncf/event/EventBusSpec.scala#L300), passed-selected; complete-Step review pending | E2,E6 | Native bus user context / authorized persistent refusal / original failure, no append/dispatch/audit/provider calls. |
| EB2 | [EventBus spec:328](../../src/test/scala/org/goldenport/cncf/event/EventBusSpec.scala#L328), passed-selected; complete-Step review pending | E2,E6 | Explicit original factory and contexts / context-free persistent and authorized nonpersistent / original receipts/results/contexts, no audit/fallback. |

Native trace/correlation/span labels reject quotes and backslashes. Their canonical timestamp and Base62 entropy widths can vary, so EA4 constructs legal labels with at most64 measured attempts, returning only genuine native objects whose actual UTF8 values are exactly512 or513 bytes. Required trace/correlation513 refuse the frame; optional span513 is omitted. Independent trace/correlation/span512 admission retains exact primitive JSON=Record agreement.

The parent's conservative static derivation bounds the closed admitted body at2914 bytes and the standard line at4163 bytes, including three512 native context references, a real512-byte EventId with alternating quote/backslash entropy, sequence Long.MaxValue, conservative occurred-at text and valid128 scope. EA4 passed the separate actual assertions for that genuine successful frame with createdAt Instant.MAX: exact original expected JSON, EventId roundtrip, primitive Record agreement, unchanged original references/counters and these bounds. Body>4096 and line>8192 are unreachable within the current closed native values; the defensive4096/8192 production guards remain unchanged. No overflow execution is claimed. Static analysis remains analysis; REP-004/ACC-001 supply selected execution evidence. Source Failure after committed effects is explicitly exercised separately from before-append Failure.

Both the compile correction (CV-PH697-C7-SCALATEST-IMPORT-01/CV-PH697-C7-NULL-PATTERN-WARN-01: narrowed explicit helper imports and case null) and native fixture/docs correction retain unwaived M2 independent assurance over the complete original JM69-09. Neither redesigns behavior or schema. CPB-PH697-NRT-DISPATCH-REFUSAL-01, CPB-PH697-NRT-CANCEL-SETTLEMENT-01, HYG-PH697-SQL-SIZE-01, HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 and historical DVL-PH697-D3-EXPECTATION-START-FIELDS-01 remain unchanged with prior C1-C6/D1-D3 history; the journal retains the original authority/base/binding/epoch and outstanding full acceptance obligations.
