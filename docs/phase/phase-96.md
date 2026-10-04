# Phase 96: Display Model Projection and Protocol

status=in-progress
planned_at=2026-09-29
checklist=phase-96-checklist.md
development_plan=../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md
development_branch=codex/phase-96-display-service
execution_baseline=6cbe877c53c116639feb11fa80546e79c051804c
execution_note=../journal/2026/10/2026-10-03-phase-96-isolated-worktree.md
driver=KnowledgeHubProject/nict-editing-studio-app
driver_phase=KnowledgeHubProject/nict-editing-studio-app Phase 4
integration_acceptance_phase=KnowledgeHubProject/nict-editing-studio-app Phase 5
server_reference_phase=KnowledgeHubProject/nict-editing-studio Phase 2
abstract_ui_dependency=asami/cozy Phase 74 semantic authority; Cozy Phase 76 producer ABI
strategy=textus-knowledge-workbench/docs/strategy/knowledge-application-integration.md

## Goal

Add a presentation-facing runtime layer above CNCF semantic View Models: Display Projection, Display Model and a versioned Display Model Protocol suitable for generic presentation clients.

The existing CNCF View/Read Model remains the meaning-preserving semantic contract. Display Model is separate and must not replace or weaken semantic Views.

## Architecture

Semantic/Application Model
  -> CNCF View Model
       -> semantic consumers
       -> Display Projection
            -> Display Model
            -> Display Model Protocol
            -> presentation clients

Display Model instances conform to the target-neutral Abstract/Logical UI semantics established by Cozy Phase 74. CNCF owns the Scala 3 runtime ABI consumed by generated component code; Cozy Phase 76 generates that ABI usage through the ordinary CML producer path.

## Development-driver Phase relationship

The UI/prototype driver is
[nict-editing-studio-app Phase 4](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-4.md).
It owns UI-driven protocol/model proposals and fixture-backed client feasibility.
[App Phase 5](../../../../Project2026/nict-editing-studio-app/docs/phase/phase-5.md)
owns real-server integration and final aggregate/end-to-end acceptance. The
2026-10-02 scope decision supersedes the earlier Phase 4 integration gate.
[nict-editing-studio Phase 2](../../../../Project2026/nict-editing-studio/docs/phase/phase-2.md)
is the server-side development child: it supplies concrete application
DisplayModel content, semantic projection, admitted mutation contracts and
deterministic reference fixtures before the client is connected.

