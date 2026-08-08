# Phase 56 CID-06 Component Identity Compatibility Adapter Plan

## Status

- Phase: 56
- Step: CID-06 Compatibility Adapters
- Current Slice: CID-06E End-to-End Compatibility Acceptance
- Slice status: ACCEPTED / REVIEWED
- Phase full validation: pending until Phase 56 release

## Goal

Legacy Component identity spellings remain bounded decode and routing aliases.
Every successful adaptation produces one already-admitted canonical
`ComponentId` before assembly, repository, cache, routing, or diagnostics use
the value. Compatibility never guesses a namespace and never becomes a second
write authority.

## Rules

- `CID06-R1`: A qualified Component selector is exact-only. Failure to find
  that exact identity never falls through to compatibility alias matching.
- `CID06-R2`: A compatibility alias is evaluated only against an explicit
  canonical candidate set supplied by the owning boundary. Assembly admission
  derives that set only from typed bindings, canonical descriptor overrides,
  and configured repository static descriptors; it never constructs a
  repository or infers a namespace from alias text.
- `CID06-R3`: A bare alias may adapt only when exactly one candidate has that
  exact local ID. The result carries a stable compatibility notice.
- `CID06-R4`: Zero candidates produce an unsupported result. Candidates from
  more than one canonical identity produce a deterministic ambiguity failure
  naming the alias, surface, alias kind, and sorted qualified candidates.
- `CID06-R5`: Canonical schema-3 descriptors remain strict and never enter a
  legacy decode path.
- `CID06-R6`: New descriptor/project serialization emits only canonical
  namespace, ID, and release inputs; compatibility state is never serialized
  as identity authority.
- `CID06-R7`: Accepted aliases produce warning and bootstrap observability with
  an explicit owner and removal trigger.
- `CID06-R8`: A legacy released CAR is admitted only through an exact
  canonical request and an exact release entry in the runtime compatibility
  registry. The archive is not rewritten or republished.
- `CID06-R9`: Legacy descriptor identity fields project only against one
  expected canonical identity supplied by the owning boundary; disagreement
  rejects and unbound decode remains untyped.
- `CID06-R10`: Legacy projection preserves source descriptor schema and
  metadata while canonicalizing only in-memory identity fields and retaining
  typed notices for CID-06C observability.

## Single Adapter Contract

`ComponentIdentityCompatibilityAdapter` owns typed compatibility decisions.
CID-06A completed the `AssemblyBinding` surface. The current CID-06A + CID-06B
boundary owns `AssemblyBinding` plus `DescriptorField`, and returns:

- `Canonical`: exact qualified candidate, with no warning;
- `Adapted`: unique bare candidate plus a compatibility notice;
- `Rejected(Ambiguous)`: more than one canonical candidate;
- `Rejected(Unsupported)`: no candidate or an unknown qualified identity.

`AssemblyBinding` canonicalizes only against typed candidates supplied by the
admitting boundary. `DescriptorField` projects legacy descriptor identity
fields only against the already-known expected canonical identity. Later
slices must not add independent normalization or fallback algorithms.

## Slice Ledger

