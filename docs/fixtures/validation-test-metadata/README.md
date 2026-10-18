# Validation/Test Metadata shared consumer example

The packaged [consumer-fixture.json](../../../src/main/resources/META-INF/cncf/validation/v1/consumer-fixture.json)
is a portable, synthetic static-analysis contract example. Its resource name is
`META-INF/cncf/validation/v1/consumer-fixture.json`. It is neither a discovered
runtime inventory nor an authoritative list of suites. The JSON illustrates
interchange; it does not define a normative inventory wire codec.

The [Executable Specification](../../../src/test/scala/org/goldenport/cncf/validation/ValidationConsumerFixtureV1Spec.scala)
reads this same resource through a private typed reader, normalizes declaration
sources with public `ValidationCmlMappingV1.resolveC`, and uses public
`ValidationDiscoveryV1.selectC` and `resolveC`. It does not discover or execute
the example suite names. Actual discovery and operation binding use the separate
real ScalaTest fixtures described in the [specification](../../spec/validation-test-metadata.md).

## Field correspondence

| Example field | Public typed interpretation / consumer responsibility |
| --- | --- |
| `comparison` | Consumer comparison context: common `scopeIdentity`, explicit population revision, baseline and candidate names. No CNCF comparability validator or KPI model is added. |
| `snapshots[].scope` | `ValidationDiscoveryV1.DiscoveryScope` and its `ArtifactScope` rows, with independent scope/artifact/source revisions. |
| `artifactLocator` | Portable synthetic locator. The private reader projects it to an absolute `/synthetic/validation/...` `Path` for pure query admission. This path is never opened; real `discoverC` instead requires an admitted available local root and caller-owned loader. |
| `completeness` | Exact `Completeness.COMPLETE`, `PARTIAL` or `UNKNOWN` within that declared population. |
| `operations[].reference` | Stable `OperationReference(component, specification, operation)`. |
| `operations[].provenance` | `Provenance`, retaining independent artifact/source/operation revisions and ABI version. |
| `operations[].selector` | `ScalaTestSelector` coordinates of synthetic registrations; they confer no authority or proof that these suites exist. |
| `operations[].specification` / `operation` | Absent (`null`) or normalized `ValidationCmlMappingV1.Properties`. The public mapper admits exact enum labels and converts positive durations, then calls the existing declaration resolver once. |
| `purposes`, `features`, `expectedDuration`, `durationClass`, `executionRequirement`, `requiredSemantics` | The matching declaration fields. Requirements use the existing `WorkflowProtocolV1.ExecutionRequirement`, without execution/provider policy. |
| `diagnostics` | Bounded public `Diagnostic` data. The variants preserve explicit synthetic enumeration gaps. |
| `variants` | Example-reader projections of a named snapshot, with explicit visible operation subset, scope revision and completeness. These are fixture conveniences, not discovery operations or a public codec. |
| `featurePopulation` | Consumer-supplied target `FeatureReference` set with identity and revision, independent of test-observed features. |
| `featureMappings` | Consumer-supplied feature-to-component/slice relationships, including mapping and target revisions. CNCF does not infer them from labels. |
| `runtimeEvidence` | Empty. No runtime success, measured duration, attempt, Candidate state or cost evidence is present. |

## Observable comparison

Both complete snapshots explicitly describe the same three-operation population.
The baseline has one `ADMISSION` + `FULL` operation, one `FOCUSED` operation
covering two scoped features, and one valid unannotated registration. The
candidate removes only `ADMISSION` from the same logical approval operation;
its selector, other normalized metadata and `FULL` membership remain. Artifact
and source revisions advance consistently; the changed operation revision
advances independently, while other operation revisions remain stable.

Within these complete comparable examples, public purpose queries expose
`ADMISSION` 1 -> 0. The `PARTIAL` and `UNKNOWN` variants also produce an empty
ADMISSION query, but their missing enumeration prevents any absence conclusion.
The explicit three-feature target population includes `uncovered-export`, which
no operation declares. Observed feature tags cannot replace that population as
a denominator. Consumers decide findings, presentation, coverage policy and
comparability; the fixture computes no KPI score.

## Consumer handoff

sm-workflow supplies real artifact scopes/loaders, purpose/feature queries,
supplemental references and logical workspace IDs. It calls the public exact
operation binder for either reference source and interprets typed outcomes in
its own runtime evidence and progression lifecycle. The actual consumer adapter
and dependency refresh are delivered under
[sm-workflow Phase 6.2](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.2.md).
Its K2 CML scenario discovers COMPLETE ascii/empty/unicode inventory, selects
ascii by purpose/feature query and unicode by explicit supplement through the
same producer binder, and consumes native typed outcomes (ordinary Test 3/3,
both exact TestOnly 1/1). Independent consumer acceptance and isolated Step
commit `b69a60a0fc3dd5e9e7df87fd08b2549f41cef220`, with supplementary L3 Slice-plan
acceptance at `157388724869c21649936d4b2de7508571507605`, are recorded in the
[handoff](../../notes/validation-test-metadata-consumer-handoff.md).

Those actual receipts do not execute this synthetic example or promote it into
runtime proof. Phase 103's accepted binding scope is exact zero-input scenario
selection; separately scoped nonempty typed-input work may coexist in the tree.
sm-workflow retains source ownership and whole-Phase acceptance. CNCF's independent S4
integrated review passed and S4 is accepted in local Step commit
`c456f09c9ddd67f3746500381d8f7b30be538d57`; all four declared Steps are accepted locally.
Full001 failed and its corrected observation tests passed focused004. Required
full002 subsequently passed compilation and 6,853/6,853 tests across 861 suites,
with zero failures or suite aborts, five canceled, one ignored and 46 pending.
Both native PostgreSQL and MySQL suites ran with explicit Test fork and both flags
true; SBT/wrapper exit 0 and lock released. Five accepted nonblocking Hygiene
dispositions are recorded in the [Hygiene journal](../../journal/2026/10/2026-10-18-phase-103-hygiene-follow-up.md).
The sole comprehensive Phase review passed on October 19 with no Current
Boundary Blockers or evidence gaps; producer Phase 103 is CLOSED under the
[checklist](../../phase/phase-103-checklist.md). The Hygiene records remain OPEN
separate follow-ups, with no Development or unpersisted accepted findings.
At preparation of this closure record, the final local release commit is pending
parent execution and committed-tree verification; its actual outcome belongs to
the native commit receipt. Neither whole Phase waits for the other's whole
completion, and no sm-workflow source edit is authorized by this task. This
producer acceptance does not execute the synthetic example or accept whole
consumer Phase 6.2.

textus-cbd-support consumes normalized metadata for static comparison under its
[October 6 static-analysis workstream](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md).
Its closed Phase 14 owns native Job integration and is not the metadata owner.
Static application implementation is outside CNCF producer acceptance.

Cozy owns CML syntax/parser/generation. CNCF supplies this normalized property
projection and equivalence specifications; generated Scala remains another
representation of the same declarations. See the
[consumer handoff](../../notes/validation-test-metadata-consumer-handoff.md).
