# Phase 96: versioned mutation UoW capture proposal

Document role: non-normative specification proposal (notes).
Status: source investigation, not an Implementation Manifest.
Scope: PHASE-96 / P96-07 / proposed internal Slice P96-07C2B3.
No source implementation or acceptance is authorized by this note.

## Original completion boundary

The [four P96-07 criteria](../phase/phase-96-checklist.md#p96-07-mutation-bridge-to-entityaggregate-runtime)
and [native PostgreSQL obligations](../spec/postgresql-phase-96-persistence.md)
remain OPEN. This seam does not replace ordinary registered Entity AND Aggregate
authorization/domain/alias/Display routes, enclosing commit, selected contextual
afterCommitResponseC Detail/List reload, positive create/delete, committed
pending/failure/no replay, PG12, P96-08..11 or original Step/full Phase/release/
ledger/reciprocal aggregate gates. No new Phase or review epoch is proposed.

## Source-backed gap

The [four producer ports](../spec/entity-versioned-mutation-port.md) already
return the constructor-controlled [versioned outcome](../spec/entity-versioned-mutation-outcome.md),
capturing actual raw persistence before optional domain projection.
There are no corresponding UoW nodes. Current save/patch interpreter cases
consume legacy projected values, then perform resident/View work. A later
failure can prevent callers from receiving the known raw mutation result.
The [create/delete capture](../spec/entity-mutation-capture-outcome.md) boundary
now retains canonical results before optional coherence; versioned integration
needs equivalent separation without changing old cases.

## Recommended shape, not yet frozen

| Candidate internal node | Existing Space producer |
| --- | --- |
| EntityStoreSaveSnapshotOutcome | _save_snapshot_outcome_c |
| EntityStoreSaveDetachedOutcome | _save_detached_outcome_c |
| EntityStoreUpdateRecordSnapshotOutcome | _update_record_snapshot_outcome_c |
| EntityStoreUpdateRecordDetachedOutcome | _update_record_detached_outcome_c |

Prefer a companion-constructed non-case EntityVersionedMutationCaptureOutcome[A]
retaining the exact existing EntityVersionedMutationOutcome[A] object and a
separate Consequence[Unit] coherence result. Final names, factory admission
and node signatures still require parent freeze. This is not a generic writer,
wire DTO, provenance certificate, supplied committed flag or public receipt.

Appending residency to the existing projection field is an alternative, but
conflates domain projection with UoW coherence and changes the established
carrier interpretation. It is not the preferred proposal.

## Ordering and compatibility to freeze

- Shape-only admission precedes lookup/effects; reuse existing admission and
  effective-policy ownership, without prematurely validating raw policy.
- Preserve typed save authorizationRecord/current loaders and both transition
  branches, and patch _authorize_and_validate_patch semantics. Freeze exact
  target correspondence and pending-event failure behavior before editing.
- Call exactly one matching canonical Space port. Preserve its exact outcome
  before optional View invalidation/eviction/resident work. Do not reconstruct
  Applied/NoOp, guess revisions, reload as receipt, fall back or retry writes.
- Keep domain projection Failure in its original outcome. Do not put a failed
  projection into residency. Freeze coherence ordering and failure reconciliation
  separately, retaining real Conclusions and bounded NonFatal/null containment;
  fatal/control signals propagate.
- New nodes are Local; construction/planning/recording stays pure. No enclosing
  commit or response-hook execution belongs to this seam.
- Preserve all old node signatures/defaults/returns/exception/authorization/
  transition/pending-event and NotImplemented recovery behavior.
- SQL NULL remains attribute omission with physical NULL preservation.
  Dedicated SQL NULL types and nullable-attribute Option remain proposals only.

## Real fixture and validation prerequisites

Use registered Component/descriptor/Realm/StandardEntityStore and one active UoW.
Observe actual Space/native objects, not manufactured results or direct-port
calls relabeled as UoW proof. Cover all four variants, both representations,
Applied/current NoOp/stale equal and different input/maximum NoOp, authorization/
transition denial, projection/coherence faults, capability/shapes, legacy parity,
Local planning/recording, native rollback/unknown ACK and no replay.

Existing port fixture supplies Probe while its descriptor registers a different
persistent object; that proof alone does not establish matching interpreter
resident admission. Existing capture fixture has matching registration,
explicit EntitySpace context and real post-ACK decoder/Realm fault seams, but
its observers/oracles currently cover create/delete. Choose the adaptation
explicitly; do not silently inherit either mismatch.

Prescribe full ordered logical Fields and complete physical schema/types/rows/
NULL/body/unrelated/security/audit/trace state independently before action.
Assert expected native primitive independently before any branch-sensitive
reused oracle. No observed plan/order/value may supply its own expectation.

Require real PostgreSQL17.11 with fsync/synchronous_commit, retaining SQLite/
memory/MySQL regressions; meaningful shrinking total laws with at least100
successes and zero discards; adjacent GWT and should vocabulary. Freeze exact
owned/preserved paths, whole-file compliance, behavioral matrix, representative
plus accumulator selectors, protected worker and fresh independent observation.
No implementation should begin before those decisions and manifest are complete.

Keep main/unrelated work unchanged. No dependency-root mutation, push, publish,
successor Phase or incidental Interpreter cleanup.
