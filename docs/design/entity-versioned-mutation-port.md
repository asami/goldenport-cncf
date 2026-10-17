# Canonical versioned mutation producer boundary

Document role: settled PHASE-96 / P96-07 / P96-07C2B1 internal design decision.
The [paired specification](../spec/entity-versioned-mutation-port.md) owns
behavior. Source and executable specifications are authored; validation and
independent observation remain pending, with no Step or Phase acceptance.

## Producer ownership stays with the canonical store

The [C2A raw outcome](entity-versioned-mutation-outcome.md) captures actual
provider persistence before optional hydration/decoding. Four package-private
store ports now compose that existing boundary with the existing save and
patch result producers inside their current owners. Save accepts typed T;
patch accepts typed P and its canonical persistent. No writing responsibility
moves into the raw carrier, Space, a Record callback or Display adapter.

`StandardEntityReadSavePart` retains `_save_managed_result`; its two additive
ports select Embedded/Detached then use the matching existing typed outcome
helper. `StandardEntityUpdatePart` retains `_update_by_id_managed_result`; its
two ports select Embedded/Detached then use the matching existing Record
outcome helper. Expected revision and attempt policy are passed unchanged,
with no concurrency override. Actual guarded/direct/CAS selection,
readback/capability checks, server complement and ContentBody planning remain
within those canonical producers.

The immutable outcome retains the actual complete raw Record reference,
branch, selected identity and binding-derived revision. Projection follows
that capture. It can fail without losing known persistence or replaying a
write. Unknown commit acknowledgment never enters this success boundary.
The outcome cannot certify enclosing UnitOfWork commit or authenticate the
origin of arbitrary supplied carrier data.

## Additive compatibility and routing

Concrete unsupported base defaults preserve legacy/custom stores without a
new override obligation. All four share the existing structured
unsupported-capability failure and do not call old methods. Standard overrides
add the producer composition; legacy methods retain their original bodies,
signatures, policy assembly and direct exception boundary. New callers consume
the contained factory; old callers continue to evaluate their original
projection expressions.

Space uses its existing collection lookup and calltree ownership to delegate
exactly one matching port and return its carrier unchanged. It neither
interprets provider results again nor performs another hydration/read/write.
Missing-provider failure stays in the existing lookup helper. The internal
API has no public schema, serialized carrier or alternate operation protocol.

## Shape admission precedes existing semantic validation

Nullable typed inputs, persistent, identity/collection, revision Options and
policy shapes reject before lookup/effects. `Some(null)` is invalid rather
than an absent observation. The guard deliberately performs only shape
admission; effective assembled concurrency must be selected by the original
producer before its existing semantic validation. Raw None does not preclude
an Optimistic registered collection. The separately seeded legacy/new
VP-E7 correspondence records that settled evaluation order.

The null-collection negative is represented by a fixture-owned concrete
EntityId subtype with real valid canonical constructor arguments and a faulty
accessor. Upstream EntityId remains the identity owner. This expresses exactly
the new guard's input without changing identity grammar or bypassing
construction invariants.

## Evidence consumes real ports and preserves full oracles

The recording fixture delegates capabilities and every selected primitive to
the actual provider. Its typed captured-result sum observes Versioned or
Native results and retains their actual authoritative Record references;
it never supplies a substitute result. Real memory/SQLite and mandatory
PostgreSQL exercise all four ports. Complete root/body/unrelated/security/
audit/non-NULL Fields and independent physical maps remain prescribed oracles.
The [SQL NULL boundary](sql-null-record-boundary.md) supplies absent logical
readback attributes while retaining physical columns and NULL cells. Exact
provider object/raw reference capture consumes this state unchanged. Native
SQLite/PostgreSQL guard preparation leaves logical rows unchanged and appends
only the existing physical deleted_at column/type and NULL cells; complete
selected/unrelated/body oracles still compare full state. Invalid boundary
nulls and supplied legacy ContentBody inputs retain their existing contracts.

Pure typed Probe faults occur only after the actual provider result. Legacy
comparisons use independent freshly seeded sessions, so parity is not command
replay. Closed-resource cases instead close the actual factory-owned SQL
writer after known ACK inside the observing delegate. Same-context hydration
then reaches the real managed borrow failure; independent live readers retain
full physical observation. Record-only variants use this reachable read fault
without adding a new production probe seam.

Regular native owned pools retain normalized column convention. Existing ACK
writer/reader retain their shared default false convention, with Normal,
Reject and explicit/throw/null post-real-commit unknown acknowledgment. Neither
fixture policy nor positive native gate is weakened. Bounded sequential total
laws combine generated multilingual values/revisions/modes with deterministic
matrix coverage; their required success/discard gates are executable
obligations, not current authoring results.

## Later ordinary integration retains its original owners

Authorization/domain/alias checks, EntityCollection/Aggregate residency,
UnitOfWork planning/interpreter and commit, ordinary Display Operations,
transport metadata and contextual response reload remain outside these ports.
The [existing bridge](../spec/display-mutation-bridge.md) and
[native PG12 contract](../spec/postgresql-phase-96-persistence.md) retain their
original requirements. C2B create/delete capture and Entity AND Aggregate
ordinary mutation integration, strict raw JSON/headers/CLI/MCP/SPI, and C3
actual enclosing commit with `afterCommitResponseC` Detail/List positive
lifecycle/pending/failure/no-replay evidence remain required. All four
[P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
remain OPEN. P96-08..P96-11 and original Step/Phase/release/aggregate-handoff
gates are unchanged.

## Paired artifacts

- [EntityStore internal defaults and shapes](../../src/main/scala/org/goldenport/cncf/entity/EntityStore.scala).
- [Canonical typed save adapters](../../src/main/scala/org/goldenport/cncf/entity/StandardEntityReadSavePart.scala).
- [Canonical typed patch adapters](../../src/main/scala/org/goldenport/cncf/entity/StandardEntityUpdatePart.scala).
- [Space delegation](../../src/main/scala/org/goldenport/cncf/entity/EntityStoreSpace.scala).
- [Port fixture](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortFixture.scala).
- [Portable behavior specification](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationPortSpec.scala).
- [Required native behavior specification](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityVersionedMutationPortAcceptanceSpec.scala).
- [Authoring journal](../journal/2026/10/2026-10-15-phase-96-versioned-mutation-port.md).
