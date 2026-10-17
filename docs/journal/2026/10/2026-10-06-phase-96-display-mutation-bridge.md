# Phase 96 P96-07A internal update bridge authoring

Date: 2026-10-06.
Role: source investigation, development fixture/sample provenance and authoring
record. This journal is not a normative specification or acceptance record.
Manifest: phase96-p96-07a-implementation-manifest-001 revision 1.
Branch: codex/phase-96-display-service.
Starting HEAD: 1356e41cdee41dd40bc2212c1ef0297ab8875206.
Original Phase base: 6cbe877c53c116639feb11fa80546e79c051804c.

## Source investigation

DisplayMutationBinding supplies private admitted Prepared[P], explicit target
and binding-ID evidence, logical selection comparison and strict transport
policy. Its pure precondition cannot prove atomic provider comparison.
EntityPersistentUpdate owns collection, storage serialization and storeFieldName.
SimpleEntityStorageShapePolicy.withoutManagedFields supplies existing managed,
security and lifecycle exclusion. No inverse mapping was discovered or invented.

UnitOfWorkOp.EntityStoreUpdateByIdObserved already carries typed patch,
observed revision, canonical persistent, authorization and policy.
UnitOfWorkInterpreter evaluates existing authorization and transition hooks,
delegates provider mutation, reconciles resident state and invalidates Views.
evaluateInActiveUnitOfWorkC leaves commit/abort with its caller. The fixture owner
uses that existing method and then commits successful evaluation; the bridge
constructs only a program and never produces a public committed receipt.

UnitOfWorkVersionedMutationSpec supplies the registered collection/runtime
construction pattern. EntityRevisionSpecSupport supplies the actual embedded
revision registration check. EntityIdFixtureBridge supplies canonical fixture
identities from admitted typed collection values, without invented ID strings.

The canonical unchanged directive is Update.noop[String]. Its definition was
read directly in dev2026/simplemodeling-model's Update.scala, and the parent
confirmed the consumed 0.2.2-SNAPSHOT source archive entry
org/simplemodeling/model/directive/Update.scala plus existing CNCF
directive/UpdateSpec.scala uses. Update.toChangesRecord drops Noop. This lookup
resolved API spelling during initial authoring without changing the manifest.

## Development fixture and sample provenance

BridgeEntity and BridgePatch are new development-only typed models.
Both title and description explicitly target Entity owner BridgeItem; the
existing DisplayMutationContractFixture's description is instead Aggregate
Capture. The admitted capture-detail definition from
TypedDisplayProjectionFixture supplies preparation metadata only. This fixture
is neither Cozy output nor an Editing Studio production Entity registration.

Explicit storage mapping is title to item_title and description to
item_description. Update[String] plus an unchanged seed preserves unselected
business fields. Illustrative sample title Before, description Description,
changed title After and intervening value Concurrent are local deterministic
specification setup. Lifecycle/audit seed metadata is fixed at
2026-10-06T00:00:00Z with bridge-owner attribution for the fixture only.
These samples are not promoted to the normative contract.

The observation wrapper delegates all datastore state and atomic mutation to
DataStore.inMemorySearchable. It records provider entries, successful changes
and reads without creating a shadow map. The real StandardEntityStore,
embedded-revision EntityCollection with memory, ExecutionContext and
UnitOfWorkInterpreter remain the effect path. A registered View cache exposes
canonical invalidation. A real Component UnitOfWork authorization extension
can reject, and a real transition hook can reject independently or enforce
nonblank title. An explicitly pass-through preparation validator in one failure
scenario isolates the canonical domain check; ordinary preparation also rejects
the same blank title.

## Authored specification map

The [contract](../../../spec/display-mutation-bridge.md) and
[design](../../../design/display-mutation-bridge.md) describe only this internal
boundary. The behavior suite documents UB-E1..UB-E7 including current no-op,
stale equality, denial, domain/transition rejection and pre-effect guards.
The property suite documents UB-P1 selected-field preservation with exact
revision delta and UB-P2 stale-before-equality through real provider paths,
each requiring at least 100 successes and zero discards.

## Pending parent verification and remaining Step obligations

No SBT, static validation, independent review or acceptance was executed by the
implementation worker. Parent verification and selected representative then
accumulator execution remain pending. The private .cozy/launcher.yaml selector
is preserved unstaged. No accepted source, build/config, checklist, shared
directive or other repository is edited.

All four original P96-07 criteria remain OPEN. Strict atomic delete, positive
create/delete lifecycle, Aggregate integration, ordinary mutation routes,
Detail/Edit/Save/refreshed Detail, canonical committed outcomes and actual
selected contextual reload remain required. Later response integration retains
ActionEngine afterCommitResponseC. Unsupported-only delete, pure Receipt
validation and empty framework markers are not proof of lifecycle or commit.
Phase acceptance, review, transitions and commits belong to the parent.
