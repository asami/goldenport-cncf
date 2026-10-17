# Job User and Operator Experience Specification

status=accepted
phase=69.6
slice=JM69-08A

## Scope and compatibility

`job_control.job_experience` is a projection over canonical Job management. It
does not introduce a Job store, cache, lifecycle, retention clock, or result
state. Existing Job management APIs, case-class arities, cursor wire values for
the empty default policy, protocol names, and control outcomes remain valid.

The original scopes are `mine`, `application`, and `operator`. An application is
normalized with `NamingConventions` and rejects blank, separator, and traversal
input. Scope never accepts a subject or recipient selector. Mine requires a
non-anonymous session-first owner even when that caller has broad capabilities.
Application requires canonical broad read capability and stored
`web.application-job=true` plus stored normalized `web.app`. Operator requires
`job_admin` or `content_admin`. A cursor combines the nonempty scoped visibility
key with the management filter fingerprint; it consequently rejects a changed
scope, application, caller, authorization set, snapshot, or malformed token.

### Separately configured Audit read scope (CC4-S11)

`audit` is an additional read-only scope with visibility key `audit` and no
application selector. It observes only the existing subsystem's canonical Job
engine. It requires an explicitly supplied `RuntimeOperationSecurityPolicy`,
Production policy and execution context, enabled production administration,
trusted provider authentication, at least system privilege, and a configured
jobs role under `AdminAuthorizationPolicy.operationRule("admin.jobs.index", policy)`.
The default jobs roles remain `system_admin,audit_viewer`; no capability is
granted by this scope. Admission is repeated against the actual execution
context for every list and exact read. Missing policy, non-Production mode,
disabled admin, forged provenance, low privilege, and role mismatch deny access.

Audit uses canonical bounded management summaries and details, filtering and
pagination. Its cursors bind to Audit visibility, caller/authorization, filters
and snapshot; they cannot be reused across scopes or subjects. Missing and
denied details remain indistinguishable. Audit never queries result payloads,
task pages or timeline pages. Its result is unavailable, controls are empty,
diagnostics and every control attempt are denied before effect, even when the
subject also has administrator capabilities. Detail refresh is manual only.

Only the configured Web server supplies this policy. After resolving the actual
request context, requested Operator Web list/detail routes use Audit when the
subject lacks `job_admin` and `content_admin`; otherwise they retain Operator.
Descriptor, authentication and admin-enabled ingress checks still apply.
Audit links use `/web/system/admin/jobs` and its exact detail paths. The default
protocol/REST service receives no policy: selecting `scope=audit` cannot grant
authority, and inert polling URL metadata does not authorize a REST read.
Mine, Application, Operator and their existing controls retain their contracts.

## Projection, results, diagnostics, and controls

The public view keeps canonical status and maps it to Queued, Running, Paused,
Cancelled, Completed, or Failed. Server-resolved Japanese receives Japanese
labels; all other locales receive English. Active Jobs report indeterminate
progress and recorded task count. They do not infer completed-task counts or a
percentage from a bounded task page.

Live successful results are displayed as escaped text. Failed results use the
fixed safe failure wording. Pending terminal results are unavailable, and
unavailable-after-restart remains distinguishable. No result is reconstructed,
retained, or polled after it is terminal/unavailable.

Records and HTML expose bounded Job id, status, persistence, origin, timestamps,
retry/count fields, and the admitted controls. They omit subject/session, input,
debug/request/calltree data, free-form failures, task error messages, timeline
notes, and diagnostic payload references. Missing and denied exact reads both
return the same operation-not-found result. Operator diagnostics use bounded
canonical task and timeline pages and expose only ids, kinds, status, and time.

Runtime Submitted/Running Jobs may offer Cancel/Suspend; Suspended offers
Cancel/Resume; Failed/Cancelled offers Retry; Succeeded offers none. Durable
records advertise no unsupported control. A control reauthorizes the exact
scoped Job and calls the engine once under the existing control policy. The
engine owns all transition and replay semantics.

## Notification inbox and forwarding