| Slice | Scope | Status | Acceptance |
| --- | --- | --- | --- |
| CID-06A | Typed adapter contract; exact qualified and unique/ambiguous/unsupported bare assembly admission; activate CID-01 E4. | ACCEPTED / REVIEWED | `Phase56ComponentIdentityCompatibilitySpec` plus active E4 in `Phase56ComponentIdentityContractSpec`. |
| CID-06B | Legacy descriptor `name`/`component`/`componentName` agreement, expected-identity-bound canonical in-memory projection, strict schema-3 preservation. | ACCEPTED / REVIEWED | `Phase56ComponentDescriptorCompatibilitySpec` direct and assembly/static-repository matrix. |
| CID-06C | Runtime selector, Help/Meta, Web alias, warning/Admin projection convergence. | ACCEPTED / REVIEWED | [Runtime selector and observability plan](phase-56-cid06c-runtime-selector-observability-plan.md); RF-CID06C-001 through RF-CID06C-012 are closed, `52869` passed 119 tests, and independent focused re-review returned PASS. |
| CID-06D | Exact deferred-release registry and scoped legacy generated-Core admission for unchanged CARs. | ACCEPTED / REVIEWED | [Deferred-release compatibility plan](phase-56-cid06d-deferred-release-compatibility-plan.md); post-fix invocation `74557-20260808T044055Z` passed 5 suites/109 tests warning-free and independent focused re-review returned PASS. |
| CID-06E | End-to-end Step convergence. | ACCEPTED / REVIEWED | [Compatibility Step acceptance plan](phase-56-cid06e-compatibility-step-acceptance-plan.md); RF-CID06E-001 carries exact deferred provenance through GenericSubsystemFactory rematerialization, `85353` passed 11 suites/179 tests warning-free, RF-CID06E-002 reconciles the lifecycle ledger, and independent focused re-review returned PASS. |

## Deferred Release Registry

CID-06D packages one machine-readable runtime registry for exactly:

| Canonical Component ID | Release | Legacy artifact | Owner/removal trigger |
| --- | --- | --- | --- |
| `org.simplemodeling.textus.Corpus` | `0.1.0` | `textus-corpus` | `textus-corpus`; exact equality only, greater release migrates, lower/uncomparable/malformed is an inventory error. |
| `org.simplemodeling.textus.Experiment` | `0.1.0` | `textus-experiment` | `textus-experiment`; exact equality only, greater release migrates, lower/uncomparable/malformed is an inventory error. |
| `org.simplemodeling.textus.GeoResolver` | `0.2.1` | `textus-georesolver` | `textus-georesolver`; exact equality only, greater release migrates, lower/uncomparable/malformed is an inventory error. |
| `org.simplemodeling.textus.Sanpomap` | `0.2.1` | `textus-sanpomap` | `textus-sanpomap`; exact equality only, greater release migrates, lower/uncomparable/malformed is an inventory error. |

The runtime registry is the executable admission authority. A parity
specification must compare it with
`phase-56-cid01-car-migration-ledger.yaml`; the ledger remains the inventory
and migration-owner record. SNAPSHOT, advanced, lower, incomparable, malformed,
or absent entries are not compatibility admissions.

These releases also contain generated calls such as `ComponentId("Corpus")`.
CID-06D therefore uses a scoped expected-identity boundary around their
ClassLoader/factory execution. Outside that exact registered scope,
`ComponentId.apply` and `ComponentId.parseC` remain strict.

## Non-goals

- No namespace inference from artifact or display text.
- No schema-3 relaxation.
- No CAR rewrite or release republish.
- No repository index v2 schema change.
- No CID-07 lint classification or CAR source migration.
- No Phase full test until the Phase 56 release gate.

## CID-06 REVIEW #1 and Review Fix Record

`REVIEW #1` admitted the following findings:

- `R1-F1`: discover static ComponentDevDirRepository candidate identities
  without expanding `ComponentRepository.Specification`.
- `R1-F2`: restrict the adapter and its decision ADT to `private[cncf]`.
- `R1-F3`: expand direct descriptor projection evidence for every accepted
  spelling and metadata preservation.
- `R1-F4`: group behavior specifications semantically with `which`.
- `R1-F5`: correct the single-adapter contract and review-state documentation.
- `R1-F6`: correct CID-05 and CID-06 lifecycle/checklist reporting.

`REVIEW_FIX #1` is applied. Component-dev static discovery evaluates each
typed descriptor identity through the central `AssemblyBinding` adapter,
preserves ambiguity candidates, and does not infer namespaces. Direct and
assembly specifications now state the required boundary behavior. Focused
evidence invocation `10489-20260808T013738Z` completed four suites with 36
tests succeeded; zero failed, canceled, ignored, pending, or aborted; main and
test compile succeeded; `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`.
The discarded-Assertion warning was then repaired by `VF-CID06B-003`. Final
warning-free focused evidence invocation `13371-20260808T014534Z` used the
exact logical argv:

