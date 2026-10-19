# Validation/Test Metadata V1 consumer handoff

Date: 2026-10-19
Status: P103-S3 producer accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`; concrete
sm-workflow consumer evidence is available. P103-S4 independent integrated CNCF review
passed and S4 is accepted in local Step commit `c456f09c9ddd67f3746500381d8f7b30be538d57`.
All four Steps are accepted locally; Phase 103 is CLOSED after required full
validation and its sole comprehensive review passed. At preparation of this
closure record, the final local release commit is pending parent execution and
committed-tree verification; its actual outcome belongs to the native commit
receipt.

The public [metadata specification](../spec/validation-test-metadata.md) and
[design](../design/validation-test-metadata.md) own declaration, discovery and
binding semantics. The [portable example guide](../fixtures/validation-test-metadata/README.md)
describes the one packaged synthetic static-analysis input shared by consumers.
It is example interchange rather than a normative new inventory wire codec.

## Runtime consumer: sm-workflow

The required connection for [sm-workflow Phase 5](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-5.md)
is actual local discovery -> pure purpose/feature selection -> exact reference
resolution/binding -> typed `SbtExec` -> current-context admission -> existing
driver -> typed result interpretation. Use the same binder for query-selected
and supplemental references:

```scala
val selected = ValidationDiscoveryV1.selectC(inventory,
  ValidationDiscoveryV1.SelectionQuery(purposes, features))
