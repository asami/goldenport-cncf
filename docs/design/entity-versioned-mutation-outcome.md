# Entity versioned mutation outcome boundary

Document role: settled PHASE-96 / P96-07 / P96-07C2A internal design decision.
The [paired specification](../spec/entity-versioned-mutation-outcome.md) owns
behavior. This document records stable ownership and compatibility intent,
without asserting validation or acceptance.

## Raw persistence precedes optional projection

The canonical versioned provider returns Applied, NoOp or Stale. A successful
raw branch can precede hydration, admission or domain decode failure. An
internal immutable outcome therefore retains the exact admitted provider Record
and binding-derived revision before attempting projection. Applied is taken
only from the actual branch. A revision comparison, guessed successor or later
read cannot establish that branch.

The companion exclusively constructs the non-case carrier, owns metadata
admission and contains optional NonFatal projection defects. The carrier has
no writer or serialization responsibility and renders only its fixed name.
Data declaration ownership is instance-independent. No mutation retry,
provider fallback, extra transaction or new failure vocabulary owner is added.
Existing typed `operationInvalid`, stale and committed-projection utilities
express the frozen failure taxonomy.

## Legacy expressions keep their original evaluation boundary

Four private projection expressions factor existing hydration/admit/decode
logic. Legacy callers still evaluate those expressions directly and preserve
direct throw/null behavior. New outcome callers evaluate the same expressions
inside the containing factory, then require nonnull value, exact selected
identity and captured revision. Typed carriers use the selected persistent;
Record carriers use the factory's identity admission. Neither path compares
hydrated domain data to raw overflow storage.

The existing provider planning, `_record_mutation_result`, `_managed_record`
and `_mutation_unit` responsibilities do not move. EntityCollection resident
coherence, authorization, UnitOfWork commit, Action response and contextual
View/Display reload remain with their existing owners.

## Fault evidence respects actual source behavior

Absent overflow body is a successful raw fallback in the current content policy.
The existing canonical Root decoder requires identity and therefore accepts
that fallback. MissingBody examples must preserve success and prove the full
post-deletion baseline remains unchanged after projection.

A closed factory-owned SQL resource is a different event. Hydration through
the original context reaches the managed borrow boundary and receives
structured read Failure. Separate live SQLite/native readers establish full
durable state independently. The raw outcome survives this failure without
replaying mutation. Injected caller-owned datasources and synthetic callback
errors do not substitute for this managed-resource case.

Pure domain probes are postprovider-only and apply to typed persistent helpers.
Record-only helpers have no such reachable probe seam; their identity/revision
guards remain source invariants and their integration is exercised through
real closed-reader failure. No production seam is added solely for a negative
test. Unknown commit acknowledgment prevents raw success from reaching outcome
construction even when independent physical data shows the commit happened.

## Persisted metadata and independent observation correspondence

The existing SQL decoder can expose body metadata as `contentStorage`,
`contentRef` and `contentCharset`. The content policy therefore reads only
those explicit spellings alongside their existing snake spellings. Existing
first-nonempty trimmed snake precedence and the final `charset` fallback remain
the lookup rule. The payload itself retains exact whitespace. This bounded
read compatibility allows exact-reference hydration, omitted-content overflow
preservation and charset-aware sizing without changing canonical snake writes,
effects, addresses, schema or raw outcome retention.

Regular native writers and independent readers continue to normalize columns.
The existing acknowledgment fixture instead constructs its writer with the
default false convention; its independent ACK reader uses that same default.
This configuration correspondence keeps both observations in the writer's
actual Record convention without changing the acknowledgment implementation.
Changed audit checks select the unique prior updated-at and updated-by key
from their explicit snake/camel pairs and compare complete retained Field
vectors and exact updated values. Raw Field order and all complete physical
table/column/ordered-row equality predicates remain authoritative. Diagnostic
clues expose only addresses, columns, counts, keys and indices, never nullable
values; unexplained physical differences remain failing evidence.

NoOp retains the exact original structured Conclusion. Independent Applied or
stale wrapper construction has fresh observation timestamps; correspondence
keeps both timestamps present, compares the other observation data under the
expected timestamp and preserves status, interpretation, disposition and
the previous Conclusion's exact reference. This does not change failure
construction or legacy evaluation semantics.

## Provenance and lifecycle limit

The outcome is neither a producer-provenance certificate nor whole-UnitOfWork
committed receipt, public wire DTO or Display reload. Supplied data admission
does not authenticate its origin. The tests obtain positive raw values from
the actual canonical provider; later C2B/C3 integration must establish that
same origin in ordinary Operations and committed response handling.

This decision adds no schema/version/routes/write/transaction/security policy.
Original create/delete, ordinary Operations, strict raw HTTP, commit/contextual
reload, PG12 and the four original P96-07 criteria remain mandatory and OPEN.
The kernel does not confer Step/Phase/release acceptance.

## Paired artifacts

- [Carrier and factory](../../src/main/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcome.scala).
- [Canonical helper integration](../../src/main/scala/org/goldenport/cncf/entity/StandardEntityRevisionPart.scala).
- [ContentBody storage policy](../../src/main/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicy.scala).
- [ContentBody executable specification](../../src/test/scala/org/goldenport/cncf/entity/ContentBodyStoragePolicySpec.scala).
- [Portable executable specification](../../src/test/scala/org/goldenport/cncf/entity/EntityVersionedMutationOutcomeSpec.scala).
- [Required PostgreSQL executable specification](../../src/test/scala/org/goldenport/cncf/entity/PostgresqlEntityVersionedMutationOutcomeAcceptanceSpec.scala).
- [Authoring journal](../journal/2026/10/2026-10-14-phase-96-versioned-mutation-outcome.md).
