# Phase 116 — CAR/SAR Managed Archive and Extraction Cache

Status: IN_PROGRESS — CC-01 through CC-03 accepted and committed; CC-04 implementation underway
Created: 2026-10-21
Owner: CNCF Component Repository / runtime resource lifecycle
Checklist: [Phase 116 checklist](phase-116-checklist.md)

## Goal

Reuse CAR/SAR expansion under `~/.cncf/cache`, separately from original archives,
protect every active consumer and reclaim unused cache within a bounded policy.
Remove repeated expansion as a source of disk exhaustion while preserving lazy
ClassLoader/resource lifetime and existing repository/admission semantics.

## Delivery order

1. **CC-01 Contract binding:** bind the accepted specification to current runtime,
   repository and configuration APIs, including SNAPSHOT current selection,
   coordinate update locks, generation usage locks and capacity defaults.
2. **CC-02 CAR main route:** one expansion shared by two runtimes, real lazy use
   after one shutdown, then safe collection after the final release; also prove
   cross-JVM protection before adding secondary features.
3. **CC-03 SAR composition:** retain correct parent/child leases, reuse nested
   CARs and switch changed local SNAPSHOT generations; retain superseded payloads
   only while used and reclaim them after release, without historical retention.
4. **CC-04 Simplify the normal route, then recovery and operations:** withdraw
   capacity reservations; reuse completed generations without global scans,
   recopying managed immutable bodies or identical metadata writes; then prove
   I/O-failure cleanup, bounded collection and independent archive retention.
5. **CC-05 Integrated acceptance:** repository/runtime regressions, representative
   consumer launch/close/relaunch and operational handoff.

Detailed acceptance and progress live in the checklist; CC-01 through CC-03 are
closed, while CC-04 and CC-05 remain open pending their actual owning
validation and review. The original
planning record is preserved; Phase execution now proceeds through the existing
sm-workflow host at this repository's native `main` checkout.

The 2026-10-22 user direction separates product development from continuation
repair: implement and independently validate the approved simplification now,
while sm-workflow repairs continuation through its ordinary development path.
Neither waits for the other's entire completion. Reconcile the resulting work
with the preserved Session for formal adoption and acceptance when both are
ready. Independent implementation/test progress does not close this Phase or
reset its Goal, candidates, history, findings or repair counts.

## 2026-10-22 adoption and remaining execution order

This revision changes the remaining target, not the accepted history. CC-01–03
stay closed; their prior capacity decisions are superseded where stated below.
Continue the same Goal/Session and CC-04 work, preserving accepted commits,
issued work, repair counts, findings, logs and actual validation receipts. Do not
restart the Phase, erase failed attempts or replay accepted work to adopt this
user-authorized revision. Adoption records distinguish old evidence from the
newly required behavior; they do not claim that the revision already passes.

1. **Ordinary I/O main route (CC04-S1):** remove reservation slots/manifests,
   capacity-only locks/handles, per-chunk accounting scans and persisted policy
   equality. Keep actual generation/staging use protection and atomic publication.
   Run capture/extract/publish with ordinary I/O; preserve the primary failure,
   clean incomplete owned work and leave the previous current generation usable.
2. **Cheap reuse (CC04-S2):** remove hit-time `adopt`/size recount, global recovery
   and discarded staging copies of managed immutable sources. Reuse their proven
   generation identity; keep controlled ingestion for mutable external inputs.
   Remove unused publication hash work and identical reference/retirement writes.
3. **Bounded operations (CC04-S3):** make targets reclamation guidance; bound
   discovery before materialization/sorting, then selected recovery/deletion.
   Expose truncation and scoped totals; changing a target does not reject reuse.
4. **Acceptance and CC-05:** use local fixtures for the revised main route,
   fault injection and work-count assertions. Reuse applicable CAR/SAR, cross-JVM,
   lazy-reader and shutdown evidence; rerun affected scenarios only, then the
   repository-required integrated acceptance. Do not create a benchmark project
   or repeated broad reviews to prove the absence of unnecessary work.

