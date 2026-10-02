# Phase 96 Development Plan Proposal

Date: 2026-10-03
Status: proposed implementation plan
Target: Phase 96 — Display Model Projection and Protocol

## Objective

Implement the smallest CNCF server-side Display Model vertical slice that can replace the Editing Studio application's fake List/Detail data source without coupling CNCF to Flutter/TFAF.

The implementation should freeze a target-neutral protocol around semantic View -> Display Projection -> Display Model -> DisplayService, including standard Display Mutation, while preserving direct CNCF Business Operation invocation.

## Planning principles

1. Preserve the semantic View Model as the meaning-bearing source. Display Model is a presentation projection, not a replacement read model.
2. Implement a narrow List -> Detail -> Edit/Save/Delete slice before expanding the abstract UI vocabulary.
3. Reuse existing CNCF Operation, Entity/Aggregate mutation, authorization, revision and validation mechanisms. Do not build a parallel mutation engine.
4. Keep Flutter/TFAF types out of CNCF. Cozy Phase 74 is the abstract/logical UI contract dependency.
5. Make the protocol deterministic and fixture-driven so textus-flutter-core/TFAF can consume a frozen contract.
6. Treat Business Operations separately from standard Display Mutation: describe them as actions, invoke them through the existing Operation/REST boundary, then reload Display state.
7. Keep Phase 98 Experiment integration as a compatible handoff surface, not a prerequisite for the core Phase 96 vertical slice.

## Proposed implementation sequence

### P96-01 — Contract inventory and Phase 74 alignment

Before implementation, freeze the exact Cozy Phase 74 runtime types/semantics Phase 96 consumes.

Produce an inventory mapping:

- semantic CNCF View/List/Detail concepts;
- Cozy List/Detail/Section/Field/Value/Action concepts;
- identities/version metadata;
- editable/constraint metadata required by standard mutation;
- intentionally unsupported Phase 74 concepts.

Exit condition: there is one documented minimum Display Model vocabulary and no unresolved ownership ambiguity between CNCF and Cozy.

### P96-02 — Core Display Model value model

Introduce application-neutral CNCF Display Model value types.

Minimum candidates:

- DisplayModelIdentity / protocol version;
- DisplayObjectIdentity and source semantic reference;
- List Display Model / List item;
- Detail Display Model;
- Section;
- Field;
- DisplayValue with explicit type/representation;
- presentation roles such as title/subtitle/status;
- ActionDescriptor;
- compatibility/diagnostic metadata.

Keep these values immutable and transport-neutral. Do not introduce Flutter widgets, layout pixels, navigation controllers or target-specific visual objects.

Exit condition: pure model construction/validation specs pass without requiring server/runtime state.

### P96-03 — Display Projection SPI/API

Define the projection boundary from semantic View Model data into Display Model.

The projection must support:

- semantic property -> presentation-role mapping;
- labels/order/section assignment;
- one-to-many or combined display fields where declared;
- opaque/server-native value -> displayable typed value conversion;
- action descriptor projection;
- deterministic output for the same admitted semantic input and projection definition.

Projection configuration/definition authority must be explicit; avoid heuristic field-name guessing as runtime semantics.

Exit condition: deterministic List and Detail projection fixtures exist for the Editing Studio/Knowledge Candidate scenario.

### P96-04 — Protocol codec and compatibility boundary

Define the versioned Display Model Protocol representation and deterministic codec.

Prove:

- encode/decode round trip;
- stable identity/version fields;
- unknown/incompatible protocol version rejection;
- structured diagnostics for unsupported projection/value/action kinds;
- no domain object or Flutter implementation leakage into the wire contract.

This step should freeze the first consumer handoff shape before TFC/TFAF integration.

### P96-05 — Component-scoped DisplayService read path

Implement DisplayService as the UI-facing component service.

Initial read/query operations should cover only the reference vertical slice:

- list/query Display Objects;
- retrieve Detail Display Object;
- preserve semantic/source identity and revision needed for later mutation.

DisplayService delegates to existing semantic View/runtime facilities and Display Projection. It does not become a second View store or duplicate Aggregate/View authority.

Exit condition: server-side List -> Detail can be exercised entirely through DisplayService and returns protocol objects.