val bound = ValidationOperationBindingV1.bindC(inventory, reference, workspaceId)
// bound.operation retains metadata, provenance and ExecutionRequirement.
// bound.intent is UnitOfWorkOp.SbtExec; the existing interpreter admits it.
```

An empty query dimension adds no filter; purpose and feature membership each use
OR, with AND between dimensions. Supplemental references can select a valid
unannotated operation or a purpose outside the query. They never rewrite its
purposes or weaken execution requirements. Missing, invalid and ambiguous
references fail distinctly. Keep the inventory's scope/completeness/diagnostics
beside the selection; empty results from incomplete scope prove no absence.

Binding creates intent only. The current UnitOfWork context must independently
grant workspace Manage and the actual component's host TestOnly capability for
the identical binding. The selected class must occur in that host allowlist.
One exact command item is `testOnly <class> -- -t "<name>"`; unsupported quotes,
backslashes and semicolons fail rather than being escaped. Compile/Test reject
scenario selection. Host JSON remains class-only and receives no new field.

Consume `SbtExecutionResult.outcome`, including `UnverifiedTests`, together with
the retained exact target/scenario and actual raw process result. Scenario
success needs one matching class and a complete ordinary successful summary
with exactly one test. A suite-only receipt does not independently name the
operation; the producer's typed intent requests the exact selector. Parsing
cannot authenticate forged logs. No declaration or bound operation stores
runtime pass/fail, observed duration, attempts or execution authority.

The S3 deterministic driver specifications passed within the focused validation
of 79 tests across eight suites. The parent-selected top-level fixture
`testOnly ...FunMetadataFixtureSpec -- -t "parameter case 1"` separately passed
one test in one suite, checking real literal ScalaTest selection (total 80 tests
across nine suites, zero failures or aborts, SBT/wrapper exit 0, shared lock
released). Those S3 checks remain producer/framework evidence.

### Actual consumer delivery — October 19

[sm-workflow Phase 6.2](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.2.md)
owns the delivered consumer implementation and dependency refresh. Its
[selection adapter](https://github.com/asami/sm-workflow/blob/main/src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseValidationMetadata.scala)
uses actual discovery and purpose/feature selection, then the identical producer
`ValidationOperationBindingV1.bindC` for selected and supplemental references.
The [native metadata adapter](https://github.com/asami/sm-workflow/blob/main/src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeMetadata.scala)
retains the inventory and selection for
[native validation](https://github.com/asami/sm-workflow/blob/main/src/main/scala/org/simplemodeling/textus/smworkflow/admission/GoalPhaseNativeValidation.scala).
The [owning execution path](https://github.com/asami/sm-workflow/blob/main/src/main/scala/org/simplemodeling/textus/smworkflow/admission/WorkflowManagedProcessExecution.scala)
runs the typed intent through `Execution.runSbtC` and checks the returned exact
target and operation before outcome consumption. These links identify the
consumer source; the parent inspected the local implementation and actual
retained receipts rather than inferring runtime success from source alone.

The actual K2 CML authoring scenario produced a COMPLETE three-operation
ascii/empty/unicode inventory. Its purpose/feature query selected ascii and its
explicit supplement selected unicode; empty remained discoverable and ran in
ordinary Test. Ordinary Test passed 3/3, and each exact ASCII/Unicode TestOnly
passed 1/1. Real independent consumer review accepted that candidate, followed
by isolated Step commit `b69a60a0fc3dd5e9e7df87fd08b2549f41cef220`. Actual L3
Slice-plan integration has separate independent acceptance and isolated Step
commit `157388724869c21649936d4b2de7508571507605` as supplementary evidence.
These commits accept the isolated consumer Steps, not its dirty primary tree,
primary Phase 6.2 release or whole main acceptance.

This consumer-owned delivery resolves the former CNCF source-admission
prerequisite. This CNCF task authorizes and performs no sm-workflow source edit;
neither whole Phase waits for the other's whole completion. The accepted Phase
103 operation binding here is exact zero-input scenario selection. Nonempty
typed-input parameters and invocation variants remain separately scoped work,
which may coexist in the current producer/consumer trees; no acceptance of that
feature is attributed to Phase 103. The synthetic static fixture remains
separate from these actual execution receipts.

sm-workflow's Slice plan/file lifecycle, execution policy, evidence, timing budgets,
reasoning/provider decisions and convergence guards remain consumer-owned.

## Static consumer: textus-cbd-support

The actual owner is the
[October 6 Validation/Test Metadata static-analysis journal](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md),
whose checked-out source was inspected read-only. Current
[Phase 14](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-14.md)
is closed native Job integration, not the metadata workstream. This handoff
neither reopens that Phase nor changes cbd-support sources.

The example includes comparable baseline/candidate scope and population
revisions, independent provenance, one sole ADMISSION declaration removed on
the same logical operation, valid unannotated rows, PARTIAL/UNKNOWN variants,
explicit target features including an uncovered feature, revisioned mappings,
and empty runtime evidence. A complete comparable ADMISSION query changes 1 -> 0;
incomplete variants cannot establish absence. Static analysis owns findings,
lint, KPI presentation and human judgment. It must use explicit target
populations rather than observed tags and must not turn declarations into
runtime success. Static application completion is outside producer acceptance.

## CML consumer: Cozy

`ValidationCmlMappingV1.Properties` is the normalized projection boundary.
Exact purpose and duration-class labels, scoped features, duration amount/unit,
existing execution requirements, required semantics and ABI version map to
the same admitted declaration and existing S1 normalization. BigInt conversion
rejects nonpositive or overflowing nanos before `FiniteDuration` construction.
Cozy retains ownership of syntax, parsing and generation; CNCF adds none of
those mechanisms and no independent merge or requirement policy.

## Compatibility and producer closure

`SbtTarget(workspaceId)` source construction remains valid via the defaulted
scenario field. Case-class extractor arity changes from one to two; extractor
callers must update. No binary compatibility is claimed for precompiled
SNAPSHOT callers. Discovery, annotations, metadata and the host codec retain
their accepted contracts. Existing class-only TestOnly behavior remains.

Independent protected-focused S3 Step review returned PASS with zero Current
Boundary Blockers, Hygiene or Development Candidate findings and no evidence
gaps. S3 is accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`.
Actual consumer evidence is available; independent CNCF integrated S4 review
passed and S4 is accepted in local Step commit `c456f09c9ddd67f3746500381d8f7b30be538d57`.
October 19 producer closure-focused
validation passed compilation and 161/161 tests across 19 suites (no failures,
aborts, canceled or pending tests; SBT/wrapper exit 0; shared lock released).
The earlier failed full invocation omitted native DB flags. Subsequent full001
enabled PostgreSQL/MySQL and passed compilation, then failed with 6,851 passed
and two failed tests out of 6,853 across 861 suites, no suite aborts, five
canceled, one ignored and 46 pending; SBT/wrapper exit 1 and lock released.
Its C3-C2 historical-caller/current-Action no-op oracle and C3-H5 direct SPI
observer bypass were test observation defects, not native DB failures. Both
were corrected without weakening ownership, no-replay or full-state assertions.
Focused004 passed compilation and 173/173 tests across 21 suites, including the
previous 19 and both failure suites, private helper compilation and actual
Action/caller history assertions. It had no failures, aborts, canceled, ignored
or pending tests; SBT/wrapper exit 0 and lock released. Required full002 subsequently
passed compilation and 6,853/6,853 tests across 861 suites, with zero failures or
suite aborts, five canceled, one ignored and 46 pending. Both native PostgreSQL
and MySQL suites ran with explicit Test fork and both flags true; SBT/wrapper
exit 0 and lock released. Five accepted nonblocking Hygiene dispositions are
recorded in the [Hygiene journal](../journal/2026/10/2026-10-18-phase-103-hygiene-follow-up.md).
The sole comprehensive Phase review passed on October 19 with no Current
Boundary Blockers or evidence gaps; producer Phase 103 and VM-01..04 are CLOSED.
The Hygiene records remain OPEN separate follow-ups, with no Development or
unpersisted accepted findings. At preparation of this closure record, the final
local release commit is pending parent execution and committed-tree verification;
its actual outcome belongs to the native commit receipt.
The [checklist](../phase/phase-103-checklist.md) owns current acceptance status.
This handoff grants no execution permission and computes no cost KPI; its
consumer evidence establishes no whole consumer Phase completion. Producer
closure additionally rests on CNCF's four accepted Steps, required full
validation and sole comprehensive review.
