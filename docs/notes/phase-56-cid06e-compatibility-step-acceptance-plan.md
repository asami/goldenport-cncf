# Phase 56 CID-06E Compatibility Step Acceptance Plan

## Status

- Phase: 56
- Step: CID-06 Compatibility Adapters
- Slice: CID-06E End-to-End Compatibility Acceptance
- Slice status: ACCEPTED / REVIEWED
- Phase full validation: pending until the Phase 56 release gate

## Goal

Close CID-06 with one behavior-focused executable acceptance scenario that
crosses the real assembly, repository, ClassLoader, factory/Core, runtime
routing, projection, observability, and shutdown boundaries. The scenario
must prove that an unchanged exact deferred release remains usable while all
internal identity is canonical and every compatibility decision remains
bounded by CID-06A through CID-06D.

## Frozen Acceptance Story

The acceptance fixture is an exact `textus-corpus:0.1.0` schema-2 CAR with the
frozen generated `ComponentId("Corpus")` call, valid ABI evidence, and no
runtime manifest. A real assembly descriptor requests the component through
the legacy local spelling `Corpus`; the configured component repository is
the only canonical candidate authority.

One executable example must prove, in order:

1. assembly admission resolves `Corpus` to exactly
   `org.simplemodeling.textus.Corpus` from repository static metadata;
2. repository extraction preserves raw legacy evidence, projects the
   effective descriptor, validates ABI evidence, and installs the exact
   deferred-release scope only while scanning and creating the factory/Core;
3. the admitted Component, default instance, descriptor binding, artifact
   metadata, resolver slot, and cache-visible identity are canonical;
4. canonical public routing executes a fixture operation, and the unique
   legacy selector executes the same operation only through the typed adapter;
5. Help and Meta projections advertise canonical authority and expose the
   legacy selector only as an accepted compatibility alias;
6. the owning AssemblyReport/Admin surface contains the bounded alias warning
   and exactly one deduplicated `exact-deferred-release` warning with release,
   artifact, owner, and removal reason;
7. an unknown qualified selector, a malformed selector, and a neighboring
   legacy spelling do not fall through to compatibility;
8. shutdown closes the retained component-local ClassLoader exactly once and
   leaves no deferred identity scope behind.

## Fixture Boundary

- Extend the package-local Corpus generated fixture with one small scalar
  service/operation; retain its intentional bare `ComponentId("Corpus")`.
- Extract only the CAR construction mechanics needed by both CID-06D and
  CID-06E into one test-support helper. Do not duplicate archive admission or
  runtime logic in the acceptance spec.
- Keep all work under
  `target/cncf-test/work/phase56-component-identity-compatibility-acceptance`
  and remove it in `afterAll`/`finally`.
- Do not read or mutate the external Textus CAR repositories. The fixture is
  hermetic and represents the exact frozen release shape.

## Executable Specification

Add
`src/test/scala/org/goldenport/cncf/subsystem/Phase56ComponentIdentityCompatibilityAcceptanceSpec.scala`
with one `E1` example using `AnyWordSpec`, `Matchers`, `GivenWhenThen`, one
semantic `which` group, and canonical afterWord metadata for phase 56,
slice CID-06E, and rules CID06-R1 through CID06-R10.

The example must use `GenericSubsystemFactory` and public runtime routing; a
direct adapter call, direct repository helper call, or direct warning
insertion cannot satisfy the acceptance boundary.

## Implemented Files

- this plan;
- `docs/notes/phase-56-cid06-component-identity-compatibility-adapter-plan.md`;
- `docs/phase/phase-56.md`;
- `docs/phase/phase-56-checklist.md`;
- `src/test/scala/org/goldenport/cncf/subsystem/Phase56ComponentIdentityCompatibilityAcceptanceSpec.scala`;
- `src/test/scala/org/simplemodeling/textus/corpus/LegacyCorpusGeneratedCoreFixture.scala`;
- the bounded shared helper
  `src/test/scala/org/goldenport/cncf/component/testutil/LegacyDeferredReleaseCarFixture.scala`;
- `src/test/scala/org/goldenport/cncf/component/Phase56DeferredReleaseCompatibilitySpec.scala`
  only for the corresponding helper call-site migration.

The acceptance exposed RF-CID06E-001 and the parent froze the bounded repair
across `Component.scala`, `ComponentRepository.scala`, and
`GenericSubsystemFactory.scala`. No other production file is admitted.

