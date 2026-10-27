# Managed continuation supersession

The Component storage factory provides `bindDecisionSupersessionC` and
`bindWorkOrderSupersessionC`. Each returns a typed managed binding with
`loadRetainedC(identity, instance)`. Decision retains
`executeC(identity, expectedRevision, current, entry, TypedValue(checkpoint))`.
WorkOrder requires `executeC(identity, expectedNativeRevision,
expectedEntityRevision, current, entry, TypedValue(checkpoint))`; the mandatory
entity revision is carried directly from the inspected target, never silently
replaced with the latest revision. A retained result exposes
`receipt.identity`, `sourceRevision`, `nextRevision`, `payload` and `current`.
The WorkOrder binding additionally provides read-only `inspectC(identity,
instance)`. Its `Pending` exposes the exact current native record, immutable
`issued` interaction, native `continuation` record (including status/claimId),
and actual continuation `entityRevision`. It returns no pending target once the
current active suspension no longer selects that identity. Inspection grants no
claim or external nonexecution proof; execute reselects and guards its commit.
Both facades use one package-internal transaction engine and the existing
Component-owned guarded datastore. There is no new lifecycle or storage owner.

## Admission and ownership

The application determines the admissible correction, exact-target authority,
external execution assessment, unchanged business state and normal continuation
position. The typed checkpoint should retain the original issue, reason,
authority/evidence, source, unchanged business state and pending-reissue inputs.
CNCF does not interpret that application's payload as authorization.

CNCF selects the actual current active suspension, original atomic issue and
matching Component, definition, instance, completion operation, context snapshot,
completion and evidence bindings. WorkOrder calls must provide the exact current
native record. The native revision and indexed entity revision must agree. The
selected continuation must be Available and unclaimed. Its entity revision must
match the caller's exact inspected expectation before preparation, and the same
continuation entity revision and instance entity revision guard the commit.
Claim/release ABA invalidates the old expectation even if native state and claim
status appear unchanged; renewed application admission must use a fresh target.
Native records compare through semantic native JSON, including definition
identity/revision and all current/history fields, rather than DefinitionBinding
object reference equality. Wrong kind,
foreign target, stale revision, claimed/completed target and concurrent claim or
completion refuse progression.

**Available and unclaimed does not prove external nonexecution.** Supersession
neither stops external work nor certifies cancellation. Completed work requires
actual result collection; live work requires observation or an existing supported
cancellation path. Unknown effects remain unknown. The application must qualify
its authority and execution assessment before allowing another attempt.

## Atomic disposition and ordinary replacement

One existing managed unit of work conditionally appends the application's exact
active, unsuspended native history entry, changes only the selected continuation
to Superseded, and saves the typed checkpoint beside it. Original issued bytes
and all prior native history remain immutable. No answer, completed result,
successor ID or replacement request is manufactured. The application later uses
its ordinary atomic WorkOrder issue path to publish a distinct replacement and
persist the real old/new relationship at actual issuance.

Success requires verified managed readback. A projection/codec failure after
native commit can be indeterminate; it is not rollback proof. Do not repeat an
external effect on that basis. Collect the durable checkpoint using a working
application codec and reconcile the actual native history. Encoding failure
before preparation does not mutate either record.

A repeated `executeC` may refuse the already consumed frontier. The consumer
implements idempotent recovery by collecting `loadRetainedC`. Collection checks
the saved receipt against actual adjacent historical revisions: the original
active suspension with its original contract bindings and the following active,
unsuspended entry. It remains available after later issuance, later completion
and physical provider reopen. `current` describes the current frontier, which
may already be suspended again or completed. Historical collection grants no
claim, dispatch, completion or new-effect authority. An old late completion is
not accepted as the replacement's result.

## Wire compatibility

The Decision public binding, execute/collection signatures, receipt shape,
`decisionSupersessionPayload` field and
`cncf.workflow-decision-supersession.v1` schema remain supported. Both receipt
formats have exactly `schemaVersion`, `identity`, `sourceRevision`,
`nextRevision`, `typeIdentity`, and `payload`; revisions are canonical decimal
strings. WorkOrder uses `workOrderSupersessionPayload` and
`cncf.workflow-workorder-supersession.v1`. Public callers cannot choose a kind,
provider, arbitrary lifecycle or receipt policy.

## Executable specification boundary

`WorkflowCompletionDataStorePersistenceSpec` retains the original managed
Decision scenario and adds managed SQLite WorkOrder cases for immutable issue,
normal later replacement and completion, duplicate collection, physical reopen,
stale/foreign/wrong-kind/claimed/completed rejection, altered suspension facts,
late old completion refusal, encoding failure, indeterminate decode and an
actual competing native claim between selection and conditional commit, and
Available claim/release ABA with stale expectation refusal and separately
specification-owned renewed admission. All
application authority/correction descriptions in these tests are
specification-owned; they are not real Human authorization or provider
nonexecution observations. Source coverage is not an execution or acceptance
claim. Application integration, real authority/effect evidence, validation,
independent review and publication belong to their owning workflow.
