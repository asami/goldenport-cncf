# 2026-10-04 — Phase 96 Logical UI ABI handoff

Document role: non-normative journal and sample/evidence handoff material.
Scope: PHASE-96 / P96-03 / P96-03B3, PLAN epoch 1.
Handoff: LUI-ABI-01, CNCF consumer input prepared for Cozy producer consumption.

## Status and ownership

Current status: LUI-ABI-01 is issued, LUI-GEN-01 producer delivery is accepted,
and P96-03 is independently accepted with all seven criteria CLOSED.
LUI-ACCEPT-01 focused consumer proof is accepted. Local P96-03 Step commit is
PREPARED/PENDING, not executed; the separately owned Cozy P76-05 reciprocal
receipt remains pending. This remains a local working-tree handoff with the
disclosed source accumulator; no released/published artifact or final consumer
commit is claimed. Earlier chronological sections below retain their original
prepared, failed-attempt and pending states. The final dated acceptance entry
records the current review/admission evidence.

The consumer checkout is branch `codex/phase-96-display-service`. Its accepted
HEAD baseline is `fed26152045b1326e87de8a6929dc84e2fc05b18`; original Phase
inventory base is `6cbe877c53c116639feb11fa80546e79c051804c`. Consumption
requires the explicitly uncommitted P96-03 source/spec accumulator in that
checkout, including A static bootstrap, B1 projection and B2 contextual
bindings/publication. HEAD alone does not contain B2. The
[build declaration](../../../../build.sbt) gives
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT` and Scala `3.3.8`. These are
source-build coordinates, not proof of a corresponding published binary.

CNCF owns consumer ABI, admission and runtime projection; Cozy owns semantic
modeling and deterministic generation. The locally observed
[Cozy Phase 76 checklist](../../../../../cozy-phase76/docs/phase/phase-76-checklist.md)
is planned, with P76-01..06 open, in branch
`codex/phase-76-logical-ui-producer` at
`c6b108b8f301d4b1928e3f7f1b43d3e17f8e3739`. This observation concerns that
local checkout, not a fresh GitHub assertion or every other checkout. Its
dirty planning files remain producer-owned. This journal starts no Cozy
implementation and changes no normative contract.

## Public consumer surface

The canonical [Logical UI generated ABI specification](../../../spec/logical-ui-generated-abi.md)
and [GeneratedLogicalUiAbi source](../../../../src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiAbi.scala)
define package `org.goldenport.cncf.display`, identity
`cncf.logical-ui-generated.v1`, integer version `1`. The auxiliary
[GeneratedLogicalUiMetadataProvider](../../../../src/main/scala/org/goldenport/cncf/display/GeneratedLogicalUiMetadataProvider.scala)
exposes:

```scala
def generatedLogicalUiDefinitions: Vector[GeneratedLogicalUiAbi.Definition]
```

The exact `SemanticContract` in the specification and
[expected-generated fixture](../../../../src/test/scala/logicalui/ExpectedGeneratedLogicalUiFixture.scala)
is:

| Member | Value |
| --- | --- |
| `schema` | `cozy.logical-ui-runtime.v1` |
| `version` | `1` |
| `semanticAuthoritySchema` | `cozy.logical-ui-semantics.v1` |
| `assurance` | `provisional-scenario` |
| `driver.repository` | `KnowledgeHubProject/nict-editing-studio-app` |
| `driver.revision` | `48eb43b8eb839137e17ef4c944b3db4a73d0cec3` |
| `driver.path` | `docs/phase/phase-2.md` |
| `driver.scenarioId` | `resource-list-detail` |

That historical driver provenance does not relabel current app Phase 5
integration or Phase 4 prototype ownership. ABI version, semantic descriptor,
definition revision, source provenance revision and runtime resource revision
remain separate spaces.

The linked [S02/S03 vocabulary](../../../spec/logical-ui-generated-abi.md#lui96-s02-static-definition-shape)
has only List/Detail metadata; roles are Content, Title, Subtitle, Description
and Status, and kinds are Text, Number, Boolean, Timestamp and Missing.
Date/choice/reference/structured kinds and extensible alternatives are outside
v1. IDs, bindings, definition revisions and provenance require
`value != null && value.nonEmpty && value == value.trim`; titles and labels,
including optional section titles, require
`value != null && value.trim.nonEmpty`. Supplied bytes and order are preserved.

[S04 scoped uniqueness](../../../spec/logical-ui-generated-abi.md#lui96-s04-scoped-uniqueness-and-references)
covers screens per selected vector, sections per Detail, fields per container,
actions per action scope and non-content roles per field container. List
targets resolve to Detail within the same vector. The
[S05 admission gate](../../../spec/logical-ui-generated-abi.md#lui96-s05-admission-and-deterministic-structured-diagnostics)
returns immutable admitted definitions or its closed structured diagnostics:
top vector shape/empty first, each definition in supplied order, duplicate
screens, then List target integrity. Raw definitions are untrusted; no prefix,
repair or normalization is admitted.

[ComponentFactoryDisplayPart](../../../../src/main/scala/org/goldenport/cncf/component/ComponentFactoryDisplayPart.scala)
implements [S06/S07](../../../spec/logical-ui-generated-abi.md#lui96-s06-provider-selection):
component provider first, factory only if absent, no fallback from an invalid
selected provider, complete static admission and a later atomic typed join.
The [contextual runtime contract](../../../spec/display-model-runtime.md#p96-03b2-component-scoped-contextual-typed-browser-binding)
and [runtime design](../../../design/display-model-runtime.md#p96-03b2-contextual-capability-closure-and-late-publication)
govern the post-View join and final publication. These statements describe
their linked authority; this journal introduces no behavior.

## Hand-written expected-generated sample map

[ExpectedGeneratedLogicalUiFixture](../../../../src/test/scala/logicalui/ExpectedGeneratedLogicalUiFixture.scala)
is HAND-WRITTEN, not Cozy output. Both definitions use revision
`ess-definition-1` and View key `ess.reference`. List `collecting-resources`
has title `Collecting Resources` and targets Detail `capture-detail`, whose
title is `Capture Detail`. Its section `main` has no section title. Order is:

| Container | Field ID | Label | Role | Value binding | Allowed kinds |
| --- | --- | --- | --- | --- | --- |
| List items (1) | `title` | `Title` | Title | `ess.title` | Text only |
| List items (2) | `stage` | `Capture stage` | Status | `ess.stage` | Text only |
| Detail `main` (1) | `title` | `Title` | Title | `ess.title` | Text only |
| Detail `main` (2) | `stage` | `Capture stage` | Status | `ess.stage` | Text only |
| Detail `main` (3) | `description` | `Description` | Description | `ess.description` | Text only |

List screen and item actions are empty. The sole Detail action is
`request-review` / `Request Review` / `ess.request-review`. It is inert metadata;
runtime availability does not authorize Business Operation execution.

The fixture's `SourceProvenance` is
`cncf.expected-generated-fixture` / `ess2-01` / `fixture-1`. This is fixture
provenance, not actual producer provenance. The producer supplies its own
valid opaque provenance; ABI admission does not pin its revision or require
these fixture strings. Actual receipts separately identify proof revisions.

The original [ESS2-01 mapping](2026-10-03-phase-96-generated-abi-contract.md#ess2-01-development-fixture-mapping)
and [unchanged development sample](../../../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md)
retain inverse-write responsibility: title and description require explicit
application/domain Entity/Aggregate updates with byte-preserving text
conversion; title must be nonblank, description may be empty. Stage derives
from workflowState and is readonly. No setter or inverse mapping is inferred.
Business `request-review` is directly invoked through ordinary Operations
with its own authorization/revision boundary, followed by explicit reload.

Generated definitions contain no runtime values, resource revisions,
capability decisions, callbacks, routes, widgets or editor constraint maps.
The [typed projection fixture](../../../../src/test/scala/logicalui/TypedDisplayProjectionFixture.scala)
and [typed runtime fixture](../../../../src/test/scala/logicalui/TypedDisplayRuntimeFixture.scala)
are consumer-side application adapters, not generator emission requirements.
Their Capture and Badge types do not establish production Entity contracts.
ESS2-01 remains development-only, with unsupported create/delete; neither
this sample nor the focused proof establishes positive CRUD acceptance.

## Executed focused evidence

The actual passed receipt is command request
`phase96-p96-03b2-command-request-005` revision `1`, result
`phase96-p96-03b2-command-result-005` revision `1`, for validation subject
`phase96-p96-03b2-validation-subject-001` revision `5`. The exact logical SBT
argv was `--batch` followed by this single command:

```text
testOnly org.goldenport.cncf.display.DisplayRuntimeSpec org.goldenport.cncf.component.ComponentFactoryDisplayRuntimeSpec org.goldenport.cncf.entity.view.ViewSpaceSpec org.goldenport.cncf.component.ComponentFactoryLogicalUiBootstrapSpec org.goldenport.cncf.display.DisplayProjectionSpec org.goldenport.cncf.display.DisplayProjectionPropertySpec org.goldenport.cncf.display.GeneratedLogicalUiAbiSpec org.goldenport.cncf.display.GeneratedLogicalUiAbiPropertySpec org.goldenport.cncf.display.DisplayModelSpec org.goldenport.cncf.display.DisplayModelPropertySpec org.goldenport.cncf.workflow.GeneratedWorkflowAbiSpec org.goldenport.cncf.component.ComponentFactoryAggregateViewBootstrapSpec org.goldenport.cncf.component.ComponentFactoryStateMachineProviderBootstrapSpec org.goldenport.cncf.component.ComponentFactoryContinuationRuntimeBootstrapSpec org.goldenport.cncf.component.ComponentFactoryRuntimePlanActivationSpec
```

Result: 133/133 tests passed, 15 suites completed, 0 aborted; SBT exit `0`,
wrapper exit `0`, serial lock released. The log reports total time `71 s`
and completion `2026-10-04 01:39:31 JST`. Compilation was this command's
prerequisite, not a separately claimed run.

Operational evidence locations:

- Result: `/private/tmp/skill.cncf.d/a-9106b454-1048-4145-8839-d7cfaab37714/command-result.json`.
- Log: `/private/tmp/skill.cncf.d/a-1827cead-29b7-4d38-8efa-91761478bb5f/1234-20261003T163817Z.log`.
- Summary: `/private/tmp/skill.cncf.d/a-1827cead-29b7-4d38-8efa-91761478bb5f/1234-20261003T163817Z.summary.json`.

Coverage includes external public ABI fixture compilation and admission,
normal factory provider selection and late atomic join, typed projection,
contextual execution and the retained Workflow/Aggregate/View/StateMachine/
Continuation/runtime-plan regression accumulator. The
[A authoring journal](2026-10-03-phase-96-logical-ui-bootstrap.md),
[B1 history](2026-10-03-phase-96-typed-display-projection.md#historical-parent-focused-validation-result)
and [B2 additional-repair history](2026-10-03-phase-96-contextual-display-runtime.md#2026-10-04--additional-repair-and-focused-validation)
retain the preceding authoring and failed-attempt history. This evidence
establishes no repository full test, producer round-trip, independent Step
review or Step commit.

## Remaining three-way handoff

Under [S08](../../../spec/logical-ui-generated-abi.md#lui96-s08-producer-evidence-and-executable-specification-ownership)
and the [design handoff table](../../../design/display-model-runtime.md#producerconsumer-handoffs),
LUI-ABI-01 is this CNCF-to-Cozy intermediate input and evidence. It does not
prove Cozy consumption or generation.

LUI-GEN-01 must return actual generated Scala 3 definitions/provider, input
fixture and exact generation command, producer and consumer revisions with
any working-tree delta disclosed, target ABI identity and deterministic
generation proof. The hand-written fixture cannot substitute for that proof.

CNCF P96-03C must consume those same generated files through compilation,
normal ComponentFactory admission and typed projection, including invalid
cases and reference List/Detail equivalence of values, identities and order,
without a runtime Cozy Scala 2.12 dependency. LUI-ACCEPT-01 then returns that
actual acceptance to Cozy P76-05 and the corresponding ledgers.

No intermediate full test, public release or full Phase review is added by
this handoff. Full P96-03 acceptance remains the already selected fresh
protected conformance review after C and all receipts. Later wire, service,
mutation, positive create/delete, context and separately owned app acceptance
remain governed by their existing contracts and owners.

## 2026-10-04 — Accepted producer return and authored actual consumer

The preceding entries preserve the original B3 handoff state. LUI-ABI-01 was
issued and consumed by Cozy P76-01..04. Parent handoff
`phase96-cozy76-p76-04-acceptance-handoff-001` r1 records accepted actual
producer delivery and local Step commit
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, following generation source HEAD
`8a4c1794b7ad4557af5b4260d1233acd07520c6e`. Correct producer coordinate is
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, Scala `2.12.18`; consumer stays
`org.goldenport:goldenport-cncf_3:0.5.3-SNAPSHOT`, Scala `3.3.8`. Original
review FINDINGS and the accepted coordinate M0 amendment remain historical;
the result is not relabeled. Two ordinary CLI runs, deterministic two-file
delivery and the 254/254 six-suite producer result are accepted producer facts,
not CNCF consumer execution. The unresolved second-run Logback observation
retains its producer-journal status without a causal repair claim.

P76-01..04 are CLOSED and P76-05/06 OPEN. CNCF P96-01/02 remain CLOSED;
P96-03 is IN_PROGRESS with seven unchecked obligations and P96-04..11 OPEN.
The [actual consumer journal](2026-10-04-phase-96-generated-consumer-acceptance.md)
maps committed producer resources to the two unmodified Scala Test sources
and records authored GC1..GC6/GCP1 plus the exact planned, unexecuted commands.
Its external application fixture uses the existing typed Capture/Browser
bindings and normal ComponentFactory.bootstrapC exclusively for publication.
No Cozy/library/main checkout is changed by this consumer work.

LUI-GEN-01 producer delivery is accepted. LUI-ACCEPT-01 remains pending
actual consumer compilation, contextual bootstrap/model proof and classpath
inspection, then fresh independent whole-Step acceptance and exact local
P96-03 commit. The consumer HEAD is still
`fed26152045b1326e87de8a6929dc84e2fc05b18` plus the explicitly uncommitted
28-path A/B1/B2/B3/C accumulator; HEAD alone does not identify the proof inputs.
Cozy P76-05 reciprocal acceptance and both Phase release/closure gates remain
separate. This first documentary pass records no new consumer command result.

## 2026-10-04 — Actual consumer proof candidate after focused execution

The preceding first-pass pending/unexecuted entries remain chronological
history. Current parent-admitted representative request/result
`phase96-p96-03c-representative-request-001` r1 /
`phase96-p96-03c-representative-result-001` r1 passed GC1..GC6/GCP1, 7/7 tests
in one completed suite, with successful Compile/Test. GCP1 passed executed
expectations of at least 100 successful cases and zero discards; a separately
printed exact property-case count is not claimed. Current accumulator
request/result `phase96-p96-03c-accumulator-request-001` r1 /
`phase96-p96-03c-accumulator-result-001` r1 passed 133/133 tests across the
unchanged 15 completed suites. Both commands have zero aborted/failed/canceled/
ignored/pending results, terminal SBT/wrapper exit 0, released serial locks and
consumed typed permissions, admitted against current validation subject
`phase96-p96-03c-validation-subject-001` r1. The
[actual consumer evidence](2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--actual-focused-execution-and-sourceclasspath-inspection)
records exact intakes, logs, summaries and the unchanged argv.

Parent inspected complete Test/fullClasspath Attributed stdout lines 14–136:
the ordinary Scala3.3.8 CNCF Test configuration has no cozy_2.12 runtime jar.
Both selected producer-delivery/source comparison commands exited 0; the
generated definitions/provider remain unchanged with no added headers.
Ordinary Compile/Test executed CNCF's pre-existing Information CML generator
hook under target/cncf-information-cml. No Phase76 ESS2-01 Logical UI producer
was explicitly rerun, and no publication or dependency refresh occurred.
Current focused total is 140/140 tests across 16 suites, not a full-suite run.

These results prepare LUI-ACCEPT-01 proof candidate against accepted producer
`ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`, coordinate
`org.simplemodeling:cozy_2.12:0.3.3-SNAPSHOT`, and consumer HEAD
`fed26152045b1326e87de8a6929dc84e2fc05b18` plus the explicitly uncommitted
28-path accumulator inventoried in the consumer journal. Original Cozy/library
heads remain clean and unchanged per the parent's current context. The
editor changes no Scala file or private execution/review record.

P96-03 is focused-validated awaiting independent protected-focused review of
the whole 28-path/19-program Step; all seven criteria remain unchecked.
LUI-ACCEPT-01 final consumer acceptance/local commit and separately owned
Cozy P76-05 receipt remain pending. Phase96 remains IN_PROGRESS, P96-01/02
CLOSED and P96-04..11 OPEN. No independent acceptance, final consumer commit,
Phase full review, release or either Phase closure is claimed.

## 2026-10-04 — P96-03 independent Step acceptance and shared consumer proof

Independent protected-focused whole-Step review
`phase96-p96-03-step-review-001` revision 1 by
`/root/phase96_p96_03_step_review_001` returned PASS for all 28 paths and
19 programs, with zero blockers. Parent admission in `phase96-review-ledger-001`
revision 19 accepts all seven P96-03 criteria. The
[consumer acceptance entry](2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance)
records the actual representative and accumulator request/result names and
logs, 140/140 focused tests across 16 suites, successful Scala 3.3.8 compilation,
unchanged generated sources and complete classpath inspection without a
cozy_2.12 runtime jar. The original PASS and nonblocking
HYG-P96-03-SPEC-TRACEABILITY-001 remain retained without cleanup.

LUI-ABI-01 is issued and LUI-GEN-01 is accepted at the unchanged producer
commit `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`. LUI-ACCEPT-01 is accepted
focused consumer proof, including criterion 7's generated-source/fixture
correspondence. Local P96-03 Step commit is PREPARED/PENDING, not executed;
Cozy P76-05 reciprocal receipt remains separately pending. Consumer HEAD
`fed26152045b1326e87de8a6929dc84e2fc05b18` still requires the disclosed
28-path uncommitted accumulator; no final consumer commit reference is invented.

P96-01/02/03 are CLOSED, current_entry is P96-04 and P96-04..11 remain OPEN.
Phase96 remains IN_PROGRESS with zero full Phase reviews. Cozy P76-05/06,
final reciprocal/full validation, releases and both Phase closures retain their
existing owners and pending status. All preceding chronological sections,
including failures and earlier pending facts, remain historical; this entry
changes no ABI definition, fixture, provenance, source or coordinate contract.