### P96-06 — Standard Display Mutation contract

Define standard create/update/delete Display Mutation inputs/results.

Required metadata includes:

- target/source identity;
- expected revision/concurrency information;
- editable fields;
- value/schema/constraint information needed by a generic editor;
- mutation kind;
- structured validation/conflict result.

The Display contract describes standard resource mutation only. Business Operations remain separate.

### P96-07 — Mutation bridge to Entity/Aggregate runtime

Map admitted Display Mutation deterministically onto existing CNCF Entity/Aggregate resource mutation.

Do not duplicate:

- authorization;
- optimistic concurrency/revision checks;
- domain validation;
- UnitOfWork/transaction behavior;
- failure semantics.

After successful standard mutation, reproject/return the resulting Display Object where appropriate.

Exit condition: Detail -> Edit -> Save -> refreshed Detail, Create -> Detail/List, and Delete -> List behavior is proven against the reference fixture.

### P96-08 — Business Operation Action descriptors

Add the minimum ActionDescriptor form needed to advertise an ordinary Business Operation from a Display Model.

The descriptor identifies the semantic action/target but execution continues through the existing CNCF Operation/REST API. DisplayService does not tunnel arbitrary Business Operations.

Initial consistency rule after successful Business Operation: client explicitly reloads the affected Display Object/View through DisplayService.

Exit condition: one reference Business Operation is advertised, invoked through the normal Operation boundary, and followed by a successful Display reload.

### P96-09 — Editing Studio reference fixture

Build one end-to-end fixture around a Knowledge Candidate-like resource:

```text
semantic List View
  -> DisplayService
  -> List Display Model
  -> select item
  -> Detail Display Model
  -> standard update
  -> Entity/Aggregate mutation
  -> refreshed Detail

Business Operation action
  -> ordinary Operation API
  -> explicit Display reload
```

Use fake/reference data where necessary; the goal is protocol/runtime proof, not application-specific UI completion.

### P96-10 — Phase 98 compatibility hook

Ensure the Display Model can carry an optional client-safe presentation variant and opaque Display Instance/correlation reference without making Experiment assignment part of Phase 96.

Phase 96 should only preserve/project the correlation field. Experiment/Run/Arm allocation, authority, observation and reward semantics remain Phase 98.

This should not block P96-01 through P96-09 if Phase 98 implementation is absent.

### P96-11 — Consumer handoff and closure

Freeze:

- protocol schema/version;
- DisplayService operation identities;
- reference JSON fixtures;
- mutation examples;
- Business Operation descriptor example;
- unsupported/non-goal list;
- TFC/TFAF consumer handoff notes.

Run focused tests, CNCF full suite, independent review and closure review according to normal Phase practice.

## Suggested implementation packages

Exact names may follow existing CNCF conventions, but responsibility should be separated roughly as:

```text
cncf.display.model
  DisplayModel / List / Detail / Section / Field / Value / Action

cncf.display.projection
  DisplayProjection / definition / projector

cncf.display.protocol
  versioned codec / wire representation

cncf.display.service
  DisplayService read/query + standard mutation boundary
```

Avoid one large service/object containing model, projection, codec and mutation logic.

## Executable Specification matrix

At minimum prove:

| Area | Required proof |
| --- | --- |
| Model | valid/invalid List, Detail, Field, Value, Action construction |
| Projection | deterministic semantic -> display mapping |
| Value conversion | opaque/server value -> typed display value |
| Protocol | deterministic round trip and version rejection |
| Read service | List and Detail retrieval |
| Update | expected revision + validation + refreshed Display Object |
| Create/Delete | standard generic interaction path |
| Authorization | unauthorized Display Mutation cannot reach resource mutation |
| Conflict | stale revision produces structured conflict |
| Business Operation | descriptor only; normal Operation invocation + reload |
| Separation | semantic View remains independently consumable |
| Platform neutrality | no Flutter/TFAF runtime type dependency |
| Experiment hook | optional correlation/variant is preserved without assignment logic |

## Scope guards

Do not expand Phase 96 to include:

- complete responsive-layout semantics;
- Flutter widget generation;
- TFAF implementation;
- push/subscription invalidation;
- Service Bus refresh;
- arbitrary Business Operation tunneling through DisplayService;
- generic workflow/action execution;
- Experiment allocation/reward logic;
- a new persistence model for Display Objects;
- heuristic automatic UI generation beyond admitted projection rules.

If a requirement is needed only for one Editing Studio screen and cannot be expressed as target-neutral Display semantics, keep it out of CNCF Phase 96 and handle it in the application/TFAF layer.

## Risk points to resolve early

1. Cozy Phase 74 contract drift: freeze the exact consumed subset before implementing CNCF types.
2. View identity/revision availability: mutation must not invent a second concurrency identity.
3. DisplayValue typing: avoid falling back to arbitrary JSON/string maps as the primary model.
4. DisplayService ownership: keep it a boundary/service, not a duplicate domain layer.
5. Mutation authorization: ensure Display metadata never becomes authorization authority.
6. ActionDescriptor scope: resist turning it into a second generic Operation protocol.
7. Experiment fields: keep them optional/correlation-only until Phase 98 owns the semantics.

## Recommended execution shape

The phase is large enough to benefit from internal checkpoints but does not yet require a Phase split if P96-01 freezes a genuinely minimal Cozy subset.

Recommended checkpoints:

```text
Checkpoint A: P96-01..04  model/projection/protocol frozen
Checkpoint B: P96-05      read-only List/Detail vertical slice
Checkpoint C: P96-06..08  mutation + Business Operation boundary
Checkpoint D: P96-09..11  fixture, compatibility handoff, closure
```

If Checkpoint A reveals that Cozy Phase 74 requires substantial new cross-repository implementation rather than consumption/alignment, stop and split that prerequisite instead of absorbing it silently into Phase 96.

## Completion image

Phase 96 should finish with a CNCF server capable of serving a target-neutral List/Detail Display Model, accepting standard create/update/delete Display Mutations through DisplayService, and advertising Business Operations without wrapping their execution.

That is the stable server contract TFC/TFAF can replace its fake List/Detail source with. Richer UI semantics and experiment behavior can then evolve without reopening the semantic View/runtime boundary.


## Concrete implementation proposal

Implementation should use small typed Scala values rather than Record/Map-first data. Candidate responsibilities are: `DisplayModel` for List/Detail/Section/Field/Value/Action values; `DisplayProjectionDefinition` plus a pure `DisplayProjector`; an explicit `DisplayValueConverterRegistry`; a versioned protocol codec; and a component-scoped `DisplayService` implemented as registered CNCF Operations. Exact names must be reconciled with existing CNCF conventions before coding.

### Core model candidate

Use closed kinds for List/Detail, presentation roles such as Title/Subtitle/Status/Summary, typed display values for text/number/boolean/date/datetime/choice/reference, Sections and Fields, ListItem, and ActionDescriptor. Reuse existing CNCF identity/revision/value types where they already express the semantics. For server-native values, permit only an explicit typed structured-display form/converter; arbitrary JSON maps and silent `toString` conversion must not become the default model.

### Projection definition and execution

Separate definition from execution. A projection definition should identify its source View, target Display Model kind/version, field mappings, sections and actions. Initial source expressions should be deliberately small: direct semantic property references plus explicitly admitted converter/composition forms needed by the fixture. Do not introduce a new general expression language in Phase 96.

`DisplayProjector` should be pure where possible: canonical CNCF View result in, `Consequence[DisplayModel]` out. Projection must be deterministic for the same admitted definition and semantic input.

### Value conversion

Use an explicit converter registry keyed by admitted semantic/source type. Built-ins cover common CNCF scalar/value types; application converters register explicitly. Missing conversion returns a structured failure. This prevents presentation behavior from depending on incidental JVM `toString` output.

### Protocol codec

Use an envelope carrying protocol identity/version, kind, Display Model identity, Display Object identity, source View/revision reference, sections/items/actions, and optional client-safe context such as Display Instance/correlation and presentation variant. Follow existing CNCF JSON codec/`Consequence` conventions. Commit golden fixtures for List, Detail, update request/result, delete result and Business Operation descriptor. Unknown protocol versions and unknown closed-enum values fail explicitly.

