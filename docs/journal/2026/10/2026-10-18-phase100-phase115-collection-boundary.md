# Phase 100 / Phase 115 collection boundary — 2026-10-18

Status: user-directed ownership disposition with independent read-only
assessment; Phase 100 comparison and Phase acceptance remain open.

## Authority and completion order

The user selected the shared `ai-directive/core/RULE.md`,
[CNCF collection rule](../../../rules/collection-and-semi-structured-data.md)
and [Phase 115](../../../phase/phase-115.md), then instructed continuation of
the existing source-only Workbench migration through Phase 100 completion.
Finish the handed-over Phase 100 review, validation, commits and closure before
adopting Core Phase 3 Record and canonical collection semantics in Phase 115.
Whole-Workbench completion is not a prerequisite. Preserve Phase 96, the
existing Goal, original comparison history and repair accounting.

Ordinary materialized collections use Vector; explicitly nonempty attributes
use NonEmptyVector. Existing SearchResult/effect wrappers and their metadata
remain. An old List declaration alone does not justify adding restoration.
Phase 115 migrates declarations, generators, callers and specifications together.
The selected Phase 100 Core artifact's historical null-preserving behavior is
retained; this record neither adopts new Record normalization nor rewrites data.

## Retained static Query finding and producer ownership

`CPB-PHASE100-GENERATED-OBJECT-QUERY-CONTAINER` remains a real static finding:
SimpleModeler's entity-object collection Query reader produces Vector and
passes it to Condition.is without reconstructing an old declared List container.
The original focused result is FULL_REVIEW_REQUIRED and did not seal resolution
of the six original comparison findings. No newly compiled failing
counterexample was observed; none is inferred from source inspection.

The selected Phase 100 `src/test/cozy/phase100-store.cml` declares repeated Json,
Record and ProposalSummary by multiplicity. Its generated families materialize
Vector, and Summary uses the value-reader branch. Independent assessment found
no selected caller requiring the failing `Condition[List[generated entity Item]]`
combination. DataStore SearchResult.records and EntityStore SearchResult.data
already use Vector while retaining range, cursor, count and paging contracts.
These outer results are distinct from stored attribute containers.

The user-directed disposition assigns ordinary List/Seq declaration, producer,
caller and specification migration to Phase 115 with SimpleModeler/Cozy ownership.
The previous compatibility-restoration proposal is superseded. This is a changed
ownership/target assessment, not a fix, false-positive classification or sealed
independent resolution. Keep the original finding and invalidation in history.
Phase 115 must verify generated signatures and actual Vector/NonEmptyVector
reconstruction, empty/nonempty admission, order, duplicates and search metadata.
Do not patch generated products or add historical List restoration.

SimpleModeler R5, E7 and compiled-admission restoration descriptions are qualified
together as retained behavior. E6 controls and the explicit nested
`List[Vector[Record]]` fixture remain historical observations. The ordinary
generated EntityStore suite uses Query.plan and does not invoke the normal
generated Query family's createC; only the nested legacy Query builder is
invoked. Five passing cases therefore do not prove all generated Query builders
or canonical Phase 115 adoption.

## NICT downstream validation and separate runtime owner

NICT's direct four-suite consumer check passed 26 tests. Its initial runtime
probe failed at generated-output layout; the later probe selected the canonical
Scala output directory, generated 12 Scala sources and compiled 16 main sources,
then failed in
cozyPrepareRuntime. NICT CML has no STYLE and the required component-style
snapshot or authored descriptor is absent. Preserve both failures as failures.

The direct tests construct an admitted Subsystem or use pure/recording
collaborators and ports. They establish direct consumer compatibility, not
development-manifest or CAR startup readiness. Phase 100's acceptance requires
selected Workbench restart, mapped storage, durable consumers and downstream
checks; it does not prescribe NICT CAR startup. Independent assessment supports
separate NICT application ownership under
`DEV-P100-NICT-DEVELOPMENT-RUNTIME-ADOPTION-001`. Its resume condition is actual
application adoption followed by the failed runtime-preparation/startup check.
NICT source remains outside the five-repository repair scope. No fabricated
descriptor, blind STYLE choice, suppressed provenance or generated-source patch
is part of this disposition.

## Evidence retained and gates still open

Whole-framework validation, the later composed external-format SQL case,
Workbench original-text/human-edit persistence and provider reopen, and NICT
direct consumers have separate observed evidence. Earlier whole-suite results
do not include the later test-only addition. Failed preparation attempts remain
distinct from direct consumer success. Detailed native results and review
reports remain in ignored private workflow storage under the shared retention
policy; this ownership record does not publish their execution appendices.

The independent boundary assessment supports this bounded documentation
disposition and established no new Phase 100 blocker from these two issues.
It is not the original comparison review, a Phase full review, or Phase 115
acceptance. Complete original comparison obligations and the sole recovery-full
chain, owning checklist gates, accepted finding persistence and release commits
before Phase closure. The exact historical pre-producer ABI handoff chronology
remains unproved and must be explicitly assessed in the retained incomplete-history
route. Unknown historical counters remain unknown. No checklist item or Phase
is closed by this journal. Detailed native results remain private local evidence.

## Explicit historical ABI criterion disposition — 2026-10-18

The user answered “両方を承認して進める” to the concrete two-part proposal:
retain generated fixture content with four exact-path blank-at-EOF rules and
finish the remaining CNCF integration once through a new request; explicitly
replace checklist DSM-100-02 item 02.02's historical ordering condition and
complete the remaining focused review and release.

The original condition was to freeze the producer-facing ABI/generated fixture
and record its exact handoff revision before producer implementation. That
historical revision/order remains UNPROVEN. No contemporary evidence was
recovered, and current green tests do not prove the earlier chronology.

For this resumed Phase 100 only, the user's explicit replacement accepts the
current frozen producer ABI, actual generated fixture/consumer conformance,
completed functional validation and independent assessment as the criterion's
acceptance basis. Retain the original checklist text, prior finding and this
dated disposition together. Do not backdate evidence, relabel the old condition
as proved, waive functional validation/review or start Phase 115.

The four-path .gitattributes convention is a persistent changed whitespace
policy, including future/multiple final blank lines on those exact generated
paths and other attribute-aware Git operations. It adds no text conversion or
fixture rewrite. Current default trailing-space and space-before-tab checks
remain. The original partial integration request stays consumed; completed
SimpleModeler/Workbench commits are preserved. A fresh CNCF-only request and
focused closure of the existing review chain are still required. This record
is the actual human disposition, not a Phase closure or new full review.