Phase 96 is a generic infrastructure dependency, not the owner of Editing
Studio domain semantics. Develop the minimum projection/protocol contract from
the concrete server fixtures; do not require the application to wait for all
Phase 96 scope before its first contract handoff. Cozy
[Phase 74, Abstract UI Runtime Contract](https://github.com/asami/cozy/blob/main/docs/phase/phase-74.md)
owns the shared target-neutral vocabulary. Flutter Core consumes the versioned
protocol/runtime fixtures, and TFAF owns visual realization.

Handoffs identify the protocol/vocabulary version, fixture provenance and
focused verification evidence. Scoped child/dependency handoffs do not require
full-test runs; app Phase 5 owns final real-server aggregate/E2E validation for this
integration line. Phase 96 retains its independent repository acceptance:
app acceptance does not imply closure of unrelated generic scope.

Use an existing admitted resource or an explicitly development-only fixture.
The real Workbench/KnowledgeHub admission gate in server Phase 1.2 is separate;
no production Knowledge Candidate/admission API is invented here to bypass it.
Preparation is recorded in the non-normative
[Phase 96 Display proposal](../notes/display-model-phase-96-proposal.md),
Editing Studio's
[ESS2-01 sample journal](../../../../Project2026/nict-editing-studio/docs/journal/2026/10/2026-10-01-display-model-phase-96-sample.md),
and the [preparation history](../journal/2026/10/2026-10-01-display-model-phase-96-preparation.md).
These records do not freeze the contract or close a Phase/stage.

The 2026-10-02 [UI-driven Presentation Context proposal](../notes/display-protocol-presentation-context-proposal.md)
adds the server-side handoff for presentation-relevant ExecutionContext
projection, generic context families and validated continuation. Core carries
context, TFAF uses supplied arm/variant identity to realize registered UI, and
the app remains unaware of experiment logic. This is proposed Phase 96 scope
for review against existing admission rules; no runtime or protocol is accepted.

## Development checklist

P96-01 source inventory is recorded in the non-normative
[contract inventory](../notes/display-model-phase-96-contract-inventory.md)
and [inventory journal](../journal/2026/10/2026-10-03-phase-96-contract-inventory.md).

The [Phase 96 checklist](phase-96-checklist.md) incorporates P96-01 through
P96-11 from the [development-plan proposal](../journal/2026/10/2026-10-03-phase-96-development-plan-proposal.md).
Internal checkpoints A through D group the Logical UI ABI/producer round trip,
protocol/read, mutation/Business Operation separation, and fixture/context/consumer closure.
The [ABI boundary proposal](../notes/logical-ui-generated-abi-display-runtime-boundary.md)
and [reconciliation record](../journal/2026/10/2026-10-03-phase-96-phase-76-plan-reconciliation.md)
record the 2026-10-03 GitHub correction. P96-01A remains historical inventory
work; its earlier documentary review does not accept the revised ABI plan.
P96-01 is CLOSED after independent documentary contract review and parent
admission, recorded in the [acceptance journal](../journal/2026/10/2026-10-03-phase-96-contract-promotion.md#p96-01-documentary-acceptance).
Its selected contracts are [runtime design](../design/display-model-runtime.md),
[generated ABI spec](../spec/logical-ui-generated-abi.md) and
[runtime spec](../spec/display-model-runtime.md). P96-02 is CLOSED on its
separately accepted pure ABI/model proof. P96-03 is CLOSED with all seven
unchanged obligations checked; P96-04 is CLOSED as an acceptance candidate,
the current checklist entry is P96-05, and P96-05 through P96-11 remain OPEN. A/B1/B2 focused
history and the issued LUI-ABI-01 remain retained. Accepted Cozy P76-04
LUI-GEN-01 delivery at `ccf2b8d7b89582c22f5f4a933b8354812a8ee2a3`
is consumed unchanged by the authored P96-03C specification. The
[consumer journal](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md)
retains the first-pass unexecuted commands as history and records their
parent-admitted current results: GC1..GC6/GCP1 passed 7/7 tests in one suite,
the original accumulator passed 133/133 in 15 suites, source comparisons
exited 0 and complete classpath inspection found no cozy_2.12 runtime jar.
Both commands retain terminal SBT/wrapper exit 0 and released serial lock.
Independent protected-focused whole-Step review `phase96-p96-03-step-review-001`
revision 1 by `/root/phase96_p96_03_step_review_001` passed all 28 paths and
19 programs, with zero blockers and nonblocking
HYG-P96-03-SPEC-TRACEABILITY-001 retained. Parent admission in
`phase96-review-ledger-001` revision 19 accepts all seven criteria, including
criterion 7's LUI-ACCEPT-01 focused consumer proof, as recorded in the
[acceptance entry](../journal/2026/10/2026-10-04-phase-96-generated-consumer-acceptance.md#2026-10-04--p96-03-independent-step-acceptance).
Local P96-03 Step commit is `bef59e7`; the separate Cozy P76-05 reciprocal
receipt is accepted at `3546755`, and P76-06A documentary handoff at `ddeadab9`.
Phase96 remains IN_PROGRESS
with zero full Phase reviews; no full-suite run, release or Phase closure is claimed.

P96-04A-WIRE implements the [strict read protocol](../spec/display-model-protocol.md)
and its [design](../design/display-model-protocol.md). The
[protocol acceptance entry](../journal/2026/10/2026-10-04-phase-96-protocol-contract.md#2026-10-05--p96-04-independent-step-acceptance)
records the actual October 4 representative 8/8 and accumulator 143/143 results:
151/151 focused tests in 18 suites, both terminal SBT/wrapper exit 0 and serial
lock released. All three properties passed their >=100 successful/zero-discard
expectations, without a separately printed exact case count claim.
Independent whole-Step review `phase96-p96-04-step-review-001` revision 1 by
`/root/phase96_p96_04_step_review_001` passed all 15 paths, all five complete
Scala programs and all four criteria; parent admission in
`phase96-review-ledger-001` revision 25 accepts the four unchanged obligations.
The journal retains planned UNEXECUTED authoring history and accepts DSP-WIRE-01
only as the strict first List/Detail read snapshot codec handoff; registered
DisplayService Operations and live HTTP proof retain P96-05 ownership.
P96-04 is CLOSED as an acceptance candidate with local Step commit
PREPARED/PENDING. The unchanged 54-criterion ledger has 24 checked and 30 pending
outcomes, with current_entry=P96-05. P96-05 through P96-11 remain OPEN;
Phase 96 remains IN_PROGRESS with zero full Phase reviews. Later service,
mutation, Business Operation, context and aggregate acceptance, separate Cozy
Phase 76 final release/closure and app Phase 5 aggregate/E2E acceptance remain
pending. The seven prior Hygiene records remain OPEN.

## Initial scope

Implement the minimum infrastructure required for the Editing Studio List -> Detail -> standard Mutation vertical slice.

Phase 96 also defines the consumer-side Logical UI generated ABI. Cozy itself remains Scala 2.12 and is not a runtime binary dependency of Scala 3 CNCF. As with other CML model elements, Cozy emits Scala 3 source/metadata against CNCF-owned public ABI types and ComponentFactory discovery/bootstrap seams.

Phase 96 also establishes the component-scoped `DisplayService` as the UI-facing public service. Aggregate and View runtime access remain internal for this phase; ordinary Business Operations remain independently published through the existing CNCF Operation/REST mechanism.

- CNCF-owned Scala 3 Logical UI generated ABI types/metadata corresponding to the admitted Cozy Phase 74 semantic subset;
- generated metadata/provider discovery through the existing ComponentFactory pattern, with admission/validation before runtime use;
- a hand-written CNCF consumer fixture that freezes the exact Scala 3 source shape Cozy Phase 76 must generate;
- Display Model identity/version metadata;
- Display Projection SPI/API from semantic View Models;
- CNCF List and Detail Display Model instances conforming to the admitted Cozy Phase 74 semantics;
- Section/Field/displayable Value representation;
- presentation roles such as title/subtitle/status where admitted by the Abstract UI contract;
- component-scoped `DisplayService` access Operations for Display Object read/query and standard mutation;
- standard Display Mutation contracts for create/update/delete, including identity/revision and editable-field/constraint metadata;
- deterministic mapping of standard Display Mutation to Entity/Aggregate resource mutation;
- standard mutation result/projection suitable for List -> Detail -> Editor -> Save/Delete UI patterns;
- Business Operation descriptors may be presented as actions, but Business Operations are invoked directly through their existing REST Operation interface rather than tunneled through DisplayService;
- deterministic protocol codec/representation;
- diagnostics for unsupported/incompatible projection or protocol versions; and
- focused fixture/proof suitable for later textus-flutter-core consumption.

## Projection rules

Display Projection is semantic-to-presentation projection, not DTO renaming only.

It may:

- map semantic properties to presentation roles, e.g. product_name -> title;
- combine or reshape semantic properties;
- project one semantic value into multiple display fields where needed;
- convert JSON and other server-native/opaque values into displayable typed values;
- select labels, ordering and presentation metadata; and
- expose available actions through abstract Action descriptors.

The Display Model boundary must not require a presentation client to know domain/application meaning.

## Protocol boundary

The protocol carries abstract presentation semantics, values, actions, identity and compatibility metadata. It must not carry Flutter Widget trees or make CNCF depend on Flutter/TFAF.

Semantic clients may continue to consume CNCF View Models independently.

## Producer/consumer ABI boundary

Use the same mechanism as existing CML-generated StateMachine/Workflow contracts:

```text
CML / Cozy semantic model
  -> Cozy producer projection/code generation (Scala 2.12 implementation)
  -> generated Scala 3 source + bounded metadata/provider surface
  -> CNCF public Logical UI ABI
  -> ComponentFactory discovery/admission
  -> component Logical UI definitions
  -> Display Projection + semantic View
  -> Display Model instance
```

CNCF must not import `cozy_2.12`, reflect Cozy runtime classes, copy Cozy runtime objects as a second authority, or introduce a special cross-built Logical UI library solely for this integration. Cozy Phase 74 remains semantic authority/reference; CNCF Phase 96 owns runtime consumer types and admission; Cozy Phase 76 owns producer mapping and source generation.

The consumer ABI should follow existing generated-ABI conventions: closed/versioned definition values, deterministic order, stable identity/provenance, provider-neutral metadata, ComponentFactory discovery, fail-closed admission, and an actual generated producer fixture pinned to fixed development coordinates for cross-repository acceptance.

## Cross-Phase development handoffs

These are intermediate development handoffs, not a requirement to close one
whole Phase before starting the other. Exact artifacts and evidence are
tracked by the [checklist](phase-96-checklist.md) and the
[ABI proposal](../notes/logical-ui-generated-abi-display-runtime-boundary.md).

| Handoff | Producer -> consumer | Required result |
| --- | --- | --- |
| LUI-ABI-01 | CNCF P96-01..03 -> Cozy Phase 76 | Versioned ABI, provider/admission contract, hand-written expected-generated Scala 3 fixture and focused List/Detail projection proof |
| LUI-GEN-01 | Cozy Phase 76 -> CNCF P96-03 | Actual generated definitions/provider, fixed producer/consumer revisions and deterministic generation evidence |
| LUI-ACCEPT-01 | CNCF P96-03 -> both Phases | Scala 3 compilation, ComponentFactory admission and equivalent List/Detail projection for that generated fixture |

After LUI-ACCEPT-01, CNCF continues P96-04 protocol and the remaining
read/mutation/context work. Cozy can close its producer scope once its own
checklist and validation are complete; it does not wait for CNCF's later REST,
mutation or app E2E work. Each Phase retains its own closure requirements.
The same focused acceptance evidence can be referenced by both ledgers.
A development handoff identifies usable source/artifact coordinates; it does
not require a public release or full-test run at every handoff.

## Development proof

Use the concrete resource fixture supplied by Editing Studio Phase 2 to prove
the following. A Knowledge Candidate example remains explicitly development-
only until its production contract is admitted:

1. a hand-written/generated-shape Logical UI ABI fixture -> ComponentFactory admission/discovery;
2. semantic List View + admitted Logical UI definition -> List Display Model;
3. semantic Detail View + admitted Logical UI definition -> Detail Display Model;
4. semantic property -> presentation-role mapping;
5. opaque/server-native value -> displayable value conversion;
6. Detail Display Object -> standard update mutation -> Entity/Aggregate update -> refreshed Display Object;
7. create/delete metadata sufficient for standard client interaction patterns;
8. one Business Operation action descriptor whose execution target remains the ordinary REST Operation API;
9. deterministic encode/decode fixture for a future Flutter Core client; and
10. a Cozy Phase 76 generated producer fixture accepted through the same CNCF ABI with no Cozy runtime binary dependency.

The later integration acceptance is replacement of the Android mock source with a CNCF Display Model source without rewriting standard List/Detail UI.

## Acceptance criteria

Phase 96 completes when:

- semantic View Model, Logical UI definition and Display Model are distinct public concepts;
- CNCF exposes the versioned Scala 3 Logical UI generated ABI and ComponentFactory admission/discovery route;
- a real Cozy Phase 76 generated producer fixture compiles against CNCF, is admitted through ComponentFactory and yields the same List/Detail projection as the hand-written fixture, without CNCF depending on `cozy_2.12`;
- Display Projection produces Cozy Phase 74-conformant runtime models;
- the minimum List/Detail/Value/Action protocol is versioned and deterministic;
- no Flutter-specific Widget semantics enter CNCF;
- semantic View consumers remain unaffected;
- the reference fixture proves List, Detail and standard Display Mutation;
- DisplayService is component-scoped and exposes only the Display access/mutation boundary;
- Aggregate/View runtime access remains internal in this phase;
- Business Operations remain direct CNCF Operations and are not wrapped by DisplayService;
- the initial post-Business-Operation consistency rule is client-initiated Display reload; and
- the protocol is sufficiently frozen for textus-flutter-core client/runtime implementation.

## Initial synchronization rule

Standard Display Mutation may return/reproject the resulting Display Object because the mutation is part of the Display contract.

Business Operations are different. A presentation client invokes the existing Business Operation REST endpoint directly. After success, the initial protocol requires the client to reload the affected Display Object/View through DisplayService. Push invalidation, subscriptions and Service Bus-driven refresh are future extensions, not Phase 96 requirements.

## Standard UI implication

Display Mutation metadata is intentionally sufficient for a generic presentation runtime to realize standard interaction patterns from configuration: List -> Create Editor -> Detail, Detail -> Edit -> Save -> Detail, and Detail -> Delete confirmation -> List. CNCF defines target-neutral semantics and validation; TFAF owns Flutter visual realization.

## Non-goals

- Flutter client implementation.
- TFAF visual design.
- Complete abstract UI vocabulary.
- Replacing CNCF View/Read Model.
- Application-specific Editing Studio UI implementation.
- Encoding a target Widget tree.

## References

- docs/journal/2026/09/2026-09-29-display-model-protocol-direction.md
- docs/journal/2026/09/2026-09-27-operation-client-ui-continuity.md
- asami/cozy Phase 74 semantic authority
- asami/cozy Phase 76 Logical UI producer ABI/code generation

## Online Experiment presentation handoff

Phase 96 does not own Experiment assignment, but the Display Model Protocol must be able to carry a server-resolved presentation variant for CNCF Phase 98 online experiments.

CNCF assigns the subject to an Experiment Arm. DisplayService/Display Projection exposes only the client-safe Experiment presentation context required to render the assigned UI variant, together with a Display Instance/correlation reference. The presentation client must not perform random assignment or choose another Arm.

The client may use the assigned Arm/variant to select between target-specific UI realizations that cannot be expressed solely by target-neutral Display Model fields. Detailed Experiment/Run/assignment state remains server-side where possible.

Standard Display Mutation must preserve the Display Instance/correlation reference. CNCF restores the authoritative Experiment/Run/Arm assignment from server-side context and records mutation lifecycle/outcome against that Arm. The client-supplied Arm value is not the authority for later mutation or Business Operation evaluation.

This permits one Experiment assignment to correlate:
Display variant -> user interaction -> Display Mutation -> Entity/Aggregate mutation -> Business Operation -> optional AI Interaction -> downstream business outcome.

TFAF and other clients render the assigned variant; CNCF owns assignment, correlation, observation and subsequent routing.

### Multi-arm presentation semantics

Display Model does not assume binary A/B experiments. The client-safe presentation variant identifies the server-assigned Arm from an N-arm Experiment. A/B UI testing is simply the two-arm case. Allocation policy, including future adaptive allocation, remains server-side; the presentation client only renders the assigned variant.
