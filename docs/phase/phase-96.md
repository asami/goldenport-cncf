# Phase 96: Display Model Projection and Protocol

status=planned
planned_at=2026-09-29
driver=KnowledgeHubProject/nict-editing-studio-app
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

## Initial scope

Implement the minimum infrastructure required for the Editing Studio List -> Detail -> standard Mutation vertical slice.

Phase 96 also defines the consumer-side Logical UI generated ABI. Cozy itself remains Scala 2.12 and is not a runtime binary dependency of Scala 3 CNCF. As with other CML model elements, Cozy emits Scala 3 source/metadata against CNCF-owned public ABI types and ComponentFactory discovery/bootstrap seams.

Phase 96 also establishes the component-scoped `DisplayService` as the UI-facing public service. Aggregate and View runtime access remain internal for this phase; ordinary Business Operations remain independently published through the existing CNCF Operation/REST mechanism.

- CNCF-owned Scala 3 Logical UI generated ABI types/metadata corresponding to the admitted Cozy Phase 74 semantic subset;
- generated metadata/provider discovery through the existing ComponentFactory pattern, with admission/validation before runtime use;
- a hand-written CNCF consumer fixture that freezes the exact Scala 3 source shape Cozy Phase 76 must generate;
- Display Model identity/version metadata;
- Display Projection SPI/API from semantic View Models;
- List and Detail Display Model shapes based on the Cozy runtime contract;
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

The consumer ABI should follow existing generated-ABI conventions: closed/versioned definition values, deterministic order, stable identity/provenance, provider-neutral metadata, ComponentFactory discovery, fail-closed admission, and a released producer fixture for cross-repository acceptance.

## Development proof

Use an Editing Studio/Knowledge Candidate fixture to prove:

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
- Cozy Phase 76 can generate a conformant producer fixture without CNCF depending on `cozy_2.12`;
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
