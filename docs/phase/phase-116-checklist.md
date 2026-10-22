# Phase 116 Checklist

Status: IN_PROGRESS — CC-01 through CC-06 accepted and committed; CC-07 ordinary source validation/review passed, native CC-07 issued; checks and acceptance pending
Acceptance entries close only after their actual checks and independent
acceptance. CC-01 passed contract-compile and independent review and was committed
by the native Workflow as `0f3a433809e2f9ee6b575d114d1fcb7ba8c7b5cc`.
CC-02 passed its native 10-example CAR check, affected independent re-review and
public same-Session closure recovery, and was committed as
`9da7d491d87d0e01ab630cbdd7cd05ba2181816a`. Its evidence covers CAR only;
CC-03 subsequently passed its native ten-example SAR/SNAPSHOT check, affected
independent review and ordinary closure, and was committed as
`69284867c58915f2b6a0d138e5fdfb608247b03d`. CC-04 passed its revised native
16-example recovery check and independent re-review and was committed through
public closure recovery as `a8676706c1062a5e09da6c87ae4371747cd8bba8`.
CC-05 was accepted and committed as `365250dc6057933b7fdf8f007864516ff0605f7e`;
CC-06 was accepted and committed as `d7e4ce8255a57343270d5f9de88309bee5d1ddd1`
(D7). The original repository-full receipt651 records 876 suites / 6427 passed /
0 failed / 4 canceled / 1 ignored / 46 pending. Final review found the remaining
source-reader defect; CC-07 and Phase closure remain open. Old dated evidence
and repair counters retain their original scope, including WorkArea116/116.

2026-10-22 adoption: checked CC-01–03 entries retain their historical acceptance.
Their reservation/peak-space decisions are superseded by the revised specification;
the revised CC-04 obligations below were subsequently accepted under Plan 2. Existing results,
Session history and repair counts are not reset or relabeled as new acceptance.

## CC-01 Contract binding

- [x] Bind C1–C6 to existing ComponentRepository, extractors, resource ownership and configuration APIs without a second resolver.
- [x] Freeze typed source/extraction lease and collection results, internal mutable-source generation storage and publication metadata.
- [x] Freeze SNAPSHOT current-selection/retirement state and coordinate update versus generation usage locks, with nonblocking acquisition retries and consistent current-reference checks.
- [x] Freeze operation/configuration names, concrete byte/idle/collection defaults and peak staging-space policy; keep original retention separate.
- [x] Record supported local-filesystem lock/atomic-publication behavior and explicit unsupported outcomes.

## CC-02 CAR main route

- [x] Cache miss publishes one complete generation; a later hit performs no repeated extraction.
- [x] Two runtimes in one JVM share it; closing one preserves the other's lazy class/resource access; closing the last permits collection.
- [x] Two test-owned JVMs share the same entry and cannot collect it while either holds a lease.
- [x] Concurrent builders converge; acquisition racing with eviction never returns a missing/partial path; unrelated keys remain independent.
- [x] Exceptional startup and ordinary shutdown release leases after dependent ClassLoaders close; repeated close is harmless.
- [x] An evicted expansion is rebuilt offline from its retained source through the actual ComponentRepository route.

## CC-03 SAR composition and source generations

- [x] SAR packed-CAR and directory-CAR fixtures preserve every backing lease; nested consumers reuse expansions.
- [x] Changed bytes at the same local SNAPSHOT coordinate yield a new generation while old active readers remain usable.
- [x] Switch current SNAPSHOT atomically: existing leases keep old bytes, subsequent acquisitions select the new generation, and failed preparation/switch preserves the prior selection.
- [x] Superseded source/tree payloads are independently reclaimed at replacement or after their last use, without idle/history retention; a generation still current elsewhere survives.
- [x] Identical-content/profile refresh reuses the existing generation; competing refresh, acquisition and collection cannot expose an unleased path or blindly commit a stale selection.
- [x] Source reads/ingestion cannot race managed replacement or source eviction; externally owned originals and development directories remain intact.
- [x] Existing identity, admission, resource authorization and mode-policy acceptance remains authoritative on hits and misses.

## CC-04 Recovery and operations

