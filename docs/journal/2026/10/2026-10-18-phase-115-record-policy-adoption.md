# Phase 115 connected Record policy adoption — 2026-10-18

Status: RCP-CONSUMERS focused-validated; independent complete protected focused
Step review PASS; local commits pending. Phase 115 remains IN_PROGRESS, with all five
RCP checklist obligations OPEN. This journal records the adoption; it introduces
no normative contract or acceptance decision.

[Phase 115](../../../phase/phase-115.md) and [its checklist](../../../phase/phase-115-checklist.md)
retain RCP-MAIN's accepted 51/51 history and local commit
`f7af5c79c50f93a6f36f63b1af86cc0086c9d157`. The connected consumer Step retains
the original Goal/authority/start/epoch and repair history. The sole Phase full
review, full tests in all four affected repositories and final release/checklist
closure remain pending. HYG-CNCF-P115-001 stays separate; no successor starts.

## Producer ownership and selected baselines

The source inputs are Core `53b1612faadf43840a75cf7d1c6d54ea5f248015`, model
`1f752f1791730860a043736da2111dc3c0e18474`, Modeler
`ed6fbe79876f6deb8f2f7c2ced875b64e7fe42c0` and original CNCF
`11d4e2843f03a787d5a4f5900e97ed04e4b63276`. The current consumer changes build
on these inputs; they do not relabel them as the completed consumer Step.
Selected dependency local refreshes succeeded at unchanged development
coordinates Core 0.4.3-SNAPSHOT, model 0.2.2-SNAPSHOT, Modeler 1.1.26-SNAPSHOT
and CNCF 0.5.3-SNAPSHOT. No public publication is recorded.

Core owns generic external conversion, strict collection readers and canonical
Record/raw Json meaning. The model library adds explicit model-group, nominal
scalar and Update/Condition projection. Modeler emits thin declared type,
multiplicity and ordered-alias calls. CNCF retains storage/update instructions
and foreign-input policy. No duplicate Record normalizer or generated-source
hand edit is part of adoption.

The [collection rule](../../../rules/collection-and-semi-structured-data.md)
selects ordinary Vector and explicitly declared NonEmptyVector, while existing
search result containers, page metadata and ordering keep their own semantics.
Semantic Record null members become absent; empty objects/arrays remain present.
Only explicit typed SQL clear clears a field. Raw Json retains its null values,
array positions and lexical numeric values.

KnowledgeNodeId and RdfNodeName implement existing NominalScalar;
ExternalKnowledgeIdentifier and KnowledgeEntityBinding implement existing
RecordPresentable. These four application types preserve their established
scalar/Record wire without adding application classes to Core. Their ordinary
Information routes and [Knowledge conversion specification](../../../../src/test/scala/org/goldenport/cncf/knowledge/Phase115KnowledgeExternalConversionSpec.scala)
exercise this ownership.

## Actual generated and foreign-input consumers

The [ordinary CML fixture](../../../../src/test/cozy/phase115-record-policy.cml)
and [generated runtime specification](../../../../src/test/scala/org/goldenport/cncf/entity/Phase115GeneratedCollectionPolicySpec.scala)
exercise Vector/NonEmptyVector memory and test-owned SQLite persistence, reopen,
search and retained values. Actual ordinary `Query.createC` reconstructs typed
and Record application elements into `Condition[Vector[ChildPolicy115]]`, retaining
empty input, order and duplicates while rejecting invalid elements. The actual
generated ScalarCode115 DATATYPE establishes nominal scalar projection apart
from ChildPolicy115's structured VALUE projection. Earlier CountryCode/PostalCode
VALUE fixtures do not prove nominal scalar semantics.

The [original Phase 100-to-115 boundary finding](2026-10-18-phase100-phase115-collection-boundary.md)
remains historical negative source evidence: an old declared List Query path
received Vector, and the selected Phase 100 suite did not invoke the ordinary
Query.createC route. Current canonical Vector declaration/producer/caller
migration and actual runtime reconstruction do not make that earlier observation
false or retroactively seal its original comparison.

