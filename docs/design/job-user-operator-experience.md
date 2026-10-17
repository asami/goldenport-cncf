# Job User and Operator Experience Design

status=accepted
phase=69.6
slice=JM69-08A

The experience layer is a boundary adapter around `JobEngine` management
queries. `JobExperienceService` builds a scoped `JobQueryPolicy`, applies it
before query filtering/count/order/page/exact read, and maps only canonical
bounded values to records and HTML. `JobExperiencePolicy` composes the prior
session-first owner predicate with Mine, Application, and Operator admission.
The policy's visibility key is empty for existing callers; nonempty UX policies
bind management cursors to the normalized scope and application.

`JobExperienceView` owns presentation vocabulary, bounded progress, result
availability, and admitted controls. It owns no state. Diagnostic projections
redact task results and timeline notes at the edge. `JobControlPolicy` and the
engine remain authoritative for mutations and transition/replay outcomes.

The notification inbox is provider-owned. `UserNotificationInboxProvider`
extends the unchanged send provider only for implementations that offer inbox
persistence. `UserNotificationInboxRuntime` resolves that provider from current
component/subsystem wiring, validates returned page and exact-entry invariants,
and projects public fields. It contains neither notification storage nor a
success/unread cache. The event forwarder similarly delegates stable dedupe to
the provider and records accepted/refused/failure diagnostics without mutating
Job state.

`JobExperienceProtocol` supplies the only service descriptor, actions,
record/JSON output, Help metadata, OpenAPI methods, and REST shape. Its codec is
shared with Web parsing. `Http4sHttpServer` keeps descriptor ingress checks and
resolves the Job-control execution context before invoking the facade. The
narrow renderer contains escaped no-JavaScript HTML; forms reenter the standard
form/CSRF dispatcher, while `JobExperiencePolling` supplies a pure bounded
admission state machine used by the progressive detail script.

This design deliberately leaves final retained-result semantics, durable
integrity, retention/delete operations, and operational assurance to Phase
69.7. The catalog names those unavailable capabilities rather than presenting a
destructive or unsupported action.


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
remain authoritative. The [consolidated matrix](../spec/job-cross-process-acceptance-contract.md#jm69-10d2-consolidated-restart-acceptance-matrix--2026-10-06)
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
replace that admission. The [current-caller query contract](../spec/durable-job-operation-response-query-contract.md)
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

The [recovery truth table](../spec/durable-job-recovery-contract.md)
continues to classify Running/Suspended or Submitted with startedAt present as
RecoveryRequired; existing ActiveLifecycle and ExecutionAlreadyStarted reasons
remain unchanged. The [rehydration R2/R3 contract](../spec/durable-job-runtime-rehydration-contract.md)
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

The accepted Phase69.6 adapter/policy/notification/route design remains intact.
The [current operator specification](../spec/job-user-operator-experience.md#current-native-operator-guarantees--jm69-10d4--2026-10-06)
projects settled native guarantees; the
[closure boundary](../journal/2026/10/2026-10-06-phase-69.7-cncf-job-closure-boundary.md)
records CBD exclusion/HANDED_OFF_UNVALIDATED and pending Step/Phase acceptance.

The [original Ready owner/bridge](../spec/durable-job-runtime-owner-contract.md)
owns access and activation. [Native UOW R1–R5](../spec/job-unit-of-work-submission-contract.md)
reserves capacity before issuing a Persistent Async identity, retaining it
unpublished until outer commit: no JobRecord/Entity Job, paid root, evidence/event,
queue, timer or body; no staged registry or submit fallback. The original owner
lease then State/publication path activates the same ID at commit-time now.
Abort/dispose/failure releases unactivated capacity only; acknowledged paid
authority persists. Committed callback Failure permits no rollback, replacement
owner, second ID or automatic resubmit. [UJS1–UJS10](../../src/test/scala/org/goldenport/cncf/job/JobUnitOfWorkSubmissionSpec.scala)
maps these actual native boundaries.

The [truth table](../spec/durable-job-recovery-contract.md),
[rehydration R2/R3](../spec/durable-job-runtime-rehydration-contract.md) and
[parent conformance decision](../../.codex-workflow/phases/PHASE-69.7/JM69-10-native-restart-conformance-decision01.json)
keep eligible proof-complete unstarted V2 Submitted work on its original
port/timer/queue and Running/Suspended/startedAt-present work RecoveryRequired.
Operators retain safe refusal without replay/resubmit, raw-row/proof/grant
repair, refund or reset. [Scheduled SC5](../../src/test/scala/org/goldenport/cncf/job/JobScheduledCrossProcessAcceptanceSpec.scala)
and [retry RC5](../../src/test/scala/org/goldenport/cncf/job/JobRetryCrossProcessAcceptanceSpec.scala)
remain refusal oracles, distinct from eligible unstarted success and completed
writer retry with exact fresh terminal reads. The previous OPEN design question
is decided-conformance-review-pending; positive started-work restart is not claimed.

Exact eligible owned OperationResponse payload reading still uses
[current-caller policy/access](../spec/durable-job-operation-response-query-contract.md)
under the same Ready owner's lease; legacy Absent/raw getResult=None and
authorized UnavailableAfterRestart do not reconstruct terminal results or CBD
Reports. [Experience](../../src/test/scala/org/goldenport/cncf/job/JobExperienceCrossProcessAcceptanceSpec.scala)
and [security](../../src/test/scala/org/goldenport/cncf/job/JobSecurityCrossProcessAcceptanceSpec.scala)
retain tenant/subject/scope isolation, Mine/Operator policy and redaction without
administrator/startup-credential bypass.

[Retention/maintenance](../spec/job-operations-contract.md) retains expiry,
hold, authorized delete/cleanup and native refusal/paid authority. Ready's
maintenance timer is separate from zero replay effects; denied startup installs
no owner and owned shutdown closes timer/scheduler/native resources. Notification
inbox remains provider-owned and forwarding evidence proves no generic fresh inbox.
Current representative9/accumulator132 is planned-not-run; guidance promotion,
protected native Step review/local commit and separate Phase full validation,
sole original-epoch review, version/local release and closure audit remain pending.
