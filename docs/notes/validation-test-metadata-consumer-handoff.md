# Validation/Test Metadata V1 consumer handoff

Date: 2026-10-18
Status: P103-S3 producer accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`; concrete
sm-workflow S4 acceptance remains pending. Phase 103 stays in progress.

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
released). Neither
is an accepted actual sm-workflow consumer fixture. Source changes and dependency
refresh in sm-workflow require separate S4 admission after producer acceptance.
Its Slice plan/file lifecycle, execution policy, evidence, timing budgets,
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

## Compatibility and remaining gates

`SbtTarget(workspaceId)` source construction remains valid via the defaulted
scenario field. Case-class extractor arity changes from one to two; extractor
callers must update. No binary compatibility is claimed for precompiled
SNAPSHOT callers. Discovery, annotations, metadata and the host codec retain
their accepted contracts. Existing class-only TestOnly behavior remains.

Independent protected-focused S3 Step review returned PASS with zero Current
Boundary Blockers, Hygiene or Development Candidate findings and no evidence
gaps. S3 is accepted in local Step commit `1df07da9be8716edf0e0462bcba221ca8d020c60`.
The actual sm-workflow S4 fixture, full connected repository
validation, one full Phase review and Phase release acceptance remain open.
This handoff supplies no execution permission, runtime proof, cost KPI,
publication or completed consumer/Phase claim.
