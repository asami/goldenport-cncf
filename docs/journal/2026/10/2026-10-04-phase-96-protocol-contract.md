# 2026-10-04 — Phase 96 protocol contract and first consumer handoff

Document role: chronological history, sample provenance and focused acceptance evidence.
Scope: PHASE-96 / P96-04A-WIRE / P96-04A.
Manifest: `phase96-p96-04a-implementation-manifest-001` revision 2, replacement
locator `phase96-p96-04a-implementation-manifest-002.json`.
Current status: focused validation passed 151/151 tests in 18 suites;
independent P96-04 Step review PASS and parent admission accept all four criteria.
P96-04 is CLOSED as an acceptance candidate; local Step commit is PREPARED/PENDING.
The checklist has 24 checked and 30 pending outcomes, with current_entry=P96-05.
Phase 96 remains IN_PROGRESS with zero full reviews. This current status and the
[acceptance entry](#2026-10-05--p96-04-independent-step-acceptance) supersede the
historical first-authoring status below; the dated body, planned UNEXECUTED
matrices and earlier 20-checked status remain authoring history.

Historical first-authoring status — 2026-10-04: implementation authoring;
validation, independent review and Step commit acceptance UNEXECUTED/PENDING.
Phase 96 remained IN_PROGRESS with zero full reviews at authoring.

## Retained authority and predecessor receipts

The original Phase base remains `6cbe877c53c116639feb11fa80546e79c051804c`,
on `codex/phase-96-display-service`, in the isolated CNCF checkout. Accepted
P96-03 Step commit is `bef59e729834addd3ac00c1930034bb23a8b428f`.
The existing [actual consumer journal](2026-10-04-phase-96-generated-consumer-acceptance.md)
retains its original preparation and subsequent focused acceptance chronology.
Its 140 focused tests are historical prerequisite evidence, not codec proof.
Cozy P76-05 reciprocal receipt is accepted at `3546755`; P76-06A documentary
handoff at `ddeadab9a97fda818fb2842df4873dbc7adf03e1`. Their repository-final
validation, Phase full review, release and closure remain separately owned.

LUI-ABI-01 is issued, LUI-GEN-01 actual producer delivery is accepted at
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, and LUI-ACCEPT-01 focused
consumer proof is accepted. Producer coordinate is
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala 2.12.18; CNCF coordinate
is `org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala 3.3.8.
No source regeneration or dependency refresh is part of this codec pass.

## DSP-WIRE-01 first read snapshot handoff

The authoritative [read protocol](../../../spec/display-model-protocol.md)
R1..R8 and [design](../../../design/display-model-protocol.md) promote only
strict v1 List/Detail snapshots from the earlier proposal. Physical identity is
`cncf.display-model.v1`, integer version `1`, media type `application/json`.
This first handoff is prepared for presentation-client implementation; focused
codec evidence remains UNEXECUTED and must be appended by the parent before
acceptance. No PASS or receipt execution is asserted by authoring.

Application source is typed ComponentId
`org.goldenport.cncf.test.GeneratedLogicalUiConsumer`, resource type
`collecting-reference`. Source is not authorization proof. Actual producer
provenance is `cozy.logical-ui-generated.v1` / `ess2-01` / `ess2-01-cml-1`;
definition revision is `ess-definition-1`, independently of each resource revision.
The actual source files are unchanged
[definitions](../../../../src/test/scala/domain/logicalui/DomainLogicalUiDefinitions.scala)
and [provider](../../../../src/test/scala/domain/logicalui/DomainLogicalUiMetadataProvider.scala).
Their exact ABI1 and complete Phase74 semantic descriptor remain separate
metadata dimensions. Original expected fixture provenance remains
`cncf.expected-generated-fixture` / `ess2-01` / `fixture-1`, never normalized.

The [List golden](../../../../src/test/resources/display/protocol/v1/ess2-01-list.json)
and [Detail golden](../../../../src/test/resources/display/protocol/v1/ess2-01-detail.json)
describe normal actual consumer bootstrap, contextual query `collecting-resources`
and matching read `capture-detail` from captureC(1,7) / captureC(2,19).
The two opaque resource values are:

```text
fixture-display-entity-ec1_7_fixture_7_display_7_Capture-1800000000000-projection_resource_1
fixture-display-entity-ec1_7_fixture_7_display_7_Capture-1800000000000-projection_resource_2
```

These are source-derived canonical fixture values from EntityId.restore and
UniversalId.Parts.value, not a new ID grammar or runtime verification claim.
Revisions are independent strings `7` / `19`. Golden content preserves the
existing Japanese/Greek/combining title, whitespace/control description,
Draft stage and enabled review availability. Each resource is canonical encoder
text plus exactly one LF, with no generic trim during comparison.

Independent all-kind fixture provenance is `cncf.protocol-all-kind-fixture` /
`pure-values` / `fixture-1`; the empty layout uses sourceId `empty-values`.
That fixture admits all five content value kinds, arbitrary UTF16 units, exact
BigDecimal unscaled coefficient/Int scale, Instant nanos/limits, maximum positive
revision, scoped ID reuse, finite order permutations and optional/empty containers.
It does not attribute those definitions to the actual Cozy text-only delivery.

Compatibility is exact across protocol, ABI, every semantic descriptor/driver
member, trusted source, definition revision/screen and original provenance.
All JSON objects are closed; duplicate/unknown/missing/null members and unknown
kinds fail structurally. The codec performs no source call, provider reread,
authorization, callback or action execution. The Test-only component-package
accessor returns exactly the stored Option/wrapper from normal bootstrap and
does not fabricate another admission.

The existing success envelope owns `data`/`execution` and optional framework
members; the codec owns only the business OBJECT, never quoted JSON data.
GET `/rest/v1/{component}/display/query.json` takes resourceType/screenId;
read.json additionally takes resourceId. Machine callers select JSON and
`textus.output.shape=envelope`, with UTF-8 transport. P96-05 owns normal
Operation registration and formatter/adaptor/live proof that data stays an object.

TFC owns transport/runtime, TFAF visual realization, Editing Studio Phase 2
application semantics and app Phase 5 real-server aggregate/E2E acceptance.
This subset carries no editable fields, constraints, capabilities, executable
Business descriptors, mutation outcomes, context, Experiment or Flutter objects.
P96-06/08/10 retain separate future artifact/schema decisions; P96-11 retains
final handoff and closure. All 54 original criteria remain, with 20 checked,
P96-04 IN_PROGRESS and its four criteria unchecked, P96-05..11 OPEN.

## Planned executable matrix — UNEXECUTED

The [example suite](../../../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecSpec.scala)
and [property suite](../../../../src/test/scala/org/goldenport/cncf/display/DisplayProtocolCodecPropertySpec.scala)
have visible E identities, afterWord spec/example/rules/phase metadata and
adjacent Given/When/Then with should expectations at each behavior boundary.

| Scenario | Authored behavior | Status |
| --- | --- | --- |
| E1 | Actual bootstrap/query/read, exact two goldens, trusted Option/wrapper/vector/definition identity and idle source/provider counts | UNEXECUTED |
| E2 | Independent five-kind scalar boundaries, UTF16, coefficient/scale, Instant limits/nanos and Long.MAX_VALUE revision | UNEXECUTED |
| E3 | Scoped reused IDs, ordered arrays, empty layouts and None/Some title/snapshot | UNEXECUTED |
| E4 | Separate protocol/ABI/all semantic and driver compatibility member changes | UNEXECUTED |
| E5 | Malformed/nonobject/duplicate JSON, every nested missing/unknown/null/wrong primitive member and null entry/API guard | UNEXECUTED |
| E6 | Source/definition/provenance/screen/tag rejection with unchanged authority objects | UNEXECUTED |
| E7 | Canonical resource rejection and unchanged original Display semantic diagnostics | UNEXECUTED |
| E8 | Reordered whitespace recanonicalization and noncanonical decimal/Instant rejection | UNEXECUTED |
| Property E1 | Direct valid generated typed snapshots with no discarded input | UNEXECUTED |
| Property E2 | Canonical idempotence and exact UTF16/coefficient/scale/order/absence representation | UNEXECUTED |
| Property E3 | Finite one-defect table, exact typed code/path and unchanged source/admission/model objects | UNEXECUTED |

Each property requires Test.check minimum100 successes, passed true,
succeeded >=100 and discarded ==0, with GWT both per generated case and around
the checker setup/action/expectations. Authoring claims no executed case count.

## Exact planned validation — UNEXECUTED

Repository: `/Users/asami/src/dev2025/cloud-native-component-framework-phase96`.
Representative logical argv is `--batch` followed by exactly:

```text
testOnly org.goldenport.cncf.display.DisplayProtocolCodecSpec
```

Accumulator logical argv is `--batch` followed by exactly:

```text
testOnly org.goldenport.cncf.display.DisplayProtocolCodecPropertySpec org.goldenport.cncf.component.ComponentFactoryGeneratedLogicalUiAcceptanceSpec org.goldenport.cncf.display.DisplayRuntimeSpec org.goldenport.cncf.component.ComponentFactoryDisplayRuntimeSpec org.goldenport.cncf.entity.view.ViewSpaceSpec org.goldenport.cncf.component.ComponentFactoryLogicalUiBootstrapSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.display.DisplayProjectionPropertySpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec org.goldenport.cncf.workflow.GeneratedWorkflowAbiSpec org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec org.goldenport.cncf.component.ComponentFactoryStateMachineProviderBootstrapSpec org.goldenport.cncf.component.ComponentFactoryContinuationRuntimeBootstrapSpec org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec
```

The parent selects two sequential registered serial runner requests with scoped
permissions and terminal SBT/wrapper exits plus released-lock evidence. Ordinary
testOnly covers Compile/Test in the existing Scala3 project; no extra compile,
full test, live HTTP/client, publication or generation command is selected here.
The implementation editor executes no validation, review, transition or commit.
Parent appends actual focused outcomes and independent acceptance later.

## Authoring continuity and debt

Revision1 stopped at the private[component] access edge after authoring only the
protocol spec. Revision2 retains that document, corrects its numeric rationale
without claiming platform parser failure, and freezes the direct Test-only
component-package bridge. The sole editor preserves all existing fixtures and
production runtime/Component visibility. A native patch could not initially
create the new golden parent directory; the parent created only that admitted
directory through normal scoped escalation before golden authoring continued.
This is a permission event, not a validation or product failure.

The five new Scala programs have Oct.4,2026 since/version headers and naming
selected at declaration time. No preexisting target-program debt exists.
Seven accepted Hygiene records remain OPEN; five absent literal blocks are
appended to the [Hygiene ledger](2026-10-03-phase-96-hygiene-follow-up.md),
retaining the two existing blocks verbatim. No new Development Candidate or
unrelated source repair is admitted. Parent owns exact projection verification.

## 2026-10-05 — P96-04 independent Step acceptance

The parent admits the actual 2026-10-04 focused executions: representative
8/8 tests in one suite and accumulator 143/143 tests in 17 suites, totaling
151/151 tests in 18 suites. Both executions completed with terminal SBT and
wrapper exit 0 and the serial lock released. The retained request/result pairs
are `phase96-p96-04a-representative-request-001@1` /
`phase96-p96-04a-representative-result-001@1` and
`phase96-p96-04a-accumulator-request-001@1` /
`phase96-p96-04a-accumulator-result-001@1`; log basenames are
`58572-20261004T142826Z.log` and `63335-20261004T143801Z.log`, respectively.
All three properties passed their executed >=100 successful and zero-discard
expectations; no separately printed exact case count is claimed.

Independent native reviewer `/root/phase96_p96_04_step_review_001` returned
PASS in `phase96-p96-04-step-review-001` revision 1 over all 15 owned paths,
all five complete Scala programs and all four P96-04 criteria. Parent admission
is recorded in `phase96-review-ledger-001` revision 25. The review reported no
blockers, Hygiene or Development Candidates; the seven previously accepted
Hygiene records remain OPEN and unchanged.

| Unchanged P96-04 criterion | Accepted evidence |
| --- | --- |
| 1: schema/version, metadata, REST binding and canonical encoding | Read protocol R1..R8 and design fix the strict physical binding and separate metadata dimensions; E1/E4/E8 and independent review accept the recorded contract. |
| 2: List/Detail goldens and property round trips | E1..E3/E8 and Property E1/E2 accept canonical actual-producer goldens and independent admitted typed-value, identity, scope, order and metadata preservation. |
| 3: structured strict rejection | E4..E7 and Property E3 accept incompatible versions, unknown kinds, malformed/missing members and duplicate/null rejection under the admitted policy. |
| 4: first presentation-client codec handoff after LUI-ACCEPT-01 | DSP-WIRE-01 records actual producer/source provenance, strict first List/Detail read snapshot subset, compatibility rules and the focused evidence above; later mutation/context artifacts retain P96-06/P96-10/P96-11 ownership. |

DSP-WIRE-01 is accepted only as the strict first List/Detail read snapshot codec
handoff. It does not establish live HTTP or registered Operation execution;
P96-05 retains DisplayService registration, canonical View delegation and live
read-boundary proof. Mutation, Business Operation, context and final aggregate
acceptance retain their later owners.

P96-04 is CLOSED as an acceptance candidate with local Step commit
PREPARED/PENDING; no commit hash is claimed. All 54 criterion texts are retained,
with 24 checked and 30 pending; current_entry=P96-05 and P96-05 through P96-11
remain OPEN. Phase 96 remains IN_PROGRESS with zero full Phase reviews.
Cozy Phase 76 final validation/review/release/closure and app Phase 5 aggregate/E2E
acceptance remain separate and pending. The original Goal, binding, base and
PLAN epoch 1 remain; Step ownership stays 15 paths, repair-cycle and
non-convergence counts remain zero, and the two admitted additions are retained.
