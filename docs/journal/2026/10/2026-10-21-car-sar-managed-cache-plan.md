# CAR/SAR Managed Cache — Decision and Development Handoff

Date: 2026-10-21
Status: specification and planning recorded; cache implementation not started here.

## Context and confirmed baseline

Following the [disk exhaustion incident](2026-10-21-workarea-disk-exhaustion-cleanup.md),
the user proposed treating archive expansions as reusable caches, protecting
active use and collecting unused entries. The user selected `~/.cncf/cache` and
asked to separate archive bodies from extracted results in the specification
and development plan.

At inspection, ComponentRepository.defaultStandardRepositoryDir points at
`~/.cncf/cache`; standard archive fetching uses its `car/` and `sar/` children.
CarExtractor/SarExtractor still expand into WorkArea directories. The current
repair adds retained ownership so lazy runtime ClassLoaders can keep extracted
files alive. ComponentResourceLifecycleStore has in-memory ownership state,
which does not itself provide cross-JVM filesystem exclusion.

The previous repair and its 116/116 focused verification remain evidence for
WorkArea ownership/cleanup. They do not demonstrate the new shared cache. The
private incident evidence remains under
`~/backup/journal/2026/10/cncf-workarea-disk-recovery-20261021/`; no evidence is
moved into a cache or replaced by a new empty ledger.

## Adopted decisions

- Preserve existing `cache/car` and `cache/sar` repository placement. Add separate
  `extracted/car`, `extracted/sar`, dated staging and stable entry-lock storage.
  The earlier conceptual `repository/` example does not require relocating the
  current downloaded archive cache.
- Source bytes plus extraction format/profile identify immutable generations;
  coordinates alone cannot identify changed local SNAPSHOT contents. Compute
  content identity at ingestion, not repeated full-tree scans or workflow hash gates.
- Acquire typed leases before exposing paths. Reuse the current runtime owner;
  close ClassLoaders before releasing backing leases. Completed entries survive
  ordinary close and can be shared by multiple runtimes and JVMs.
- Coordinate use/build/collection per entry. JVM-local reference counts complement
  native cross-process protection. Lock files stay outside removed payloads and
  are not unlinked by ordinary collection. Advisory locks cannot prevent manual deletion.
- Prefer collection of unused expansions and retain source archives for offline
  reconstruction. Make source eviction a separate policy. Account for staging,
  skip active entries and report insufficient reclaimable space explicitly.
- Ordinary execution WorkArea and journal/evidence retain their separate roles.
  Old flat temporary trees remain a separate cleanup decision because existing
  processes have no new cache leases.

Behavior is now owned by the [specification](../../../spec/car-sar-cache-management.md)
and [design](../../../design/car-sar-cache-management.md), not this journal.

## Development handoff

[Phase 116](../../../phase/phase-116.md) and its
[checklist](../../../phase/phase-116-checklist.md) are PLANNED. Establish the CAR
main route before SAR and collection-policy extensions:

```text
resolved CAR → stable source generation → one published extraction
           → two runtime leases → one runtime closes → other keeps working
           → final release → managed collection → offline re-extraction
```

Prove same-JVM and two-JVM use through actual runtime/resource ownership. Then
add SAR backing relationships, changed source generations, process-death recovery
and bounded capacity/inspection operations. Local test fixtures suffice; do not
make Docker or external services a prerequisite. Reuse current admission and
resource checks; a cache hit must not bypass them or introduce duplicate gates.

CC-01 must bind internal lease/result types, mutable-source generation storage,
operation/configuration names and concrete limits to current code before
implementation. These are bounded design details, not permission to reopen the
approved lifetime/storage decisions or expand into distributed cache management.

Preserve all existing source edits, WorkArea validation, active Goals/sessions,
history and repair counts. Do not restart ongoing development or automatically
adopt/delete old extracted directories. This update edits documents only and
does not run tests, publish artifacts, commit or activate the Phase.

Phase 112 remains the separate owner of Documentation Subcomponent/ScalaDoc
projection. Large documentation was the incident's main payload, but dropping
it silently is not this plan: reuse the complete declared generation first.
Selective extraction would require an explicit profile aligned with that contract.

## SNAPSHOT refinement — 2026-10-21

The user approved replacing the current SNAPSHOT selection and retaining old
generations only while used. This refines the earlier original-retention default:
current/reusable originals remain available for offline reconstruction; unused
superseded SNAPSHOT originals and expansions are not historical archives.

The specification C2.1/C3, lifecycle design and Phase 116 CC-01/03/04 now require:

- Prepare an immutable replacement, then atomically switch the coordinate's
  current generation. Existing users keep their old leases; subsequent users
  acquire the new generation. Identical content/profile reuses the same object.
- A short coordinate update lock protects both switching and selecting/pinning
  the current generation. Separate generation usage locks protect source/tree
  payloads for their actual use lifetime. No download/extraction or long wait
  holds the update lock. Retrying acquisition reselects current state.
- Attempt retirement collection at replacement and after final release. Active
  payloads remain protected; unused payloads have no history/idle retention.
  Source and expansion use are independent; current references elsewhere prevent
  mistaken deletion of shared content. Caller-owned originals remain untouched.
- Retry interrupted/failed retirement through normal bounded collection. Persist
  or derive pending retirement from committed selection state. Stable lock files
  stay outside removed payloads; the collector coordinates reference checks and
  takes generation exclusion without creating a reverse lock-order wait.

Acceptance now includes concurrent refresh/acquire/collect, continued lazy access
to an old generation, collection after release, same-content reuse and interrupted
retirement. These are planned acceptance obligations, not new test results.
Phase 116 remains PLANNED; existing implementation, evidence and active work are
preserved. This refinement changes documentation only.

## CC-01 contract authoring

After the pre-existing work was preserved in purpose-separated local commits,
the ordinary host issued CC-01. The lifecycle design now binds actual APIs:
`ComponentCreate.configuration`, `StandardRepository.cacheRoot`, the existing
CAR/SAR discovery and extraction paths, `SystemNode.ownRuntimeResourceC` and
`Subsystem.shutdownOwned`. The latter keeps ClassLoader shutdown before backing
lease release. The owner is the existing `subsystem` package; an absent `system`
directory is not a reason to introduce a parallel owner.

The selected design fixes typed source/tree leases, independently immutable
captured source bodies, current-selection revisions, conservative coordinate
reference publication and collection rechecks. A collector obtains known
coordinate locks before generation exclusion, rereads the full reference set
and retries if it discovers another coordinate. Prepared candidates stay pinned
across the switch; a stale refresh cannot overwrite a newer pointer revision.
Retired source and tree payloads have independent use and collection decisions.

Capacity defaults distinguish 16 GiB of source payloads, 8 GiB of expansions and
a 2 GiB staging subset. Persistent per-pool slot reservations avoid a global
cache lock, include temporary copies and survive interruption conservatively.
Retained coordinate bodies and independent captured originals both count.
Inspection distinguishes exact bytes from rounded reservations; insufficient
safe capacity yields a structured failure. Existing admission remains authoritative.

This entry records engineering decisions, not a native Journal Entry or an
accepted Step. Compile, independent review, real cache behavior and integrated
acceptance remain pending. Earlier WorkArea results, Goals, histories and repair
counts retain their original meaning; no previous evidence was moved or reset.
