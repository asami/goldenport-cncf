# Phase 116 — CAR/SAR Managed Archive and Extraction Cache

Status: CLOSED on 2026-10-23 — CC-01 through CC-07 committed, repository-full and final integrated review accepted; native Session completed
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
6. **CC-06 Full-test repair:** isolate test-owned runtime resources and restore
   owned shutdown, local-first discovery and XML-safe malformed-input diagnostics.
7. **CC-07 Source readers:** retain existing managed source protection throughout
   CAR/SAR metadata reads, including cross-JVM collection and root-alias cases.

All seven Steps are accepted and committed. Required repository-full validation,
final integrated review and native Phase completion are accepted; see
[final acceptance](#final-phase-acceptance--2026-10-23) and the checklist.
The dated execution sections below preserve earlier frontiers and failed
attempts; their historical pending statements do not describe the current status.

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

## CC-04 acceptance and CC-05 continuation

The same Session adopted Plan revision 2 through the actual Human channel.
Native recovery validation `ordered-validation:477` passed all sixteen revised
examples; affected independent review resolved issues 9–13. Public closure
recovery retained the first failed commit and corrected the host physical
selection to the fourteen reviewed files. Its separately authorized new attempt
created commit `a8676706c1062a5e09da6c87ae4371747cd8bba8` with reviewed tree
`e66256f1c1a6d96140349d1ec852e772d3945805`. Ordinary Advance issued
`ImplementStep / CC-05`; the accepted prefix and consumed CC-04 repair count 3/10
remain historical facts. CC-05 starts its own ordinary repair window.

CC-05 adds a real repository/Subsystem close/relaunch/reuse/collection/offline
reconstruction example and the operational handoff in the lifecycle design.
The selected seven-suite lifecycle check and repository-full check retain their
native owners. An earlier full run failed and its isolated HTTP/launch failures
and datastore interaction remain unresolved; no passing full result or final
Phase acceptance is claimed by implementation completion.

## 2026-10-23 accepted prefix and CC-07 source preparation

The accepted native prefix is CC-01 `0f3a4338`, CC-02 `9da7d491`, CC-03
`69284867`, CC-04 `a8676706`, CC-05
`365250dc6057933b7fdf8f007864516ff0605f7e`, and CC-06
`d7e4ce8255a57343270d5f9de88309bee5d1ddd1` (D7). Earlier dated entries retain
their original observation scope. The original repository-full receipt651
records 876 suites, 6427 passed, 0 failed, 4 canceled, 1 ignored and 46 pending.
It is actual retained evidence for that run, not evidence for later source edits.
WorkArea116/116 continues to cover only its original ownership/cleanup repair.

The final independent integrated review source654
`p116-integrated-independent-review@1` found P1
`P116-INTEGRATED-SOURCE-READERS`: eager assembly metadata, descriptors, defaults
and canonical-selection probes could open managed coordinate ZIPs without the
shared source protection required by C3. Explicit source eviction could remove
the selected body before ZIP open. The repair uses the existing coordinate or
source-generation pin around one synchronous selected-archive callback, including
paired descriptor/default reads. Repository selection/admission and original-path
provenance remain authoritative; eager API class bytes need no escaping lease.

Actual Human `01a165a5-d1af-7182-afb3-6f2c7e30e567` adopted Plan4's exact
seven-path CC-07 scope and its Submit was accepted. The following Advance stopped
409 while reconstructing historical CC-06 repair. No native CC-07 work was
issued. Under the earlier explicit direction to progress product development
independently of Workflow recovery, these seven paths are ordinary source
preparation only. They add the read boundary and authored local CAR/SAR
coordinate/generation, callback-failure, cross-JVM, publication-contention,
custom/default-root and caller-ownership regressions. No passing result is claimed
for the prepared source. Native CC-07 issuance, checks, independent review,
commit and final Phase review/closure remain pending. Existing Session history,
accepted results, findings and repair counters are preserved; no native Entry or
issued-work context is manufactured.

### Ordinary development validation and review — 2026-10-23

The final prepared source passed `ManagedCacheRecoverySpec` and
`ComponentRepositoryCarSpec`: two suites, 134 passed, zero failed, aborted,
canceled, ignored or pending; compilation and SBT/wrapper exits succeeded.
Invocation `p116-cc07-ordinary-20261023-focused03` terminated with
`serialization=sbt-native`. The parent admitted its original result, log and
summary. The first run's seven invalid SAR-fixture failures and the intermediate
115-passed result remain recorded rather than replaced.

Independent review01 narrowed the original finding to
`CB-P116-CC07-ROOT-PIN-IDENTITY`: lexical directory aliases could miss the
collector's real-root pin. The correction reconciles only guard/membership
identity, preserving the original callback argument and descriptor provenance.
E19 adds sixteen deterministic CAR/SAR coordinate/generation alias histories;
E75D–F cover custom, initialized and default alias roots. Fresh independent
review02 passed and closed both source findings for ordinary source assurance.
Existing cleanup/cross-JVM/caller-ownership judgments remain applicable.
Formal native CC-07 work, acceptance, commit and final Phase gates remain open.

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

### Native CC-07 issuance — 2026-10-23

Qualified installed runtime `d93f67fc-d100-4ba4-a8bc-30e5d4303199` resumed
the same Session through one ordinary Advance, exit0. Actual
`ordered-implementation:658` issued CC-07 under Plan4 with CC-01–06 intact,
original base unchanged and automatic0/ordinary0/limit10. The exact seven
prepared files are now integrated into the owning checkout; no new executable
changes were added after the ordinary134-pass/review02 candidate. Native
checks, independent acceptance, Step commit and final Phase gates remain open.

## Final Phase acceptance — 2026-10-23

The existing Session `06016330fa63b5eac6097ff2b8ff42c2` completed through its
normal public operations. Its source702 completion-policy target received the
actual Human choice `AdoptLocalCompletion`; adoption returned
`accepted=true / local-completion-adopted`. The following ordinary Advance
returned `Terminal / completed`. No accepted Step, check, review or commit was
replayed, and no Session storage was edited.

The completion retains Plan4 `cncf-phase-116-managed-cache`, original base
`2232026fadb0e2b387089da0e1fbc02a3a18266f`, all seven accepted Step commits and
reviewed candidate `3c3d8ed8f67cd8e8d0a91578d77917552fa7f299` /
tree `cb272b953f58cb222103da6e53425e244b7ee817`. CC-07's native checks,
independent review and commit are accepted. Required repository-full receipt697
completed 876 suites with 6,466 tests passed, zero failures or aborted suites,
four canceled, one ignored and 46 pending. The latter cases are not claimed as
executed coverage. Final integrated review
`p116-final-integrated-independent-review@1` was accepted with no blocking
findings and every required review issue resolved.

This accepts C1–C6, including connected CAR sharing/lazy loading, same-JVM and
separate-JVM usage protection, SAR composition, SNAPSHOT generation retirement,
offline re-extraction, ordinary I/O, bounded recovery/collection and protected
metadata reads. Local fixtures establish these boundaries; no external Textus
deployment is claimed. WorkArea's earlier 116/116 remains evidence only for its
original ownership and cleanup scope.

The completion independently reports synchronization `deferred`:
"No current independently granted Phase closure authority; synchronization was
not attempted." No RepositorySync child was started. Separately reviewed ordinary
work advanced `main` to `bbb324086fd43d59c612ca16ddc6906008646fd1` and was
synchronized with GitHub before this completion; that descendant does not replace
the historical reviewed candidate. The final documentation update and its
ordinary Git synchronization are distinct from native Phase acceptance.

Original failed attempts, accepted results, repair counts and histories remain
preserved. Existing nonblocking cache/configuration maintenance remains in the
[canonical journal](../journal/2026/10/2026-10-21-car-sar-managed-cache-plan.md)
and [configuration follow-ups](../journal/2026/10/2026-10-23-hygiene-follow-up.md).
The sm-workflow owner's later synchronization features remain with that owner;
they are not unfinished cache acceptance. No successor Phase, old temporary-tree
sweep or evidence cleanup is started by this closure.
