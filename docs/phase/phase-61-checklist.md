# Phase 61 Checklist - Information CML Runtime Canonicalization

status=planned
phase=[Phase 61 - Information CML Runtime Canonicalization](phase-61.md)

This checklist is the authoritative Phase 61 state ledger after Phase 61
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
the Phase 60 series closes in Phase 60.8.

## IC-01: Inventory and Failing-First Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF Information, Entity, projection, provider, and downstream
  maintainers
- Entry rule: Phase 60.8 is closed.
- Completion rule: The exact current split, target contract, migration surface,
  and failing-first acceptance identities are recorded before implementation.

- [ ] Inventory every hand-written Information model and helper.
- [ ] Inventory every CML-generated Entity, input, view, value, powertype, and
  state-machine output.
- [ ] Record which generated types are used by runtime and which are only
  compilation/specification evidence.
- [ ] Inventory InformationSpace, Behavior DSL, Component ownership, provider
  requests, projections, serialization, and persistence references.
- [ ] Inventory Textus Knowledge Editor and Textus SIE public and persisted
  dependencies.
- [ ] Record current source, binary, JSON/YAML/XML/Form, schema, operation, and
  persisted-state compatibility surfaces.
- [ ] Fix `src/main/cozy/information.cml` as the canonical source and generated
  `entity.Information` as the target runtime identity.
- [ ] Fix the temporary alias/adapter and final removal policy.
- [ ] Fix InformationSpace as the public curation/capability boundary and the
  Entity repository as its persistence/OCC boundary.
- [ ] Fix the exact expected use of CML state-machine output.
- [ ] Register failing-first Executable Specification identities for every
  Phase 61 acceptance group.
- [ ] Add a runtime reference test that fails while InformationSpace still
  uses the hand-written Information class.

Evidence:
- Pending.

## IC-02: Canonical CML and Generator Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF CML, simple-modeler, and Cozy maintainers
- Entry rule: IC-01 is DONE.
- Completion rule: One CML source generates the complete usable
  revision-aware Entity/value/lifecycle family required by runtime migration.

- [ ] Reconcile every hand-written runtime field with `information.cml`.
- [ ] Reconcile generated common `SimpleEntity` attributes and Information
  fields without duplicate timestamps, lifecycle, publication, or security
  semantics.
- [ ] Fix generated package and canonical type naming.
- [ ] Verify required/optional/default behavior for Entity and nested values.
- [ ] Verify one canonical generated class for every CML-defined Value and
  Powertype.
- [ ] Verify generated `Information` outputs contain one managed revision.
- [ ] Verify generated Create, Update, and Query inputs omit managed revision.
- [ ] Verify Update uses explicit `Update` semantics for optional and
  collection-valued fields.
- [ ] Inspect the generated `informationLifecycle` output for executable
  transition metadata/planning.
- [ ] Complete simple-modeler/Cozy state-machine generation if the current
  output does not expose the CML transition contract.
- [ ] Add cold-generation deterministic-output specifications.
- [ ] Add generated schema/codec/record/persistence round-trip specifications.
- [ ] Add invalid lifecycle transition specifications against generated
  transition evidence.

Evidence:
- Pending.

## IC-03: Generated Type Adoption

Stage Status:
- Current status: PLANNED
- Owner: CNCF Information model maintainers
- Entry rule: IC-02 is DONE.
- Completion rule: Runtime source uses the generated Entity/value/powertype
  family with only explicitly bounded compatibility adapters remaining.

- [ ] Introduce the canonical generated Information import/facade policy.
- [ ] Replace the hand-written Information case class in InformationSpace
  snapshots and method signatures.
- [ ] Replace duplicated ValidationIssue, IdentityBinding,
  ResolutionCandidate, PublicationStatus, Conflict, FieldEvent, snapshot, and
  count representations where defined by CML.
- [ ] Preserve required helper construction and safe `ValueReader` behavior
  through generated builders/codecs or explicit non-model utilities.
- [ ] Preserve `InformationId` compatibility while using canonical `EntityId`.
- [ ] Preserve deterministic field names and external wire aliases.
- [ ] Add explicit compatibility decoding for admitted legacy payloads.
- [ ] Reject ambiguous payloads that could select different hand-written and
  generated interpretations.
- [ ] Mark temporary root aliases/adapters with removal criteria.
- [ ] Add compile-time and runtime class-identity specifications.

Evidence:
- Pending.

## IC-04: InformationSpace Entity Persistence and OCC

Stage Status:
- Current status: PLANNED
- Owner: CNCF InformationSpace, Entity runtime, and persistence maintainers
- Entry rule: IC-03 is DONE.
- Completion rule: InformationSpace uses the standard Entity
  repository/UnitOfWork/revision path without a parallel persistence kernel.

- [ ] Define the component-scoped Information Entity collection identity.
- [ ] Register the generated Information Entity descriptor deterministically.
- [ ] Bind InformationSpace to the owning Component Entity repository.
- [ ] Replace private mutable snapshot authority with repository-backed
  reads/writes while retaining a storage-neutral InformationSpace API.
- [ ] Apply create defaults for id, revision, common attributes, audit, and
  security without admitting managed input.
- [ ] Advance revision on every effective Information mutation.
- [ ] Apply `WriteIfChanged` only where the Information operation contract
  explicitly selects it; preserve the standard default otherwise.
- [ ] Require observed revision for user-visible edit/save paths according to
  Phase 50 policy.
