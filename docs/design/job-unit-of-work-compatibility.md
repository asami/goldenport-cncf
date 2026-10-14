# Job UnitOfWork Compatibility

The [contract](../spec/job-unit-of-work-compatibility-contract.md) owns R1–R5,
the public fresh-context API and its seven executable examples. This design
describes only their shared source-build prerequisite responsibilities. Native
staging comes from accepted source commit
`5500e8e4c4ababd55d551bfce771318433484fbe`; it is reconciled through the selected
three-file boundary rather than an import of the whole commit.

## Native reservation and publication

JobEngine provides a structured unsupported staging default. The original
InMemoryJobEngineAdmissionPart validates a real Ready durable owner/bridge and
Persistent Async input, reserves native resource/lifetime capacity and issues
the identity using the submitting UOW's context. It creates no staged Job
registry or record. Its record builder and publication helper serve ordinary
submission and post-commit activation together, retaining metadata, priority,
input, scheduling and ordinary Sync/Async behavior.

JobUnitOfWorkSubmission has one responsibility: ownership of the unpublished
native lease. Its Pending/Activated/Released state is UOW resource lifecycle,
not Job status or durable authority. After registration, only Pending may stage
the callback. Normal Abort/Dispose release Pending. Activation enters the same
native owner lease before the State monitor, verifies native pending ownership
and builds the record with commit-time now.

Publication retains durable admission -> lifetime root -> resource commit ->
ownership-transfer callback -> cancellation scope -> record/event -> Async
queue/timer. At the callback the lease becomes engine-owned. Pending cleanup
runs in finally, while acknowledged publication remains engine-owned after
later exceptions. Post-commit Failure is aggregated under Committed termination
without undoing the outer transaction or introducing retry/resubmission.

The [existing owner design](durable-job-runtime-owner.md) and
[action execution semantics](../spec/action-execution-semantics.md) retain their
native lease, dispatch and lifecycle responsibilities. No owner, lifecycle
bridge, response codec, provider, SQL pool or runtime replacement is required.

## Public context transform

The purpose-named public `withFreshUnitOfWorkContext` delegates the existing
private rebinder for supported Instances and returns structured operationInvalid
for unsupported carriers. The private rebinder self-binds a fresh UnitOfWork
and real interpreter through the profile-preserving runtime helper, retaining
the original runtime token and lifecycle actions. It preserves core/security,
clock/random/entropy/IDs/control, invocation, Job/cancellation and runtime
facilities while separating UOW transaction/callback state. Existing context
transforms, response transport lineage and private runtime visibility are
unchanged.

CBD's transferred execution task consumes the returned context and its public
UOW, retaining its reference/self-context, ActionCall core, authorization,
Provider cancellation, exact completion and successful committed-result checks.
Its gateway passes the submitting context's public UOW to genuine staging.
These two API adjustments do not own production StartReview, Review persistence,
durable fresh reads/replay or acceptance of the transferred Phase 14 work.

## Source package graph and proof boundary

CBD root and fixture apply one shared source-JAR task. It dynamically selects
the admitted CNCF RootProject, checks effective version against the declared
dependency and obtains the actual Compile/packageBin JAR. A regular non-symlink
JAR enters the ordinary unmanagedJars/dependencyClasspath path, allowing normal
CAR resolution to consume its real runtime descriptor. Artifact mode contributes
no source JAR and retains normal dependency checks.

The graph flows root Test resource prerequisite -> fixture generation/package/
API/Scaladoc/CAR/SAR/project-output tasks -> selected CNCF packageBin -> CNCF
generated inputs. It has no reverse CNCF -> CBD edge, JAR rewrite, metadata
fabrication, artifact fallback, local publication or generator recursion.

JC01–04 observe the genuine native fixture's reservation, publication, body and
ordinary scheduling behavior. FUC01–03 observe profile preservation, isolated
actual UOW callbacks and the declared unsupported public boundary. They add no
broad recovery/fault/cross-process chapter or new result serialization contract.

This synchronization supplies build prerequisites only. It does not reaccept
canonical Phase 69.7 or complete CNCF78, Cozy64, CBD11 or CBD14. Real Cozy normal
production, generated Scala 3/native metadata, Directory/CAR public Factory and
actual CBD no-CML-reparse proof remain required. Separate Step/Phase validation,
review, release and authorized main integration remain owed; CBD repository-full
validation remains deferred to Phase 11.1.
