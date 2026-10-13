# Component record Creates in Workflow completion

## Public capability

`ComponentRecordStorage.bindForCallC` binds an exact component, logical store,
collection and entry under the current native managed call. Host-captured
Create grants, the original live lease and issuing thread govern binding and
use. This is storage scope, not actor authentication or Operation authorization.

`stageCreateC(record): Consequence[ExecUowM[Unit]]` freezes a Create for that
fixed target without writing. Null, payload-selected physical `id` and
unsupported mutable/scalar values are rejected. Supported nested Records and
sequences are copied into an immutable declarative intent. Changing the source
payload after staging cannot change the eventual committed payload.

The returned program is data, not a retained grant, provider or committed
publication receipt. It may be composed into the host-selected closing program
ending in a complete typed Completed result. Ordinary UnitOfWork execution
rejects the staging operation. No public raw intent, guarded update, Save or
Delete is introduced; existing immediate `createC` behavior is unchanged.

## Canonical execution admission

The verified Subsystem Workflow route derives local Create collections only
from its captured record grants matching the current Workflow component and
that Workflow's declared logical store. Neither submitted payloads nor staging
programs expand this policy. Validate the captured declaration against every
Workflow instance/Continuation and EventStore cursor/event/index collection
before resource resolution. Missing declarations add no local Create scope;
foreign-owner or foreign-store grants add nothing to this completion.

Keep the base instance/Continuation owner, collections, cursor guard and exact
cursor target unchanged. The raw/nonverified route gains no local Create scope.
Execution still requires the native current lease and one shared physical
managed resource for Workflow and EventStore. No component owns another pool
or calls SQLite directly. EventStore's event-kind policy still selects event
persistence; a record grant does not change event selection.

## Atomic publication and observation

Admitted Creates join the selected closing result and adjacent instance/history
update in the existing canonical completion transaction. Create never upserts
or resets an existing record. Duplicate targets, existing-target collisions,
unsupported effects and invalid closing results cannot partially publish the
new set. A failure before commit rolls the complete transaction back.

Successful publication requires the existing authoritative committed-readback
checks, not merely successful program construction or SQL calls. An uncertain
post-commit observation remains indeterminate; do not split writes, retry a
Create as an upsert or report the rows as verified. Reopen observes the exact
committed records through newly admitted capabilities. A semantic decision
that produces no enrichment stages no partial enriched inputs.

## Executable specifications

- `org.simplemodeling.cncfclient.WorkflowClosingCreateSpec`: public consumer,
  canonical completion and pool reopen, collision/no-partial-publication and
  unresolved decision behavior.
- `ComponentRecordStorageHostSpec`: live fixed-target admission, payload
  snapshot isolation and captured owner/store/reserved-target exclusion.
- `WorkflowClosingProgramPreparationSpec` and
  `WorkflowCompletionDataStorePersistenceSpec`: bounded inert preparation,
  ordinary-interpreter rejection, native transaction rollback and readback.

These specifications do not establish an application's production allocator,
human verifier, public advance, server/MCP deployment or Phase acceptance.