`UserNotificationInboxProvider` is optional and additive to the existing send
SPI. Providers own persistence, deduplication, expiry, unread/read state,
authoritative recipient/application filtering, and opaque cursor identity.
CNCF validates provider page identity, count bounds, recipient/application,
unread state, expiry, and same-origin absolute-path links before projecting.
Provider absence, inconsistency, rejection, or exception is structured as an
unavailable provider result and never changes a Job.

Public notification views omit recipient, dedupe key, provider identity, and
private metadata. Exact read/mark-read treats denied, missing, and expired as
not found. Mark-read calls the provider once after exact admission and preserves
provider first-read/replay semantics. Only `notification_admin` or
`content_admin` can use the bounded provider update entry; it cannot alter
identity, recipient, application, creation time, dedupe key, or read time.

Forwarding has no process-global sent set. Every matching event carries its
stable `(jobId, trigger)` dedupe key to the provider, which owns replay and
restart idempotence. Forwarded title/body contain bounded job identity, status,
and safe recovery wording. A false acceptance, provider failure, absence, or
nonfatal exception records a forwarding failure and remains retry-eligible.

## Protocol and Web

The descriptor service is `job_control.job_experience`: `list_my_jobs`,
`list_application_jobs`, `list_operator_jobs`, `get_job_experience`,
`get_job_diagnostics`, `control_job_experience`, `list_my_notifications`,
`get_my_notification`, `mark_notification_read`, and `get_operator_catalog`.
Reads explicitly project GET; `control_job_experience` and
`mark_notification_read` explicitly project POST. The shared codec rejects
invalid scope, boolean, integer, status, and origin input.

The server-rendered routes are `/web/system/jobs`, `/web/{app}/jobs`,
`/web/{app}/admin/jobs`, and `/web/system/admin/jobs`, with equivalent exact
detail paths. They resolve the Job-control execution context and retain both
descriptor ingress checks and scoped facade authorization. Notification pages
use the generic resolved provider; no component name is hardcoded. Private
pages and provider/error responses are no-store, and all text/attributes and
opaque Next links are escaped.

Controls and mark-read use canonical `/form/job_control/job_experience/...`
POST forms with the existing CSRF cookie/token and no identity/capability/return
URL hidden field. A successful command has the scope-specific PRG destination;
existing descriptor redirects take precedence. GET does not change read state.

Initial HTML provides headings, labelled filters, captioned tables, scoped
headers, result/progress text, empty/error states, keyboard controls, manual
refresh, and an aria-live polling state. Only active Submitted/Running exact
details poll, with same-origin credentials, one request, a five-second interval
and abort timeout, at most sixty attempts, and textContent-only updates. It
stops for paused/terminal/hidden/unload/error/redirect/invalid response or user
pause.

## Executable specification mapping

| Requirement | Executable evidence |
| --- | --- |
| UX69-01 | `JobExperienceSpec`, `JobExperienceHttpSpec` scope/filter rejection and compatibility behavior |
| UX69-02/03 | `JobExperienceSpec` canonical vocabulary, progress, result and control projection |
| UX69-04/05 | `UserNotificationInboxSpec`, `UserNotificationProviderRuntimeSpec` provider, identity, replay, and failure behavior |
| UX69-06 | `JobExperienceProtocolSpec` descriptor/OpenAPI discovery |
| UX69-07/08 | `JobExperienceWebSpec`, `JobExperienceHttpSpec` renderer, CSRF form, Web codec, and polling bounds |
| UX69-09 | All named specs use `AnyWordSpec`, `GivenWhenThen`, and matchers with adjacent clauses |
| CC4-S11 Audit read | `JobAuditReadSpec`, `JobExperienceHttpSpec` configured provider authority, bounded read-only projection, cursor isolation, and default REST denial |

## Frozen scenario ledger

`JobExperienceSpec` carries J1 Mine session-first ownership, J2 Application and
Operator admission, J3 scoped cursor/page binding, J4 vocabulary/result safety,
J5 direct-control outcome projection, and J6 bounded diagnostics. Its adjacent
Given/When/Then clauses identify the subject, ingress, and safe outcome.

