# Job UnitOfWork Submission Contract

status=authored_review_pending
phase=69.7
slice=JM69-10CBD2B
validation_status=focused_passed
acceptance_status=notaccepted

This native prerequisite supplies an issued Job identity for a durable consumer
binding before its outer transaction commits. The original JM69-10 and Phase
acceptance obligations remain open. The executable chapter is
[JobUnitOfWorkSubmissionSpec](../../src/test/scala/org/goldenport/cncf/job/JobUnitOfWorkSubmissionSpec.scala).

## R1 — Native staging admission

`JobEngine.stageSubmit(tasks, unitOfWork, option): Consequence[JobId]` MUST have
an unsupported structured default. Native staging MUST require Persistent,
Async, the existing Ready Subsystem-owned durable owner and its bound lifecycle
bridge. It MUST use `unitOfWork.executionContext`. Under the existing State
monitor it MUST validate runtime, inputs, options, task/note cardinality and
lifetime policy before reserving native retained/pending/root capacity and
issuing one genuine JobId. Capacity refusal MUST precede ID issuance.

## R2 — Unpublished reservation

Before commit, the ID MUST have no JobRecord, Entity Job, lifetime root, durable
payment, evidence request, event, queued work, timer or task execution. The root
ticket is pure. Staging MUST NOT call metadata getters used to construct a
record; existing pure task-declaration validation retains its original law.
No staged Job registry, fabricated Submitted state or immediate-submit fallback
is permitted. Option metadata/input and original tasks MUST be retained for
publication, and record timestamps MUST use actual commit-time now.

## R3 — UOW lifecycle and native activation

One private UnitOfWorkResource MUST own Pending/Activated/Released transitions.
After registration it MUST check Pending, because an ended UOW releases late
resources synchronously while returning successful registration. Only Pending
resources may stage a post-commit callback. Termination/disposal releases Pending
exactly once; a later commit MUST NOT resurrect it.

After outer transaction and event commit, activation MUST enter the original
DurableJobRuntimeOwner lease, then the existing State monitor. It MUST recheck
admission/force-close and native `isPending`. It MUST build and publish the
already-issued ID through ordinary durable admission, lifetime publication,
resource commit, cancellation scope, record/event and Async queue/timer paths
in their existing order. Native publication transfers ownership to the engine;
UOW termination MUST NOT release an Activated lease. Submission success metrics
MUST reflect publication, never an uncommitted reservation.

## R4 — Refusal and resource cleanup

Abort, prepare rejection, disposal, late registration, shutdown and activation
Failure MUST release unpublished capacity exactly once and dispatch no body.
Throwable cleanup MUST run in finally and preserve original control propagation.
Native acknowledged publication/evidence MUST survive a later exception; cleanup
MUST NOT refund paid durable authority. Ordinary Sync, Async and delayed submit
behavior, observations and resource release MUST remain compatible.

## R5 — Committed failure and recovery

An activation Failure MUST flow through original UOW post-commit aggregation.
The outer transaction remains Committed and cannot be rolled back by this
callback. An earlier unrelated callback Failure MUST NOT veto later activation.
No retry, second ID, replacement runtime or automatic resubmission is allowed.
A process exit loses an unpublished reservation. The later CBD reader must
project a durable Review binding whose exact native Job is missing as explicit
RecoveryRequired; that consumer projection belongs to subsequent CBD integration.
Original started-work RecoveryRequired/recovery contracts remain unchanged.

## Executable GWT mapping

All leaf metadata uses `spec:job-unit-of-work-submission-contract`, phase69.7
and sliceJM69-10CBD2B. GWT clauses sit at each actual boundary, including inside
every generated property case.

| Example | Rules | Given | When | Then |
| --- | --- | --- | --- | --- |
| UJS1 | R1,R2,R3 | Real ready native owner and open UOW | Stage, commit, manual drain | Exact ID unpublished before commit, signed Published then terminal revision, one run |
| UJS2 | R3,R4 | Open or prepare-rejecting UOW | Abort or rejected commit, repeat endings | Exact reserved capacity released, no publication/dispatch |
| UJS3 | R3,R4 | Disposed reservation or ended UOW | Commit or late stage | No resurrection, no ID issuance success, resources released |
| UJS4 | R4,R5 | Native keyed/bridge faults or post-publication exception | Commit | Attributed Failure and Committed termination; no dispatch/refund/retry |
| UJS5 | R1,R4 | Genuine reserved native job | Quiesce/forceCancel then commit | Refused activation, pending/provisional capacity gone |
| UJS6 | R1,R2 | Invalid inputs/options/tasks/notes or absent owner/bridge | Stage | Structured refusal before ID/evidence/body; default unsupported |
| UJS7 | R1,R4 | Genuine leases fill all but one native pending slot | Stage twice, abort, stage again | One fits; refusal before ID; exact freed slot reusable |
| UJS8 | R3,R4 | Fresh native owner per 1..8 cardinality/ending case | Commit/Abort/Dispose, seed69710201/min24, normal shrinking | Exact IDs/counts and native pending/run counts; other endings never dispatch |
| UJS9 | R3,R5 | Earlier failing post-commit callback | Commit, repeat dispose/commit | Aggregate Failure and retained native publication; one activation/run |
| UJS10 | R2,R3 | Future/elapsed schedules and metadata/input | Stage, advance, commit, native timer/drain | No precommit timer; same ID/metadata; commit-time scheduling; ordinary submit compatibility |

Related native authority: [runtime owner](durable-job-runtime-owner-contract.md),
[durable record](durable-job-record-contract.md), and
[design](../design/job-unit-of-work-submission.md).

## Selected validation evidence

The parent-admitted subject3 focused command completed eight suites and90tests:
90passed, zero failed/canceled/ignored/pending and no aborted suites. SBT and
wrapper exited0; permission consumed; shared lockreleased. Actual result:
PHASE-69.7-JM69-10CBD2B-focused03-result@1. This establishes the native UOW
submission prerequisite, preserving all original whole-Step protected review
and complete CBD/fresh-process/Phase acceptance obligations. See the
[journal](../journal/2026/10/2026-10-06-phase-69.7-job-unit-of-work-submission.md)
for repair history, actual clause mapping and runtime warning limitations.
