# Strict Entity create atomic-provider contract

Document role: normative intended behavior for PHASE-96 / P96-07 / P96-07B2B2A.
Implementation, new executable acceptance and whole-Step acceptance are pending.
This contract records the parent-decided requirements; it does not claim that
the selected port or provider implementations already exist.

The [shared rules](../../RULE.md), [repository rules](../rules/repository-rules.md)
and [document lifecycle](../../ai/directive/core/document-lifecycle.md) govern
this document. The existing [revision contract](entity-conflict-and-conditional-transition.md)
owns managed initialization and authorization. All four original
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. Duplicate body corruption and fresh-root rejection leaving an
orphan body remain unresolved product blockers.

The [PostgreSQL Phase 96 persistence contract](postgresql-phase-96-persistence.md)
adds mandatory native PostgreSQL behavior and acceptance alongside retained
memory, SQLite and MySQL obligations. The
[primary Knowledge Store decision](../design/postgresql-primary-knowledge-store.md)
owns the selection rationale. The existing 17-path atomic-create draft remains
authored and unvalidated; neither its presence nor historical backend results
establish PostgreSQL safety or P96-07 acceptance.

## Scope

This contract applies to strict canonical `EntityStore.create`,
`EntityStoreSpace.create` and `UnitOfWorkOp.EntityStoreCreate`, including
Embedded and Detached managed initialization and admitted collections without
a managed binding. Generic `DataStore.create`, upsert, guarded successor
transition and Update/Delete APIs retain their existing contracts. They gain
no new safety claim from this specification.

## Selected additive port

The following signature-only declarations describe the intended API shape,
omitting implementation bodies and defaults. They are additions owned by
`org.goldenport.cncf.datastore`. They are contract labels, not links to future
source files. Data declarations MUST have instance-independent package or
stable object ownership.

```scala
final case class EntityCreatePlan(
  collection: DataStore.CollectionId,
  entryId: DataStore.EntryId,
  record: Record,
  sideEffects: Vector[EntityVersionedSideEffect] = Vector.empty
)

final case class EntityCreateResult(record: Record)

trait EntityCreateDataStore { self: DataStore =>
  def entityMutationProviderCapabilities: EntityMutationProviderCapabilities
  def createEntity(plan: EntityCreatePlan)(using ctx: ExecutionContext):
    Consequence[EntityCreateResult]
}
```

`DataStoreSpace.createEntity(plan)(using ctx): Consequence[EntityCreateResult]`
MUST own selected-provider, capability, same-provider and result admission.
`AtomicCreate` MUST be appended to the existing `EntityMutationProviderFeature`
cases without changing prior cases, ordinals, defaults or persisted/wire
encodings. Only providers implementing the actual atomic operation may
advertise it; trait presence alone is insufficient.

The explicit port's `createEntity` default MUST reject unsupported creation through
the existing structured `EntityNativeMutationSupport` capability helper.
It MUST NOT delegate to separate generic writes or fabricate readback.
The additive API retains current unchanged SNAPSHOT coordinates, existing
generic signatures and public `CreateResult[T]`. Strict canonical creation
on an unqualified legacy custom provider intentionally fails closed before
storage mutation. A compatible wrapper MUST explicitly delegate the real
atomic port and its capabilities. Multi-write fallback is forbidden.

The stable `EntityCreateCheckpoint` ADT/object has
`BeforeSideWrite(effect: EntityVersionedSideEffect)`, `BeforeRootWrite` and
`BeforePublish`. Memory and SQL providers expose the override-expected hook
`protected def create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit]`
with default unit. This follows the shared `xxx_Yyy` hook rule. Public
`createEntity` and public/protected parameter labels remain camelCase; concrete
data alternatives belong only at package level or in stable objects.

## Required behavior

### CA1: Canonical initialization and authority

The canonical owner MUST retain existing ID selection/generation,
`EntityPersistentCreate`, domain/security/transition authorization, server
complements, managed-field rejection and INITIAL1 initialization. It MUST NOT
invent an existing root, a dummy expected revision, a new input contract or
client-supplied authority. No-managed-binding admission remains supported.

### CA2: Pure root and side preparation

Strict create MUST use `ContentBodyStoragePolicy.planForVersionedSave` to
prepare the root and typed Save/Delete effects. Preparation MUST finish
without root or side mutation. It MUST NOT invoke eager `prepareForSave` or
perform separate datastore writes.

### CA3: Actual provider and capability admission

`DataStoreSpace` MUST choose the real root provider and require both
`AtomicCreate` and authoritative-record capability (`AuthoritativeRecordResult`).
It MUST additionally require `AtomicSideEffects` whenever effects exist, and
reuse actual same-provider admission for every side collection. Missing port,
missing features or cross-provider selection MUST fail before effects.
Provider substitution and distributed pseudo-transactions are forbidden.

### CA4: Plan and storage-target validity

The provider MUST validate a non-null, well-shaped plan/root identity and
backend-addressable side targets before effects. No side target may alias its
root entry; ambiguous repeated side targets MUST reject. Identity comparisons
MUST use existing normalized backend keys, EntryId and Record conventions,
not full CollectionId equality to determine storage aliases. No new identity
grammar is introduced. Pure canonical initialization already owns INITIAL1.

