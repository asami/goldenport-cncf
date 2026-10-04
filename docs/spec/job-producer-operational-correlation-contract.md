# Native Job Producer Operational Correlation Contract

status=normative
phase=69.7
step=JM69-09
slice=JM69-09C6
implementation=selected-validation-passed
validation_status=selected-validation-passed
review_status=independent-complete-Step-review-pending
initial_authoring_status=authored_test_pending
Step-acceptance=pending

Companion to the [Job Operations Contract](job-operations-contract.md),
[Job Lifecycle Operational Audit Contract](job-lifecycle-operational-audit-contract.md)
and [producer design](../design/job-producer-operational-correlation.md).
Native Workflow and JCL associations acknowledge only their normally returned
source effects; they create no durable audit authority or Workflow recovery store.

## Normative rules

### R1 — PC1

New private[cncf] JobProducerOperationalAudit has exactly fixed workflow and jcl observation methods. No provider/engine lookup, Task getter, SecurityContext read, payload traversal, clock/ID generation, parser, hash, thread, cache, new durable schema/state or metrics. Use only already captured original values. Observation catches NonFatal only and never modifies domain results/effects.

### R2 — PC2

WorkflowEngine._submit_action private result becomes (JobId, resolvedselector, original ActionId) solely to carry the already-created ActionTask.actionId. _handle_entry destructures it, keeps original _record arguments/order, then invokes fixed observer AFTER original _record returns normally and BEFORE original _decision. Pass entry.definition.id, updated native instance, new jobid and original actionid. No extra record/decision/entity/resolver call. Only actual successful submission gets an observation. Missing match/id/entity/status/rule and submit failure/throw emit no producer frame. Existing Workflow public API/decision/instance/history/selectedAction remain unchanged.

### R3 — PC3

JclRuntimeBridge.submitAction existing map first evaluates original component.logic.awaitJobResult(jobid) exactly once into local response, constructs the original tuple, invokes fixed jcl observer with jobid, roottask.actionId, original definitionSnapshot and response AFTER await returns normally, then returns the same tuple/inner Consequence object. Task Failure response still yields submitted producer observation with result-outcome=failed. Failure/throw before submit/await normal return emits nothing. Keep all preparation, flow/wrapping/continuation/compensation, scheduling, persistence, synchronization and failure precedence unchanged.

### R4 — PC4

Closed producer facts only: producer=workflow/jcl-action, job-id, operation-action-id (actual ActionId, never an Operation-definition identity claim), workflow-instance-id and workflow-definition-id only workflow, workflow-status=submitted and occurred-at=updated.updatedAt only workflow, job-definition-present Boolean and optional job-definition-id/omitted flag only JCL, result-outcome=succeeded/failed only JCL. No selector/action name/component/service/registration/event name/definition key/JCL source/profile/parameters/input/result.message/response/debug/entity business ID/credentials/tenant/subject/provider/path/raw failure. Canonical authorized runtime objects and existing consumers retain original identities/names. No identity inferred from arbitrary parameters or event attributes.

### R5 — PC5

All original opaque IDs use .value only (snapshot.id is already original ID string). Required job-id and operation-action-id must be nonempty, no ISO control, <=512 UTF8 inclusive; workflow instance/definition IDs also required. Malformed required producer identity suppresses entire producer frame. Optional JCL definition null/invalid ID retains presence=true, omits raw ID and records job-definition-id-omitted=true; absence=false, no fabricated ID. No parse/normalization/format reconstruction/entropy access/new Task ID. Null/malformed observation remains best effort and preserves original domain outcome.

Native required `JobId`, `ActionId`, `WorkflowInstanceId` and
`WorkflowDefinitionId` are final actual types whose validated nonempty labels,
fixed kind, timestamp and delimiters always contribute to `UniversalId.Parts.value`.
An empty canonical `.value` is therefore unreachable, including with empty entropy.
The defensive nonempty guard remains. PA2 uses real native constructors with
valid labels, explicit `Some(Instant.EPOCH)` and explicit entropy: it measures
the fixed prefix overhead, pads entropy to exactly 512 total UTF8 bytes, and
checks adjacent 513-byte/control-bearing values and null typed references
(including null `WorkflowInstance.id`). No reflection, final-class override or
production seam fabricates an empty value. Optional `JobDefinitionSnapshot.id`
is a String, so empty/null/control/over-bound values and `Some(null snapshot)`
are reachable and must preserve presence=true with only the omitted flag.

