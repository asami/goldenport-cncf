# Job Experience Operational Audit Contract

status=normative
phase=69.7
step=JM69-09
slice=JM69-09C4
implementation=authored-selected-validation-passed-independent-complete-Step-pending
independent-review=complete-original-Step-pending
Step-acceptance=pending

This companion to the [Job Operations Contract](job-operations-contract.md) and
[accepted Job user/operator experience](job-user-operator-experience.md) defines
completed-operation observation at the existing authorized facade. The original
Phase 69.6 scope, protocol, controls, raw management model, and notification
semantics remain authoritative. C4 covers the facade associations below; the
remaining native producers and configured health/lifecycle obligations stay open.

## Normative rules

### R1

Preserve actual original authorization, lazy query, model projection, control and result/effect order. Each facade evaluates its original full body exactly once into a normally returned result before observational work; audit helpers take results strictly by value and return the same Consequence instance. No catch encloses the original body; original thrown/fatal behavior remains. Only the already-specified safe diagnostics projection deliberately removes free-form diagnostic fields; raw engine models remain unchanged.

### R2

Exactly five audited facade methods: list/get/diagnostics/control/operatorCatalog. Closed operations list/get/diagnostics/control/catalog. Normal list/get/diagnostics/catalog outcomes returned/failed; control applied/replayed/failed from actual response.changed. No dynamic operation/diagnostic string, new authority, state, queue, timer, quota, retry, persistence, metrics or provider calls.

### R3

Failed, denied, missing or invalid requests emit at most a fixed call record with context-only opaque diagnostic IDs: no requested Job/Task/application/scope/cursor/subject/tenant/principal/session/credential/provider/path, request, response, Conclusion text/status detail, exception or partial model. Known-denied and missing get/diagnostics have the same audit call fields. Successful references come solely from the already authorized returned model, never another query or policy call.

### R4

Existing core-owned opaque value IDs are safe correlation references under the canonical ID contract. Copy .value only; no generation, parsing, label interpretation, hashing, substitute ID or clock read. UTF-8 reference bound512 bytes inclusive, nonempty and no ISO control characters; invalid optional refs omitted, invalid required Job ref marks job-id-omitted=true and suppresses its row correlations. Context trace/correlation IDs and scope.name must pass the same byte/control guards (scope bound128); invalid required context suppresses emission, not original result. Optional span omitted when invalid. These are transport guards only, no semantic ID branching.

### R5

Emit only fixed scalar allowlist. Shared fields audit.kind=job-experience, operation, outcome, record-kind plus trace-id, optional span-id/correlation-id. Call records contain only operation-specific observed primitive counts/status/origin/attempt-count/recovery-required/changed, admitted job-id when applicable. Success list rows contain only job-id/status/origin. Success diagnostics rows contain admitted job-id, task-id/parent-task-id/compensates-task-id if valid, closed status/task-kind/relation/transaction/compensation facets/recovery-required, or timeline sequence/closed event-kind/actual occurred-at/task relations. No free-form component/service/operation/action/source/Workflow/definition/CallTree/payload/failure text or security/provider/path data.

### R6

One call record per normally returned facade invocation; successful list contributes at most first100 returned Job rows, successful diagnostics at most first100 tasks and first100 timeline rows, exact returned order. Count fields reflect actual returned sizes; truncated=true only when input result vector exceeds100. Truncation affects audit rows only, never result pages/cursor/totalCount/offsets. Get/control/catalog have one call record. No retained audit collection, callback, mutable input-keyed map or shadow history in production.

### R7

The same fixed scalar vector creates Record.dataAuto and an explicit escaped Circe JSON object; serialize no Record/result/exception/debug/request object. Audit event name is exactly job.audit.v1 bytes=<UTF8 byte length> <compact JSON>. Body bound4096 UTF-8 bytes inclusive, framing plus guarded original ObservabilityEngine prefix/context produces a single line within8192 bytes. Oversized body is omitted, never truncated into invalid JSON. Use existing ctx.observability.emitInfo(ctx.cncfCore.scope,...,record), preserving existing visibility/log backend. Standard text-only and structured backends receive the same allowed facts; byte framing separates JSON from the original appended trace/correlation text without interpreting IDs.

### R8

All production audit extraction/encoding/emission occurs after original result and inside an observer-only NonFatal guard. Invalid observational shape, disabled/Noop backend and backend exception cannot replace, fail, retry or reevaluate the domain result. Fatal errors are not caught. No stderr/stdout fallback, logger reconfiguration, framework access or asynchronous worker. This is best-effort diagnostics, not durable audit acknowledgement, delivery guarantee, health/readiness or acceptance.

### R9

Safe UX diagnostic projection preserves page arity/offset/limit/total/fetched counts, exact typed IDs/parent/compensates IDs, status/time, boolean facts and closed known kind/transaction/relation/compensation facets. Remove component/service/operation/compensationActionRef/result.message/compensationFailureSummary and timeline.note; unknown nonoptional kinds become redacted, unknown optional facets become None. No mutation/truncation of the canonical engine pages, semantic Event log, retained signed record or authorized normal result/control payload.

### R10

