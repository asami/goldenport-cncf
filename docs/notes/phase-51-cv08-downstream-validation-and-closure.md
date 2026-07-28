# Phase 51 CV-08 Downstream Validation and Closure

## Purpose

CV-08 validates the complete Phase 51 generation and CAR contract at its
owning build, package, publication, and runtime boundaries. It does not infer
compatibility from similar version numbers, weaken runtime admission for older
archives, or make Cozy a runtime dependency.

## Verified Development Chain

The focused validation published the current dependency chain locally in
order:

1. `simplemodeling-model_3:0.2.1-SNAPSHOT`
2. `simplemodeler_2.12:1.1.25-SNAPSHOT`
3. `goldenport-cncf_3:0.5.2-SNAPSHOT`
4. `cozy_2.12:0.3.1-SNAPSHOT`
5. `sbt-cozy:0.1.16-SNAPSHOT`

These are development publications only. They do not create immutable release
evidence or authorize a public release.

## CNCF Build and Runtime Evidence

The serialized cold build ran:

```text
clean; verifyInformationCmlGenerationDeterminism;
testOnly
  org.goldenport.cncf.phase51.build.Phase51Cv03BuildIntegrationSpec
  org.goldenport.cncf.phase51.build.Phase51Cv05BuildProvenanceSpec
  org.goldenport.cncf.component.CarRuntimeAdmissionSpec;
Test/compile
```

It generated the 25 Information Scala files twice and verified the same file
set, content, and provenance bytes. All 14 focused scenarios passed.

`CarRuntimeAdmissionSpec` proves both sides of the runtime boundary:

- an incompatible CNCF range fails before component code is exposed; and
- a compatible integrity- and ABI-valid CAR activates while the Cozy
  implementation class is unavailable.

Generation provenance remains opaque integrity-protected data at runtime.

## Cozy and sbt-cozy Evidence

Seven focused Cozy suites passed 76 scenarios covering exact compatibility
pairs, descriptor identity and digest checks, development/release lifecycle,
CAR project metadata, provenance, runtime manifest, scaffold, and publication.
The aggregate run found a specification-isolation defect: two leak assertions
compared the complete shared operating-system temporary directory before and
after an operation. A parallel suite could delete its own snapshot between
those reads. The corrected assertions verify that the operation leaves no new
generation-provenance snapshot without claiming ownership of other suites'
files.

Four sbt-cozy suites passed their five directly owned unit scenarios. Five
integration-owned scenarios were intentionally canceled without an exported
scripted classpath. The authoritative scripted projects then passed:

- `cozy/project-yaml-car` exercises standard release and local publication
  routing from a CAR project that declares exact Cozy and CNCF coordinates.
- `cozy/review-evidence` produces the provider-owned review evidence bundle.

The scripted CAR fixture had predated the exact-coordinate rule. CV-08 added
`build.cozyVersion: 0.3.1-SNAPSHOT` and the single
`org.goldenport::goldenport-cncf:0.5.2-SNAPSHOT` compile coordinate rather than
bypassing the Phase 51 admission boundary.

## Representative CAR Evidence

ArtScene is the representative downstream CAR. Its clean build:

- generated 155 Scala sources;
- compiled 167 main sources;
- built `textus-art-scene-0.1.2-SNAPSHOT.car`;
- locally published the identical archive under the CNCF local CAR repository;
- passed 15 focused scenarios across Phase 51 acceptance, exact collection
  identity, persisted nominal String lifecycle, and launcher assembly; and
- completed `Test/compile`.

Normal CAR lint accepts the exact Cozy generator, CNCF compile coordinate, and
runtime tested value. It also confirms that the packaged descriptor declares
name, version, and component. The remaining missing historical ABI baseline,
deliberate SNAPSHOT plugin, and nominal String wrapper findings are recorded
development warnings, not Phase 51 compatibility contradictions.

The first full standalone-assembly probe additionally revealed an intentional
boundary: independently released dependency CARs produced before CV-06C2 do
not contain `car-runtime-manifest.json`. CNCF rejects them rather than silently
treating them as compatible. Phase 51 neither rewrites those immutable
artifacts nor weakens admission.

