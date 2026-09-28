# Phase 96: Display Model Projection and Protocol

status=planned
planned_at=2026-09-29
driver=KnowledgeHubProject/nict-editing-studio-app
abstract_ui_dependency=asami/cozy Phase 74
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

Display Model instances conform to the target-neutral Abstract/Logical UI runtime contract owned by Cozy Phase 74.

## Initial scope

Implement the minimum infrastructure required for the Editing Studio List -> Detail -> one Action vertical slice:

- Display Model identity/version metadata;
- Display Projection SPI/API from semantic View Models;
- List and Detail Display Model shapes based on the Cozy runtime contract;
- Section/Field/displayable Value representation;
- presentation roles such as title/subtitle/status where admitted by the Abstract UI contract;
- Action descriptor binding to CNCF Operation/Command semantics;
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

## Development proof

Use an Editing Studio/Knowledge Candidate fixture to prove:

1. semantic List View -> List Display Model;
2. semantic Detail View -> Detail Display Model;
3. semantic property -> presentation-role mapping;
4. opaque/server-native value -> displayable value conversion;
5. one Action descriptor -> CNCF Operation/Command binding; and
6. deterministic encode/decode fixture for a future Flutter Core client.

The later integration acceptance is replacement of the Android mock source with a CNCF Display Model source without rewriting standard List/Detail UI.

## Acceptance criteria

Phase 96 completes when:

- semantic View Model and Display Model are distinct public concepts;
- Display Projection produces Cozy Phase 74-conformant runtime models;
- the minimum List/Detail/Value/Action protocol is versioned and deterministic;
- no Flutter-specific Widget semantics enter CNCF;
- semantic View consumers remain unaffected;
- the reference fixture proves List, Detail and one Action binding; and
- the protocol is sufficiently frozen for textus-flutter-core client/runtime implementation.

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
- asami/cozy Phase 74