### CA5: Absent-root atomicity and duplicates

Absent-root creation MUST have one atomic provider boundary. An existing root
MUST return native `DataStoreDuplicate` taxonomy without root, body or unrelated
mutation for Overflow, Inline, Absent and SetNull. Absence preflight alone is
insufficient: publication or a unique INSERT MUST enforce absence inside the
actual storage boundary. Create MUST NOT overwrite/upsert or return NoOp/Stale.

### CA6: Memory publication

The built-in and searchable memory providers MUST retain their existing
synchronized provider boundary, compute candidate collection snapshots with
typed effects and normalized root, and check before-side, before-root and
before-publish hooks. They MUST publish the complete next collection map once.
Rejection or a throw before publication MUST leave the actual store unchanged.
Candidate maps are real provider staging, not a test shadow datastore or
application lock.

### CA7: SQL transaction

SQL MUST use one owned JDBC connection and one transaction for every side
Save/Delete, unique root INSERT and authoritative readback. A pre-existing
duplicate guard MUST precede effects. Side-first then root execution MUST stay
inside that transaction so actual root-after-side rejection proves rollback.
Known precommit failure MUST roll back the whole unit. Generic create/save/delete
methods acquiring another connection MUST NOT be called for these operations.
Native PostgreSQL is mandatory alongside SQLite and MySQL. Its id-arbitrated
root INSERT MUST retain strict absence enforcement without querying a
transaction aborted by a unique error; the
[settled PostgreSQL algorithm](../design/entity-create-atomic-provider.md#postgresql-native-create-decision)
owns this provider decision.

### CA8: Schema preparation and durable visibility

MySQL schema/table/columns MUST be prepared outside the data transaction through
the existing schema lifecycle. Row operations inside the transaction MUST NOT
execute new MySQL DDL whose implicit commit could publish a partial resource.
Data atomicity does not promise transactional rollback of schema DDL. SQLite
uses its native transaction behavior; durable observations use independent readers.
That DDL qualification applies to MySQL. PostgreSQL MUST prepare or resolve its
native schema, columns and addresses on the same transaction connection before
row effects. Its admitted transactional schema preparation and root/side rows
form one rollback unit. Native catalog, quoted case, search_path, primary key
and actual id collation MUST govern admission and writes; unsupported or
ambiguous schema fails structurally before effects. Preparation does not
authorize user-database migration or implicit revision initialization.

### CA9: Authoritative result and trust

Success MUST return the exact authoritative stored root only after local
provider publication/commit acknowledgment. Owned providers MUST validate
identity and admitted managed-revision correspondence before publication when
they can do so. Invalid/null foreign success or an unknown escaped result MUST
NOT become successful `CreateResult` or a false unchanged-state guarantee.
It MUST yield structured indeterminate/untrusted-result failure without replay.
A candidate record MUST NOT be substituted for authoritative stored readback.

### CA10: Failure and indeterminate acknowledgment

Existing structured `providerFailure`, `transactionFailure` and
`transactionIndeterminate` taxonomy and reasons MUST be retained. Known
precommit/commit rejection with verified rollback MUST retain every selected
and unrelated row. An uncertain acknowledgment may mean both rows committed
or neither; callers MUST NOT assert absence, compensate, retry or label it a
known rejection. A positively committed pair followed by indeterminate return
is explicitly not a success receipt.

### CA11: Canonical result and enclosing lifecycle

The public result MUST remain `CreateResult[T](id, Some(authoritativeRecord))`,
with canonical cache reconciliation/authorization and exact collection, ID,
INITIAL1 and body values. A provider acknowledgment does not establish a
public whole-UoW/Display committed receipt. Enclosing UoW, ActionEngine
committed-outcome acquisition and contextual `afterCommitResponseC`
reload/projection/no replay retain original Slice C ownership and remain open.

### CA12: Real executable acceptance

Acceptance MUST exercise real native PostgreSQL, memory, SQLite and MySQL, both managed
representations, complete selected/unrelated state, duplicate body modes,
root/side prewrite rejection, before-publish failure, commit rejection and
after-real-commit indeterminate behavior. Existing CFS preservation laws and
independently registered cases MUST remain. Given/When/Then belongs at actual
setup/action/expectation boundaries, with semantic `should`/`which`/`in` and
total bounded PBT requiring at least 100 successes and zero discards. Output
trimming, pending/ignore/cancel and expectation inversion MUST NOT hide errors.
Documents and failing properties provide no acceptance credit.
PostgreSQL MUST execute positively through normal ComponentDataStore and
SystemNode-managed routing as required by PG1/PG11; identity-only admission,
test-only JDBC and disabled/unavailable or skipped cases cannot satisfy it.

## Required executable traceability

Every row below is a future acceptance obligation, not a passing result.
Existing [memory behavior](../../src/test/scala/org/goldenport/cncf/entity/EntityCreateFailureSafetySpec.scala),
[SQLite behavior](../../src/test/scala/org/goldenport/cncf/entity/SqliteEntityCreateFailureSafetySpec.scala)
and [properties](../../src/test/scala/org/goldenport/cncf/entity/EntityCreateFailureSafetyPropertySpec.scala)
retain the original desired CFS laws. Future provider/create/compatibility/PostgreSQL/MySQL
declarations are intentionally unlinked until their actual sources exist.

| Matrix IDs | Required observations | Contract |
| --- | --- | --- |
| CFS-E1/S5 | Generated ID, exact collection and authoritative INITIAL1, full overflow hydration and old-row retention for Embedded/Detached; actual memory, independent SQLite and positively executing real PostgreSQL and MySQL equivalents. | CA1, CA2, CA7–CA9, CA11 |
| CFS-E2/E3/E4/S1/S2/P1 | Duplicate Overflow/Inline/Absent/SetNull retains complete root/overflow/unrelated maps and native duplicate taxonomy on required PostgreSQL/memory/SQLite/MySQL providers; total bounded PBT at least 100 successes, zero discards. | CA2, CA5–CA8, CA10, CA12 |
| CFS-E5/S3/P2 | Actual side prepare/write boundary once, then exact fresh root prewrite rejection once on required PostgreSQL/memory/SQLite/MySQL; provider marker retained, fresh root/body absent, unrelated maps identical after abort/rollback, independent SQL readers; passing PBT at least 100 successes, zero discards. | CA6, CA7, CA10, CA12 |
| CFS-E6/S4 | Side prewrite rejection once, root unreached, complete snapshots unchanged on actual memory/SQLite and positive real PostgreSQL/MySQL. | CA6, CA7, CA10, CA12 |
| CFS-E7/E8 | Managed revision rejects before all effects; actual UoW create authorization denial preserves persistence, resident and View state with original fixtures/grants, including native PostgreSQL plus retained memory/SQLite/MySQL correspondence. | CA1, CA11, CA12 |
| CA-PORT | Missing port, AtomicCreate/AtomicSideEffects/readback, cross-provider mapping, invalid/aliased plans and malformed result cannot yield success; known preflight rejection does not mutate. Real compatible delegation wrapper preserves positive canonical creation on required PostgreSQL/memory/SQLite/MySQL; PostgreSQL storage aliases use native address/collation admission. | CA3, CA4, CA9 |
| CA-PUBLISH | Memory before-publish failure retains actual state; PostgreSQL/SQLite/MySQL failure after root and side work but before commit retains full independent durable snapshots. | CA6–CA8, CA10 |
| CA-COMMIT | Real PostgreSQL/SQLite/MySQL precommit rejection rolls back. Actual connection.commit followed separately by explicit indeterminate return, escaped throw or null ACK leaves both durable rows with correct INITIAL/body, no successful result or false known-unchanged claim and exactly one attempt/no replay. This injects acknowledgment loss; it is not proof of a real network outage. | CA7–CA11 |
| CA-RACE | Two attempts at one fresh ID through real PostgreSQL/memory/SQLite/MySQL boundaries cannot publish a partial/mismatched pair or overwrite the winning body. At least one acknowledged consistent winner; loser has native duplicate or legitimate structured provider contention failure without losing-state mutation. Independent SQL connections and native PostgreSQL id arbitration are required. No caller mutex. | CA4–CA7, CA9, CA10 |
| CA-ACCUMULATOR | Later exact class-C executable PLAN freezes one representative creation acceptance and the original affected 41-suite accumulator extended with exact new PostgreSQL/provider/create/legacy/current-consumer suites; real PostgreSQL and MySQL environments explicitly enabled with positive execution and no skip. Historical 41-suite and proposed 56-suite subjects are not PostgreSQL evidence. No unselected generic full test; final full release gate retains original ownership. | CA11, CA12 |

Existing CFS fault adapters MUST later attach to actual
`create_EntityCheckpoint` in real owned providers, replacing obsolete generic
create/save interception for this strict-create path. BeforeSideWrite(Save)
and BeforeRootWrite count exact frozen plan targets and retain original
taxonomy, full-state oracles and side-before-root rejection proof. BeforePublish
models unpublished candidate/transaction failure. SQL uses the existing
`versioned_mutation_commit` boundary to exercise both precommit rejection and
actual connection.commit followed by transactionIndeterminate, never fabricated
persistence or outcomes.
The after-real-commit matrix also retains escaped-throw and null-ACK handling;
unknown rollback remains indeterminate. Native PostgreSQL faults and durable
observations must follow the same exact checkpoints and complete-state laws.

## Retained integration obligations

The original [UB13 obligations](display-mutation-bridge.md#ub13-remaining-original-obligations)
remain mandatory: actual registered Entity AND Aggregate, ordinary mutation
routes, committed outcomes and selected contextual reload. P96-08 through
P96-11, final release/validation/review, the original whole CNCF96 objective,
studio Phase 2 and app Phase 5 integration (Phase 4 prototype) remain required.
This class-D document slice changes no runtime, route, schema, read-v1,
generated ABI or packaging behavior.