REVIEW_FIX then built the AI runtime, scraper, and toolchain provider CARs from
clean temporary source snapshots against the exact
`0.5.2-SNAPSHOT`/`0.3.1-SNAPSHOT` Phase 51 stack. The builds generated and
compiled 15/42, 65/72, and 7/10 Scala sources respectively; every archive
contains its exact-coordinate `car-runtime-manifest.json`, ABI evidence,
integrity set, and packaged component JAR.

The maintained ArtScene standalone acceptance script now has an explicit
representative packaged-smoke mode. It starts an isolated JVM/test home,
disables inherited/default component repositories and project-class
discovery, and supplies ArtScene plus all three dependencies as explicit CAR
files. The resulting four-CAR assembly passed standalone application
description, idempotent seed, facility search, exhibition definition,
`want_to_visit` review persistence, facility registration, and persisted
failing-source update-report behavior. This separate-process packaged
execution is the downstream smoke evidence for CV-08; the older immutable
release CARs remain rejected and unmodified.

The following independent RE_REVIEW found one regression in the maintained
script: packaged isolation flags were also applied when no provider-CAR
directory was selected, replacing the normal repository-discovery boundary
with a missing-provider-API failure. REVIEW_FIX now selects isolated
four-CAR execution only when `ARTSCENE_ACCEPTANCE_COMPONENT_DIR` is present,
requires that directory when packaged-smoke-only mode is requested, and
restores the ordinary CNCF invocation otherwise. The explicit four-CAR
behavior smoke passed again. A negative configuration probe rejected
smoke-only mode without its provider directory, while the normal path reached
the documented pre-Phase-51 missing-runtime-manifest boundary without the
missing-provider regression.

The next independent RE_REVIEW found two remaining acceptance-contract
defects. The cleanup check had dropped its explicit `dry_run=true` preview even
though omission is destructive, and the operator documentation categorically
prohibited changing `user.home` while the explicit packaged-CAR smoke gives
only its isolated child JVM a test home. REVIEW_FIX restored the explicit
non-destructive preview and documented that narrow repository-isolation
exception without changing the `test.yaml` datastore boundary.

The complete four-CAR path then exposed two exact-identity assumptions behind
the cleanup and REST checks: raw records read canonical `EntityId` values
through String-only access, and the raw persistence adapter reparsed a
canonical ID after decode, losing its exact owning collection. REVIEW_FIX now
uses nominal-aware normalization, preserves canonical `EntityId` objects, and
matches scalar ingress with restored identity by physical value. The
exact-identity and operational-task lifecycle suites passed 5 of 5 focused
scenarios. A rebuilt ArtScene CAR passed explicit cleanup preview with zero
deletions, restart persistence, packaged Web and REST, review-backup export,
and clean-datastore restore. Normal CAR lint remained failure free with only
the already recorded development warnings.

The next independent RE_REVIEW found four closure defects in that repair:
context-aware raw decode could still reach the total `EntityPersistent.id`
fallback for a missing or malformed physical ID, scalar normalization reparsed
an existing canonical `EntityId` and discarded its owner, the executable
specification placed a second operation beneath the first behavior boundary,
and the CI-01 note prohibited the raw adapter behavior that its normative
contract requires. REVIEW_FIX now preserves an existing canonical `EntityId`,
parses only scalar ingress, and makes context-aware raw decode reject a missing
or malformed physical ID before exact owner restoration. The specification
uses a distinct FetchExhibitions When/Then boundary and adds a malformed-row
regression, while the CI-01 note explicitly owns parsing and exact-owner
restoration at the raw persistence adapter.

The corrected exact-identity and operational-task lifecycle suites passed 6 of
6 scenarios plus `Test/compile`. The rebuilt ArtScene CAR passed the complete
four-CAR standalone cleanup-preview, restart, packaged Web/REST, backup-export,
and clean-restore path again. Normal CAR lint and repository diff checks passed;
its historical ABI-baseline, deliberate development SNAPSHOT, and nominal
String wrapper warnings remain recorded readiness notes rather than new
review-fix targets.

## Remaining Closure Gates

The clean accumulator RE_REVIEW entered PHASE_RELEASE_COMMIT. The first full
gate passed `simplemodeling-model` and its local development publication, then
found one stale pre-projection generated-source expectation in
`simple-modeler` `EntityCustomTypeResolutionSpec`. PHASE_TEST_FIX changed only
that expectation and its executable-contract wording. The suite passed all 7
scenarios plus `Test/compile`, and the repository-scoped REVIEW, REVIEW_FIX,
and clean RE_REVIEW accepted the correction.

