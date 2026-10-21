# Phase 116 — CAR/SAR Managed Archive and Extraction Cache

Status: IN_PROGRESS — CC-01 contract authored; native validation/review pending
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
4. **CC-04 Recovery and operations:** interrupted staging, bounded automatic and
   explicit collection, capacity reporting and independent archive retention.
5. **CC-05 Integrated acceptance:** repository/runtime regressions, representative
   consumer launch/close/relaunch and operational handoff.

Detailed acceptance and progress live in the checklist; all acceptance items
remain OPEN pending their actual owning validation and review. The original
planning record is preserved; Phase execution now proceeds through the existing
sm-workflow host at this repository's native `main` checkout.

## CC-01 implementation record

The lifecycle design now binds the current repository, extractor, resolved
configuration and SystemNode resource-owner APIs, with typed lease/result names,
stable source generations, complete reference checks for SNAPSHOT retirement,
independent bounded quota reservations and concrete defaults. Only the five
issued CC-01 documents were edited. No cache code or cache-validation success
is claimed at this contract stage. CC-02 begins only after ordinary Step
validation, independent review and the actual Step commit.

## Completion boundary

All CC obligations require actual evidence. Multiple runtimes/JVMs must safely
reuse an immutable generation; active entries survive collection, unused entries
are reclaimed, and subsequent access rebuilds from a retained source without
network. CAR and SAR must use the real repository/runtime ownership route.
Capacity failure and process interruption must leave no usable partial entry.
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
- [Existing WorkArea incident and repair](../journal/2026/10/2026-10-21-workarea-disk-exhaustion-cleanup.md)