```text
["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentDescriptorCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.SubsystemAssemblyAdmissionSpec"]
```

Four suites completed with zero aborted; 36 tests succeeded; zero failed,
canceled, ignored, or pending; compile was warning-free; `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Independent focused re-review returned
PASS with no findings and `FULL_REVIEW_REQUIRED=no` on status SHA
`cd38b345a039fdf0d850b5b9b3db4167e4989eaf098219f2a540bdac1b5ddb16` and
tracked diff SHA
`5af442141553a258093364af59afe476c9f7c313c5872438096db0b9561a344f`; all ten
target hashes were exact. `R1-F1` through `R1-F6` and `VF-CID06B-001` through
`VF-CID06B-003` are closed. This CID-06B record is historical; the current
lifecycle has CID-06C through CID-06E accepted/reviewed, while the CID-06 Step
feature-test/commit and Phase full validation remain pending.

## CID-06B Implementation Record

CID-06B is specified separately in the
[component descriptor compatibility plan](phase-56-cid06b-component-descriptor-compatibility-plan.md).
The central adapter evaluates legacy descriptor fields against one exact
expected `ComponentId`, preserves source descriptor metadata, and revalidates
schema 3 without rewriting it. `SubsystemAssemblyAdmission` applies the result
to explicit overrides and configured repository static descriptors before
closure. Ordinary legacy decode remains untyped. Status:
`ACCEPTED / REVIEWED`.

## CID-06C Review-Fix Record

The CID-06C repair passes apply RF-CID06C-001 through RF-CID06C-012. The
current slice status is `ACCEPTED / REVIEWED`.
The earlier post-fix invocation `43771-20260808T030432Z` completed 11 suites
with 119 tests succeeded, zero failed/canceled/ignored/pending/aborted,
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The required full
re-review found no runtime defect and admitted the four bounded compliance
groups below. Their repairs are applied. Initial validation
`52300-20260808T033036Z` stopped at test compilation on three stale internal
field references. After correcting those exact call sites, authoritative
invocation `52869-20260808T033203Z` completed 11 suites with 119 tests
succeeded, zero failed/canceled/ignored/pending/aborted, no warnings,
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Independent focused
re-review returned PASS with no findings on status SHA
`33c605da019b1eddca1fae3697e720b65c5894adab5042318641ccab040ff5a6`
and tracked diff SHA
`50ed3a22239bd8ed5dab2819957c421d72562abc1a0f683b4b409517a53252c6`;
`FULL_REVIEW_REQUIRED=no`. CID-06C is accepted/reviewed with focused re-review
PASS; CID-06E is also accepted/reviewed, while the CID-06 Step commit remains
pending.
Historical pre-review invocation `26232` is superseded and does not close any
CID-06C criterion.

## CID-06D Review-Fix Record

The exact deferred-release registry, raw/effective archive projection,
runtime/ABI evidence exception, thread-local generated-Core scope, repository
wrapper, canonical Core postcondition, and one deduplicated AssemblyReport
warning are implemented. `Phase56DeferredReleaseCompatibilitySpec` and the
package-local Corpus generated-Core fixture own E1-E10. Status:
`ACCEPTED / REVIEWED`. RF-CID06D-001 through
RF-CID06D-004 restrict deferral to schema 2, reject existing non-regular
runtime-manifest paths, structure the executable specification by semantic
`which` groups and Given/When/Then boundaries, and reconcile lifecycle truth.
Historical invocation `66284` failed during compilation; `66671` compiled and
reported three failures; `67421` completed five suites with 109 tests passed,
no warnings, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. That historical evidence
predates and is superseded by the review fixes. Post-fix invocation
`74557-20260808T044055Z` completed the same five suites with 109 tests passed,
no warnings, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Independent
focused re-review verified the exact repaired hashes, closed RF-CID06D-001
through RF-CID06D-004 without new findings, and returned PASS with
`FULL_REVIEW_REQUIRED=no`. No Step commit or Phase full-test claim is made;
CID-06E is accepted/reviewed; the CID-06 Step commit remains pending.

## CID-06E Implementation Record

`Phase56ComponentIdentityCompatibilityAcceptanceSpec` now owns one hermetic
E1 that composes the previously accepted CID-06A through CID-06D behavior at
the real GenericSubsystemFactory, configured packed-CAR repository,
component-local ClassLoader/factory/Core, public Request routing, Help/Meta,
owning AssemblyReport/Admin, negative-selector, and shutdown boundaries. The
frozen Corpus fixture adds one scalar operation but retains
`ComponentId("Corpus")`. `LegacyDeferredReleaseCarFixture` centralizes the
schema-2 descriptor, ABI, fixture-JAR, and packing mechanics used by CID-06D
and CID-06E.

RF-CID06E-001 is applied. A package-internal, nonserialized Component field
carries only the exact `CarExtracted.deferredRelease` entry attached after
canonical repository admission. GenericSubsystemFactory wraps only the later
factory/Core rematerialization in its expected-identity scope and propagates
the same provenance to the recreated Component. Artifact metadata remains
unchanged and no registry lookup, name/path inference, strict-case relaxation,
or registry expansion is introduced.

Validation chain: `80957` aborted on invalid WorkAreaId; `81384` exposed
missing capabilities; `81844` exposed the provider owner; and `82252` plus
`82742` exposed the exact scope loss at GenericSubsystemFactory
rematerialization. Smallest corrective invocation `84975-20260808T051049Z`
passed E1. Authoritative invocation `85353-20260808T051139Z` completed 11
suites with 179 tests passed, no warnings, `sbt_exit=0`, `wrapper_exit=0`, and
`lock=released`. Full review found no production or specification defect and
admitted only RF-CID06E-002 stale lifecycle wording. The documentation repair
is applied. Independent focused re-review returned PASS with no findings and
`FULL_REVIEW_REQUIRED=no`. Status: `ACCEPTED / REVIEWED`.

The CID-06 Step full review then admitted `CID06-FR-001` through
`CID06-FR-003`: failure-safe acceptance shutdown cleanup, complete semantic
grouping/metadata in `SubsystemAssemblyAdmissionSpec`, and synchronized
CID-06C/D lifecycle truth. Invocation `98250-20260808T055120Z` passed the two
affected suites and all 10 tests without warnings, with `sbt_exit=0`,
`wrapper_exit=0`, and `lock=released`. Independent focused re-review returned
PASS with no findings and `FULL_REVIEW_REQUIRED=no`. The CID-06 Step
feature-test/commit and Phase full validation remain pending.

| Finding | Applied boundary repair | Current evidence state |
| --- | --- | --- |
| RF-CID06C-001 | Qualified/local/presentation precedence and cross-kind ambiguity are centralized. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-002 | Packed and expanded CAR repository specifications enumerate canonical static candidates without activation. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-003 | `AssemblyReport` warning insertion and snapshots are synchronized and atomically deduplicated. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-004 | Factory notices are observed only through the owning supplied runtime scope, never ambient foreign state. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-005 | Factory, resolver, Help/Meta/Web, and Admin real-boundary evidence covers E1-E9. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-006 | CID-06 lifecycle truth and status are aligned. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-007 | Changed CID-06C specs carry correct metadata and semantic grouping. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-008 | The duplicate dead resolver algorithm is removed. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-009 | OperationResolver E24/E25 metadata and Runtime Web semantic `which` grouping are corrected. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-010 | Private compatibility-model fields use internal flatcase names and all call sites agree. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-011 | `AssemblyReport` method-local names use flatcase without changing selection semantics. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-012 | Edited Scala headers use the canonical Aug.  8, 2026 form with preserved history. | Closed by `52869` and focused re-review PASS. |
