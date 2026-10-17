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
its own runtime evidence and progression lifecycle. Its actual source fixture
and dependency refresh remain separately admitted Phase 103 S4 work.

textus-cbd-support consumes normalized metadata for static comparison under its
[October 6 static-analysis workstream](https://github.com/asami/textus-cbd-support/blob/main/docs/journal/2026/10/2026-10-06-validation-test-metadata-static-analysis.md).
Its closed Phase 14 owns native Job integration and is not the metadata owner.
Static application implementation is outside CNCF producer acceptance.

Cozy owns CML syntax/parser/generation. CNCF supplies this normalized property
projection and equivalence specifications; generated Scala remains another
representation of the same declarations. See the
[consumer handoff](../../notes/validation-test-metadata-consumer-handoff.md).