C4 closes only the authorized JobExperience facade and its diagnosis/audit consumer layer. Full native lifecycle/Event/Operation/Workflow/definition/CallTree/payload/recovery correlation and configured health/D remain original required implementation, not satisfied by omitted refs, counters or a mock. JM10 actual two-process/CBD acceptance, ancestry, independent complete Step review covering all unwaived M2/CPBs, aggregate full validation, sole epoch1 Phase review and local release remain open.

## Closed operation and scalar records

Every record has `audit.kind=job-experience`, `operation`, `outcome`,
`record-kind`, original `trace-id`, and valid optional `span-id` and
`correlation-id`. String, Int, Long and Boolean are the only scalar atom types.
Status and origin are typed enum projections in lowercase. No request argument
or generic object serialization supplies audit fields.

| Facade method | Operation | Outcomes | Call-specific fields | Additional rows |
|---|---|---|---|---|
| list | list | returned, failed | returned-count, truncated on success | first 100 job rows: job-id, status, origin |
| get | get | returned, failed | admitted job-id, status, origin, attempt-count, recovery-required on success | none |
| diagnostics | diagnostics | returned, failed | admitted job-id, task-count, timeline-count, truncated on success | first 100 task rows and first 100 timeline rows |
| control | control | applied, replayed, failed | admitted job-id, changed, status on success | none |
| operatorCatalog | catalog | returned, failed | none | none |

A failed call has only shared fields. An invalid required successful Job
reference replaces `job-id` with `job-id-omitted=true`; its Job-associated rows
are omitted. Invalid optional Task/parent/compensates/span references are omitted.
Task rows contain only `job-id`, valid `task-id`, `parent-task-id`,
`compensates-task-id`, `status`, `task-kind`, `relation`, `transaction-role`,
`transaction-scope`, `transaction-outcome`, `compensation-status` and
`recovery-required`. Timeline rows contain only `job-id`, `sequence`,
`event-kind`, `occurred-at`, valid `task-id` and `parent-task-id`.
`occurred-at` renders the already-returned typed Instant; observation reads no clock.

The body limit is **4096 UTF-8 bytes inclusive**; each reference is **512 bytes
inclusive**, nonempty and free of ISO control characters. The original scope
name must be **128 bytes inclusive**, nonempty and free of ISO controls. Required
trace and present correlation guards protect the original textual suffix;
unsafe required context omits emission. The complete line stays within **8192
UTF-8 bytes**. A returned vector above **100** sets `truncated=true` for list or
diagnostics alone. Counts always describe the original returned vectors.

## Text and structured consumers

The exact event name is `job.audit.v1 bytes=N <compact escaped JSON>`, where N
is the actual UTF-8 body byte length. Existing ObservabilityEngine adds its
original scope prefix and trace/correlation suffix. A consumer finds the fixed
frame, reads N bytes beginning after its single separator, then decodes that
JSON body. It does not parse ID labels or use the suffix as JSON. Record.dataAuto
and the framed Circe JSON derive from the identical fixed scalar vector.
Standard LogBackend.logStructured delegates to log, so FileLogBackend receives
these facts in actual text even when it does not consume structured attributes.

Extraction, encoding and each emission occur only after a normally returned
result under an observer-only NonFatal guard. A backend exception, hidden info
level, Noop logger, invalid shape or oversized body preserves the original result.
Each row is independently best effort; logging causes no backend retry. Fatal
errors and original domain exceptions retain their original propagation. This
contract supplies no durable audit acknowledgement or delivery guarantee.

## Exact closed diagnostic tables

Unknown nonoptional task or timeline kinds become `redacted`. Unknown optional
facets become None; there is no prefix, substring, normalization or case folding.

| Facet | Exact accepted values |
|---|---|
| task-kind | `action`, `operation`, `aggregate`, `event`, `continuation` |
| target-kind | `action`, `operation`, `aggregate`, `event`, `continuation` |
| relation | `root`, `child`, `event-reception`, `continuation`, `compensation` |
| transaction-role | `own`, `join`, `none` |
| transaction-scope | `per-task`, `whole-job`, `none` |
| transaction-outcome | `pending`, `running`, `committed`, `failed`, `compensated`, `compensation-committed`, `compensation-failed` |
| compensation-status | `not-required`, `pending`, `succeeded`, `failed` |
| timeline-kind | `created`, `submitted`, `running`, `completed`, `succeeded`, `failed`, `cancelled`, `suspended`, `resumed`, `job.submitted`, `job.async.queued`, `job.running`, `job.scheduler.started`, `job.delayed.scheduled`, `job.delayed.enqueued`, `job.succeeded`, `job.failed`, `job.cancelled`, `job.suspended`, `job.resumed`, `job.retry.submitted`, `job.retry.immediate.submitted`, `job.retry.delayed.scheduled`, `job.retry.delayed.submitted`, `job.retry.delayed.enqueued`, `job.retry.exhausted`, `job.dead-letter`, `job.poison`, `job.recovery-required`, `job.runtime-rehydrated`, `job.same-job-async.queued`, `job.compensation.started`, `job.compensation.finished`, `job.durable-start-intent`, `job.durable-running-intent`, `job.durable-terminal-outcome-checkpoint`, `task.running`, `task.succeeded`, `task.failed`, `task.transaction.committed`, `task.transaction.failed`, `task.compensation.started`, `task.compensation.succeeded`, `task.compensation.failed`, `task.compensation.missing`, `task.durable-start-intent`, `task.durable-outcome-checkpoint` |