Old reservation-specific examples/optimizations remain recorded as historical
work, but are not requirements to preserve. Replace tests tied only to withdrawn
quota semantics with relevant behavior checks; retain reusable interruption,
partial-cleanup and generation-protection scenarios. No compatibility shim for
unreleased reservation APIs/data is required. Do not delete old live metadata or
locks while an older process may own them; this plan authorizes no cache sweep.

## CC-01 implementation record (historical)

The lifecycle design now binds the current repository, extractor, resolved
configuration and SystemNode resource-owner APIs, with typed lease/result names,
stable source generations, complete reference checks for SNAPSHOT retirement,
independent bounded quota reservations and concrete defaults. Only the five
issued CC-01 documents were edited. No cache code or cache-validation success
is claimed at this contract stage. Native contract-compile passed, the independent
review was accepted, and the Workflow committed CC-01 as
`0f3a433809e2f9ee6b575d114d1fcb7ba8c7b5cc`. The same Session subsequently accepted and committed CC-02 as recorded below.

## CC-02 acceptance and CC-03 continuation

The native CAR check passed 10/10 examples after the retained repairs, and the
affected independent re-review was accepted. Public closure recovery preserved
the unknown old intent and every original receipt, authorized a distinct new
attempt and committed exactly the reviewed 13 paths as
`9da7d491d87d0e01ab630cbdd7cd05ba2181816a`. The same Session now issues
`ImplementStep / CC-03`; no Start, accepted Submit, successful check or review
was replayed. The four separate runtime-recovery files remain outside this
managed-cache candidate. CC-03 authoring does not yet establish SAR/SNAPSHOT
validation or acceptance.

## CC-03 acceptance and CC-04 continuation

The native SAR/SNAPSHOT check passed all ten examples, including independent
source/tree retirement, exceptional cleanup and cross-JVM old-reader continuity.
The affected independent re-review resolved issues 6–8; its complete issue report
was accepted. Ordinary native closure committed the eleven reviewed files as
`69284867c58915f2b6a0d138e5fdfb608247b03d`, preserving the same Session,
original base, accepted prefix and accumulated history. CC-04 is now issued for
recovery and capacity operations. Those obligations remain unvalidated until
its selected native check and independent review succeed.

## Completion boundary

All CC obligations require actual evidence. Multiple runtimes/JVMs must safely
reuse an immutable generation; active entries survive collection, unused entries
are reclaimed, and subsequent access rebuilds from a retained source without
network. CAR and SAR must use the real repository/runtime ownership route.
Write failure (including disk full) and process interruption must leave no usable
partial entry. A completed managed-generation hit performs no reservation,
whole-cache/tree scan, full-body copy/hash or unchanged durable reference write.
Collection targets are soft; exploration is bounded before candidate selection,
and diagnostic truncation remains explicit. Configuration changes do not require
deleting a healthy cache. Necessary usage/current-selection protection remains.
SNAPSHOT replacement must preserve existing readers, route new acquisitions to
the new current generation and reclaim retired source/tree payloads after their
own final use. Prove switch/acquire/collection races, identical-content reuse and
retry after interrupted retirement without blocking unrelated coordinates.

Preserve the existing WorkArea repair, ongoing source changes, Goal/session
history, repair counts and verification results. Reuse applicable evidence within
its tested scope; the earlier WorkArea tests do not establish cache correctness.
No mandatory Docker/external-service acceptance, broad cleanup, artifact publication
or consumer-project rewrite is introduced. Phase 112 documentation work remains
independent. Closed phases are not reopened.

## References

- [Specification](../spec/car-sar-cache-management.md)
- [Design](../design/car-sar-cache-management.md)
- [Planning and developer handoff](../journal/2026/10/2026-10-21-car-sar-managed-cache-plan.md)
- [2026-10-22 simplification handoff](../journal/2026/10/2026-10-22-phase116-cache-simplification-handoff.md)
- [Existing WorkArea incident and repair](../journal/2026/10/2026-10-21-workarea-disk-exhaustion-cleanup.md)
