# Job UnitOfWork Compatibility Contract

This shared build prerequisite synchronizes only the native staging boundary
from accepted source commit `5500e8e4c4ababd55d551bfce771318433484fbe` and adds
public fresh-UOW context access. Its implementation is limited to JobEngine,
InMemoryJobEngineAdmissionPart, JobUnitOfWorkSubmission and the ExecutionContext
companion. The [design](../design/job-unit-of-work-compatibility.md) explains the
responsibility and package task boundaries.

This contract supplies no reacceptance of canonical Phase 69.7 and no completion
of CNCF Phase 78, Cozy Phase 64 or CBD Phases 11/14. Separate Step and Phase
validation, review, release and authorized main integration retain their owners.

## R1 — Native staging admission

`JobEngine.stageSubmit(tasks, unitOfWork, option): Consequence[JobId]` has a
structured unsupported default. Native staging requires Persistent Async and
the existing Ready Subsystem-owned durable owner with its original bound
lifecycle bridge. It uses `unitOfWork.executionContext`. Existing input, option,
task/note and lifetime validation precedes native capacity reservation and
genuine ID issuance. A refusal must not issue an ID or dispatch a body.

## R2 — Unpublished reservation

Staging retains the original tasks, metadata, priority, input and schedule in
one native reservation. Before commit there is no JobRecord, durable Job,
published lifetime root, durable payment, evidence request, event, queued work,
timer or body execution. The lifetime ticket remains pure. Metadata getters
used to build a record are deferred until publication. No fabricated Submitted
state or immediate-submit substitute is permitted. Record timestamps use
actual commit-time now.

## R3 — UnitOfWork activation and native ownership

The private JobUnitOfWorkSubmission resource owns only Pending, Activated and
Released. It registers through `registerResourceC`, checks Pending after
registration and stages `stagePostCommitC`. Pending termination releases the
native lease; Activated ownership belongs to the engine.

After outer transaction/event commit, activation enters the original owner's
lease before the State monitor. It rechecks the Ready owner/bridge and native
pending reservation, then publishes the already-issued ID through the ordinary
builder and publication path. The order is durable admission, lifetime root
publication, resource commit, ownership-transfer callback, cancellation scope,
record, submitted event and Async queue/timer. Submission success metrics belong
to publication. Ordinary Sync, immediate Async and delayed Async submission
share the same record builder/publication helper and retain their execution laws.

## R4 — Refusal and release

Normal Abort and Dispose release an unpublished reservation without publication
or dispatch. The accepted resource lifecycle also retains prepare rejection,
late registration, shutdown and activation-Failure cleanup; this compatibility
chapter does not expand those lower-level specifications. Pending release runs
in finally and preserves original control propagation. Publication transfers
ownership before record/event/queue work; cleanup must not refund acknowledged
durable authority after that transfer.

## R5 — Committed failure boundary

Activation Failure flows through the existing post-commit aggregation while
the outer transaction remains Committed. Other callback failures do not veto
later callbacks. No retry, second ID, replacement runtime or automatic
resubmission is added. Process exit loses an unpublished reservation; later
CBD integration owns explicit missing-native-Job RecoveryRequired projection
and its separate fresh-read/replay acceptance.

The existing [native runtime owner](durable-job-runtime-owner-contract.md) and
[action execution semantics](action-execution-semantics.md) remain authoritative
for managed leases, lifecycle, explicit Job intent and post-commit failures.

## Public fresh UnitOfWork context

`ExecutionContext.withFreshUnitOfWorkContext(ctx)` returns
`Consequence[ExecutionContext]`. For the supported Instance it delegates the
existing private `_rebind_runtime_context`. The returned context owns one stable
new UnitOfWork and real interpreter; that UnitOfWork is distinct from the
original and self-bound to the returned context. The transform preserves the
original core, clock, IDs, random/entropy, execution control, invocation key,
security, Job/cancellation context, framework, observability, resource and
evaluation facilities. Existing runtime core, token, context and lifecycle
actions remain preserved. Independent UOW transaction/callback state does not
commit the original UOW. Ordinary transforms and response-cell lineage remain
unchanged; no additional runtime visibility or implicit Job is introduced.

Null and non-Instance inputs return structured operationInvalid with
`execution-context-uow-rebind-unsupported`. They must not return unchanged-
context success or create a UOW effect.

## Executable examples

The authored chapter consists of exactly four
[native compatibility examples](../../src/test/scala/org/goldenport/cncf/job/JobUnitOfWorkCompatibilitySpec.scala)
and three
[fresh-context examples](../../src/test/scala/org/goldenport/cncf/context/ExecutionContextFreshUnitOfWorkSpec.scala).
Their Given/When/Then clauses accompany actual setup, actions and expectations.
They await parent validation and independent review.

| Example | Rules | Given | When | Then |
| --- | --- | --- | --- | --- |
| JC01 | R1–R3 | Ready native fixture and open UOW | Stage, commit, manually drain | Genuine ID absent before commit, exact native publication/metadata, one queued body and one successful run |
| JC02 | R2–R4 | Separate Ready fixture/UOW for each normal ending | Stage then Abort or Dispose | Retained/pending capacity released, no query/evidence/body, zero drain |
| JC03 | R1,R4 | Ready owner with Sync/Ephemeral options; separate fixture before start | Request staging | Structured refusal before ID/evidence/queue/body and unchanged resource baseline |
| JC04 | R3,R4 | Ready native owner and ordinary Sync/immediate/future Async | Submit, drain, advance manual time | Exact IDs and metadata retained, inline/queued/due-time execution preserved, each body once |
| FUC01 | Fresh context | Explicit clock/security/deterministic IDs/invocation/Job/cancellation | Apply public transform | Distinct stable self-bound UOW and original profile/facilities retained |
| FUC02 | Fresh context | Open original UOW with callback | Stage/commit fresh callback, independently abort original | Fresh callback once, original callback zero, independent termination |
| FUC03 | Fresh context | Null and delegating unsupported subclass | Apply public transform | Structured operationInvalid without unchanged-context success or UOW effects |

## Selected source package dependency

CBD root and its output-exclusion fixture retain source Compile/Test dependencies
on the same explicitly selected CNCF project. Both request its actual
`Compile / packageBin` JAR after comparing effective and declared versions;
the regular non-symlink JAR enters ordinary unmanagedJars/dependencyClasspath.
The direction is root Test resource task -> fixture normal package/output tasks
-> selected CNCF packageBin -> CNCF generated inputs. There is no CNCF -> CBD
edge, substituted artifact, fabricated descriptor or publication prerequisite.
Artifact mode and all ordinary generated/API/main-JAR/Scaladoc/CAR/SAR products
remain intact.

These package and API prerequisites preserve CBD's required real Cozy production,
generated Scala 3 accessor, independent native metadata, Directory/CAR public
Factory discovery and actual CBD consumption without CML reparsing. The seven
examples do not close that producer proof or the separately planned CBD Phase 14.
