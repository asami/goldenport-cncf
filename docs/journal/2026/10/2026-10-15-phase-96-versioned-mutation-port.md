# 2026-10-15: Canonical versioned mutation producer ports

P96-07C2B1 authors the settled internal producer seam between existing typed
save/patch primitives and the C2A raw outcome. The
[specification](../../../spec/entity-versioned-mutation-port.md) records the
exact internal APIs and behavioral correspondence; the
[design](../../../design/entity-versioned-mutation-port.md) records ownership
and compatibility intent. Source and executable specifications are authored;
validation and independent observation remain pending. No execution,
Step/Phase acceptance or closure is established by this entry.

The initial authoring continuation retained the existing partial adapters,
fixture and portable specification. Exact existing EntityId constructor and
test subtype source context was admitted read-only to complete the originally
required null-collection scenario. Its fixture subtype supplies valid
collection/timestamp/entropy super arguments and overrides only the collection
accessor to null. The selected action compares the exact existing
`argumentMissing("id.collection")` failure and retains zero-effect expectations.
Production adapter bodies retain the original canonical producer composition;
source history was retained under the frozen continuation instruction.
The required native specification and paired documents complete original
authoring scope without upstream mutation or a new behavior decision.

## Development fixture samples and provenance

The [port fixture](../../../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortFixture.scala)
reuses [AggregateObservedSnapshotFixture](../../../../src/test/scala/org/goldenport/cncf/action/AggregateObservedSnapshotFixture.scala)
Root, lawful canonical collection/identity, complete seeded business/audit/
security/NULL fields and overflow content. Fixed time remains the original
Aggregate fixture time. C2A Probe modes and independent raw/physical oracles
retain their existing source provenance. These are development-only samples,
not production resource registration or authorization evidence.

Portable matrix sample title is `日本 Alpha Ω`, retained business is
`retained business`, and starting revision is 7. Native matrix title is
`Native 日本 Alpha Ω`, retained business is `native retained business`, and
starting revision is 11. Changed samples use the explicit `changed:` prefix;
separate projection/ACK/effective-policy examples use `Before`/`After` at
revision 1. All four save/patch representation variants are exercised.
Generated title/retained values use nonempty ASCII/Japanese/Greek characters
and positive revisions 2..50 with current/changed/stale/projection-fault modes
and shrinking that retains lawful nonempty text.

The recording delegate observes actual provider results and preserves actual
capabilities. Native scenarios reuse the existing mandatory PostgreSQL fixture,
owned pools, full physical snapshots and ACK fault source. Normal pools use
normalized columns; the unchanged ACK writer and independent ACK reader use
their existing default false convention. Actual writer close is a test-owned
post-ACK resource fault and uses the same original projection context.
The tests' source obligations do not constitute native execution evidence.

## B1 oracle authoring corrections

The first focused execution exposed three B1 expectation mismatches against
unchanged canonical behavior. The bounded repair records their causal source
history without establishing a subsequent execution or acceptance result.

`B1-ORACLE-PATCH-OBSERVABILITY-001`: a title-only patch reaches the existing
update complement without an existing record, so absent trace/correlation
values come from the selected immutable ExecutionContext. Full typed save
supplies the seeded record and retains its old values. The B1 oracle now
prescribes applied patch values independently from pre-action rows and context,
preserving Field keys, metadata and positions and the complete physical
snake_case cells. The same expectation covers durable changed state after a
real unknown acknowledgment. Typed save, NoOp, stale and rejected
acknowledgment retain their existing full-state expectations.

`B1-ORACLE-CONSTRUCTED-SOURCE-001`: independently constructing the expected
failure in a specification creates a different typed SrcPos from the producer.
The B1 vocabulary separately requires singleton source facets at the same
index, positive line/column and the prescribed producer/specification file
origins, then compares the complete remaining Observation and Conclusion
with timestamp normalization and exact previous identity. The selected origins
are StandardEntityRevisionPart for embedded mismatch, EntityStore for missing
id.collection, and EntityMutationPolicy for invalid effective policy or missing
observation. The inherited C2A same-origin, structured failure, stale and
closed-resource vocabulary remains the source for its original comparisons.

`B1-ORACLE-HYDRATE-REVISION-ORDER-001`: canonical typed projection hydrates
overflow content before Probe admission upserts the intentionally wrong
revision. The legacy projection expectation now independently performs that
same hydration-before-admission ordering from persisted rows and owned body,
then uses the unchanged projection vocabulary with the expected hydrated
record. Full ordered Fields, legacy identity/revision, separate legacy
sessions and probe effect counts remain required, including native scenarios.

These source corrections retain the original matrix, positive native and total
property obligations. Parent focused validation and independent observation
remain pending; this history supplies no Step/Phase acceptance or closure.

## Native guard-schema oracle authoring correction

`B1-ORACLE-NATIVE-GUARD-SCHEMA-001`: the existing native direct/CAS
planner prepares the absent exclusion guard column as nullable `deleted_at`
TEXT. The B1 fixture now prescribes that preparation from the complete
pre-action rows and physical snapshot with an explicit caller-selected backend
expectation. Only the native patch-selection scenarios enable this expectation;
their Given clauses require the guard to be absent before the action.

SQLite omits the NULL guard from logical Records. PostgreSQL instead appends
exactly `Field("deletedAt", Field.Value.Single(null))` to the selected and
unrelated root Records. The physical expectation appends `deleted_at` with
SQLite `TEXT` or PostgreSQL `text` and adds a NULL cell to every preexisting
root row, preserving complete columns, ordered rows, existing cells, body
tables and unrelated tables. These prepared baselines compose with the
existing title/revision/audit and applied patch context expectations, and
Then clauses retain full logical and physical comparisons plus explicit guard
key, singleton NULL shape and position checks.

The SQLite diagnostic boxing clue came from JDBC `getObject` returning Integer
and the diagnostic cell comparison using Java `Objects.equals`; the actual
Scala Map comparison retains its original numeric equality and unchanged Long
revision expectation. No numeric normalization or predicate change is made.
This entry records authoring history only and supplies no execution receipt or
acceptance/closure claim.

## Remaining original work

All four original [P96-07 criteria](../../../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. Original C2B still requires canonical create/delete capture before
residency, Entity AND Aggregate authorization/domain/alias correspondence,
ordinary registered Display mutation/descriptor Operations, strict raw JSON
and ordered physical headers, and CLI/MCP/SPI revision representation.
Original C3 still requires actual enclosing commit and selected contextual
`afterCommitResponseC` Detail/List reload, positive create/delete lifecycle,
committed pending/failure and no replay on native PostgreSQL PG12.
P96-08..P96-11, protected Step reviews/commits, the sole epoch-1 full Phase
review, final validation/release/ledger and reciprocal aggregate handoff retain
their original owners. Existing separate source-size/type Hygiene stays
outside this producer-port authoring.