### R6 — PC6

Reuse one existing JobOperationalAuditTransport with fixed producer method selecting job.producer.audit.v1 and audit.kind=job-producer, operation=correlate, outcome=submitted, record-kind=association. Widen object only to private[cncf] for two genuine producer owners. No public/generic prefix, dynamic audit kind or configurable emitter. Existing experience/lifecycle fixed methods preserve exact bytes, order, guards and result behavior. Original required trace/optional correlation/scope and optional span guards remain: IDs<=512, scope<=128, body<=4096UTF8 and line<=8192UTF8, primitive-only explicit JSON plus Record.dataAuto identical atoms, existing context.emitInfo/scope/visibility. No truncation, retries, fallback output or bootstrap logger replacement.

### R7 — PC7

Native associations are source-bound acknowledgements: Workflow instance definition/job/action captured only after _record, JCL action/optional definition/job captured after native submission and wait. Operation is the actual resolved Action execution represented by original ActionId, not an invented Operation-definition ID; selector stays in existing authorized canonical source. Correlation survives through native logs according to existing backend ownership; no invented durable Workflow store/fresh-process acceptance. Existing C5 Job/Task/attempt/Event/trace/span/recovery/presence/omission semantics remain untouched. Canonical global Event acknowledgement, original all17 integrated proof, actual JM10 and whole-Step acceptance remain required after C6.

### R8 — PC8

Add exactly PA1..PA6, PW1..PW4 and PJ1..PJ4 behavioral leaves below. Use existing native fixtures in WorkflowEngineSpec and JclJobControlComponentSpec, no fake producer as integration proof. New private[cncf] JobProducerOperationalAuditTestSupport owns synchronized recording backend/filter/framed UTF8 parser and delegates existing job audit fixture exact holder/visibility finally restoration. Do not consume/mutate global bootstrap buffer. All GWT text remains visible, no unsolicited print snapshots. Native effects/logical history/clock-ID counts must equal independent Nop vs observing runs at meaningful native integration edges. PA2 follows the native reachability contract above; PA1 and PA3 are the only two properties.

### R9 — PC9

Author normative companion, design and dated journal before source; link all14 leafs/rules and publish authored_test_pending initially. Update only current C6 sections in parent spec/lifecycle companion/phase/checklist, preserve old receipts and retained DVL/CPB/M2/Hygiene chronology. Record all17 facet responsibility matrix and exact C6 coverage/remaining global Event and integrated operational/JM10 boundaries; no all17 or complete-Step claim. Keep JM69-09 IN_PROGRESS, JM69-10 OPEN, all original Step/Phase gates unchecked, epoch1/Step1/Phase0 and original authority/base/history. No success counts before actual runner results.

### R10 — PC10

Only fifteen owned paths. Do not edit inherited B/D sources or test fixtures, EventBus/engine or core/evaluation/Phase87/93/94/97/datastore/Cozy/generated/config/build/dependency paths. Do not fix unrelated original debt. If existing consumer expects exact global backend counts newly affected, report exact edge rather than weaken assertions or broaden paths. No SBT/runtime/scripts/CLI/self-review/commit/delegation by worker. Parent owns validation and complete-Step protected review.

## Closed producer records

Shared ordered fields are `audit.kind=job-producer`, `operation=correlate`,
`outcome=submitted`, `record-kind=association`, original `trace-id`, valid optional
`span-id` and `correlation-id`. The fixed frame is `job.producer.audit.v1 bytes=N`
followed by compact primitive JSON from the same atom vector as Record.dataAuto.
Workflow atoms in order: producer, job-id, operation-action-id,
workflow-instance-id, workflow-definition-id, workflow-status, occurred-at.
JCL atoms in order: producer, job-id, operation-action-id, job-definition-present,
optional job-definition-id or job-definition-id-omitted, result-outcome.
The Operation association means the actual resolved Action execution; it does
not assert an Operation-definition identity. Original selectors remain canonical.

Required original IDs are nonempty, free of ISO controls and at most512 UTF8 bytes
inclusive. Optional malformed JCL definition ID retains presence and only a fixed
omitted flag. Unsafe required context suppresses the whole frame; unsafe optional
span is omitted. Body<=4096 and full line<=8192 UTF8 bytes, without truncation.
Body overflow is reachable from safe escaped IDs. With the closed field set and
context bounds an independent line-only overflow is unreachable; PA4 does not
invent fields or an impossible fixture to claim that coverage.

