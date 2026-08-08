# Phase 56 CID-06C Runtime Selector and Compatibility Observability Plan

## Status

- Phase: 56
- Step: CID-06 Compatibility Adapters
- Slice: CID-06C Runtime Selector and Compatibility Observability
- Slice status: ACCEPTED / REVIEWED
- Phase full validation: pending until the Phase 56 release gate

## Goal

Every runtime Component selector uses the same bounded identity adapter as
assembly and descriptor admission. Exact qualified selectors remain exact.
Legacy display, local-ID, and artifact spellings may resolve only against the
already admitted runtime Component set and only when they identify one
canonical `ComponentId`. Every successful non-canonical adaptation produces
one typed notice that is deduplicated in the runtime assembly report and is
therefore visible through the existing Admin warnings/report surfaces.

## Rules

- `CID06C-R1`: Qualified Component selectors are exact-only. Unknown or
  malformed dotted selectors never fall through to display or artifact alias
  matching.
- `CID06C-R2`: Runtime alias candidates come only from admitted Component
  identity, `displayName`, and admitted artifact metadata. No namespace is
  inferred from selector text.
- `CID06C-R3`: A local, display, or artifact alias is accepted only when all
  matches belong to one canonical `ComponentId`. Distinct canonical matches
  are ambiguous and select nothing.
- `CID06C-R4`: Exact canonical selection emits no compatibility notice. Every
  accepted non-canonical selection emits the central adapter's typed notice
  with surface, alias kind, source alias, and canonical identity.
- `CID06C-R5`: Assembly-binding and descriptor-field notices are retained by
  a typed admission result and consumed at the runtime factory boundary. They
  are not reconstructed from rewritten canonical state.
- `CID06C-R6`: Operation routing, Help, Meta, and Web Component selection use
  the central adapter decision. They do not keep independent normalization or
  first-match algorithms.
- `CID06C-R7`: Help `selector.accepted` and `usage` advertise a presentation
  alias only when the same adapter would accept it uniquely. Ambiguous aliases
  advertise canonical selectors only.
- `CID06C-R8`: Accepted notices become deterministic
  `component-identity-compatibility` assembly warnings. The existing Admin
  warnings/report operations and Web pages expose those records without a
  second warning store.
- `CID06C-R9`: Warning insertion is idempotent. Repeated resolution of the
  same surface/alias/canonical identity does not inflate warning counts.
- `CID06C-R10`: Compatibility aliases and notices are runtime diagnostics,
  never serialized descriptor, repository, cache, or identity authority.

## Frozen Design

### Central alias candidates

`ComponentIdentityCompatibilityAdapter` remains `private[cncf]`. CID-06C adds
runtime surfaces and a package-internal alias-candidate value containing one
canonical `ComponentId` plus its admitted presentation aliases. Resolution
orders exact qualified identity, exact local ID, then admitted presentation
aliases. The result remains `Canonical`, `Adapted`, or deterministic
`Rejected`; no consumer implements its own fallback.

### Typed notice retention

`SubsystemAssemblyAdmission` gains a package-internal detailed admission
result containing the canonical descriptor and typed notices. Existing
`resolveC` remains source-compatible and projects only the descriptor.
`GenericSubsystemFactory` uses the detailed boundary and records notices in
the active `GlobalRuntimeContext.assemblyReport` before runtime consumers see
only canonical state.

### Runtime consumers

- `OperationResolver` exposes a package-internal detailed resolution carrying
  adapter notices while its public result remains unchanged. `Subsystem`
  consumes the detailed form and records notices.
- `MetaProjectionSupport` resolves Component prefixes through the central
  adapter. Help requests name the `HelpProjection` surface; other projections
  use `MetaProjection`.
- `HelpProjection` derives alias acceptance and usage from the central
  adapter's uniqueness decision.
- `Http4sHttpServer` selects Component Web roots through the same adapter and
  records accepted Web aliases on the subsystem assembly report.
- A package-internal observer converts typed notices into existing
  `AssemblyWarning` records. `AdminComponent` and the system Admin Web pages
  continue projecting `AssemblyReport`; they do not gain a parallel schema.

## Executable Acceptance Matrix

One new `Phase56ComponentIdentityObservabilitySpec` owns the Slice narrative,
with existing focused suites retained for consumer regressions.