### DisplayService Operations

Implement DisplayService through registered CNCF Operations, conceptually `list`, `get`, `create`, `update`, and `delete`. Requests identify an admitted Display Model/projection and object identity/query. They must not let clients supply arbitrary View identities, converter names, Aggregate operation names, datastore paths or implementation classes.

Read flow:

```text
DisplayService Operation
  -> resolve admitted DisplayProjectionDefinition
  -> canonical View/query facility
  -> DisplayProjector
  -> Display Model
  -> Operation response / protocol codec
```

Update flow:

```text
DisplayService.update
  -> resolve admitted projection
  -> validate editable field identities
  -> decode admitted Display Values
  -> build canonical Entity/Aggregate mutation input
  -> existing authorization/revision/validation/UnitOfWork
  -> mutation result
  -> query/reproject Detail Display Model
  -> DisplayUpdateResult
```

The bridge must call the existing resource mutation API. No Display-specific datastore write, transaction manager or authorization model is introduced.

### Mutation metadata

Project only enough constraint metadata for a generic editor: required, bounded text, numeric range, choice/options and similar admitted constraints. Prefer existing domain/schema constraint authority. Display constraints may narrow client input but never weaken authoritative server-side domain validation.

Implement update before create/delete. Update is the strongest common proof because it exercises identity, revision, editable fields, validation, mutation and reprojection. Create/delete should reuse the same boundary after update is stable.

### Action descriptors

Keep standard Display Mutation targets and ordinary Business Operation targets distinct. A Business Operation descriptor carries an existing CNCF Operation identity/presentation reference; the client invokes the ordinary Operation/REST endpoint. DisplayService does not proxy/tunnel it. Reuse existing Operation input schema/presentation metadata rather than cloning a second schema where possible.

### Definition registration

Avoid heuristic runtime discovery. Display projection definitions should be component-owned and registered during normal assembly/bootstrap, then resolved by component + Display Model identity/version. Before adding a new provider SPI, inspect current ComponentFactory/provider registration patterns and reuse the canonical mechanism where possible.

### Editing Studio fixture

Use a deliberately small KnowledgeCandidate-like semantic fixture with fields such as id, title, candidateType, status, summary, capturedAt and revision. List maps title/subtitle/status roles. Detail has a Summary section with editable title/status/summary and an Evidence section with read-only capturedAt. Include one standard update and one ordinary Business Operation descriptor. The fixture proves architecture without importing the full Editing Studio domain into CNCF tests.

### Test structure

Prefer cohesive specs such as `DisplayModelSpec`, `DisplayProjectionSpec`, `DisplayValueConverterSpec`, `DisplayModelJsonSpec`, `DisplayServiceReadSpec`, `DisplayMutationSpec`, `DisplayBusinessOperationDescriptorSpec`, and `DisplayModelEditingStudioFixtureSpec`, adjusted to repository naming conventions. Run these as focused Phase validation before the full CNCF suite.

### Coding order inside checkpoints

Checkpoint A: inventory existing View/identity/value/revision types; implement the minimum Display model; pure projection fixture; codec/golden fixture; only then registry/provider wiring.

Checkpoint B: prove `DisplayService.get` for one Detail object first, then List. Detail is the stronger read proof because identity, revision, sections, fields and actions all appear there.

Checkpoint C: implement update first, then create/delete, then Business Operation descriptor/reload behavior.

Checkpoint D: freeze consumer artifacts only after mutation and Business Operation separation pass review.

## Implementation inventory questions

Before coding, inspect the repository and answer:

1. What canonical CNCF View result/value type should `DisplayProjector` consume?
2. Which existing identity/revision types can be reused?
3. Which existing schema/constraint model should project editor constraints?
4. Which existing Entity/Aggregate mutation service should DisplayService call?
5. How are component-owned service/providers registered through ComponentFactory today?
6. Which JSON codec utilities and protocol-version conventions should Phase 96 follow?
7. Which Cozy Phase 74 List/Detail/Section/Field/Action types should CNCF consume/adapt rather than duplicate?

These are inventory questions, not redesign prompts. Reuse a canonical facility whenever it already satisfies the requirement.