## Seventeen-facet responsibility matrix

| Facet | C6 responsibility and retained boundary |
|---|---|
| Job | Exact new native submitted JobId.value; no inferred historical job. |
| Task | Actual root ActionId only; C5 retains Task/parent/compensates refs. No Task getter. |
| attempt | No C6 field; original C5 captured attempt/retry facets unchanged. |
| Event | No Event ID/name/payload claim; canonical global Event acknowledgement remains pending. |
| Operation | Original resolved ActionId as operation-action-id; no selector/name or invented definition ID. |
| Workflow | Original updated instance ID and original definition ID after native _record; submitted status and existing updatedAt only. No durable Workflow store. |
| definition | Actual optional JCL snapshot ID/presence; malformed ID omitted. No business key/source/profile. |
| trace | Original guarded context traceId.value only. |
| span | Original valid optional spanId.value; malformed optional span omitted. |
| CallTree | No traversal or C6 field; original C5 presence and authorized consumers unchanged. |
| payload | No input/result/Event/JCL/body traversal; original canonical payloads unchanged. |
| recovery | No C6 claim; original native recovery/C5 flags remain. Fresh process acceptance pending. |
| subject | Omitted without SecurityContext/principal reads. |
| tenant | Omitted without authorization/tenant reads. |
| credential | Always omitted, including exception/failure messages and nested data. |
| provider | Omitted without lookup/invocation/configuration/name/error reads. |
| path | Always omitted, including source paths, URLs and raw failure text. |

## Executable specification map

Fourteen authored visible GWT/should leaves passed selected validation and are bound through afterWord to this
spec, their Example, rules and Phase69.7. Exactly two ordinary shrinking properties:
PA1 and PA3, each declares seed69709601 and minimum24 successful checks.
PA1 uses `Gen.choose(0,90)` with real typed identity constructors; PA3 uses
`Gen.choose(0,1)` for its two finite hostile variants. Both use ordinary
`Prop.forAll` shrinking and `Test.check`; both passed their actual assertions in
selected validation. The seed and minimum are declared property parameters;
no printed variant counts are claimed. Initially all fourteen leaves were
authored_test_pending with no execution result. Current source-map status is
passed-selected; independent complete-Step review remains pending for every leaf.
Actual selected results and the unwaived two-file M2 fixture repair are recorded
in the [canonical C6 journal](../journal/2026/10/2026-10-04-phase-69.7-native-producer-correlation.md).