The next full-gate attempt passed the complete `simple-modeler` suite and
`publishLocal`. Cozy then passed 737 of 739 tests before two
`BridgeContractSpec` publication fixtures failed the stricter Phase 51 CAR
metadata contract. PHASE_TEST_FIX changed only those fixtures: they now build
admitted SNAPSHOT CARs with canonical project metadata, explicit ABI evidence,
and the current proven development generation pair, and they verify that
SNAPSHOT publication writes the archive without adding a release catalog. The
focused bridge suite passed all 13 scenarios plus `Test/compile`; the scoped
REVIEW and clean RE_REVIEW found no actionable issue.

The following final-gate attempt froze CNCF staged identity
`fb22f2ac1f9acee3dd30f1525d7cd7408f60d5ce5ff30547eace95586d0de8e0`.
From the CNCF repository it ran the serialized command
`run-sbt-serial.sh "test" "publishLocal"`.
`simplemodeling-model` passed 58 tests across 29 suites and
`simple-modeler` passed 44 tests across 13 suites; both were locally published.
CNCF then verified deterministic generation of the 25 Information Scala files,
but its full suite completed 2,611 tests with 2,588 succeeded and 23 failed
across ten action/entity/UnitOfWork/blob/component suites. CNCF
`publishLocal` therefore did not run.

The failing suites were:

- `ActionCallEntityAccessMetricsSpec`
- `ActionCallConditionalTransitionDslSpec`
- `ChildEntityBindingWorkflowSpec`
- `EntityConditionalTransitionRevisionSpec`
- `EntityConditionalTransitionCoherenceSpec`
- `EntityStoreQueryRouteSpec`
- `UnitOfWorkPlainMutationProviderParitySpec`
- `UnitOfWorkConditionalTransitionSpec`
- `BlobStoreSpec`
- `ComponentFactoryDefaultAggregateCollectionSpec`

Cozy and sbt-cozy were independently valid without the failed CNCF
publication. Cozy passed all 739 tests across 68 suites and sbt-cozy passed all
123 tests across 27 suites; both were locally published and preserved their
frozen staged identities. Normal ArtScene CAR lint remained free of `FAIL`
with only the recorded development warnings, but its full suite was not run
because it requires the failed CNCF publication. The final gate created no
commit.

The CNCF PHASE_TEST_FIX for the ten failing suites and its scoped REVIEW and
clean RE_REVIEW are complete. The remaining stages are:

1. fresh full accumulated suites from the preserved staged identities in every
   affected repository; and
2. final release evidence reconciliation and eligible release commits.

Only the final stage may record complete release evidence and close Phase 51.

The next fresh final gate passed and locally published the model, modeler,
CNCF, Cozy, and sbt-cozy repositories while preserving every frozen staged
identity. ArtScene normal CAR lint remained free of `FAIL`, and
`cozyBuildCar` produced `textus-art-scene-0.1.2-SNAPSHOT.car`. Its full suite
then completed 41 suites and 389 tests with 388 succeeded, one canceled, and
one failed in `ArtSceneFacilitySubscriptionSpec` at line 55. ArtScene
`publishLocal` did not run, and no commit was created. The next
PHASE_TEST_FIX and scoped review are limited to this latest ArtScene failure.

The ArtScene-only correction canonicalizes the raw subscription lookup id to
the generated collection and uses the resolved aggregate's authoritative id
for the generated update command input. The focused subscription suite passed
all 5 scenarios plus `Test/compile`; scoped REVIEW and clean RE_REVIEW found no
actionable issue. The fresh ArtScene full gate and final release
evidence/commits remain.

The final affected-repository gate preserved the reviewed ArtScene tree, built
and locally published `textus-art-scene-0.1.2-SNAPSHOT.car`, and passed all
389 tests across 41 suites with one environment-owned scenario canceled. The
unchanged upstream trees retain their passing full suites and local
publications: `simplemodeling-model` 58 tests, `simple-modeler` 44, CNCF
2,613, Cozy 739, and sbt-cozy 123. Normal ArtScene CAR lint has no `FAIL`;
its ABI-baseline, explicit development SNAPSHOT, and nominal String wrapper
warnings remain publication-readiness notes rather than Phase 51
contradictions. Every CV-08 closure gate passes.
