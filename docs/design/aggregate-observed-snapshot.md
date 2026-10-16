# Canonical Aggregate observed outcome design

Document role: settled PHASE-96 / P96-07C1 responsibility decision.
Status: initial implementation authored; parent verification, selected
validation and independent observation remain required. The
[behavior contract](../spec/aggregate-observed-snapshot.md) owns outcomes.

## Existing owner and exact alias correspondence

`ActionCallRepositoryPart.aggregate_update_observed_snapshot_c` is one protected
final subroutine returning the actual canonical EntityRecordSnapshot. It
retains the existing Aggregate chokepoint and declared update authorization.
The selected EntityCollection comes from the existing exact root resolver,
including AggregateDefinition's explicit Aggregate-name to Entity-name alias.
The registered AggregateCollection is selected by the exact Aggregate name and
resolves with the current ExecutionContext. Selecting only the ID's collection
name would lose that explicit registration and is not this design.

An authoritative canonical EntityStore read supplies both root and revision.
The root persistent remains responsible for full entity Record representation.
The helper checks managed-field-stripped root-key subset correspondence against
the selected Aggregate and requires its exact canonical target ID. It uses a
tuple for transient root/revision state, introducing no instance-owned data
type. Aliases retain the resolved root's Entity View name.

## Strict ordering and persistence outcome

Input validation precedes authorization and all callbacks. Authorization
precedes resolution of the domain command. Admission requires the existing
Optimistic plan and managed Embedded or Detached revision representation.
The helper explicitly validates Optimistic, WriteIfChanged, ObservedRequired
and the supplied observed revision. Authoritative stale comparison precedes
Aggregate command execution and business equality, including stale identical
input. It does not demand a successor revision for a current maximum-revision
NoOp.

Fixed redacted NonFatal/null boundary handling contains contextual Aggregate
builder, persistent Record conversion, typed command and result conversion.
Returned structured failures retain their original Conclusion. Exact Record
validation and equality of the updated canonical ID to the target precede
persistence; same-collection foreign IDs confer no authority.

Every admitted updated value reaches the selected canonical collection save,
even when its business value is unchanged. The provider owns atomic expected
revision comparison, no-op equality, revision advancement, root/body
transaction and authoritative readback. A write between initial resolution
and persistence is rejected by that provider, with no application lock or
alternate writer.

Embedded persistence returns its actual EntityRecordSnapshot unchanged.
Detached persistence adapts the actual returned Record/revision carrier.
There is no fabricated receipt, manual revision increment or later datastore
read used as a replacement outcome. Canonical collection residency and stale
eviction remain in EntityCollection. Successful changed revision invalidates
only the selected root Entity View; current NoOp retains its View. Existing
committed projection failure handling retains the resolved owner.

## Real-provider specification boundary

The development fixture owns instance-independent Root, Sample, Snapshot and
Attempt types in its companion. Root retains the complete canonical Record,
including business, security, audit, nullable and body metadata. The actual
contextual Aggregate builder reads through canonical EntityStore rather than
capturing a static domain snapshot. The ActionCall probe records only its
actual return and callback count. Persistence state exists solely in the real
memory, SQLite or PostgreSQL provider.

Fixtures seed a selected INITIAL1 root and unrelated revision91 root, each
with independently specified full security/audit/business data and overflow
content. Canonical readers use separate contexts and native/SQLite readers use
independent provider resources. Raw physical SQL snapshots preserve all tables,
columns and rows. Changed-outcome expectations alter only the independently
specified title/revision/update audit values; all other selected, body and
unrelated values must remain equal. NoOp and rejected attempts require exact
complete preservation.

The two memory laws and one native law reuse bounded root pairs and pools,
seeding sequentially at INITIAL1. Total nonempty samples and explicitly
imported shrinkers reduce value lengths and mode complexity without discards.
Required native execution uses the existing pinned PostgreSQL acceptance
lifecycle and closes all owned pools through its finally boundary.

## Retained lifecycle and compatibility

All existing Aggregate signatures, legacy early-equality behavior, default
policies, codecs, datastore and UnitOfWork APIs retain their contracts. This
helper is eager Consequence evaluation; it introduces no ExecUow wrapper or
claim of pre-effect free-program construction.

Its snapshot is a provider outcome, not enclosing UnitOfWork/Action commit
evidence. Commit, abort, response receipt, ordinary mutation routes and actual
selected contextual Display reload remain P96-07C2/C3 responsibilities.
PG12, the original four P96-07 criteria, later Steps, full Phase review, final
validation, release and aggregate handoff remain mandatory.