| Leaf | Source | Rules | Behavior | Status |
|---|---|---|---|---|
| PA1 | [JobProducerOperationalAuditSpec.scala PA1](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L30) | R1,R4,R5,R6 | Closed original typed Workflow/JCL identities, exact fields and primitive framed JSON=structured Record; ordinary shrinking identity property seed69709601/minimum24 preserve exact .value and canonical objects. | passed-selected; complete-Step review pending |
| PA2 | [JobProducerOperationalAuditSpec.scala PA2](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L78) | R5 | Actual native required IDs at inclusive 512 total UTF8 bytes survive; adjacent 513-byte/control/null references suppress the whole frame. Empty native canonical value is unreachable; the defensive guard remains. Optional String definition IDs cover empty/null/control/over-bound and Some(null snapshot) with presence and omitted flag; None is absent. | passed-selected; complete-Step review pending |
| PA3 | [JobProducerOperationalAuditSpec.scala PA3](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L166) | R1,R4,R5 | Hostile source object/parameters/result/JCL key/source/security/path markers never read/serialized; one ordinary shrinking finite hostile-value property with two value variants, seed69709601/minimum24. PA1 and PA3 are exactly two properties total. Confirm payload toString/getter cannot run and original response object retained by native callers. | passed-selected; complete-Step review pending |
| PA4 | [JobProducerOperationalAuditSpec.scala PA4](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L217) | R5,R6 | Required context trace/correlation/scope invalid suppresses; optional span invalid omitted; reachable true UTF8 body overflow above4096 omits without truncation. Every emitted actual line remains <=8192; closed body/context bounds make independent line-only overflow unreachable, so do not invent an impossible producer fixture or add oversized unadmitted fields. Existing experience/lifecycle bytes/order unchanged. | passed-selected; complete-Step review pending |
| PA5 | [JobProducerOperationalAuditSpec.scala PA5](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L272) | R4,R6,R8 | Actual standard UTF8 file backend consumes producer frame with exact byte count and JSON/structured agreement, multibyte values, bounded owned target file finally cleanup; no private text in either consumer. | passed-selected; complete-Step review pending |
| PA6 | [JobProducerOperationalAuditSpec.scala PA6](../../src/test/scala/org/goldenport/cncf/job/JobProducerOperationalAuditSpec.scala#L303) | R1,R6,R8 | Nop/visibility refusal/throwing backend do not propagate or retry, no extra clock/ID/provider/security reads, exact original holder and visibility restoration. | passed-selected; complete-Step review pending |
| PW1 | [WorkflowEngineSpec.scala PW1](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L291) | R2,R4,R7 | Native admitted component metadata, resident entity, real resolver/prepared ActionTask/submitted Job and updated Workflow instance: exact Job/Action/Workflow/definition IDs in one producer frame; underlying canonical chosen operation and one body/history retained. | passed-selected; complete-Step review pending |
| PW2 | [WorkflowEngineSpec.scala PW2](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L337) | R2,R7 | No workflow match, missing entity/id/status/rule, actual unknown target/submit refusal leave original decisions/failures/instance history and no producer association; no guessed historical last job. | passed-selected; complete-Step review pending |
| PW3 | [WorkflowEngineSpec.scala PW3](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L404) | R2,R5,R7 | Repeated real event reuses actual instance with two distinct acknowledged jobs/actions; priority winner exact original definition; exactly one producer frame per actual accepted submission and no new IDs from audit. | passed-selected; complete-Step review pending |
| PW4 | [WorkflowEngineSpec.scala PW4](../../src/test/scala/org/goldenport/cncf/workflow/WorkflowEngineSpec.scala#L437) | R1,R2,R8 | Independent deterministic Nop vs recording/throwing native runs preserve decisions, public instance/history, original Jobs/action outcome and ID/clock/body counts; real domain throw propagates and no producer retry/observation before native return. | passed-selected; complete-Step review pending |
| PJ1 | [JclJobControlComponentSpec.scala PJ1](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L1793) | R3,R4,R7 | Create and load actual stored JobDefinition through existing JCL protocol, submit real accepted action, audit exact stored native definition ID/new Job/Action ID and original success; key/source/body parameters never in producer frame. | passed-selected; complete-Step review pending |
| PJ2 | [JclJobControlComponentSpec.scala PJ2](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L1836) | R3,R4,R5 | Real inline JCL action without definition emits present=false with exact native Job/Action ID and no inferred definition from forged jcl.jobDefinition.* parameters. | passed-selected; complete-Step review pending |
| PJ3 | [JclJobControlComponentSpec.scala PJ3](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L1881) | R3,R7,R8 | Native failed action response still correlates its actual submitted Job/Action with result-outcome=failed, exact original failure retained; unknown resolver/admission refusal creates no producer association and no retry/body effects. | passed-selected; complete-Step review pending |
| PJ4 | [JclJobControlComponentSpec.scala PJ4](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JclJobControlComponentSpec.scala#L1929) | R1,R3,R4,R8 | Native JCL with hostile input/key/source/parameters/security/path values and recording versus throwing backend preserves existing execution/trace/results while producer text/structured atoms leak none; original bootstrap/backend/visibility restored. | passed-selected; complete-Step review pending |

## Remaining original acceptance

JM69-09 and Phase69.7 remain IN_PROGRESS; JM69-10 remains OPEN. Original all17
integrated security/retention/hostile/lifecycle/operational/global-Event proof,
all original CPBs and C1..C5 and C6 M2 and Hygiene/DVL history remain unwaived. Actual
JM69-10 two-process/scheduled-retry/crash/cursor/payload/corruption/version/
migration/JCL/governance/CompositeQuery/user-admin/notification/security and CBD
Support independent fresh-runtime acceptance remain required. Phase69.4..69.6
ancestry/deferred audits, whole-Step protected review/local acceptance commits,
aggregate final full/downstream validation, sole epoch1 full Phase review and
final local release remain pending. Epoch1/Step repair1/Phase repair0 and original
authority/base/binding and retained17 history events/10 budgets/no acceptance
reviews are retained. Parent-owned selected testOnly validation passed62 tests
in7 representative suites and804 tests in97 accumulator suites, with one
existing SqliteDataStoreSpec pending example in the accumulator. These are
selected results only; initial authoring claimed no pass counts. Parent owns
diff verification, complete-Step review and transitions. No Step/Phase acceptance,
commit, push, publication, deployment or successor work is claimed.
