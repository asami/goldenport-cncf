# Managed terminal closure continuation

This contract extends managed Workflow persistence with a dedicated closure
continuation. It does not change ordinary lifecycle transition admission.

## R1 — Dedicated application-admitted continuation

`WorkflowComponentStorage.bindTerminalClosureContinuationC` binds the normal
owning Component, explicit instance/checkpoint collections and definition catalog.
The application admits its exact closure purpose, public target and independent
authority before invoking it. Configuration, passive data and this API do not
certify business acceptance, external nonexecution or Human consent.

Inspection returns the actual unsuspended Completed frontier, immutable terminal
entry and Entity revision. The typed target fixes owner, instance, execution
reference, correlation and terminal facts. Execution re-reads native authority;
it accepts only that exact frontier and expected revisions. It adds one Active
entry with typed original-terminal provenance and a separately retained business
checkpoint. It creates no Session, WorkOrder, execution queue or second ledger.

## R2 — Immutable terminal history and strict ordinary paths

The original Completed entry and all earlier history remain unchanged. Additive
`terminalClosure` provenance names the owning operation, continuation reference
and actual immediately preceding terminal sequence/revision/causation. The new
entry preserves correlation, composite occurrence and committed predecessor.
Ordinary append and mechanical paths cannot append this marker or reopen a
Completed instance. Only the dedicated managed primitive constructs this append.
Subsequent ordinary progress and completion use existing owners. No application
identity, Phase, Task or incident is interpreted by the framework.

Old histories and JSON without the new optional provenance keep their meaning.
Definition reopening uses admitted execution references, not raw source equality.

## R3 — One managed same-store transaction

Native instance CAS and unique checkpoint Create use the existing canonical
conditional transition, managed completion participant and verified host lease.
No raw provider, physical transaction or resource escapes. Actual expected Entity
revision, owner and collection admission apply. Atomic guard/Create refusal
publishes no partial history/checkpoint. Uncertain commit/readback remains
transaction-indeterminate; no automatic retry establishes effect settlement.

## R4 — Collection and conflict

Exact duplicate input collects the saved receipt without another append. A
changed request under that reference refuses. Independent retained collection
validates the saved source/successor against actual native history, including
later ordinary progress; it confers no new effect authority. Absent receipt does
not prove external nonexecution. Stale/wrong-owner/definition/correlation/terminal
or Entity revisions refuse. Concurrent requests can publish one successor only.

## Executable specification and boundary

`WorkflowTerminalClosureContinuationSpec` covers E1 additive history/strict
ordinary admission and generated variants; E2 actual managed SQLite execution,
reopen, duplicate and later progress; E3 refused targets and competing writers;
E4 encode/readback uncertainty and authoritative collection. Test observations
are specification-owned. Validation, independent review, local producer delivery
and consumer qualification remain separate observations. This producer API does not
perform Git, tracked records, artifact activation, old live Session recovery or
application business closure.


## Public contract

`WorkflowComponentStorage.bindTerminalClosureContinuationC[A](operation, codec)`
returns `WorkflowManagedTerminalClosureContinuation[A]`. Direct `bindC` has the
same captured Subsystem, Component, configuration, operation, definition catalog,
instance collection and checkpoint collection arguments as the mechanical binding.
Neither binding opens an independent datastore.

| Public operation | Input and retained result |
| --- | --- |
| `inspectC(instance)` | Loads an actual unsuspended Completed instance and returns `WorkflowTerminalClosureContinuation.Target`. |
| `executeC(intent)` | Revalidates target; publishes one native append with its checkpoint or collects an identical retained intent. Returns `Completed(receipt, current)`. |
| `loadRetainedC(reference, instance)` | Returns `Option[Retained(receipt, current)]` after exact owner/operation/reference/instance and native-prefix validation. |

`Target` fixes Component, instance identity, admitted definition identity/revision,
correlation, terminal history sequence, native revision, original terminal
causation and expected Entity revision. `Intent[A]` supplies that target, an
explicit `WorkflowMechanicalTransition.Reference`, a progression in the admitted
execution definition, new causation and a `WorkflowProtocolV1.TypedValue[A]`.
The application selects its own durable purpose/authority payload and public
target before calling this storage primitive.

The returned `WorkflowMechanicalTransition.Receipt[A]` is a reused passive DTO;
its `current` is the original Completed source and `next` the one Active append.
The dedicated closed receipt schema is
`cncf.workflow-terminal-closure-continuation.v1`. The checkpoint is namespaced
separately from mechanical receipts and uses the same configured complete-receipt
byte bound. The returned wrapper's `current` is the currently loaded native
frontier and may contain later ordinary progress. These facts are deliberately
distinct. No execution program or external side-effect callback is accepted.

A reference reused for different target, progression, causation, payload or bound
operation refuses. The same original request may collect a saved receipt after
later progress; it does not make that old terminal target eligible for another
append. A new reference with a stale target refuses. Collection absence is a
storage observation only. After an indeterminate managed attempt the caller uses
the exact reference and instance to inspect retained native data; external
business effects remain the application's independently established evidence.
