# Phase 103 — Declaration Metadata Interpretation

Date: 2026-10-18
Status (updated 2026-10-19): Phase 103 CLOSED; Steps P103-S1/S2/S3/S4, required producer full validation and sole comprehensive Phase review accepted. At preparation of this closure record, the final local release commit is pending parent execution and committed-tree verification; its actual outcome belongs to the native commit receipt.

Phase 103 began the normalized declaration interpretation Step. The frozen
boundary is an additive public metadata ABI and its pure class/operation
composition. The [specification](../../../spec/validation-test-metadata.md) and
[design](../../../design/validation-test-metadata.md) promote the resolved
semantics from the October 6 planning note.

The authored public API distinguishes missing declarations from explicit empty
declarations through origins. Independent purpose membership, explicit purpose
replacement, scoped feature union, operation-duration normalization and binding
to the existing execution aggregate are represented as typed data. Compatibility
and malformed input fail through structured Consequence policy diagnostics.
Both declarations are admitted before composition, including shadowed defaults.

The singular execution requirement reuses the existing Workflow aggregate and
validator. Its metadata binding canonicalizes capability identities, preserves
the strongest existing reasoning and required review, and rejects differing
opaque risk or incompatible optional execution facts. This does not add a
provider or executor-selection model.

The authored Executable Specification covers declaration presence, inheritance,
purpose overrides, feature permutation/extension, duration units and overrides,
compatibility, null admission, execution composition and deterministic input
preservation. Focused validation passed all 17 declaration scenarios and 16
existing execution-contract regressions across three suites, with zero failures.
The serialized SBT wrapper reported successful terminal completion and released
its lock. An independent protected conformance review accepted the complete
Step accumulator with no blocker, Hygiene or Development Candidate findings.
The parent accepted the Step in local commit
`ac443ed353cc682251e4d17bf4e64a1285955798`. This is not full Phase acceptance.

The parent clarified the finite-duration boundary: values excluded by Scala's
`FiniteDuration` constructor are outside the typed ABI's constructible input
space. The specification exercises the largest positive nanosecond value and
unit normalization, with CNCF domain rejection for constructible zero/negative
values. It does not fabricate duration instances or attribute constructor
exceptions to CNCF admission.

S1 provides no annotation, discovery or invocation support. S2 authors scoped
executable identity/revision provenance, completeness and real scenario discovery.
Typed CNCF invocation, CML mapping and concrete runtime/static consumer fixtures
remain S3/Phase obligations. The [phase](../../../phase/phase-103.md) and
[checklist](../../../phase/phase-103-checklist.md) retain that connected delivery
boundary; this Step does not close Phase 103.

## Step P103-S2 authoring handoff

Frozen P103-S2A authors public Java runtime annotations directly usable from
Scala 3 and a typed `ValidationDeclarationProvider`. Providers are admitted for
subclass, public no-arg construction and matching local artifact provenance
before construction. The annotation interpreter reads each source declaration
once and delegates valid defaults/overrides to the accepted S1 resolver.

The public discovery API takes an explicit local artifact/package scope and
caller-owned ClassLoader. Bounded directory/JAR enumeration obtains actual
ScalaTest Suite registrations, with public no-arg flat FunSuite and WordSpec
fixtures. Stable component/specification/operation references are separate from
artifact/source/operation revisions and typed framework selectors. Complete,
partial and unknown scopes retain bounded diagnostics; invalid metadata rows
remain inspectable and unannotated rows remain valid without inferred purposes.
Duplicate logical references invalidate all colliding rows.

Pure queries implement OR within purpose and feature dimensions, AND between
dimensions, and empty-dimension admission. Exact supplemental references retain
the same metadata and execution aggregate, distinguishing absent, invalid and
ambiguous operations. The authored specifications exercise actual compiled test
artifacts, loader and suite/provider provenance, local JARs, completeness,
determinism, invalid declarations/anchors and a single concrete parameter case
through in-process ScalaTest events. Fixture bodies pass independently of any
invalid metadata they declare.