| Example | Behavior |
| --- | --- |
| E1 | Bare assembly binding is canonicalized and its typed notice reaches the runtime assembly report. |
| E2 | Legacy descriptor-field projection retains field notices until the factory observes them. |
| E3 | A unique runtime display/artifact selector resolves the canonical operation and adds one warning. |
| E4 | Repeated alias resolution is idempotent; exact canonical resolution adds no warning. |
| E5 | Distinct canonical Components sharing an alias are ambiguous and add no accepted-alias warning. |
| E6 | Help accepted selectors and usage include only canonical plus a uniquely accepted alias. |
| E7 | Meta selection accepts one unique alias but rejects unknown qualified and malformed selectors. |
| E8 | Web-root selection accepts one unique alias, rejects ambiguity, and records the Web surface notice. |
| E9 | Admin assembly warnings/report expose kind, surface, alias kind, source alias, and canonical identity. |

Every executable leaf uses `AnyWordSpec`, `Matchers`, `GivenWhenThen`,
semantic `which` grouping, and the required afterWord metadata.

## Focused Validation

The implementation-focused invocation will cover the new acceptance spec and
the existing assembly, resolver, projection, Web, and Admin regressions. A
successful focused `testOnly` invocation is also compile evidence for this SBT
project; no duplicate standalone `Test/compile` is planned.

## Non-goals

- No CID-06D deferred-release registry or legacy generated-Core scope.
- No CAR rewrite, republish, repository-index change, or cache-key change.
- No schema-3 relaxation or unbound namespace inference.
- No new public adapter, resolver, warning, or Admin API.
- No Phase full suite before the Phase 56 release gate.
- No CID-07 lint or source-CAR migration.

## Implementation Record

CID-06C implementation extends the private compatibility adapter with admitted
runtime candidates, selector surfaces, and typed notices; retains admission
notices through `SubsystemAssemblyAdmission` to `GenericSubsystemFactory`; and
uses the same decision in operation routing, Help/Meta projection, and HTTP Web
selection. `ComponentIdentityCompatibilityObserver` projects accepted notices
into the existing `AssemblyReport` warning surface, including Admin report
projection, with synchronized atomic deduplication.

Executable evidence E1-E9 is added in the CID-06C observability suite and the
existing assembly, resolver, projection, Web, factory, and Admin boundaries.
The CID-06C review-fix passes apply RF-CID06C-001 through RF-CID06C-012.
The earlier post-fix invocation `43771-20260808T030432Z` completed all 11
focused suites with 119 tests succeeded, zero failed/canceled/ignored/pending/
aborted, `sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. The required full
re-review found no runtime defect and admitted four bounded compliance groups:
OperationResolver metadata plus Web semantic grouping, internal adapter
flatcase fields, AssemblyReport local naming, and canonical edited-file
headers. Those repairs are applied. Initial validation
`52300-20260808T033036Z` stopped during test compilation on exactly three stale
test references to renamed internal fields. After correcting those references,
authoritative invocation `52869-20260808T033203Z` completed all 11 suites with
119 tests succeeded, zero failed/canceled/ignored/pending/aborted, no warnings,
`sbt_exit=0`, `wrapper_exit=0`, and `lock=released`. Independent focused
re-review returned PASS with no findings on status SHA
`33c605da019b1eddca1fae3697e720b65c5894adab5042318641ccab040ff5a6`
and tracked diff SHA
`50ed3a22239bd8ed5dab2819957c421d72562abc1a0f683b4b409517a53252c6`;
`FULL_REVIEW_REQUIRED=no`. CID-06C is accepted/reviewed with focused
re-review PASS; no Step commit is claimed. Historical pre-review invocation
`26232` is superseded. CID-06D and CID-06E are also accepted/reviewed. The
CID-06 Step commit, Phase full validation, CID-07, and
compatibility-notice removal ownership remain pending.

## RF-CID06C review-fix record

| Finding | Boundary repair status | Evidence state |
| --- | --- | --- |
| RF-CID06C-001 | Qualified/local/presentation precedence and cross-kind ambiguity repaired. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-002 | Packed and expanded CAR static candidate enumeration repaired. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-003 | Assembly warning insertion and snapshots made synchronized and atomic. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-004 | Factory notice ownership bound to supplied runtime scope. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-005 | Real factory/resolver/Help/Meta/Web/Admin boundary evidence added. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-006 | Lifecycle/status documentation aligned. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-007 | Changed-spec metadata and semantic grouping repaired. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-008 | Duplicate dead resolver algorithm removed. | Closed by `52869`; focused re-review PASS. |
| RF-CID06C-009 | OperationResolver E24/E25 metadata and Runtime Web semantic `which` grouping corrected. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-010 | Private compatibility-model fields and all internal call sites use flatcase. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-011 | `AssemblyReport` method-local selection/origin names use flatcase. | Closed by `52869` and focused re-review PASS. |
| RF-CID06C-012 | Edited Scala headers use canonical Aug.  8, 2026 formatting with history preserved. | Closed by `52869` and focused re-review PASS. |
