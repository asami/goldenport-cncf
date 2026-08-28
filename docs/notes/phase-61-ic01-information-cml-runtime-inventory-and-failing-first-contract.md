# Phase 61 IC-01A Information CML Runtime Inventory and Failing-First Contract

Status: non-normative inventory and acceptance handoff

This note records the Phase 61 IC-01A inventory before generated-runtime
adoption. It is an implementation handoff, not a replacement for the Phase 61
specification or the canonical CML source.

## Current handwritten runtime

`src/main/scala/org/goldenport/cncf/information/InformationModel.scala` contains
the current handwritten model and its compatibility/helper surface:

- `InformationId` is a type alias to `EntityId`, with `apply` and `createC`.
- `InformationLifecycleState`, `InformationBindingStatus`,
  `InformationPublicationState`, `InformationConflictState`,
  `InformationFieldState`, and `InformationImportContext` are root aliases or
  facades over generated value/powertype types.
- The root `Information` case class owns `id`, `domain`, `rawData`,
  `workingData`, lifecycle state, import context, validation issues, resolution
  candidates, identity bindings, publication statuses, conflicts, field
  events, `confirmedAt`, and `updatedAt`; `data` is a working-data helper.
- The handwritten value cases are `InformationValidationIssue`,
  `InformationIdentityBinding`, `InformationResolutionCandidate`,
  `InformationPublicationStatus`, `InformationConflict`, and
  `InformationFieldEvent`. `InformationIdentityBinding.createC` and its
  `ValueReader` accept the legacy `rdfSubject`/`rdf_subject` and
  `knowledgeNodeId`/`knowledge_node_id` spellings through the private
  `_record_get_as_c` helper.
- `InformationSpaceSnapshot` and `InformationSpaceCounts` are handwritten
  aggregate/count shapes. `PaperInformation.from` and
  `WebResourceInformation.from` are domain record readers, with private
  `_strings` and `_string` helpers. `InformationCapabilities` is the string
  capability catalog (`read`, `import`, `edit`, `validate`, `resolve`,
  `confirm`, `reject`, `publish`, `conflictRead`, `conflictResolve`,
  `auditRead`, and `all`). `InformationResolutionCandidate.label` and
  `Information.data` are convenience projections over the handwritten fields.

`src/main/scala/org/goldenport/cncf/information/InformationSpace.scala` uses
that root model directly. Its `private var _snapshot` is the mutable snapshot
authority. `registerInformation` creates handwritten `Information` values;
snapshot/count/clear, lookup/search, update, field-event, validation,
resolution-candidate, confirmation, rejection/reopen, publication, conflict,
and materialization methods all expose or mutate the handwritten type. The
class-level helpers `_update_information`, `_replace_information`,
`_information_collection`, `_next_key`, `_state_after_candidate_update`, and
`_same_binding` support that path. Its companion and adjacent helpers retain
Information-specific validation, Tagging, RDF-node naming, and
Information-to-Knowledge projection behavior.

The broader local surface inventory is: `ActionCallFeaturePart` exposes the
protected `information_*` Behavior DSL operations and CallTree records; the
owning Component supplies the component-scoped InformationSpace and execution
context; provider requests feed raw/working Records and resolver candidates but
do not become Information state; `InformationEditorProjection`,
`InformationSpaceProjection`, and the HTTP/Web information renderer produce
editor, Help, transport, and Knowledge-facing projections; and
serialization/schema and persistence references are the field/value shapes and
snapshot contract above.
The current mutable snapshot is an in-memory runtime store only, not a durable
Entity repository or UnitOfWork implementation.

This is the failing-first boundary: the public curation surface remains useful,
but its registered runtime item is currently the root handwritten class rather
than the generated Entity class.

## Canonical CML and generated family

`src/main/cozy/information.cml` remains the canonical CNCF Information model
source. It defines the generated Entity/value/powertype/lifecycle family:

- Entity: `org.goldenport.cncf.information.entity.Information`, extending
  `org.simplemodeling.model.SimpleEntity`, with the domain, raw/working data,
  lifecycle, curation, publication, conflict, event, and audit attributes.
- Values: `InformationImportContext`, `InformationValidationIssue`,
  `InformationIdentityBinding`, `InformationResolutionCandidate`,
  `InformationPublicationStatus`, `InformationConflict`,
  `InformationFieldEvent`, `InformationSpaceSnapshot`, and
  `InformationSpaceCounts` under `org.goldenport.cncf.information.value`.
- Powertypes: `InformationLifecycleState`, `InformationBindingStatus`,
  `InformationPublicationState`, `InformationConflictState`, and
  `InformationFieldState` under the generated value package.
- State-machine source: `informationLifecycle` declares the lifecycle contract
  for imported, invalid, needs-resolution, ready-for-confirmation, confirmed,
  published, rejected, and conflict states. The current generated
  `domain.statemachine.informationLifecycle` file exists, but is an empty
  record scaffold: its `toRecord`/builder output contains no usable generated
  state or transition metadata. CML is therefore the sole semantic transition
  declaration at IC-01. IC-02 owns making the generated state-machine output
  usable; after that work, later runtime use must map InformationSpace
  operations such as validate, update, select-resolution, confirm, publish,
  reopen, and conflict resolution to that generated transition evidence.

The generated revision evidence already exists in
`org.goldenport.cncf.information.GeneratedInformationRevisionSpec`. Its output
registry covers generated root, read, operation, aggregate, view, summary, and
detail `Information` classes and verifies `SimpleEntity` plus one
`EntityRevision`; its create, update, and query registry verifies that revision
is absent from application input. That suite is existing evidence, not runtime
adoption.

## Downstream Textus dependency inventory

This is a read-only source/binary, CML public-operation/form, and
persistence/projection inventory. Neither external repository was modified;
both current external trees are dirty, and downstream acceptance and migration
remain IC-07 work rather than IC-01 validation.

- Textus Knowledge Editor has direct handwritten `Information` source/binary
  dependencies in `BookAggregate`, `AuthorityResolver`, `BookResolver`,
  `PaperResolver`, and `WebResourceMetadataProvider`, plus a local
  `InformationFieldEvent` alias. Its `impl/ComponentFactory.scala` directly
  imports and uses the root handwritten `Information` in public/runtime
  signatures, registration flows, and handwritten construction. Its
  `Cncf05MigrationSpec` directly constructs the root handwritten `Information`
  with `id`, `domain`, `rawData`, `workingData`, and `updatedAt`;
  `ComponentFactorySpec` also directly imports the handwritten `Information`
  family.
- The Knowledge Editor CML defines public editor lifecycle/form result surfaces
  for Book, Paper, WebResource, Person, Organization, TextualWork,
  TextualEdition, and TextualVolume through their corresponding
  `*EditorInformationResult` types, with `informationId` parameters and form/
  redirect uses where declared. These are CML-defined public source contracts,
  distinct from the direct handwritten dependencies above. They, including
  editor result projections, are migration dependencies for IC-07; this
  inventory does not claim external acceptance or a separate persisted
  Information record.
- Textus SIE has direct handwritten `Information` source/binary dependencies
  in `InformationKnowledgeEngineProvider`, its specification, and
  `impl.ComponentFactory`. The provider and component factory use handwritten
  Information in direct publication/result/resolution flows; the provider
  surface includes `Information.data`, id/domain, `PaperInformation`,
  publication status/result, identity binding/resolution candidate, and
  generated Knowledge/RDF/vector records.
- The SIE provider calls `publishRdfConcept`, `registerSource`, and
  `indexDocument`. The resulting persisted downstream projection/state is
  derived from Information; the observed source does not establish a separate
  persisted Information record in SIE. Its authority resolution, publication,
  and materialization flows remain IC-07 acceptance work.

