# Strict Entity create atomic-provider design

Document role: settled PHASE-96 / P96-07 / P96-07B2B2A ownership and algorithms.
The [strict-create specification](../spec/entity-create-atomic-provider.md)
defines normative intended behavior. Implementation and executable acceptance
are pending; all four [P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. The decisions below are settled inputs to subsequent production
implementation, not an account of already implemented provider safety.

The [PostgreSQL persistence specification](../spec/postgresql-phase-96-persistence.md)
adds mandatory native acceptance. Its
[primary Knowledge Store decision](postgresql-primary-knowledge-store.md)
does not replace canonical or DataStoreSpace ownership. The existing 17-path
atomic-create implementation draft is preserved and unvalidated; PostgreSQL
implementation and acceptance remain pending.

## Ownership

| Owner | Retained responsibility | Contract |
| --- | --- | --- |
| Canonical Entity/UoW boundary | Identity, EntityPersistentCreate, domain/security/transition authorization, server complements, managed rejection and INITIAL1; canonical public result and cache reconciliation. | CA1, CA2, CA11 |
| ContentBodyStoragePolicy | Pure prepared root and closed typed side Save/Delete values through planForVersionedSave. | CA2 |
| DataStoreSpace | Select actual root provider; admit explicit port, advertised capabilities, same-provider side collections and trustworthy result. | CA3, CA9 |
| Actual memory/SQL provider | Backend plan identity/alias admission, root absence enforcement, one publication/transaction, authoritative readback and structured outcome. | CA4–CA10 |
| Executable acceptance owner | Real-provider behavior, original preservation laws, failure/race/compatibility matrix and selected accumulator. | CA12 |
| Enclosing UoW/ActionEngine/Display integration | Whole committed outcome and actual selected contextual afterCommitResponseC reload/projection without replay. | CA11; original Slice C |

The provider-neutral `EntityCreatePlan` contains collection, entryId, prepared
Record and Vector[EntityVersionedSideEffect]; `EntityCreateResult` contains the
authoritative Record. `EntityCreateDataStore` is an additive auxiliary capability
with self-type DataStore, advertised EntityMutationProviderCapabilities and
public `createEntity(plan)(using ctx)`. These future declarations are owned by
`org.goldenport.cncf.datastore` at package or stable object level. No concrete
data ADT belongs to an enclosing implementation instance.

`AtomicCreate` is appended to the existing feature enum while preserving prior
cases/ordinals, defaults and persisted/wire encodings. Capability advertisement
belongs to actual implementing providers. Port presence is insufficient.
The explicit port's createEntity default rejects unsupported creation using existing structured
`EntityNativeMutationSupport`; it performs no generic fallback writes or
invented readback. Generic APIs and public CreateResult[T] retain their
signatures under unchanged SNAPSHOT coordinates. Legacy custom providers
fail closed before storage writes until explicitly qualified; real wrappers
delegate both atomic port and capabilities without substituting a datastore.

## Canonical preparation and admission algorithm

The existing [canonical create source](../../src/main/scala/org/goldenport/cncf/entity/StandardEntityCreatePart.scala)
owns ID choice and managed initialization. The intended strict path retains
those decisions and uses the pure
[ContentBody preparation](../../src/main/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicy.scala)
to finish the complete root/side plan before execution. Eager prepareForSave
is excluded from strict create. A no-managed-binding collection remains admitted
through existing canonical policy, without inventing a revision representation.

DataStoreSpace resolves the real root provider, requires AtomicCreate and
AuthoritativeRecordResult, and requires AtomicSideEffects whenever the plan has
effects. It reuses the actual provider-instance correspondence boundary in
[DataStoreSpace](../../src/main/scala/org/goldenport/cncf/datastore/DataStoreSpace.scala)
for each side collection before provider invocation. Missing capability/port or
cross-provider selection returns structured rejection before effects. It does
not own a distributed transaction or replace the selected root provider.

Provider admission uses existing normalized backend collection keys and EntryId
and Record conventions to reject malformed/null plans, inaccessible side targets,
root/side aliases and ambiguous repeated targets before effects. Full typed
CollectionId equality alone is not a storage-alias test. Canonical initialization
retains admitted INITIAL1; a dummy revisioned root or expected revision is not
part of absent-root creation.

## Memory algorithm

The [built-in memory provider](../../src/main/scala/org/goldenport/cncf/datastore/DataStore.scala)
already owns a synchronized boundary and replacement collection-map staging for
atomic mutations. Strict create uses that actual provider boundary, including
the searchable implementation:

1. Admit plan and confirm root absence inside the synchronized boundary.
2. Snapshot affected collection entries and compute candidate states using typed
   side effects; check BeforeSideWrite for each exact effect before its staging.
3. Check BeforeRootWrite and stage the prepared normalized root with absent-root
   enforcement; obtain the authoritative candidate stored representation and
   validate identity/admitted revision correspondence before publication.
4. Check BeforePublish and publish the complete replacement collection map once.
5. Return EntityCreateResult only after publication succeeds.

All affected maps are actual provider staging, including Save/Delete effects.
Any rejection or throw before the single publication leaves actual store state
unchanged. Duplicate Overflow/Inline/Absent/SetNull cannot damage the winning
body or unrelated rows. A second attempt cannot overwrite the acknowledged
winner. This uses no caller lock or shadow test datastore.

## SQL algorithm and schema boundary

The existing [SQL provider](../../src/main/scala/org/goldenport/cncf/datastore/sql/SqlDataStore.scala)
owns JDBC connections, schema lifecycle, transaction wrapper and
`versioned_mutation_commit`. Strict create extends this provider ownership:

1. Validate plan. Prepare needed MySQL schema/table/columns outside the data
   transaction using existing schema lifecycle; PostgreSQL preparation follows
   the same-transaction decision below.
2. Acquire one owned JDBC connection and begin one transaction.
   Prepare/resolve PostgreSQL native schema, columns and addresses on this
   connection before row effects; SQLite retains its existing behavior.
3. Guard against a pre-existing root before any side effect.
4. Check BeforeSideWrite and apply each typed Save/Delete on that connection.
5. Check BeforeRootWrite and perform the unique root INSERT on that connection.
   The unique INSERT enforces absence against races beyond the preflight guard.
6. Read the authoritative root on that connection and validate its identity and
   admitted managed revision correspondence before publication.
7. Check BeforePublish, then use the shared versioned_mutation_commit boundary.
8. Return the authoritative result only after successful commit acknowledgment.

Known failure before commit rolls back every root/side row operation as one
unit. The side-first sequence deliberately retains a real root-after-side
rejection point for rollback proof. The implementation must not call generic
create/save/delete methods that acquire separate connections. No new MySQL DDL
executes among transaction row operations: DDL implicit commits could otherwise
publish a partial resource. Schema changes themselves are not covered by a
transactional DDL rollback claim. SQLite retains native transactional behavior;
independent durable readers establish the final row state.
That nontransactional DDL qualification applies to MySQL; admitted PostgreSQL
schema preparation shares the row transaction and its rollback unit.

### PostgreSQL native create decision

The native PostgreSQL path prepares or resolves schema, columns and addresses
on the actual transaction connection before row effects. It then guards
pre-existing root absence, applies the typed side-first Save/Delete effects,
and inserts the root with PostgreSQL `ON CONFLICT DO NOTHING` targeted at its
unique id arbiter. An update count of one continues authoritative readback,
identity/admitted-revision validation, BeforePublish and commit acknowledgment.
An update count of zero returns structured `DataStoreDuplicate` and rolls back
the complete transaction, including side writes and transactional preparation.
An actual SQL error rolls back the whole unit; no duplicate recovery query runs
in a transaction aborted by a unique error. Other constraint failures are not
reclassified as root-id duplicates.

Native catalog relation identity, effective search_path, quoted case, actual
columns/primary key and id collation determine address correspondence. Catalog
admission and writes use the same connection. Root/side aliases and ambiguous
repeated targets reject before effects, while legitimate case-distinct names
remain admissible. MySQL ASCII folding or case-insensitive table assumptions
cannot be transferred to PostgreSQL. Inaccessible, unsupported or ambiguous
schema fails structurally before effects; preparation is not user-database
migration or implicit revision initialization.

Generic Record/scalar/ContentBody carrier encoding remains unchanged and is not
automatically native JSONB storage. Exact PostgreSQL versions, physical type
choices, APIs, dependency/container baselines and test methods belong to the
next parent native implementation plan. This documentary decision selects none
of those details. Root upsert, separate generic writes, application locks,
compensation and create replay remain excluded.

## Checkpoints, failure and acknowledgment

The settled stable `EntityCreateCheckpoint` has BeforeSideWrite(effect),
BeforeRootWrite and BeforePublish. The protected override-expected provider
hook is `create_EntityCheckpoint(plan: EntityCreatePlan, checkpoint: EntityCreateCheckpoint): Consequence[Unit]`,
defaulting to unit. The `xxx_Yyy` spelling follows
[shared protected-hook roles](../../RULE.md#role-categories-for-protected-methods);
public createEntity and public/protected parameter names retain camelCase.
Future fault adapters attach to this actual memory/SQL hook, count exact Save
and root targets and retain original provider markers and full-state oracles.
Generic create/save overrides no longer describe the strict atomic path.

Known provider/precommit/commit rejection with verified rollback retains selected
and unrelated rows through existing providerFailure/transactionFailure taxonomy.
Unknown publication/result or acknowledgment retains transactionIndeterminate
semantics without compensation or replay. Foreign null/malformed success is
untrusted and cannot be projected as a successful CreateResult or a claim of
unchanged storage. No candidate is fabricated into authoritative readback.

The SQL commit matrix distinguishes rejection before commit from an explicit
real connection.commit followed by an injected transactionIndeterminate return.
The latter leaves a positively persisted correct root/body pair but no success
receipt. It demonstrates handling of injected acknowledgment loss, not a real
network outage. Uncertain acknowledgment generally admits both committed or
neither committed, never a partial pair; a later authoritative read resolves
state without automatic create replay.
The same after-real-commit requirement covers an escaped throw and null ACK:
neither may become a successful receipt or known unchanged-state failure.
Exactly one attempt and the correct independently durable pair must be observed;
unknown rollback retains indeterminate semantics. This applies to native
PostgreSQL and the retained SQL backends without changing enclosing ownership.

## Acceptance and consumer boundary

The specification's required matrix maps CA1–CA12 to CFS-E1/S5,
CFS-E2/E3/E4/S1/S2/P1, CFS-E5/S3/P2, CFS-E6/S4, CFS-E7/E8,
CA-PORT, CA-PUBLISH, CA-COMMIT, CA-RACE and CA-ACCUMULATOR. Real native PostgreSQL,
memory, independent SQLite and positively executing real MySQL remain mandatory,
including Embedded/Detached, full maps and total PBT. Existing failed properties
are counterexamples, not passing-case evidence. The later executable PLAN owns
exact provider/create/legacy/consumer source paths and focused commands before
implementation and independent conformance review.
It must include actual normal ComponentDataStore and SystemNode-managed native
PostgreSQL routing, the PG1–PG12 matrix, a representative and the exact expanded
current-consumer accumulator with positive PostgreSQL/MySQL execution and no
skip. Historical 41-suite and proposed 56-suite subjects establish no native
PostgreSQL acceptance.

The unchanged [Display bridge design](display-mutation-bridge.md) and
[UB13](../spec/display-mutation-bridge.md#ub13-remaining-original-obligations)
retain registered Entity AND Aggregate integration, ordinary mutation routes,
whole committed-outcome acquisition and contextual afterCommitResponseC reload.
Local provider acknowledgment cannot replace that receipt. CLI/HTTP/SPI/MCP,
Cozy/generated ABI, Flutter, read-v1 and packaging have no document-slice runtime
delta. Their actual later consumers retain original selected validation and
the complete class-C accumulator; final full validation/review/release remains
with the original owner.

## Rejected alternatives

The settled design rejects reordering two independent generic writes, eager
side effect plus compensation, caller/application pseudo-exclusion locks,
dummy revisioned roots, conditional successor or claimOrLoad/upsert used as
absent-root create. It also rejects unsupported-only create acceptance,
omitting real MySQL, replacing preservation laws with observed corruption and
treating local provider readback as an enclosing UoW/Display committed receipt.
Omitting required native PostgreSQL, substituting identity-only admission or
test-only JDBC, and querying after a unique error in an aborted PostgreSQL
transaction are likewise excluded.