- [ ] Use atomic conditional transition for stale-write rejection.
- [ ] Ensure failed/stale mutations do not partially modify nested state,
  field events, Tags, publication records, or Knowledge projections.
- [ ] Preserve component ownership and isolation for multiple Components.
- [ ] Add in-memory, SQLite, and representative provider OCC specifications.
- [ ] Add concurrent update, replay, restart, and rollback specifications.

Evidence:
- Pending.

## IC-05: Curation and Knowledge Lifecycle Migration

Stage Status:
- Current status: PLANNED
- Owner: CNCF Information, Knowledge, Tag, and provider maintainers
- Entry rule: IC-04 is DONE.
- Completion rule: Phase 26/27 curation and Knowledge behavior is preserved on
  the generated revision-aware Entity.

- [ ] Migrate register/import while preserving raw and working data separation.
- [ ] Migrate update and field-event append behavior.
- [ ] Migrate validation and actionable issue projection.
- [ ] Migrate candidate creation, selection, clearing, and binding state.
- [ ] Migrate confirm, reject, and reopen through the CML lifecycle contract.
- [ ] Migrate publication success and failure behavior.
- [ ] Migrate conflict recording and resolution.
- [ ] Migrate snapshot, count, lookup, list, and search behavior.
- [ ] Preserve Information-specific capability checks and separation of duty.
- [ ] Preserve Tag bindings and dedicated Information TagSpace behavior.
- [ ] Preserve Information-to-Knowledge materialization and 1.5-hop
  neighborhood behavior.
- [ ] Preserve distinct Information, Entity, RDF, external, Tag, Knowledge
  node, and Knowledge frame identities.
- [ ] Preserve provider failures without false publication or revision state.
- [ ] Add lifecycle property tests and invalid-transition matrices.
- [ ] Re-run Phase 26/27 Information and Knowledge regression specifications.

Evidence:
- Pending.

## IC-06: DSL, Transport, Help, and Editor Projections

Stage Status:
- Current status: PLANNED
- Owner: CNCF Behavior, projection, HTTP/Web, and Help maintainers
- Entry rule: IC-05 is DONE.
- Completion rule: Every CNCF access surface uses the canonical Entity and
  preserves revision, authorization, and managed-input boundaries.

- [ ] Migrate protected `information_*` Behavior DSL operations.
- [ ] Preserve ExecutionContext and CallTree recording for every operation.
- [ ] Project revision as system/read-only output metadata.
- [ ] Exclude revision from application Create input.
- [ ] Require and validate observed revision on applicable edit/update forms
  and requests.
- [ ] Migrate Information editor descriptors, field projections, actions, and
  disabled reasons.
- [ ] Migrate system admin/debug Information projections.
- [ ] Migrate static Web form, HTTP, JSON/YAML/XML/Form, schema, OpenAPI, and
  MCP projections.
- [ ] Preserve raw provider-payload exclusion.
- [ ] Preserve structured stale-conflict presentation.
- [ ] Add projection parity and authorization-isolation specifications.

Evidence:
- Pending.

## IC-07: Downstream and Migration Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Textus Knowledge Editor, Textus SIE, and representative
  application maintainers
- Entry rule: IC-06 is DONE.
- Completion rule: Supported downstream and persisted Information flows use
  the canonical generated model without silent incompatibility or data loss.

- [ ] Define supported legacy persisted Information shapes.
- [ ] Implement deterministic migration or explicit incompatibility
  diagnostics.
- [ ] Preserve ids, lifecycle, working/raw data, candidates, bindings,
  publications, conflicts, events, audit, and revision provenance.
- [ ] Add migration preview and rollback-safe failure behavior.
- [ ] Validate Textus Knowledge Editor list/detail/edit/lifecycle flows.
- [ ] Validate Textus SIE authority resolution, publication, and
  materialization flows.
- [ ] Validate book, paper, web-resource, Person, Organization, and textual
  work/edition/series/volume profiles.
- [ ] Validate Tag filtering and local Knowledge materialization.
- [ ] Validate Help/API compatibility for development source and packaged CAR
  execution.
- [ ] Run focused downstream suites and representative end-to-end smoke tests.

Evidence:
- Pending.

## IC-08: Duplicate Removal and Canonical Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF and affected downstream maintainers
- Entry rule: IC-07 is DONE.
- Completion rule: No competing Information model remains, all required
  validation passes, and canonical documentation matches verified behavior.

- [ ] Remove the hand-written root Information case class.
- [ ] Remove duplicated hand-written CML value classes.
- [ ] Remove expired compatibility adapters and aliases.
- [ ] Remove generated-only fixture assumptions that no longer describe
  runtime behavior.
- [ ] Search source, tests, docs, generated inputs, and downstream repositories
  for obsolete runtime type references.
- [ ] Run cold CML generation and focused Phase 61 suites.
- [ ] Run full CNCF validation.
- [ ] Run full affected downstream validation.
- [ ] Perform read-only review, review-fix, and clean re-review.
- [ ] Promote verified architecture to `docs/design`.
- [ ] Promote public and persistence/migration contracts to `docs/spec`.
- [ ] Update strategy, phase, checklist, Help, and generated documentation.
- [ ] Record final version, dependency, migration, and release evidence.
- [ ] Close Phase 61 only after all completion rules and documentation gates
  pass.

Evidence:
- Pending.
