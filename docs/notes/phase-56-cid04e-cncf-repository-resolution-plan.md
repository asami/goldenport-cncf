# Phase 56 CID-04E - CNCF Repository Index and Canonical CAR Resolution Plan

status=accepted/reviewed
date=2026-08-07
phase=[Phase 56](../phase/phase-56.md)
step=CID-04
slice=CID-04E

## Frozen boundary

CID-04E makes CNCF the v2 repository-index reader and the direct canonical CAR
repository/cache consumer. The shared Java `ComponentId` and
`ComponentReleaseCoordinate` ABI is the sole authority for qualified identity,
artifact, dependency key, repository path, cache path, and catalog path. The
Cozy v2 index producer and sbt-cozy CID-04D resolver are accepted precedents.

The target paths are the CNCF repository-index codec/schema/fixtures/spec,
`CanonicalCarRepositoryResolver` and its executable specification, the Phase
51 CV-01 catalog fixture, and the linked v2 specification/design/phase records.
The focused validation is exactly:

```text
sbt --batch testOnly org.goldenport.cncf.repository.ComponentRepositoryIndexSpec org.goldenport.cncf.repository.CanonicalCarRepositoryResolverSpec org.goldenport.cncf.repository.Phase51Cv01AcceptanceSpec
git diff --check
```

## Acceptance evidence

Final focused validation invocation `70737-20260807T094615Z` passed 3 suites
and 18 tests with 0 failures and 4 expected ownership cancellations;
`sbt_exit=0`, `wrapper_exit=0`, `lock=released`. Focused re-review was PASS
with no findings. Later dependency-ordered Step validation accepted the CNCF
invocation `19701-20260807T115947Z` (3 suites/18 tests plus 4 expected
ownership cancellations) and Step scripted invocation
`20534-20260807T120122Z` (1/1 passed with PublisherProbe plus CncfProbe
online/offline); every invocation had `sbt_exit=0`, `wrapper_exit=0`,
`lock=released`. Repository-wide full suites remain Phase-release-only/pending.

## Required behavior

- Read and render only `cncf.component-repository-index.v2`; CAR identity is
  `(kind, namespace, id)` and SAR identity remains `(kind, "", artifactId)`.
- Verify CAR artifact and `car/<group-path>/<artifact>.yaml` values from the
  shared release coordinate. No local tokenization or namespace guessing is
  admitted.
- Resolve qualified CAR IDs cache-first from local paths, `file:` roots, and
  immutable HTTP(S) releases through exact shared repository/cache paths.
- Preserve shared identity diagnostics; reject malformed repository inputs,
  legacy v1 indexes, duplicate JSON object fields, unsafe paths, and projection
  disagreement.
- Keep equal CAR filenames namespace-isolated in index identity, request path,
  cache path, dependency key, and archive bytes.

## Non-goals and follow-up

This Slice does not alter legacy runtime `ComponentRepository`, Component.Core,
assembly/routing selectors, descriptor compatibility aliases, external Textus
call sites, build dependencies, or the Step scripted fixture. Those runtime and
compatibility migrations remain CID-05/CID-06; the Step scripted acceptance is
run after CID-04E review in sbt-cozy as
`scripted cozy/namespace-qualified-car-repository`.