## Implementation Record

CID-06E is implemented by one `E1` leaf in
`Phase56ComponentIdentityCompatibilityAcceptanceSpec`. The example creates an
exact schema-2 `textus-corpus:0.1.0` packed CAR, loads a real assembly binding
that spells the component `Corpus`, and supplies that CAR as the only
configured repository candidate authority. It then composes the existing
CID-06A through CID-06D production boundaries without calling the adapter or
warning observer directly.

The package-local generated fixture now exposes the scalar
`compatibility.echo` operation while preserving the intentional frozen
`ComponentId("Corpus")` call. `LegacyDeferredReleaseCarFixture` owns the shared
schema-2 descriptor, ABI, fixture-JAR, and packing mechanics; CID-06D has been
migrated to the same helper. The E1 assertions cover raw/effective extraction,
canonical assembly/Core/default-instance/artifact/cache/resolver state,
canonical and unique-legacy public requests, Help/Meta presentation, owning
AssemblyReport/Admin warning evidence, strict malformed/unknown/neighboring
negative selectors, one-time ClassLoader shutdown, and post-boundary scope
cleanup. All fixture and extraction work is rooted below the deterministic
CID-06E target work directory and is removed through `finally`/`afterAll`.

RF-CID06E-001 adds package-internal, nonserialized exact deferred-release
provenance to `Component`. Repository admission attaches only the
`CarExtracted.deferredRelease` entry after canonical archive admission.
GenericSubsystemFactory consumes only that carried entry to scope the later
factory/Core rematerialization and propagates it to the recreated component.
It never rederives compatibility from artifact metadata, paths, or names.

Validation chain: invocation `80957` aborted on an invalid fixture
`WorkAreaId`; `81384` exposed missing fixture capabilities; `81844` exposed
the provider-owner requirement; and `82252` plus `82742` reached the complete
acceptance boundary and exposed the deferred scope loss at
GenericSubsystemFactory rematerialization. Those invocations do not validate
the review fix. Smallest corrective invocation `84975-20260808T051049Z`
passed the acceptance example, and authoritative invocation
`85353-20260808T051139Z` completed the exact 11-suite accumulator with 179
tests passed, no warnings, `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. Full review found no production or executable-specification
defect and admitted only RF-CID06E-002 lifecycle-ledger contradictions. The
documentation repair is applied, and independent focused re-review returned
PASS with no findings and `FULL_REVIEW_REQUIRED=no`. CID-06E is therefore
accepted/reviewed; the CID-06 Step feature-test/commit and Phase full
validation remain pending.

The subsequent CID-06 Step full review admitted `CID06-FR-001` through
`CID06-FR-003`: failure-safe acceptance shutdown cleanup, complete semantic
grouping/metadata in `SubsystemAssemblyAdmissionSpec`, and synchronized
CID-06C/D lifecycle truth. Focused validation
`98250-20260808T055120Z` passed both affected suites and all 10 tests without
warnings, with both exits zero and the lock released. Independent focused
re-review verified the repaired hashes and returned PASS with no findings;
the Step commit and Phase full validation remain pending.

## Focused Validation and Review

The initial focused validation must run the new acceptance spec together with
the CID-06A/B/C/D executable contracts and the repository/assembly/runtime
specifications whose behavior the acceptance story composes. It must be
warning-free. Independent review must inspect the complete acceptance path,
fixture fidelity, lifecycle cleanup, and Step documentation. Findings require
review-fix, corrective focused validation, and independent focused re-review.

After acceptance review converges, the CID-06 Step gate must run one focused
feature accumulator including the acceptance spec before the Step commit.
This is not the Phase 56 full test; full validation remains deferred to the
phase release gate.

## Non-goals

- No new compatibility alias or normalization algorithm.
- No production relaxation or registry expansion.
- No external CAR rewrite, republish, or repository mutation.
- No CID-07 lint implementation.
- No Phase full validation or Phase release commit.

## Exit Criteria

CID-06E becomes `ACCEPTED / REVIEWED` only when the single real-boundary
acceptance example passes warning-free, independent review has no actionable
finding, all temporary resources and ClassLoaders are closed, CID-06A through
CID-06D remain green, and lifecycle documents record the exact evidence. Only
then may the parent prepare the CID-06 Step feature-test and commit manifest.