## Boundary and compatibility contract

InformationSpace is the public component-owned curation and capability
boundary. KnowledgeSpace remains non-editable. Standard Entity repository and
UnitOfWork are the future persistence and optimistic-concurrency boundary;
InformationSpace must not retain a parallel persistence kernel. Managed
revision is runtime/system state and is never application input.

Information, Entity, RDF, external, Tag, and Knowledge identities are distinct:
an Information `EntityId`, RDF subject, external identifier, Tag identity,
KnowledgeNode identity, and Knowledge frame identity must not be collapsed.

The migration preserves these compatibility surfaces while the generated model
is adopted:

- source compatibility for existing InformationSpace calls and known
  `InformationId`/field names, using temporary aliases or adapters only where
  evidence requires them;
- binary compatibility for existing compiled consumers where an admitted
  adapter can preserve the public boundary, without creating a second canonical
  Information model;
- JSON, YAML, XML, and Form field names and external aliases, including raw vs
  working data and nested curation values, while excluding provider payloads;
- schema and operation contracts in which generated outputs carry managed
  revision metadata and Create/Update/Query application inputs do not carry
  revision; and
- persisted-state compatibility through deterministic migration or explicit
  incompatibility diagnostics, preserving ids, lifecycle, raw/working data,
  candidates, bindings, publications, conflicts, events, audit, and revision
  provenance without silent loss.

The migration policy is temporary aliases/adapters only where the above
evidence requires them, with an explicit removal criterion after downstream and
persisted-state acceptance; all such compatibility surface is removed at
canonical closure. No application input exposes managed revision.

## Acceptance registry

The following stable registry fixes the executable identities for the Phase 61
acceptance groups. Example identifiers are part of the handoff and may be
expanded only by the owning later stage.

| Stage | Exact executable identity | Registered examples | State |
| --- | --- | --- | --- |
| IC-01 | `org.goldenport.cncf.information.InformationCanonicalRuntimeReferenceSpec` | E1 `registered-runtime-item-is-generated-information` | pending until generated-runtime adoption (IC-03) |
| IC-02 | `org.goldenport.cncf.information.InformationCmlCanonicalContractSpec` | E1 CML entity/value/powertype/lifecycle contract; E2 managed-revision input/output contract | pending |
| IC-03 | `org.goldenport.cncf.information.GeneratedInformationRuntimeAdoptionSpec` | E1 runtime class identity; E2 bounded compatibility adapter | pending |
| IC-04 | `org.goldenport.cncf.information.InformationSpaceEntityPersistenceSpec` | E1 repository boundary; E2 revision/OCC atomicity | pending |
| IC-05 | `org.goldenport.cncf.information.InformationCurationKnowledgeLifecycleSpec` | E1 curation lifecycle; E2 Tag and Knowledge materialization | pending |
| IC-06 | `org.goldenport.cncf.information.InformationProjectionCompatibilitySpec` | E1 DSL/CallTree; E2 transport/schema/editor managed-input boundary | pending |
| IC-07 | `org.goldenport.cncf.information.InformationDownstreamMigrationAcceptanceSpec` | E1 persisted-state migration; E2 representative downstream flows | planned |
| IC-08 | `org.goldenport.cncf.information.InformationCanonicalClosureSpec` | E1 duplicate removal; E2 canonical documentation and regression closure | pending |

`org.goldenport.cncf.information.GeneratedInformationRevisionSpec` is the
existing generated revision suite used as IC-02 revision evidence and IC-03
adoption precondition evidence. The IC-01 runtime-reference suite registered
above is intentionally pending while InformationSpace returns handwritten
`Information`; it exposes the current runtime class rather than asserting source
text or metadata.

Cross-repository Textus Knowledge Editor and Textus SIE mutation, acceptance,
and migration are deferred to IC-07. This note records the IC-01 read-only
dependency inventory only and makes no change to those repositories.
