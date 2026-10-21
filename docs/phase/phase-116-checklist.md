# Phase 116 Checklist

Status: IN_PROGRESS — CC-01 accepted and committed; CC-02 implementation underway
Acceptance entries close only after their actual checks and independent
acceptance. CC-01 passed contract-compile and independent review and was committed
by the native Workflow as `0f3a433809e2f9ee6b575d114d1fcb7ba8c7b5cc`.

## CC-01 Contract binding

- [x] Bind C1–C6 to existing ComponentRepository, extractors, resource ownership and configuration APIs without a second resolver.
- [x] Freeze typed source/extraction lease and collection results, internal mutable-source generation storage and publication metadata.
- [x] Freeze SNAPSHOT current-selection/retirement state and coordinate update versus generation usage locks, with nonblocking acquisition retries and consistent current-reference checks.
- [x] Freeze operation/configuration names, concrete byte/idle/collection defaults and peak staging-space policy; keep original retention separate.
- [x] Record supported local-filesystem lock/atomic-publication behavior and explicit unsupported outcomes.

## CC-02 CAR main route

- [ ] Cache miss publishes one complete generation; a later hit performs no repeated extraction.
- [ ] Two runtimes in one JVM share it; closing one preserves the other's lazy class/resource access; closing the last permits collection.
- [ ] Two test-owned JVMs share the same entry and cannot collect it while either holds a lease.
- [ ] Concurrent builders converge; acquisition racing with eviction never returns a missing/partial path; unrelated keys remain independent.
- [ ] Exceptional startup and ordinary shutdown release leases after dependent ClassLoaders close; repeated close is harmless.
- [ ] An evicted expansion is rebuilt offline from its retained source through the actual ComponentRepository route.

## CC-03 SAR composition and source generations

- [ ] SAR packed-CAR and directory-CAR fixtures preserve every backing lease; nested consumers reuse expansions.
- [ ] Changed bytes at the same local SNAPSHOT coordinate yield a new generation while old active readers remain usable.
- [ ] Switch current SNAPSHOT atomically: existing leases keep old bytes, subsequent acquisitions select the new generation, and failed preparation/switch preserves the prior selection.
- [ ] Superseded source/tree payloads are independently reclaimed at replacement or after their last use, without idle/history retention; a generation still current elsewhere survives.
- [ ] Identical-content/profile refresh reuses the existing generation; competing refresh, acquisition and collection cannot expose an unleased path or blindly commit a stale selection.
- [ ] Source reads/ingestion cannot race managed replacement or source eviction; externally owned originals and development directories remain intact.
- [ ] Existing identity, admission, resource authorization and mode-policy acceptance remains authoritative on hits and misses.

## CC-04 Recovery and operations

- [ ] Interruption during download/extraction/publication exposes no partial hit; abandoned staging can be recovered after exclusive acquisition.
- [ ] Terminating a test-owned holder process releases native protection without relying on cleanup callbacks; another live holder stays protected.
- [ ] Inspection/dry-run/collection distinguish original, expanded, staged, retained-in-use and failed bytes/entries with reasons.
- [ ] Configured bounded collection uses last-use/capacity policy and skips in-use entries; no AI polling or global serialization is required.
- [ ] Capacity pressure including staging produces a structured failure when safe recovery is insufficient, with no forced eviction or temp fallback.
- [ ] Partial deletion failure is visible and retryable; a damaged entry is never reused; stable lock files survive ordinary collection.
- [ ] Interrupted SNAPSHOT retirement/final-release collection is recovered from pending or derivable state without losing current selection or requiring an AI approval loop.
- [ ] Original-retention default and explicit source-eviction policy are independently demonstrated; old flat temporary trees and evidence are untouched.

## CC-05 Integrated acceptance and handoff

- [ ] Focused executable specifications exercise the connected CAR/SAR scenarios with local fixtures, including concurrency/interruption properties.
- [ ] Existing WorkArea, extraction, repository, admission and runtime-shutdown regressions pass for affected behavior; inherited evidence remains recorded.
- [ ] A representative actual CNCF/Textus consumer launches, closes and relaunches with reuse, then closes before managed collection; no consumer-specific cache path is introduced.
- [ ] Repository-required final validation and integrated lifecycle/semantic review are recorded; focused results are reused without claiming untested guarantees.
- [ ] Operational handoff documents inspection, collection, capacity configuration, advisory-lock limits and treatment of old unleased temporary trees.
- [ ] Phase closure records actual results and remaining explicitly relocated work without rewriting existing Goal/session/history/repair counts.