`UserNotificationInboxSpec` and `UserNotificationProviderRuntimeSpec` carry N1
provider dedupe, N2 global/application cursor lists, N3 mark-read replay, N4
bounded administrator update, N5 expiry/link/malformed page rejection, N6
provider absence/refusal/exception retry safety, and N7 forwarding redaction.
Their adjacent Given/When/Then clauses identify the provider record and the
same-or-not-found or unavailable result.

`JobExperienceProtocolSpec` carries P1 generated Help/meta and OpenAPI
discovery followed by installed resolver invocation, P2 shared parse rejection,
permission/default/bound outcomes, P3 bounded read/control and notification
read records including replay, and P4 catalog availability. `JobExperienceWebSpec`
carries W1 escaped renderer and opaque full-query Next links, W2 accessible
CSRF/localized no-JavaScript markup, and W3 bounded polling.
`JobExperienceHttpSpec` carries actual Http4s H1 Mine/Application/Operator
Web/detail/REST admission, normalized scope, count, and Next behavior; H2
forged identity plus descriptor application disablement/alias admission; H3
rendered CSRF control success, replay, and descriptor redirect priority; H4
stale-CSRF counter non-mutation; H5 provider-backed global/application inbox
GET and first-read replay PRG; H6 owner/foreign await isolation; H7 emitted
locale polling URL, terminal/unavailable JSON and first paint; and H8 admitted,
denied, form, notification, and anonymous private accessible cache outcomes.

## Phase 69.7 handoff

Retention/deletion/maintenance, durable integrity, expiry clocks, result-store
migration, provider delivery implementation, and repository-wide aggregate
validation remain owned by Phase 69.7.


## Phase 69.7 completed-operation audit and safe diagnostics companion

The [Job Experience Operational Audit Contract](job-operational-audit-contract.md)
extends the existing operator ids/kinds/status/time promise with exact closed
facet tables. Unknown kinds render as `redacted`; optional free-form targets,
selector metadata, transaction/compensation strings outside the closed tables,
result/failure text and timeline notes are suppressed at the existing diagnostic
projection. Exact canonical IDs, times, statuses, counts and page behavior survive;
raw management queries preserve their original canonical model.

The five original facade operations are observed only after their normal result,
using authorized returned references and bounded scalar facts. Failed calls never
copy requested Job IDs, scope, cursor, session or principal data. Existing Phase
69.6 scope admission, protocol, controls, normal result/control payloads and
notification semantics remain authoritative. C4 is authored with selected current validation passed as recorded below;
independent complete JM69-09 review and Step acceptance remain pending.
The original Phase 69.6 accepted chronology and scenarios above remain in force.


## C4 selected validation evidence — 2026-10-03

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


## Phase 69.7 restart and retained-result operational companion — JM69-10D2

status=authored_review_pending
stage_status=IN_PROGRESS
phase=69.7
step=JM69-10
slice=JM69-10D2
validation_status=retained-selected-ACC001-passed-static-checks-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

