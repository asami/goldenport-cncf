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
# CC-02 implementation candidate

The corrected installed sm-workflow completed the original CC-01 compile check.
Independent review accepted its contract, and the native Workflow created Step
commit `0f3a433809e2f9ee6b575d114d1fcb7ba8c7b5cc` before issuing CC-02 in the same
Session `06016330fa63b5eac6097ff2b8ff42c2`. No sbt-cozy plugin was added.

The CC-02 candidate connects the existing packed-CAR discovery route to immutable
captured sources and generation-specific shared extraction leases. Existing
CarExtractor structure/admission and SystemNode resource ownership remain the
boundaries; ClassLoaders close before the backing lease. Shared native file locks
use one JVM registry entry per stable generation lock, outside payload deletion.
Publication is atomic and collection requires exclusive generation protection.
Explicit StandardRepository roots override runtime cache configuration.

ManagedCarCacheSpec supplies local CAR fixtures for two runtime owners, two
fixture-owned JVMs, concurrent builders, acquire/collect races, unrelated-key
progress, exceptional startup, idempotent shutdown and offline rebuild through
ComponentDirRepository. Existing repository ownership tests now isolate their
managed cache and distinguish WorkArea cleanup from explicit cache collection.
These are authored checks, not yet successful validation or CC-02 acceptance.
SAR composition, coordinate/SNAPSHOT switching, automatic retirement, crash
recovery and complete root capacity reservations remain CC-03/CC-04 work.

## CC-02 interrupted native progression and development validation

The CC-02 implementation Submit was accepted. Its one paired native Advance
terminated before SBT with `Source history rule: unterminated literal` and no
business response. The runtime owner identified a legal Scala terminal quote
run in the existing repository specification. Four earlier history effects
succeeded; the fifth effect remains unknown. Preserve that prefix and unknown
outcome, the accepted Submit, original Session, Plan/base and repair history.
Do not replay Submit/Advance or treat absence of a response as non-execution.
The runtime owner is qualifying a bounded normal continuation; neither source
corrections nor fixture consent establish live Human recovery authorization.

An ownership overlap was also found: the runtime owner's RuntimeConfig change
is covered by CC-02's broad config candidate path. The earlier implementation
summary's statement that all four runtime-owner files were outside the candidate
was inaccurate. Preserve those changes and settle their separate attribution
before CC-02 staging/commit; no implicit adoption or rollback is permitted.

Independent development validation through the registered command runner selected
`testOnly org.goldenport.cncf.component.cache.ManagedCarCacheSpec org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec`.
Attempt `p116-cc02-development-b41c1ea84d79422688c7e862e363603e@1` terminated
with SBT/wrapper exit 1 before tests. Two local `_complete_` declarations needed
explicit parameter parentheses to avoid Scala's underscore/colon tokenization;
the six diagnostics included their four cascading unresolved references.
Those declarations and calls were corrected for the next focused execution.
This is development evidence, not a native validation receipt, Step acceptance,
repair-counter reset or shared-cache success. The original failed result and
complete logs remain under the original disposable allocations.

After the syntax correction, independent development attempt
`p116-cc02-development-121e608c00aa42d6b4010092c5b080fa@1`, subject
`p116-cc02-development-subject@2`, completed both selected suites: 91 tests
passed, none failed or aborted; SBT/wrapper exit 0 and the actual process
termination marker were retained and admitted against the current selection.
The new suite exercised same-JVM and cold two-JVM sharing, lazy reads after one
owner closes, active-generation collection skips, final-release collection,
offline reconstruction, generated close orders and concurrent publication.
The existing CAR repository suite also passed with its isolated cache fixtures.
This establishes the selected development tests only. Native CC-02 validation,
independent review/Step commit and later-Step behavior remain outstanding.

## Runtime prerequisite review observation

The separately owned ordinary-project SBT binding repair has its own accepted
focused source review and 44/44 successful tests. It is a prerequisite repair,
not part of the managed-cache program implementation. The following nonblocking
observation comes from reviewer `/root/project_build_binding_independent_review`
and is retained here with that attribution; this entry neither adopts a config
refactor into Phase 116 nor changes CC-02 acceptance.

HYG-RUNTIME-CONFIG-SIZE-001 — RuntimeConfig.scala remains pre-existing
ordinary-source size debt (1,146 baseline / 1,168 current lines, >1,000 threshold).
The ordinary-project SBT binding fix adds one cohesive setting without a
discovered correctness defect. Defer coherent configuration-role split
assessment to a separately selected CNCF maintenance task; no refactor is
admitted by CB-RUNTIME-V3-004.

## 2026-10-22: same-Session recovery and CC-02 review repair

The runtime owner's two installed-CLI recovery qualification cases passed 2/2
through the newly authorized registered command runner. The separate four-file
ordinary-project SBT prerequisite repair was committed as
`3ee1d844a8ac8d85f4f34f08391bf02c41170d1e`, preserving the accepted CC-01 commit
and the cache candidate. The actual user then answered the native recovery
question with `AuthorizeNewAttempt`. Its independently retained Human record was
prepared and accepted through the ordinary installed CLI; the single subsequent
Advance inspected the new candidate and completed `car-shared-main-route` with
10/10 successful tests. The original unknown source-history effect, successful
prefix, original base, Plan, Session and prior validation history remain retained.

Independent reviewer `/root/p116_cc02_independent_review` found no CAR behavioral
blocker within CC-02, and returned three concrete repository-rule corrections.
The native Workflow accepted that repair verdict and issued `issue-2` through
`issue-4` in the same Session, with ordinary/automatic repair counts 1/1 of 10.

The repair binds all ten executable examples to their real C1–C6 contract clauses
with visible E1–E10, `afterWord` metadata and specification-path/contract/example
Given descriptions; it preserves GWT execution boundaries and generated close
order coverage. The package-visible fixture helpers now use `withFixture` and
`fileIdentity`. `CacheCollectionResult` supplies a default empty argument,
canonical `empty` and explicit zero-argument `apply()`. These are authored
corrections awaiting native revalidation and affected independent re-review;
they do not establish Step acceptance or Phase completion.

The first repair's native test-only invocation terminated with exit 1 during
Test compilation: the new `withFixture` helper name collided with inherited
ScalaTest `withFixture`, so the lambda was interpreted as `NoArgTest` rather
than the intended `Path => A`. The other missing-default diagnostics cascaded
from that mismatch. The Workflow retained that failure as `issue-5` and issued
the next bounded repair at ordinary/automatic counts 2/2 of 10. The helper and
all call sites now use `withCacheFixture`; the original three rule corrections
remain. Fresh native validation and affected independent review are still owed.
