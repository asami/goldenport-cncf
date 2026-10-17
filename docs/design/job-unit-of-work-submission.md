# Job UnitOfWork Submission

status=authored_review_pending
phase=69.7
slice=JM69-10CBD2B
acceptance_status=notaccepted

The parent freezes CBD2B-API/RESERVE/UOW/ACTIVATE/FAILURE/COMPAT in
PHASE-69.7-JM69-10CBD2B-implementation-manifest01@1. The normative boundary is
[R1–R5](../spec/job-unit-of-work-submission-contract.md).

`InMemoryJobEngineAdmissionPart` retains original State, resource/lifetime
budgets, queue, timer, bridge and Subsystem durable owner. Its private record
builder and publication helper serve ordinary submission and commit activation.
Staging validates and reserves capacity under State, then issues an ID using the
UOW execution context; its pure lifetime ticket is not published.

`JobUnitOfWorkSubmission` owns only the native lease termination lifecycle. It
registers with the existing UOW resource registry and verifies Pending after
registration. Pending release is idempotent; Activated transfers ownership to
the engine. Dispose followed by commit cannot reactivate Released. This local
state has no Job status/result or durable authority and is not a shadow record.

The callback enters the same ready owner's `_execute_c` lease before State and
rechecks native capacity/admission. Publication uses the same ID and commit-time
now. The sequence remains durable admission, lifetime root publication, native
resource commit, cancellation scope, JobRecord, submitted event, then Async
queue or timer. Ownership transfers at native resource commit, before fallible
record/event work; acknowledged durable evidence is retained after exceptions.

Unactivated Failure/Throwable releases the lease in finally. UOW reports
post-commit Failure under Committed termination; it does not undo the outer
transaction. Other callback Failures aggregate and do not veto this callback.
Activated resources remain engine-owned after disposal and repeated commit.
Control exceptions propagate through the original UOW cleanup semantics.

No public activation handle, overload, caller ID, submit fallback, registry,
replacement owner, engine, SQL pool or provider is introduced. Ordinary immediate
Sync inline execution and Async/delayed submission retain their original laws.
After process exit, an unpublished reservation has no recoverable native Job.
Later CBD integration owns explicit RecoveryRequired projection of a missing
exact Job binding; no replay or orphan resubmit is added here. Started-work
RecoveryRequired and the original SC5/RC5 decision remain open.