[HTTP decoder](../../../../src/test/scala/org/goldenport/cncf/http/OperationJsonBodyDecoderSpec.scala) and
[dispatch scenarios](../../../../src/test/scala/org/goldenport/cncf/http/Phase115JsonBodyDispatchSpec.scala)
exercise raw Json ownership and explicit-clear-required errors before mutation.
[Durable codec scenarios](../../../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponseCodecSpec.scala)
and [native payload scenarios](../../../../src/test/scala/org/goldenport/cncf/job/DurableJobOperationResponsePayloadsSpec.scala)
retain old-list input and independent canonical Vector producer expectations.
The original List golden is unchanged. Actual retained native reads return Vector
without changing physical bytes, rows or quota. Missing createEntity forwarding
in two measured test wrappers accounted for the original quota bootstrap failures;
the repair and [three-route native quota witness](../../../../src/test/scala/org/goldenport/cncf/job/Phase115NativeQuotaAdmissionSpec.scala)
retain genuine native SQL and measured write counts.

Selected focused results and the distinct failed invocation history are recorded
succinctly in the Phase document. Core 79/79, model 54/54, Modeler 33/33,
Consumer A 227/227 and the later owner continuation 106/106 are separate scopes.
Original codec representatives 17/17 and Information 17/17 passed; earlier B1
regression exit255 and five HTTP oracle failures remain failed history. Decoder
52/52 passed in both original B2 and owner validation, without adding duplicates.
Native create repair ran 61/60/1, retaining its sole RP12 failure; the subsequent
affected payload suite passed 14/14 including original 12 and retained V1/V2 two
cases. No total sums overlapping scopes or substitutes for independent acceptance.

## Retained values and working contracts

This is the source-only Workbench migration with no existing Workbench database.
Tests own their stores. Retained semantic values normalize on read and subsequent
save; raw JSON retains its document values. No automatic live rewrite or bulk
data migration is introduced.

The producer working contracts remain
[Core external conversion](../../../../../simplemodeling-lib/docs/spec/external-value-conversion.md), [Core Record/readers](../../../../../simplemodeling-lib/docs/spec/record-normalization.md),
[model conversion](../../../../../../dev2026/simplemodeling-model/docs/spec/model-value-conversion.md),
[Modeler structured generation](../../../../../simple-modeler/docs/spec/structured-data-types.md) and
[Modeler storage generation](../../../../../simple-modeler/docs/spec/entity-store-mapping-generation.md).
The affected CNCF contracts are
[HTTP typed updates](../../../spec/http-form-typed-update-parameters.md) and
[Durable response codec](../../../spec/durable-job-operation-response-codec-contract.md).
Their current adoption paragraphs reflect selected tests and the independent
complete protected focused RCP-CONSUMERS Step review PASS; dated Core Phase3,
Phase100 and Phase69.7 receipts retain their original meanings and open gates.

The parent readiness cross-check identified generated Powertype/StateMachine
values with competing Record presentation. The model adapter now selects their
existing `Presentable.print` scalar meaning before generic RecordPresentable;
NominalScalar stays first and database `dbValue` stays storage-specific. Throwing
or null prints fail without Record fallback. The selected
[model conversion specification](../../../../../../dev2026/simplemodeling-model/src/test/scala/org/simplemodeling/model/value/ModelValueEncoderSpec.scala)
and original nine suites passed 56/56 (26 model cases, including two added
precedence scenarios, plus 30 original cases). The tested model 0.2.2-SNAPSHOT
local refresh succeeded, followed by generated consumer integration 44/44 in
five suites; the actual [Knowledge conversion specification](../../../../src/test/scala/org/goldenport/cncf/knowledge/Phase115KnowledgeExternalConversionSpec.scala)
requires generated Information external and stored status to be selected strings.
These selected results overlap earlier model/consumer evidence and are not
summed. This corrects existing print-contract selection without adding a Core,
generator or application registry or changing storage/wire meaning. All five
RCP obligations remain OPEN and Phase IN_PROGRESS; independent complete protected
focused RCP-CONSUMERS Step review passed. Local commits, all four full tests, the sole comprehensive Phase
review and final release remain pending. HYG-CNCF-P115-001 stays separate, with
no successor activated.