This appendix is authored guidance awaiting original Step review and accepted
promotion. The Phase69.6 accepted header, history, routes and controls above
remain authoritative. The [consolidated matrix](job-cross-process-acceptance-contract.md#jm69-10d2-consolidated-restart-acceptance-matrix--2026-10-06)
and [D2 journal](../journal/2026/10/2026-10-06-phase-69.7-restart-acceptance-matrix.md)
bind the existing executable evidence and retained selected execution.

### Existing restart and read behavior

Eligible canonically authorized, proof-complete V2 Submitted work without
startedAt/recoveryRequired reaches the provider-owned port; ordinary timer/
queue processing owns due execution. Actual interrupted started work refuses
startup before Port/Task/queue. Operators retain the fixed safe refusal and
bounded diagnostic facts rather than prescribing Retry/Resume for refused
startup, raw-row edits, fabricated Submitted state, grant expansion, refund
or proof reset. The [scheduled executable specification](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala)
and [retry executable specification](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala)
distinguish positive unstarted execution, completed writer retry with fresh
terminal observation, and SC5/RC5 started-state no-replay refusal.

Completed legacy Absent/body-free projections retain raw getResult=None and
authorized UnavailableAfterRestart. Existing B4 queryVisible alone hydrates
an exact eligible job-owned OperationResponse External/header after ordinary
model policy and the actual current-caller resolver under the same ready native
owner lease. Startup credentials, a stored grant or job_admin capability never
replace that admission. The [current-caller query contract](durable-job-operation-response-query-contract.md)
and [DurableJobOperationResponseQuerySpec](../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseQuerySpec.scala)
bind the existing consumer; this companion defines no readCompletionResponse
API, endpoint or control and synthesizes no result from a terminal summary.

Mine requires the original same current owner. Operator requires existing
capabilities and still satisfies durable tenant/subject/scope admission.
Durable controls remain only the originally admitted behavior.
[JobExperienceCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobExperienceCrossProcessAcceptanceSpec.scala)
covers owner, administrators, foreign/different-kind/anonymous/broad readers,
bounded redacted diagnostics and durable-control compatibility.
[JobSecurityCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala)
covers exact/additional-scope admission and foreign/missing/unrelated/mixed
refusals without capability bypass.

Ready may own one existing maintenance registration; refused startup installs
no owner. Zero replay/write/body/getter/queue effects remain distinct from
maintenance timers. The original owned shutdown cancels the timer, closes
the scheduler and managed native resources. C11’s raw preclosure timer1 for
Ready and timer0 for denial, then closure0, retain their actual meaning.

Notification providers own persistence, deduplication and inbox state.
[JobNotificationCrossProcessAcceptanceSpec](../../src/test/scala/org/goldenport/cncf/job/JobNotificationCrossProcessAcceptanceSpec.scala)
demonstrates accepted/internal/disabled/refused/failed/exception/unsafe-application
forwarding and fresh-reader no replay, including bounded generated modes.
Those writer/provider observations supply no generic fresh persistent-inbox
proof. Retain body-free redaction and bounded diagnostics; this guidance
contains no private row/payload/credential dump or manual destructive recovery
procedure.

### Restart boundary and retained original obligation

The [recovery truth table](durable-job-recovery-contract.md)
continues to classify Running/Suspended or Submitted with startedAt present as
RecoveryRequired; existing ActiveLifecycle and ExecutionAlreadyStarted reasons
remain unchanged. The [rehydration R2/R3 contract](durable-job-runtime-rehydration-contract.md)
retains canonical/access admission, unchanged classification and eligible V2
Submitted-only port admission, with complete replay evidence, no startedAt and
no recoveryRequired. Its ordinary timer/queue owns due dispatch.

SC1..SC3 and CR1 demonstrate genuine unstarted/scheduled admission, independently
reconstructed live execution and a third terminal reader, including unstarted
halt71 recovery. Immediate/default delayed retry completes in its writer before
fresh terminal observation. SC5 actual Running4/Pending after TaskStart halt71
and RC5 actual Running5/Pending after Failed pending retry (graceful exit or
halt71) refuse before Port/Task/queue, retaining native rows/payment without
replay, refund or reset. Those refusal fixtures do not establish positive
automatic fresh retry or recovery of started work.

The original checklist calls for the real scheduled/retry/crash-interrupted
matrix; the retained original plan separately keeps positive-started-work
closure/design choice OPEN. This tension is retained parent planning evidence,
not an implementation decision or waiver. D2 introduces no recovery policy,
automatic retry, checkpoint/schema, proof-token rule or acceptance change.

Actual CBD provider/Entity/completed full Review and independently fresh recovery
remain OPEN. Its gateway is provider-pipeline-pending and read requires reportId/
reportDigest. Framework payload/Review fixtures cannot complete that downstream
obligation. The downstream checkout stays read-only and the unanswered source
authority request remains retained. Complete compatibility and accepted guidance
promotion, original whole-Step protected M2/local acceptance commit, predecessor
ancestry/reciprocal deferred-full, aggregate CNCF/downstream full validation,
sole original epoch1 Phase review, version/closure evidence and local release
also remain OPEN.

The retained same unchanged program/configuration/inputs evidence is
[PH697-JM10C11-ACC-001](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-accumulator01-result.json):
131 completed suites, 1076 succeeded, pending1 (only the original
SqliteDataStoreSpec “should store record fields as columns”), failed0/aborted0/
canceled0/ignored0/compilerwarnings0, typed reader0/SBT0/wrapper0,
permission consumed and lock=released.
[PH697-JM10C11-REP-002](../../.codex-workflow/phases/PHASE-69.7/JM69-10C11-representative02-result.json)
passed four suites/26 succeeded, pending0 and the same zero failure/exit/warning
counts with consumed permission and released lock. REP002 covered its selected
four suites; it supplies no whole-matrix coverage claim. No D2 execution occurred.

## Current native operator guarantees — JM69-10D4 — 2026-10-06

status=authored_review_pending
acceptance_status=not-accepted

This current companion preserves the accepted Phase69.6 contract and existing
operator controls. The [closure boundary](../journal/2026/10/2026-10-06-phase-69.7-cncf-job-closure-boundary.md)
records CBD exclusion/HANDED_OFF_UNVALIDATED and pending native Step/Phase
acceptance. Earlier dated CBD/OPEN design-choice projections are historical.

The [configured native owner](durable-job-runtime-owner-contract.md) and its
original bound bridge/access remain required; unconfigured access has no fallback.
[Native UOW R1–R5](job-unit-of-work-submission-contract.md) issues one identity
only after admission/reservation for Persistent Async work under that Ready owner.
Until outer commit activation, that identity is unpublished: no JobRecord/Entity
Job, paid root, evidence/event, queue, timer or body. No staged registry or
immediate-submit fallback exists. Commit activates the same identity through the
original owner lease/State/publication path using commit-time now. Abort/dispose/
failure releases only unpublished capacity; acknowledged native evidence survives.
Committed post-commit Failure does not roll back the outer UOW or authorize a
second ID, replacement owner, retry or automatic resubmission. These guarantees
are mapped by [UJS1–UJS10](../../src/test/scala/org/goldenport/cncf/job/JobUnitOfWorkSubmissionSpec.scala).

The [recovery truth table](durable-job-recovery-contract.md),
[rehydration R2/R3](durable-job-runtime-rehydration-contract.md) and
[parent conformance decision](../../.codex-workflow/phases/PHASE-69.7/JM69-10-native-restart-conformance-decision01.json)
permit eligible proof-complete unstarted V2 Submitted work through its original
port and due timer/queue. Running/Suspended/startedAt-present interrupted work
remains RecoveryRequired, with no automatic replay or resubmit, no refund/reset
and no manual raw-row/proof/grant repair. Scheduled SC5 and retry RC5 are safe
started-work refusals, distinct from unstarted success and completed writer retry
with fresh terminal reads; see
[scheduled](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala)
and [retry](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala).
The design choice is decided-conformance-review-pending, never positive
started-work recovery achieved.

[Current-caller query](durable-job-operation-response-query-contract.md) retains
exact eligible owned OperationResponse payload hydration under the same Ready
owner's lease and original access/policy. Legacy Absent/raw body-free/getResult=None
and authorized UnavailableAfterRestart remain limits; a terminal summary cannot
reconstruct a result or CBD Report. Startup credentials and administrator labels
do not bypass current tenant/subject/scopes. Retain Mine/Operator admission,
redacted diagnostics, original durable controls and
[experience](../../src/test/scala/org/goldenport/cncf/job/JobExperienceCrossProcessAcceptanceSpec.scala)/
[security](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala)
caller isolation.

[Retention and maintenance](job-operations-contract.md) retain terminal-only
expiry, LegalHold, authorized deletion, cleanup/refusal and paid authority.
Ready's maintenance registration is distinct from zero replay/write/body/queue
effects; refused startup installs no owner, and owned shutdown closes timer,
scheduler and native resources. Notification persistence/inbox remains provider-owned;
writer forwarding and fresh no-replay observation establish no generic fresh inbox.
Current representative9/accumulator132 is planned-not-run. Accepted guidance
promotion, protected native Step review/local commit and separate Phase full
validation/review/version/local release/closure audit remain pending. This
appendix claims no CBD report recovery or accepted Step/Phase outcome.
