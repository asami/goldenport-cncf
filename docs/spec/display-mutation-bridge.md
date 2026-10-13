# Internal Display update program contract

Document role: normative PHASE-96 / P96-07 / Slice P96-07A contract.
Status: implementation authored; focused validation and parent verification pending.
All four original [P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. This Slice supplies an internal Entity update program constructor.

The [Display Mutation contract](display-mutation.md) owns pure preparation and
transport metadata. The [Display runtime contract](display-model-runtime.md)
and [bridge design](../design/display-mutation-bridge.md) establish canonical
ownership. The bridge adds no public Operation, schema, route or read-v1 change.

## UB1–UB2: Typed registration

`private[cncf] object DisplayMutationUpdateBridge` exposes internally:

```scala
entityC[P](selection: DisplayMutation.Selection, ownerId: String,
  collectionId: EntityCollectionId, fieldIds: Vector[String],
  persistent: EntityPersistentUpdate[P]): Consequence[EntityUpdate[P]]
```

`EntityUpdate[P]` is a final non-case class with private construction and
immutable private retained registration. It has no `copy` or public `apply`
bypass. Selection, explicit owner, canonical collection, persistent and a
nonempty unique vector of valid logical field IDs are required. These are
server registration values; clients cannot supply property paths.

Each admitted logical field maps only through `persistent.storeFieldName`.
Storage names must be nonempty, valid and unique. A name-only Record is checked
through the existing `SimpleEntityStorageShapePolicy.withoutManagedFields`;
managed, security and lifecycle names reject. No inferred naming transformation,
inverse discovery or EntityId parsing participates in ownership.

## UB3–UB6: Program admission

```scala
programC(prepared: DisplayMutationBinding.Prepared[P],
  authorization: UnitOfWorkAuthorization): Consequence[ExecUowM[EntityRecordSnapshot]]
```

Shapes precede correspondence. The preparation must carry operation Update,
the retained exact logical Source and complete definition/provenance selection,
an ID, observed revision, execution policy, and nonempty unique targets with
one valid unique binding ID per target. Every target must be Entity with the
retained explicit owner and an admitted logical field. Aggregate and foreign
targets reject before interpretation.

Both `prepared.resourceId.collection` and `persistent.collection(prepared.input)`
must equal the retained canonical collection. The policy is exactly Optimistic,
WriteIfChanged, ObservedRequired and Some(the exact observed revision), and its
existing `validateC` is called. Managed, AlwaysWrite or absent-policy fallback
is forbidden.

The supplied existing authorization must have `resourceFamily = "domain"`,
`accessKind = "update"`, `targetId = Some(the exact ID)`,
`collectionName = Some(collectionId.name)` and both component names equal
Some(the selection Source component name). Its access, mode, operation model,
relations and conditions are retained as supplied. This constructor neither
authenticates callers nor grants permission. A later ordinary Action must
obtain the authorization through existing framework facilities.

The bridge privately inspects
`Update.toChangesRecord(persistent.toStoreRecord(prepared.input))`. Actual storage
keys must be unique, belong to the selected targets' explicit mappings and
survive the existing managed-field policy. Partial and no-op patches may carry
fewer keys, including none. Unexpected seed fields, storage aliasing and managed
overrides reject. The Record is not an output API or replacement for typed P.
The pure persistent remains canonical interpreter input, and the interpreter
owns actual validation and serialization. NonFatal or null callback boundary
outputs produce the existing fixed redacted CALLBACK_INVALID diagnostic;
returned structured Conclusions are preserved.

## UB7: Canonical execution ownership

Successful admission constructs exactly one
`UnitOfWorkOp.EntityStoreUpdateByIdObserved(id, prepared.input, observed,
persistent, Some(authorization), strictPolicy)`, lifted by
`ConsequenceT.liftF(Free.liftF(op))`. Construction performs no provider lookup,
read, write, executor callback, commit, abort or View invalidation.

Only the existing UnitOfWork interpreter evaluates this program. It retains
canonical authorization, transition/domain checks, provider atomic revision
comparison, WriteIfChanged, resident coherence and View invalidation. The result
is the canonical authoritative record and revision snapshot. A snapshot is not
a public committed receipt; the enclosing owner still owns commit and abort.

## UB8–UB12: Executable specification

The development-only [fixture](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationUpdateBridgeFixture.scala)
registers a real embedded-revision EntityCollection with resident memory,
StandardEntityStore, an observation wrapper delegating to
`DataStore.inMemorySearchable`, ExecutionContext and UnitOfWorkInterpreter.
Provider entry-point counts record actual delegated operations rather than a
second writer or datastore map. Both title and description explicitly target
Entity owner BridgeItem. The admitted capture-detail definition supplies only
preparation metadata; it does not supply production registration or lifecycle
authority. Fixture samples and investigation provenance live in the
[journal](../journal/2026/10/2026-10-06-phase-96-display-mutation-bridge.md).

The [behavior specification](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationUpdateBridgeSpec.scala)
documents UB-E1 no eager effect and one changed update with authoritative raw,
resident and View coherence; UB-E2 current no-op preserving revision, updatedAt
and audit; UB-E3 stale-before-equality after an intervening canonical write;
UB-E4 genuine canonical authorization denial; UB-E5 domain and independent
transition rejection; UB-E6 registration, source, owner, collection, selected
patch, managed and authorization guards; and UB-E7 Aggregate rejection.

The [property specification](../../src/test/scala/org/goldenport/cncf/display/DisplayMutationUpdateBridgePropertySpec.scala)
documents UB-P1 bounded total valid values and selected subsets, with exact
revision delta iff changed; and UB-P2 current/stale identical business input
through real provider paths. Each property requires passed, at least 100
successful cases and zero discards, with no implication or filtering.

## UB13: Remaining original obligations

P96-07 still requires strict atomic delete and real positive create/delete
lifecycle proof, Aggregate ownership where selected, ordinary CLI/HTTP/MCP/SPI
mutation exposure, Detail/Edit/Save/refreshed Detail proof, authoritative commit
outcome acquisition and actual selected contextual reload through the ordinary
ActionEngine `afterCommitResponseC` response hook. Unsupported-only delete is
not acceptance. Receipt candidates cannot prove commit or query provenance.
Committed projection delay/failure, indeterminate acknowledgment and no replay
remain later canonical integration obligations. No P96-07 criterion is closed
by this Slice, and accepted P96-06 source/contracts remain unchanged.