The safe projection removes task `component`, `service`, `operation`,
`compensationActionRef`, `result.message`, `compensationFailureSummary`, and
`timeline.note`. It preserves canonical IDs, parent/compensation relationships,
status, started/finished/occurred times, recovery and success booleans, page arity,
order, offsets, limits, totals and fetched counts. Canonical raw engine pages,
semantic events and signed durable records remain unchanged.

## Correlation and redaction matrix

| Entity | Authority/privacy constraint | C4 association or remaining obligation |
|---|---|---|
| Job | Canonical opaque Job identity only from an admitted/authorized actual owner; no requested ID on hidden/refused lookup. | Authorized returned facade Job refs; failed requests exclude requested refs. |
| Task | Actual task identity and parent association; no task request/body/target arguments. | Returned Task/parent/compensates refs paired with the authorized Job. |
| attempt | Existing numeric retry attempt under actual Job; never infer from cumulative metrics. | Accepted detail retry attempt-count, with no metric inference. |
| Event | Actual existing Event identity/causation, or exact Job timeline sequence; no Event attributes/payload wholesale. | Returned timeline sequence and occurred-at under authorized Job; native global Event identity remains open. |
| Operation | Resolved operation identity only from actual owning Action/operation metadata; no arbitrary request/debug string. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| Workflow | Existing resolved Workflow owner and Job association; arbitrary Job parameter/lineage strings are not authority. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| definition | Existing selected definition identity/revision; no JCL/source/profile body or hash-derived substitute identity. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| trace | Existing core-owned opaque context trace; no new generated/parsed semantic ID. | Original core context trace copied opaquely. |
| span | Existing context span or absent; never manufacture a synthetic span. | Original context span when valid, otherwise absent. |
| CallTree | Reference/presence through original owner; no serialized tree/nodes/results/raw external path. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| payload | Owned-reference/presence and bounded count/facet; never bytes/filename/URI/digest/body or raw provider locator. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| recovery | Closed native outcome/facet and same Job association; no evidence token or raw diagnostics. | Excluded from facade records; its original native owner/correlation obligation remains open. |
| subject | Closed subject-kind/admission facet only; no principal/session value. | Excluded from facade audit; no identity/data disclosure. |
| tenant | Scoped authorization facet only; no tenant text or identity copied into broad log. | Excluded from facade audit; no identity/data disclosure. |
| credential | Always excluded, including nested values and failure messages. | Excluded from facade audit; no identity/data disclosure. |
| provider | Capability/outcome class only; no provider name, configuration or exception text. | Excluded from facade audit; no identity/data disclosure. |
| path | Always excluded from operational audit, including URLs and exception text. | Excluded from facade audit; no identity/data disclosure. |

Workflow identity must come from its resolved registration owner, never arbitrary
Job parameters or recovered lineage strings. C4 omission does not satisfy the
original Operation/Workflow/definition/CallTree/payload/recovery/global-Event
producer correlation requirements. Catalogue success retains runtime-only health
and unavailable retention; it does not prove configured persistence, maintenance,
integrity, backup/restore, migration, close or restart readiness.

## Executable specification map

All fourteen examples are authored, with actual Given/When/Then and afterWord
metadata `spec:job-operational-audit`, their exact example/rule IDs and `phase:69.7`.
Selected current validation passed as recorded below; independent complete-Step acceptance remains pending.

