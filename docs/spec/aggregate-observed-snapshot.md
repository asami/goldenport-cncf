# Aggregate observed authoritative snapshot

Document role: normative PHASE-96 / P96-07 / P96-07C1 contract.
Status: initial implementation contract; parent validation and independent
observation remain required. The original four P96-07 criteria remain OPEN.
This contract supplies the canonical Aggregate outcome seam; P96-07C2/C3,
PG12, later Steps and all original closure gates retain their obligations.

## Typed owner and input

`ActionCallRepositoryPart` provides this protected, final subroutine:

```scala
aggregate_update_observed_snapshot_c[A <: RecordPresentable](
  aggregateName: String, targetId: EntityId, commandName: String,
  observedRevision: EntityRevision
)(command: A => Consequence[A]): Consequence[EntityRecordSnapshot]
```

Aggregate and command names must be nonnull, nonempty and exactly trimmed.
The typed target, observed revision and command must be nonnull. Admission
precedes callbacks and storage effects. Existing typed identity and revision
grammar remains authoritative.

The existing Aggregate chokepoint records `update-observed-snapshot` and the
exact aggregate, target and command. Its Authorization phase applies the
existing declared Aggregate update policy before resolution or domain command.
Component, relation, condition and default authorization semantics remain
framework-owned; calling this helper grants no permission.

## Authoritative resolution and strict policy

The exact root resolver owns Aggregate-to-Entity correspondence, including
registered `AggregateDefinition(aggregateName, entityName)` aliases. The
selected EntityCollection must have an Optimistic plan and admitted Embedded
or Detached managed revision metadata. Missing, unsupported, unmanaged or
nonOptimistic bindings reject structurally before domain callbacks or effects.

Execution uses a validated explicit policy: Optimistic, WriteIfChanged,
ObservedRequired and Some(the exact supplied revision). Authoritative current
entity and revision come from the canonical EntityStore using the selected
persistent. Missing roots produce the existing entity-not-found failure.
Expected/actual revision mismatch produces the canonical stale conflict before
domain command or business equality. A current NoOp at the maximum revision
does not require a successor revision.

The actual registered contextual AggregateCollection resolves the domain
value using the current ExecutionContext. Missing registration rejects. The
selected exact root and aggregate identity must agree. Managed-field-stripped
root persistent fields must correspond to the admitted Aggregate Record under
the existing root-key subset rule. Mismatch produces the existing structured
aggregate-root-snapshot-mismatch conflict before the command.

## Domain method and canonical persistence

The Method phase invokes the typed command. Returned structured failures are
preserved. NonFatal callback exceptions, null Consequence, null result and
invalid `toRecord` boundaries yield fixed redacted structured diagnostics;
exception messages and domain secrets must not escape.

Current and updated root Records pass the selected collection's exact root
validation with a required canonical ID. The updated ID must equal the exact
target, including for a different ID in the same collection. Missing, malformed
or foreign IDs reject before persistence.

Every lawful updated value enters Persistence, including equal business data.
Embedded roots use `saveRecordVersioned`; Detached roots use
`saveRecordDetached`, each with the exact canonical Record, observed revision
and strict policy. There is no Aggregate-equality shortcut, manual revision
advance, direct datastore fallback or fabricated read-after-write receipt.
The provider owns its atomic revision recheck after a concurrent winner.

The Embedded result is the actual EntityRecordSnapshot unchanged. The Detached
carrier is adapted using its actual Record and revision. Current NoOp returns
the complete current Record and revision without body publication or View
invalidation. Canonical collection resident reconciliation and eviction remain
owned by EntityCollection. The resolved root Entity View is invalidated only
when the actual returned revision differs from the observed revision. Existing
committed-entity-projection-failure handling retains the resolved Entity owner.

## Executable correspondence

`AggregateObservedSnapshotSpec` documents both representations on real memory
and task-private SQLite: changed and current NoOp outcomes, stale identical and
different commands, aliases, missing/ambiguous/foreign roots, unsupported
metadata, declared manager-only denial, domain and callback failures, wrong
IDs, Aggregate/root mismatch and an intervening canonical winner. Complete
selected, overflow-body and unrelated rows are the preservation oracle.

`PostgresqlAggregateObservedSnapshotAcceptanceSpec` supplies the corresponding
required PostgreSQL 17.11 native outcomes with independent provider readers,
physical SQL snapshots, actual independent concurrent writes and resource
closure. Disabled native execution supplies no acceptance credit.

Two memory laws, one per representation, and one native law over both require
at least 100 successes and zero discards. Their bounded nonempty
ASCII/Japanese/Greek samples vary title, untouched data and changed/current/
stale mode. Explicit shrinking strictly reduces value lengths or mode
complexity while retaining nonempty data. Independent full-record expectations
must not be obtained from production mutation or equality functions.

Existing Aggregate create/update/command and their default policies retain
their signatures and behavior, covered by the unchanged Aggregate resolve and
edit-determinism specifications.

## Enclosing lifecycle

A returned snapshot describes the provider transaction outcome. The enclosing
UnitOfWork/Action owner still owns commit, abort, committed receipt and selected
contextual reload. This helper does not introduce a lazy/free program wrapper,
ordinary mutation route, Display receipt or postcommit reload. The original
Display integration and Phase acceptance requirements remain mandatory.