- [x] Interruption during download/extraction/publication exposes no partial hit; abandoned staging can be recovered after exclusive acquisition.
- [x] Terminating a test-owned holder process releases native protection without relying on cleanup callbacks; another live holder stays protected.
- [x] Inspection/dry-run/collection distinguish original, expanded, staged, retained-in-use and failed bytes/entries with reasons.
- [x] CC04-S1: ordinary capture/extract/publish has no reservation slots/manifests, capacity-only locks/handles, per-read reservation checks or persisted policy-equality gate.
- [x] CC04-S1: injected write/disk-full/publication errors retain their cause, clean owned incomplete output where possible, keep recoverable leftovers on cleanup failure and preserve existing current/active generations; no temp fallback.
- [x] CC04-S2: a completed managed-generation hit has no reservation adoption, full-tree size recount, whole-cache inventory, body recopy/rehash or unchanged reference/retirement durable write; external mutable inputs still receive controlled ingestion.
- [x] CC04-S2: publication recovery does not hash full content merely to record a presence flag; use actual publication state.
- [x] CC04-S3: configured soft targets guide collection; a target change permits healthy-cache reuse and does not assert a strict disk quota.
- [x] CC04-S3: candidate exploration is bounded before full materialization/sorting; an unrelated large cache does not increase hit-path work, and truncated diagnostics identify their scope.
- [x] CC04-S3: bounded collection skips in-use entries and preserves current-reference checks; no AI polling or global serialization is required.
- [x] Partial deletion failure is visible and retryable; a damaged entry is never reused; stable lock files survive ordinary collection.
- [x] Interrupted SNAPSHOT retirement/final-release collection is recovered from pending or derivable state without losing current selection or requiring an AI approval loop.
- [x] Original-retention default and explicit source-eviction policy are independently demonstrated; old flat temporary trees and evidence are untouched.

## CC-05 Integrated acceptance and handoff

- [x] Focused executable specifications exercise the connected CAR/SAR scenarios with local fixtures, including concurrency/interruption properties.
- [x] Deterministic I/O/work-count checks cover cheap hits and bounded discovery without timing thresholds or source-text tests; applicable prior evidence is reused and superseded quota examples are identified explicitly.
- [x] Existing WorkArea, extraction, repository, admission and runtime-shutdown regressions pass for affected behavior; inherited evidence remains recorded.
- [x] A representative actual CNCF consumer launches, closes and relaunches with reuse, then closes before managed collection; the accepted local repository/Subsystem fixture introduces no consumer-specific cache path and makes no external Textus deployment claim.
- [ ] Repository-required final validation and integrated lifecycle/semantic review are recorded; focused results are reused without claiming untested guarantees.
- [x] Operational handoff documents inspection, collection, capacity configuration, advisory-lock limits and treatment of old unleased temporary trees.
- [ ] Phase closure records actual results and remaining explicitly relocated work without rewriting existing Goal/session/history/repair counts.

## CC-06 accepted full-test repair and CC-07 source readers — 2026-10-23

- [x] Preserve actual accepted CC-06 commit D7 and original full651 evidence with its 876-suite / 6427-passed counts; later source preparation does not inherit that result.
- [x] Record final independent review source654 `p116-integrated-independent-review@1` finding P1 `P116-INTEGRATED-SOURCE-READERS` against C3.
- [x] Record actual Human Plan4 CC-07 adoption and accepted Submit; following Advance409 stopped historical CC-06 reconstruction without issuing CC-07 work.
- [x] Complete and verify ordinary seven-path source preparation: existing shared coordinate/generation pin around selected managed archive metadata reads, including directory aliases, preserving repository admission and original-path provenance.
- [x] Run parent-selected development validation and independent protected-boundary review: focused03 passed two suites / 134 tests with successful compilation and terminal exit0; fresh review02 closed the source-reader/root-identity findings for ordinary assurance. Earlier invalid-SAR-fixture failures and intermediate115-pass evidence remain recorded. This does not close a native Step gate.
- [x] Obtain actual native CC-07 work issuance through the preserved Session (`ordered-implementation:658`, Plan4).
- [ ] Complete native CC-07 checks, independent review and commit through that Session.
- [ ] Complete final native Phase review and closure, retaining all checks, history, findings and repair counters without fabricated Entries or issued-work contexts.
