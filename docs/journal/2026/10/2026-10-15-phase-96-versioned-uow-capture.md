# 2026-10-15 P96-07C2B3 versioned UnitOfWork capture authoring

This journal records implementation provenance. It supplies no test result,
review disposition, acceptance receipt, commit or Phase transition.

The parent admitted a bounded four-node versioned UnitOfWork capture unit under
the original PHASE-96 / P96-07 authority, Goal binding and plan epoch. Original
base `6cbe877c53c116639feb11fa80546e79c051804c` is retained. The later checkpoint
HEAD is not a replacement base. The existing five partial drafts and all
85 preserve-only dirty paths remained outside any wholesale replacement.

The first frozen manifest prescribed an Optional revision precondition for an
explicit Direct patch case. Source inspection found only Managed and
ObservedRequired in the existing enum, so initial authoring returned
SCOPE_MISMATCH. The parent checked the actual policy/planner/writer capability
and settled Managed with assembled concurrency None, AlwaysWrite and no
expected/observed revision in manifest002. No production policy enum or old
specification/fixture was changed to fit that mistaken premise.

The parent also settled the record-residency fault boundary from actual source.
The old probe's admitStoreRecord serves typed projection; the interpreter's
record residency decoder calls fromStoreRecord instead. A new fixture-owned
forwarding RegisteredProbe therefore preserves old typed admission/admitted
decoding and independently controls the real residency decoder. Its finite
decoder modes, post-ACK arming and separate Realm removal fault retain actual
provider/Space outcomes rather than supplying replacement results.

The existing input generator already reserves FailureCurrent/FailureChanged for
typed variants. That lawful domain was retained. A separate record DecodeFault
dimension and meaningful reduction were added to the new real-UoW law, with
Good-only typed decoder samples. Deterministic record-decoder cases complement
the generated law. Independent expected primitive/order/state is prepared
before action; SQL NULL omission and the old ACK writer's default-false
normalization remain unchanged.

The authored internal carrier preserves canonical mutation, projection and
local coherence separately. Four private Local nodes reuse the matching
canonical Space ports with the original authorization/transition owners.
The real fixture registers a new matching Component/collection/persistent,
copies declared context values and shares one active UnitOfWork. Its cache
View evidence is an epoch observation, not a claim of Display reload.

The paired [specification](../../../spec/unitofwork-versioned-mutation-capture.md)
and [design](../../../design/unitofwork-versioned-mutation-capture.md) link the
[portable suite](../../../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeSpec.scala),
[required PostgreSQL suite](../../../../src/test/scala/org/goldenport/cncf/unitofwork/PostgresqlUnitOfWorkVersionedOutcomeAcceptanceSpec.scala)
and [fixture](../../../../src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkVersionedOutcomeFixture.scala).
They author the UVO-01..07 and UVO-P1 contracts; no execution occurred in this
bounded implementation role. The parent owns full-delta intake, focused14 and
accumulator82 validation and independent whole-ten assurance.

All four original P96-07 criteria remain OPEN: ordinary registered
Entity/Aggregate/Display mutation and transport integration; enclosing commit
termination/result; contextual afterCommitResponseC Detail/List reload/C3;
and positive lifecycle/committed pending/failure/no-replay/native PG-E12.
P96-06 exact commit, P96-07 protected Step review/commit, P96-08..11, the single
current-epoch full Phase review, final validation/release/ledger and reciprocal
aggregate obligations remain unchanged. Interpreter size and untouched legacy
naming remain the prior separate Hygiene observations, not an expanded cleanup
scope for this unit.

The parent representative attempt
`phase96-p96-07c2b3-versioned-uow-representative-001` completed main compilation
but failed Test compilation with six E007 lifecycle parameter type mismatches;
no suites or test bodies ran. SBT and the wrapper each exited 1, and the serial
lock was released. For `P96-C2B3-TX-TYPE-001`, the `context.*` wildcard had
resolved the six unqualified `TransactionContext` parameters in
`NonVersionedStore` and `LifecycleStore` to the context type rather than the
canonical DataStore lifecycle type. The bounded mechanical correction fully
qualifies only those prepare/commit/abort parameter types as
`org.goldenport.cncf.unitofwork.TransactionContext`, preserving their `tx`
labels, bodies, forwarding, counters and all behavior scenarios. This repair
worker ran no validation and supplies no acceptance. Fresh parent focused14
and unchanged accumulator82 validation, followed by fresh independent whole-ten
assurance including this correction, remain pending; all original history,
authority, bases, epoch, counters and Phase gates are retained.

The parent representative attempt
`phase96-p96-07c2b3-versioned-uow-representative-002` completed compilation and
ran all 14 suites and 531 cases: 527 succeeded and four failed, with no aborted,
canceled, ignored or pending cases. SBT and the wrapper each exited 1, and the
serial lock was released. Three UVO-03 backend cases exposed
`P96-C2B3-LEGACY-WRONG-REV-001`: the expected legacy projection upserted revision
before body hydration, whereas the unchanged canonical owner hydrates first
and the WrongRevision probe then removes/appends the revision field. The shared
fixture now prepares that complete ordered projection before either operation
from independent expected rows/body and the declared changed input, hydrating
before revision upsert and passing body=None to the existing strict projection
vocabulary. Complete ordered fields, storage, physical state and counters remain
required.

The fourth failure, UVO-05's simple missing-current hook, exposed
`P96-C2B3-MISSING-SOURCE-001`: non-inline `Consequence.DataStoreNotFound` captures
its common utility source inside `Consequence.scala`, rather than the caller's
`StandardEntityRevisionPart.scala` source. The existing missing-current example
now uses the inherited complete constructed-Conclusion comparison, preserving
source position and every other Observation facet while normalizing only the
independent timestamp. Selected-id failure construction, patch target facets,
new/legacy hook events, zero provider attempts and full rows remain required.
This bounded repair worker ran no validation and supplies no PASS or acceptance
claim. Fresh parent representative14 and unchanged accumulator82 validation,
then fresh independent whole-ten assurance covering both corrections, remain
pending. Earlier history and all original Phase obligations remain unchanged.