Parent-selected focused validation passed 36 discovery/selection scenarios and
17 S1 declaration regressions across three suites, with zero failures or aborts.
SBT and its serialized wrapper both completed successfully and released the lock.
Initial independent protected-focused Step review found one descendant-symlink
completeness defect: skipped linked directories/classes could leave a scope
incorrectly COMPLETE. The bounded correction retains nonfollowing traversal,
reports a bounded enumeration gap diagnostic and adds two real directory-link
and class-link regressions. Fresh independent focused review accepted closure
with no remaining blocker, Hygiene or Development Candidate findings. S2 is
accepted in local Step commit `2ec6da1265d14430cf522443869a0b9b2647ea00`.
The S1 commit remains a read-only dependency. No consumer source has been changed.
S3 still adapts selectors to the existing CNCF typed execution path and delivers
CML equivalence, shared static fixtures and actual sm-workflow/cbd-support
handoffs. Full connected Phase checks and VM-01..04 remain open.

## Step P103-S3 authoring handoff

Frozen P103-S3A adds public exact `SbtScenarioSelection` and a trailing defaulted
scenario option on `SbtTarget`. Class grammar reuses the host policy; bounded
exact test names reject unsupported quote/backslash/semicolon literal rendering.
Ordinary source construction remains valid, extractor arity changes, and no
precompiled SNAPSHOT binary compatibility is claimed. Compile/Test reject
scenario intent and existing class-only TestOnly stays default behavior.

The existing actual-context admission resolves the identical workspace binding,
current host definition and actual component capability grant before narrowing
to a class in the host allowlist. The immutable derived definition retains
authority/environment/limits/projections and changes only fixed argument policy
and selection metadata. One final command item requests the literal class/name.
The existing empty request site, interpreter, codec, generic denial and process
lifecycle remain the read-only execution edges.

The public `ValidationOperationBindingV1.bindC` uses the existing exact resolver
for query-selected and supplemental references and constructs typed intent only.
It retains metadata, provenance and existing ExecutionRequirement, including
valid unannotated rows. The decoder's defaulted scenario interpretation needs
one selected class and exactly one test in a conservative successful summary;
selection/count disagreement yields ScenarioMismatch. The exact result target
retains intent, while a suite-only receipt does not independently name the
operation and parsing authenticates no forged log.

`ValidationCmlMappingV1` admits normalized properties and maps both declarations
before invoking S1 resolution once. Exact labels and structured null/unknown
failures preserve compatibility. BigInt duration conversion rejects nonpositive
and overflowing nanos before constructing FiniteDuration. Cozy retains syntax,
parser and generator ownership.

The [packaged example guide](../../../fixtures/validation-test-metadata/README.md)
and [consumer handoff](../../../notes/validation-test-metadata-consumer-handoff.md)
describe the single synthetic portable static-analysis resource. Complete
comparable baseline/candidate rows demonstrate the sole ADMISSION membership
changing 1 -> 0 on the same logical operation with updated revisions.
PARTIAL/UNKNOWN variants retain gaps; empty selections there establish no
absence. An explicit target feature population includes an uncovered feature,
and mappings retain their revisions. Runtime evidence is empty. The example is
neither actual discovery nor an authoritative suite registry or normative new
inventory codec, and computes no KPI or runtime success.

Read-only inspection confirmed the static owner is cbd-support's
[October 6 metadata journal](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md).
Its current Phase 14 is closed native Job integration; this handoff neither
reopens it nor changes cbd-support. Static application completion remains
outside producer acceptance.