| Example | Executable source | Rules | Behavior |
|---|---|---|---|
| OA1 | [JobExperienceOperationalAuditSpec.scala OA1](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditSpec.scala#L23) | R1,R2,R3,R7,R8 | Normally returned actual Success/Failure Consequence objects through all five strict observer helper signatures. Failure carries unique nested credential/provider/path/raw request markers in Conclusion; helper returns exact same instance, failed text and structured call contain only fixed allowed keys/context and no job/task/secret. Success list empty emits one fixed returned-count0 call, catalog arbitrary private Record is never traversed/serialized. Use real recording LogBackend/ObservabilityEngine route. |
| OA2 | [JobExperienceOperationalAuditSpec.scala OA2](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditSpec.scala#L83) | R1,R4,R5,R7 | Actual typed accepted JobExperienceView with positive attempt count/recoveryRequired and secret-bearing Available result/resultSummary; observeGet returns same Consequence, emitted structured and framed text have same safe fields and original opaque Job/trace/span/correlation IDs, actual status/origin/numeric attempt, no summaries/payload/debug/security. Framing parses exact UTF8 N, not trailing original trace metadata. |
| OA3 | [JobExperienceOperationalAuditSpec.scala OA3](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditSpec.scala#L116) | R1,R2,R3,R4,R5 | Actual typed control response with changed true/false and secret-bearing response payload; applied/replayed strict identity and same admitted Job ref/status, no payload. Oversize/control-character ID refs omit/mark, unsafe required trace/correlation/scope prevents audit emission while original result remains identical; exact512/513UTF8 boundary with eager typed IDs, no alternate IDs/parsing. No domain fixture represented as process acceptance. |
| OA4 | [JobExperienceOperationalAuditSpec.scala OA4](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditSpec.scala#L172) | R1,R6,R7,R8 | Use real ObservabilityEngine visibility minError/Noop and throwing backend in separate scoped cases; all strict successful and failed observer results remain exact, no stderr/fallback/reevaluation/domain callback, no claimed delivery. An oversized hostile normally returned bounded-shape observation is omitted or rows capped without mutating result/cursor, and every emitted framed JSON body<=4096 bytes/line<=8192. Restore exact backend/policy after cases. |
| DR1 | [JobExperienceDiagnosticsRedactionSpec.scala DR1](../../src/test/scala/org/goldenport/cncf/job/JobExperienceDiagnosticsRedactionSpec.scala#L17) | R5,R9 | A genuine full hostile canonical JobTaskPage/TimelinePage with distinct sentinel for component/service/operation/taskKind/target/relation/transactions/compensationActionRef/result.message/compensationFailureSummary/note/kind. Pure safe projection preserves exact IDs/time/status/boolean/counts and drops every arbitrary optional string, nonoptional unknown kinds redacted; original page values remain byte/value equal and raw content still present in original. No engine mutation claimed. |
| DR2 | [JobExperienceDiagnosticsRedactionSpec.scala DR2](../../src/test/scala/org/goldenport/cncf/job/JobExperienceDiagnosticsRedactionSpec.scala#L47) | R5,R9 | Exhaustive frozen closed tables and real typed task/timeline identity/time relationships: recognized canonical and accepted legacy simple kinds preserve exactly; noncanonical suffix/prefix/case/secret variants redact rather than normalize. Native transaction/compensation enum-derived wire facets and running/committed compatibility values preserve, free-form selector metadata never survives. No closure-shaped synthetic test. |
| DR3 | [JobExperienceDiagnosticsRedactionSpec.scala DR3](../../src/test/scala/org/goldenport/cncf/job/JobExperienceDiagnosticsRedactionSpec.scala#L188) | R3,R5,R9 | Real ScalaCheck24successful seeded69709401 normal shrinking, unique sensitive String sentinel with UTF8/newline/control/escaped/unicode variants in all raw string fields, bounded0..256 characters/pages0..3. Project actual pages, assert secret cannot appear in projected allowed scalar fields/notes while exact original typed IDs/offsets/limit/totals/count/status/time stay; original pages unmodified. Domain assertions over output objects, not source code. |
| DR4 | [JobExperienceDiagnosticsRedactionSpec.scala DR4](../../src/test/scala/org/goldenport/cncf/job/JobExperienceDiagnosticsRedactionSpec.scala#L221) | R1,R6,R9 | Independent raw canonical pages with offset/limit/total/fetched mismatch values allowed at pure projection boundary plus task parent/compensates relations; two successive safe projections are equal and preserve all counts/order/identity/time/boolean semantics. Unknown free fields removed without relabeling IDs, dropping rows, recomputing pages/percentages or touching original instances. |
| AI1 | [JobExperienceOperationalAuditIntegrationSpec.scala AI1](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L23) | R1,R2,R3,R5,R6 | Actual manual InMemoryJobEngine with three session-owned submitted Jobs and one foreign Job, real scoped facade Mine list limit2+followcursor and get. Measured genuine delegate asserts one original queryPage per list and one detail/result read per get, no extra query/IDs/clocks/events/tasks from audit. Audit rows only current returned authorized IDs, actual count/order/cursor unchanged; get records Job/actual retry facet. Canonical state snapshots before/after reads equal. |
| AI2 | [JobExperienceOperationalAuditIntegrationSpec.scala AI2](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L65) | R1,R3,R8 | Actual session-owned real Job and foreign broad-capability session/anonymous/ordinary diagnostics callers plus missing typed Job ID. Known denied versus missing get yield original failure; audit failed call fields equal and carry no requested IDs/subject/tenant/session/raw exception. Real measured delegate/counts and canonical state prove no unauthorized control/body/extra native query; low-privileged catalog/diagnostics refusal records fixed failed facet only. |
| AI3 | [JobExperienceOperationalAuditIntegrationSpec.scala AI3](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L105) | R1,R5,R6,R9 | Actual manual engine runs two real AdversarialTask implementations with secret-bearing metadata/returned failure text; native task and timeline rows exist. Admin-owned facade diagnostics limit1/offset0 and anotheroffset read one detail/tasks/timeline each in original order, sanitized fields/knownKinds/IDs/status/time retained. Audit firstbounded rows pair each actual Task and timeline sequence with the actual authorized Job/trace; neither text nor attrs include any hostile raw metadata/note/result. Raw engine canonical task/timeline still retains original values. Real task executions/ID/clock observations do not change during audit/projection. |
| AI4 | [JobExperienceOperationalAuditIntegrationSpec.scala AI4](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L168) | R1,R2,R3,R8 | Actual manual real owned submitted Job cancel through facade followed by replay and foreign/no-capability refusal. Exact engine changed/status/response semantics retained, one control per admitted call with original detail-first ordering; audit applied/replayed/failed match actual results, denied requestedID absent. One actual cancelled timeline event, no extra state changes/control/tasks/IDs/timers. Includes throwing backend after normal result to show effect/result conservation. |
| AI5 | [JobExperienceOperationalAuditIntegrationSpec.scala AI5](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L214) | R3,R4,R5,R7,R8 | Actual standard FileLogBackend to unique test-owned target/work UTF8 file, real facade get/diagnostics/control on native engine, backend default logStructured writes text only. Read actual output and decode byte-framed JSON, assert same actual Job/Task/timeline/context refs/outcomes and safe allowed fields as recording backend, one-line/byte bounds, no secret. No subprocess/network/deployment/restart assertion; close engine and delete own file subtree in finally. |
| AI6 | [JobExperienceOperationalAuditIntegrationSpec.scala AI6](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala#L275) | R1,R2,R5,R8,R10 | Actual SubsystemTestFixture default installed job_control component and its native Job engine/catalog facade under admin versus User contexts. Audit catalog returned/failed remains fixed operation record with no traversal of category paths/raw catalogue/metrics. Original catalog runtime-only health and retention unavailable are preserved, no audit success interpreted as configured persistence/maintenance/integrity/restart readiness. Genuine installed consumer plus fixture shutdown and exact backend/policy restore. |

The accepted original consumers remain
[JobExperienceSpec](../../src/test/scala/org/goldenport/cncf/job/JobExperienceSpec.scala),
[JobExperienceProtocolSpec](../../src/test/scala/org/goldenport/cncf/component/builtin/jobcontrol/JobExperienceProtocolSpec.scala),
[JobExperienceWebSpec](../../src/test/scala/org/goldenport/cncf/http/JobExperienceWebSpec.scala) and
[JobExperienceHttpSpec](../../src/test/scala/org/goldenport/cncf/http/JobExperienceHttpSpec.scala).
The pure boundary scenarios do not claim deployment or process acceptance. The
native scenarios use the manual engine; AI5 reads actual standard UTF-8 file
output only under a unique repository target/work subtree. Fixture logging and
visibility objects are restored exactly, and native engine/timer resources close
in finally.

## Remaining acceptance

Phase 69.7 and JM69-09 remain IN_PROGRESS; JM69-10 remains OPEN. Full original
C/D implementation, all seventeen native associations, configured health,
lifecycle/write bridge/maintenance owner/integrity/backup/restore/migration/close,
real two-process and fresh CBD Support runtime acceptance, Phase 69.4–69.6
ancestry/deferred handoffs, complete independent Step review including all prior
unwaived M2/CPBs, local Step commits, aggregate final repository/downstream
validation, the sole epoch1 Phase review and final local release remain required.
C4-only review cannot accept the entire Step. No push, publication, deployment or
successor Phase is authorized by this contract.


## Historical C4 selected-source validation evidence — 2026-10-03

`PH697-C4-REP-004` passed **34/34 tests in 4 suites**, zero failed and
zero pending. `PH697-C4-ACC-002` passed **593/593 tests in 72 suites**,
zero failed and one original pending example. The accumulator retains the
original66 ordered suites plus three new audit/privacy suites and three actual
Protocol/Web/HTTP consumers. All fourteen OA1–OA4, DR1–DR4 and AI1–AI6 examples
were discovered and passed. Both current-subject readers returned0, permissions
were consumed and actual SBT/wrapper exits were0 with final `lock=released`.
Compiler warnings were0; the known Scala/JVM Unsafe runtime warning is separate.

The initial `PH697-C4-REP-001` ended with Test/compile E164 at
`JobExperienceOperationalAuditFixtures.scala:60`: `CountingClock.withZone`
required `override`. No tests ran in that failed attempt. The exact one-modifier
repair `MCR-PH697-C4-CLOCK-OVERRIDE-01` changed neither its body nor assertions,
and the failed attempt remains recorded with SBT/wrapper1 and released lock.
`PH697-C4-REP-002` passed the earlier four-suite subject, but
`PH697-C4-ACC-001` then completed72 suites with592 successful, one failed and
one original pending example. AI5 received earlier-suite bootstrap audit
replay when installing the standard replay-capable file backend.
`MCR-PH697-C4-BOOTSTRAP-ISOLATION-01` isolates the private test backend without
consuming the saved original bootstrap buffer. AI5 now explicitly seeds a real
audit into a bootstrap backend, verifies its exact restoration and retained
buffer, and keeps the strict file/recorded JSON equality and all original
privacy/bounds checks. This two-file repair is M2 and remains unwaived for the
complete independent JM69-09 Step review. No production log behavior changed.

These receipts select the actual preprojection source. This factual documentation
projection changes no tested program or prerequisite and does not rebind old
receipts. Full original JM69-09 C/D/native correlation/configured health and
lifecycle obligations, all prior unwaived M2/CPBs, JM69-10 actual two-process and
CBD fresh-runtime acceptance, Phase69.4–69.6 ancestry/deferred audits, complete
independent Step reviews/local acceptance commits, aggregate final full
validation, sole epoch1 comprehensive Phase review and final local release remain
required. Phase/JM69-09 remain IN_PROGRESS; JM69-10 remains OPEN. No Step/Phase
acceptance, push, publication, deployment or successor work is claimed.

`PH697-C4-REP-003` stopped before tests because concurrently changed Cozy
source made the existing development classpath stale. Its failed receipt and
exact source drift remain recorded. `PH697-C4-COZY-CP-001` successfully ran
only the existing `cozyExportRuntimeClasspath` prerequisite with reader0 and
released lock. The unchanged Cozy0.3.3-SNAPSHOT coordinate was retained;
318 current producer source paths, including two newly added developer-owned
FailureModel files, were preserved without edits or feature acceptance.
Current selected prerequisite context therefore contains413 source paths.
The producer reported three existing missing-credentials notices; these are
separate from the selected CNCF compiler-warning count.


## JM69-09C5 native lifecycle operational audit — authored, selected validation passed

C5 is **authored; selected validation passed; independent complete Step review pending**.
Phase69.7 and JM69-09 remain **IN_PROGRESS**; JM69-10 remains **OPEN**.
The fifteen LT1–LT4, LA1–LA5 and LI1–LI6 executable leaves are authored with
visible afterWord spec/example/rules/phase binding, Given/When/Then and should
matchers. LT3, LA1, LA5 and LI1 contain ordinary shrinking ScalaCheck properties with
seed69709501 and minimum24 successful checks. Selected current execution
evidence is recorded below; independent complete Step review, acceptance and
commit remain pending.

The original C4 receipts PH697-C4-REP-004 (4 suites/34 successful) and
PH697-C4-ACC-002 (72 suites/593 successful, one original pending) remain
historical evidence for their exact pre-C5 sources. They do not validate this
changed observer, transport, fixture or additive AI4 expectation. Original
PH697-C4-REP-001 E164, ACC-001 AI5 bootstrap replay failure, REP-003 stale Cozy
classpath and their retained repairs/prerequisite receipts remain historical.
Concurrent developer-owned Cozy FailureModelCml source is preserved under the
parent external-context admission; it is neither edited nor feature-accepted.

C1/C2/C3/C4 unwaived M2, MCR-PH697-C4-BOOTSTRAP-ISOLATION-01 and both original
CPB-PH697-NRT-DISPATCH-REFUSAL-01 / CPB-PH697-NRT-CANCEL-SETTLEMENT-01 remain.
Configured health/readiness/refusal, complete native producer/all17 correlation,
production lifecycle/write bridge/maintenance owner/integrity/backup/restore/
migration checkpoints/close, actual JM69-10 two-process and CBD fresh-runtime
acceptance, Phase69.4–69.6 ancestry/deferred audits, complete independent Step
reviews/local acceptance commits, aggregate full repository/downstream validation,
sole epoch1 full Phase review and final local release remain required. C5-only
review cannot accept the complete original Step. No successor Phase, publication,
deployment or push belongs to this slice.

The normative C5 R1–R10 boundary is incorporated from [Job Lifecycle Operational Audit Contract](job-lifecycle-operational-audit-contract.md). C4 facade rules and original wire content remain authoritative; only its private transport is shared and AI4 accounts for the native Cancel write.

| C5 rule | Required boundary |
|---|---|
| R1 | Observe only after the original native publish/append returns normally. Preserve the captured source, original metadata, metric, clock, factory, bus-over-store priority and exactly one native effect. Original native exceptions escape; missing local Job emits nothing. |
| R2 | Allow only the exact fourteen native names. Observe the original immutable pre-publication JobRecord and original computed occurred-at, with actual captured status rather than event-name intent. |
| R3 | Emit only the closed primitive call/Task fields, original counts and fixed facets. Raw source objects, failure text and arbitrary strings never become audit fields. |
| R4 | Return the original Consequence instance. A single matching safe nonnegative store acknowledgement alone supplies Event ID/sequence; bus and absent sink never reconstruct identity. Failed or ambiguous results make no inferred no-effect claim. |
| R5 | Copy original opaque .value references under inclusive512-byte/no-control/nonempty guards; required trace/correlation and128-byte scope guard suppress unsafe context, optional span is omitted, unsafe Job suppresses Task rows. |
| R6 | One call plus at most first100 original Task snapshots, true full task-count/truncated flag, closed C4 facets and safe references only. No sorting, query, private Task field copying or out-of-snapshot attempt attribution. |
| R7 | Shared private transport preserves experience job.audit.v1 and distinct job.lifecycle.audit.v1 frames, exact Record/JSON atoms, UTF8 body<=4096 and full line<=8192. Oversize/malformed/unknown atoms are omitted, without truncation or fallback. |
| R8 | Catch only observation NonFatal after original domain execution; retain Nop/visibility/throwing behavior and exact fixture restoration. AI4 has one native applied Cancel plus two unchanged facade attempts, total3. |
| R9 | Apply the complete seventeen-facet matrix below, preserve canonical Event/Job data and authorized consumers, and make no comprehensive all17/native producer acceptance claim. |
| R10 | C5 cannot close original C/D, JM69-09, JM69-10, Phase, aggregate/review/release gates. Preserve all historical M2/CPB/HYG and repair evidence, with no push/publication/deployment/successor. |

### C5 seventeen-facet matrix

| Facet | Native observational treatment |
|---|---|
| Job | Original captured JobRecord.id.value, or job-id-omitted=true; actual enum status/persistence/run-mode. Invalid Job reference suppresses Task rows. |
| Task | First100 original taskReadModels in original order; safe task/parent/compensates .value only, actual status and closed C4 facets. No claim of a new Task execution event. |
| attempt | Original captured retry attempt-count/max-attempts/retry-kind and fixed exhausted/dead-letter/poison flags on call row only; no Task attempt attribution. |
| Event | Exact closed native event-kind and original occurred-at. Event ID/sequence only from one matching safe original store acknowledgement. Bus identity unavailable. |
| Operation | Raw component/service/operation/actionRef and selectors omitted; fixed observer operation=emit has no resolved application Operation identity claim. |
| Workflow | Raw lineage, request/debug strings and Workflow identifiers omitted. No global Workflow association is accepted. |
| definition | Only original debug.jobDefinitionSnapshot presence. No ID/key/JCL/source/profile body or derived identity. |
| trace | Original required context.traceId.value under UTF8/control guards, without issuance/parsing. |
| span | Original optional spanId.value under guards; invalid optional span omitted, no substitute. |
| CallTree | Only original debug.calltree presence. No tree/nodes/results/JSON/CLOB/external locator traversal. |
| payload | Only original input presence. No Event payload, input bytes, filename/URI/digest/blob/provider locator or response body traversal. |
| recovery | Original captured Job/Task recovery-required and closed retry/compensation facets only, no private failure/evidence token. |
| subject | Omitted without reading SecurityContext, principal/session or subject attributes. |
| tenant | Omitted without reading tenant or authorization identity. |
| credential | Always omitted, including nested values and raw exception/failure text. |
| provider | Omitted without provider invocation, lookup, configuration or raw provider name/error. |
| path | Always omitted, including filenames, URLs and exception text. |

### C5 executable leaves

| Leaf | Executable specification | Governing C5 rules / behavior | Status |
|---|---|---|---|
| LT1 | [JobOperationalAuditTransportSpec.scala LT1](../../src/test/scala/org/goldenport/cncf/job/JobOperationalAuditTransportSpec.scala) | R5,R7,R8; Original facade frame and Record/json exact equality after shared transport extraction; same actual opaque refs/field ordering/result objects | selected-validation-passed; complete-Step-review-pending |
| LT2 | [JobOperationalAuditTransportSpec.scala LT2](../../src/test/scala/org/goldenport/cncf/job/JobOperationalAuditTransportSpec.scala) | R5,R7; Distinct lifecycle frame with escaped quote/unicode, exact UTF8 boundary4096 and safely bounded full text8192; body oversize omitted not truncated | selected-validation-passed; complete-Step-review-pending |
| LT3 | [JobOperationalAuditTransportSpec.scala LT3](../../src/test/scala/org/goldenport/cncf/job/JobOperationalAuditTransportSpec.scala) | R5,R7,R8; Required context refusal/optional span omission, Nop/hidden/throwing logger and no fallback/provider/time/ID effects with24 seeded shrinking guard checks | selected-validation-passed; complete-Step-review-pending |
| LT4 | [JobOperationalAuditTransportSpec.scala LT4](../../src/test/scala/org/goldenport/cncf/job/JobOperationalAuditTransportSpec.scala) | R7,R8; Actual standard file logger and saved bootstrap buffer: separate two frame consumers and same structured atoms, restore exact holder/visibility and own target subtree cleanup | selected-validation-passed; complete-Step-review-pending |
| LA1 | [JobLifecycleOperationalAuditSpec.scala LA1](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditSpec.scala) | R1,R2,R3,R5,R9; Exactly fourteen names and fixed transport/outcome/status/persistence/mode facets; unknown event names no audit;24 seeded real bounded record transformations retain original facts | selected-validation-passed; complete-Step-review-pending |
| LA2 | [JobLifecycleOperationalAuditSpec.scala LA2](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditSpec.scala) | R3,R5,R6,R9; Actual hostile Task models0/100/101 and multibyte refs, first100 exact order/true counts, private fields and unknown facets omitted/redacted without mutating original record | selected-validation-passed; complete-Step-review-pending |
| LA3 | [JobLifecycleOperationalAuditSpec.scala LA3](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditSpec.scala) | R1,R4,R5,R8; Actual original store-result object: matching one ack vs null/mismatched/duplicate/negative sequence/long ID/multirow/failure, no invented ID/provider read or incorrect persistence claim | selected-validation-passed; complete-Step-review-pending |
| LA4 | [JobLifecycleOperationalAuditSpec.scala LA4](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditSpec.scala) | R1,R3,R4,R8; Actual bus Success/Failure and absent sink distinct facts with no Event ID; exact result object/no domainbody catch, raw failure and request/private payload not inspected | selected-validation-passed; complete-Step-review-pending |
| LA5 | [JobLifecycleOperationalAuditSpec.scala LA5](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditSpec.scala) | R3,R5,R6,R9; All seventeen privacy facets seeded hostile strings, object with throwing toString and arbitrary Event payload/attributes ignored; original opaque .value kept, source models/provider counters stable | selected-validation-passed; complete-Step-review-pending |
| LI1 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI1](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R2,R3,R4,R8; Real manual Sync Nil engine emits original submitted/running/succeeded native Events; each safe store ack identity equals original EventStore result, canonical Event bytes/status/credits/time/ID counts match independent deterministic Nop vs observing runs | selected-validation-passed; complete-Step-review-pending |
| LI2 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI2](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R2,R3,R8; Real future timer/promotion and real immediate RetryNow body sequence; exact native event order, original attempt counter at captured instant, no audit-driven timer/queue/retry/body/clock/ID effects | selected-validation-passed; complete-Step-review-pending |
| LI3 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI3](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R2,R4,R7,R8; Real Suspend/Resume/Cancel applied once, replay/foreign refusal no second lifecycle event; separate facade and lifecycle frames and original state/effects preserved | selected-validation-passed; complete-Step-review-pending |
| LI4 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI4](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R2,R4,R8; Actual DefaultEventBus over EventEngine/EventStore persists and dispatches original event exactly once; return/failed-ack branch no fabricated bus Event ID, original store commit-before-response-loss retained as ambiguous | selected-validation-passed; complete-Step-review-pending |
| LI5 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI5](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R3,R5,R6,R9; Real hostile failing Task keeps canonical private result/metadata while observed Task associations/closed recovery and attempt flags remain safe, no payload/response/security/provider/path leaked | selected-validation-passed; complete-Step-review-pending |
| LI6 | [JobLifecycleOperationalAuditIntegrationSpec.scala LI6](../../src/test/scala/org/goldenport/cncf/job/JobLifecycleOperationalAuditIntegrationSpec.scala) | R1,R4,R8; Actual native store/bus throw precedes observation and retains original exception/effects; throwing audit after normal native result has no propagation/retry/body/clock/ID/store change, global logger restoration verified | selected-validation-passed; complete-Step-review-pending |
| AI4 additive companion | [JobExperienceOperationalAuditIntegrationSpec.scala AI4](../../src/test/scala/org/goldenport/cncf/job/JobExperienceOperationalAuditIntegrationSpec.scala) | R8; native Cancel adds one attempt to two original facade writes, total3; existing semantic/privacy/state/order assertions retained | selected-validation-passed; complete-Step-review-pending |


## C5 selected validation evidence — 2026-10-03

`PH697-C5-REP-002` passed **55/55 tests in8 suites** with zero failed/pending.
`PH697-C5-ACC-001` passed **608/608 tests in75 suites**, zero failed
and one original pending example. The ordered accumulator retains all original72
suites and adds the three new transport/native lifecycle audit suites. All
fifteen LT1–LT4, LA1–LA5 and LI1–LI6 leaves and additive AI4 passed. LT3, LA1,
LA5 and LI1 ran their ordinary shrinking properties with seed69709501 and
minimum24 successful checks. Both actual SBT/wrapper exits and current-subject
readers were0, permissions consumed and final serial locks released. Compiler
warnings were0; JVM runtime diagnostics remain a separate category.

`PH697-C5-REP-001` failed Test/compile before tests: JobStatus is a sealed trait,
so `JobStatus.values` was unavailable at JobLifecycleOperationalAuditSpec.scala29.
It also reported two E121 unreachable-wildcard warnings at
JobLifecycleOperationalAudit.scala28/45. Frozen repair
`PHASE-69.7-C5-compile-fix01` explicitly enumerates the six existing case objects
and replaces those two branches with `case null`. The seed, shrinking, minimum
checks, scenarios, assertions, output and public/protected contracts are unchanged.
The original failed receipt, reader2, SBT/wrapper1 and released lock are retained.

Concurrent Cozy changes were read and retained as generator input context:
FailureModelCml parser/source changes, three index-only commit transitions, then
ModelGenerationTarget/Modeler/ScalaGenerator wiring and the newly referenced
FailureModelAbiGenerator. The current context contains414 explicit source inputs.
The development classpath is newer than these inputs and actual C5 generation
passed. During ACC001, one old version-history line in ModelGenerationTarget
was removed by concurrent work; parent checked that exact header-only delta.
Executable tokens, modes and index are unchanged. The actual tested inputs and
current comment-only context remain separately recorded. This Phase edited no
Cozy source and makes no external feature acceptance.

These receipts retain their actual tested fifteen-path subjects. This six-doc
factual projection changes no tested program or input and does not rebind old
receipts. Phase69.7/JM69-09 remain IN_PROGRESS and JM69-10 remains OPEN. Full
remaining C/D/configured-health/lifecycle/native correlation, all original
unwaived M2/CPBs, actual JM69-10 two-process/CBD fresh-runtime acceptance,
Phase69.4–69.6 ancestry/deferred audits, complete independent Step reviews/local
acceptance commits, aggregate final full validation, sole epoch1 full Phase
review and local release remain required. No Step/Phase acceptance or commit,
push, publication, deployment or successor work is claimed.