Four new GWT/should/property specifications author actual discovery binding,
actual-context deterministic dispatch, CML equivalence and shared-fixture query
outcomes. Existing report/DSL specifications gain causal scenario behavior and
remove the old reflective target-field assertion while retaining duplicate
definition behavior. Parent focused serialized validation passed 79 tests across
eight suites, including the deterministic driver specifications. Separate real
top-level literal selection of `parameter case 1` passed one test in one suite
(total 80 tests across nine suites, zero failures or aborts). SBT and wrapper
exit codes were 0 and the shared lock was released. Independent
protected-focused S3 Step review returned PASS with zero Current Boundary
Blockers, Hygiene or Development Candidate findings and no evidence gaps.
S3 is accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`.

At the S3 handoff, broader producer repository-full validation had not passed. Targeted non-DB
reproduction also failed; the connection to the prior Action context/UnitOfWork
ownership change has no accepted repair. Required PostgreSQL/MySQL flags were
absent from the first full invocation, and the property/JUnit harness failure
remains unresolved. Full Phase validation, review and release acceptance remain open.

At that handoff, actual sm-workflow source fixture/dependency refresh remained
required separate S4 after producer acceptance and explicit source admission. No consumer source
was changed. The original Phase/base/authority/binding/plan epoch and VM-01..04
requirements remain; full connected validation, one full Phase review and
Phase acceptance are still open.

## October 19 — consumer-owned S4 connection and focused closure checks

The parent read actual sm-workflow Phase 6.2 consumer source and retained native
receipts. The [consumer handoff](../../../notes/validation-test-metadata-consumer-handoff.md)
links the selection, native metadata/validation and owning execution adapters.
Actual discovery -> purpose/feature query -> identical producer `bindC` for
selected and supplemental references -> native typed execution/outcome is
available. The K2 CML authoring scenario produced COMPLETE ascii/empty/unicode
inventory, query-selected ascii and explicit supplemental unicode, ordinary
Test 3/3 and both exact TestOnly 1/1. Real independent consumer review accepted
the isolated candidate, followed by Step commit
`b69a60a0fc3dd5e9e7df87fd08b2549f41cef220`. Actual L3 Slice-plan integration
has independent acceptance and isolated Step commit
`157388724869c21649936d4b2de7508571507605` as supplementary concrete evidence.
Neither commit accepts the dirty primary consumer implementation or releases
Phase 6.2.

The former source-admission prerequisite is resolved by that consumer-owned
delivery and dependency refresh. This CNCF task performs and authorizes no
sm-workflow source edit. Neither whole Phase waits for the other's whole
completion. P103-S4 remains IN-PROGRESS: independent CNCF integrated review and
its exact local Step acceptance commit are pending. The accepted Phase 103
binder scope is exact zero-input scenario selection. Separately scoped nonempty
typed-input work may coexist in the current tree without becoming Phase 103
achievement evidence. No synthetic fixture is promoted to runtime proof;
normalized CML semantics and the cbd-support static owner remain unchanged.

The earlier failed full validation, missing native PostgreSQL/MySQL flags and
failed non-DB reproduction remain historical evidence. Bounded Action-context
and Display observer corrections are authored. Parent-selected
`p103-closure-focused-20261019-003` passed compilation and 161/161 tests across
19 suites, with no failures, aborts, canceled or pending tests; SBT/wrapper
exit 0 and lock released. The original seven and additional three failures,
including the List property/JUnit failure, are resolved on this focused surface.
That result did not supply independent integrated acceptance. At that point,
required producer-full validation was running with both native DB flags true;
its outcome was unknown. Full validation, one full Phase review and release commit remained
open. Original authority/base/binding/epoch/history and VM-01..04 obligations
are retained; the [checklist](../../../phase/phase-103-checklist.md) owns closure.

## October 19 — full validation result and expanded focused proof

The full001 invocation enabled both PostgreSQL/MySQL flags and passed compilation.
It completed with 6,853 tests across 861 suites: 6,851 passed, two failed,
no suite aborts, five canceled, one ignored and 46 pending. SBT and wrapper
exited 1 and the shared lock was released. The remaining C3-C2
historical-caller/current-Action no-op oracle and C3-H5 direct SPI observer bypass
were test observation defects, not native DB failures. Both were corrected
without weakening ownership, no-replay or full-state assertions.

Focused004 then passed compilation and 173/173 tests across 21 suites, including
the previous 19 and both full001 failure suites. This includes private helper
compilation and actual Action/caller history assertions. There were no failures,
aborts, canceled, ignored or pending tests; SBT/wrapper exit 0 and lock released.
The earlier failed invocations and focused003 success remain historical evidence.

Required full002 is now running with explicit Test fork and both native DB
flags; its outcome remains UNKNOWN until terminal completion. S4 remains
IN_PROGRESS pending independent CNCF integrated Step review and its exact local
Step commit. Required full validation, one full Phase review and final release
remain open. The consumer ownership, isolated K2/L3 acceptance limits and exact
zero-input Phase 103 scope recorded above are unchanged.

Fresh independent protected CNCF integrated S4 Step review passed over the complete
18 owned and 14 affected paths, with no Current Boundary Blockers, Development
Candidates or evidence gaps. The actual Step review acceptance is persisted in
the typed phase_review ledger10. Four accepted nonblocking pre-existing maintenance
Hygiene dispositions will be projected to the canonical Hygiene journal before
release. The exact local S4 Step acceptance commit remains pending, so S4 stays
IN_PROGRESS and its combined review/commit checkbox remains unchecked. Required
full002 is still running with outcome UNKNOWN; required full validation, the sole
full Phase review and final release remain open.

## October 19 — all Steps accepted and required full validation passed

P103-S4 is CLOSED and accepted in exact local Step commit
`c456f09c9ddd67f3746500381d8f7b30be538d57`, following its independent integrated
review. All four declared Steps are accepted locally. Required full002 then
passed compilation and 6,853/6,853 tests across 861 completed suites, with zero
failures or suite aborts, five canceled, one ignored and 46 pending. Both native
PostgreSQL and MySQL suites ran with explicit Test fork and both flags true;
SBT/wrapper exit 0 and the shared lock was released. Earlier failures and
pending-at-time entries above remain historical evidence.

The four accepted nonblocking maintenance dispositions are materialized in the
[Hygiene journal](2026-10-18-phase-103-hygiene-follow-up.md); there are no Development
findings. The sole comprehensive Phase review and final release remain pending,
so Phase 103 and its VM-01..04 closure ledger remain open. The exact zero-input
scope, actual isolated K2/L3 consumer acceptance limits and consumer ownership
remain unchanged.

## October 19 — Phase 103 closure decision

The sole comprehensive Phase review passed with no Current Boundary Blockers
or evidence gaps. All four declared Steps are accepted in local commits
`ac443ed353cc682251e4d17bf4e64a1285955798`,
`2ec6da1265d14430cf522443869a0b9b2647ea00`,
`1df07da9be8716edf0e0462bcba221ca8d020c60` and
`c456f09c9ddd67f3746500381d8f7b30be538d57`. Focused004 passed 173/173 tests
across 21 suites. Required full002 passed compilation and 6,853/6,853 tests
across 861 suites, with no failures or aborts; five canceled, one ignored and
46 pending retain those statuses. Both native PostgreSQL/MySQL paths ran;
SBT/wrapper exit 0 and lock released. Earlier failed and pending-at-time
entries above remain historical evidence.

Phase 103 and VM-01..04 are CLOSED on this connected producer acceptance,
including actual isolated K2/L3 consumer evidence and the shared static handoff.
Exact zero-input binding scope and consumer ownership remain unchanged; this
decision accepts neither nonempty typed-input work nor whole sm-workflow Phase
6.2. Cozy syntax/generator and cbd-support static application ownership remain
separate. The [Hygiene journal](2026-10-18-phase-103-hygiene-follow-up.md) retains
OPEN nonblocking follow-ups `HYG-P103-S4-001`, `HYG-P103-S4-002`,
`HYG-P103-S4-003`, `HYG-P103-S4-004` and `HYG-P103-FULL-001`; no Development
findings or unpersisted accepted findings remain. At preparation of this closure
record, the distinct final local release commit is pending parent execution and
committed-tree verification; its actual outcome belongs to the native commit
receipt. No successor, push, publication or deployment is started here.
