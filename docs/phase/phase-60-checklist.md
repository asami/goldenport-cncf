# Phase 60 Checklist - Component Admin and Documentation Visibility

status=planned
phase=[Phase 60 - Component Admin and Documentation Visibility](phase-60.md)
implementation_note=[Component Admin and Documentation Visibility Implementation Proposal](../notes/component-admin-documentation-visibility-implementation.md)
planning_journal=[Component Admin and Documentation Visibility Planning (historical Phase 58)](../journal/2026/07/2026-07-31-phase-58-component-admin-documentation-visibility-planning.md)
candidate_journal=[Phase 52 direct-Admin canonical-ID boundary](../journal/2026/07/2026-07-30-phase-52-direct-admin-canonical-id-boundary-consideration.md)
canonical_architecture_design=[Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md)
canonical_architecture_specification=[Component and Subcomponent Architecture Specification](../spec/component-subcomponent-architecture.md)
canonical_resource_design=[Component Resource Subcomponent](../design/component-resource-subcomponent.md)
canonical_resource_specification=[Component Resource Subcomponent Specification](../spec/component-resource-subcomponent.md)

This checklist is the authoritative Phase 60 state ledger after Phase 60
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 59.10 closes the Phase 59 series.

References to “Phase 58” below mean the full Phase 58 series, whose final
closure is Phase 58.9.

Phase 60 consumes the same four-document Phase 58 canonical identity/resource
contract and its already-resolved output. Admin must not independently scan or
resolve resources, or broaden the canonical resource/mode policy.

## ADM-01: Inventory and Executable Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF Admin, Help, configuration, runtime, datastore, and management
  maintainers
- Entry rule: Phase 59.10 is closed.
- Completion rule: Existing surfaces, identity ambiguities, scan boundaries,
  ownership, and exact failing-first acceptance identities are recorded.

- [ ] Inventory existing Admin, Help, configuration, runtime, datastore,
  Component model, and management surfaces.
- [ ] Record class/release/instance/Subsystem identity ambiguities.
- [ ] Record every direct CAR, repository, source, or documentation scan.
- [ ] Fix Help as the knowledge surface and Admin as the operator surface.
- [ ] Register exact failing-first acceptance identities.

## ADM-02: Identity and Admin View Model

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component identity and Admin view-model maintainers
- Entry rule: ADM-01 is DONE.
- Completion rule: One versioned view model distinguishes class, release,
  instance, Subsystem, implicit Subsystem, provenance, and failure states.

- [ ] Define a versioned Component Admin view model.
- [ ] Distinguish Component class, logical release, loaded instance,
  Subsystem, and implicit Component Subsystem.
- [ ] Preserve exact source/provenance for every projected field.
- [ ] Define unavailable, forbidden, stale, incompatible, and corrupt states.
- [ ] Add codec, compatibility, ambiguity, and multi-instance specifications.

## ADM-03: Configuration and Composition Visibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF configuration, Resource SubComponent, and Admin maintainers
- Entry rule: ADM-02 is DONE.
- Completion rule: Admin projects Phase 55 configuration and the same
  canonical Phase 58 identity/resource contract's already-resolved composition
  without independent scanning, resolution, or policy broadening.

- [ ] Consume Phase 55 `ConfigurationBindingCollection` and provenance.
- [ ] Show effective typed values, winning and overridden bindings, scope,
  source location, and selection trace.
- [ ] Consume the same Phase 58 already-resolved resource projection,
  `ResolvedComponentResources` or its accepted equivalent.
- [ ] Show primary, Documentation, and SourceCode artifact identity,
  availability, integrity, access, and physical provenance.
- [ ] Verify Admin performs no independent physical-resource scan or
  resolution and does not broaden the resource/mode policy.

## ADM-04: Component Contract and Model Visibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component contract, model, schema, and Admin maintainers
- Entry rule: ADM-03 is DONE.
- Completion rule: Service, Operation, dependency, schema, model, and diagram
  evidence is visible from authoritative Phase 59 contracts.

- [ ] Show Service, Operation, SPI, capability, and dependency contracts.
- [ ] Show Entity, Powertype, StateMachine, Value, Datatype, and relationships.
- [ ] Consume Phase 59 class/state diagrams and schema/model metadata.
- [ ] Preserve contract authority and distinguish generated projections.
- [ ] Verify visibility grants no invocation or management authority.

## ADM-05: Runtime and Datastore Visibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF runtime, datastore, lifecycle, and Admin maintainers
- Entry rule: ADM-04 is DONE.
- Completion rule: Runtime and datastore state is projected with exact
  identity, provenance, lifecycle state, and instance isolation.

- [ ] Show lifecycle, health, runtime, dependency, and ClassLoader state.
- [ ] Show datastore, schema, collection, Entity ID, and collection ID
      evidence through their authoritative runtime contracts.
- [ ] For every admitted Admin Entity-ID input, require its declared backing
      `EntityCollection` and exact collection equality before resolution.
- [ ] Prove scalar locator, foreign canonical ID, entropy fallback, missing
      owner, and ambiguous owner are rejected deterministically.
- [ ] Distinguish configured, resolved, active, degraded, and failed state.
- [ ] Keep instance state isolated across versions and Subsystems.
- [ ] Add standalone and multi-user `ExecutionContext` acceptance.

## ADM-06: Documentation Navigation

Stage Status:
- Current status: PLANNED
- Owner: CNCF Help, documentation-resource, knowledge, and Admin maintainers
- Entry rule: ADM-05 is DONE.
- Completion rule: Admin links exact Phase 59 knowledge resources and the
  same already-resolved Phase 58 projection without generating, scanning, or
  resolving documentation independently.

- [ ] Consume the Phase 59 Component knowledge manifest.
- [ ] Link exact User Guide, Reference Manual, Help, Scaladoc, model diagrams,
  examples, source availability, and troubleshooting resources.
- [ ] Show local, cached, remote, restricted, unavailable, and incompatible
  resource state accurately.
- [ ] Keep Help navigation and Admin operational context mutually linked
  without duplicating ownership.
- [ ] Verify Admin generates, scans, or resolves no documentation itself.

## ADM-07: Authorized Management

Stage Status:
- Current status: PLANNED
- Owner: CNCF authorization, management-operation, audit, and Admin maintainers
- Entry rule: ADM-06 is DONE.
- Completion rule: Every admitted management action enforces explicit
  authorization, validation, lifecycle safety, audit, and deterministic failure.

- [ ] Inventory current Component-owned management Operations.
- [ ] Define the admitted management-action catalog separately from ordinary
  Component Operations.
- [ ] Enforce explicit authorization, validation, lifecycle preconditions,
  idempotency where required, and audit.
- [ ] Prevent read-only resource visibility from granting management access.
- [ ] Add forbidden, conflict, unavailable, stale-instance, and retry
  specifications.

## ADM-08: Surface and Security Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web, HTTP, CLI, projection, security, and downstream maintainers
- Entry rule: ADM-07 is DONE.
- Completion rule: All admitted surfaces and security profiles pass focused,
  full, downstream, and independent review evidence.

- [ ] Project one view model through Web, HTTP, CLI, and machine-readable
  surfaces.
- [ ] Verify redaction, source disclosure, path safety, integrity, and
  authorization.
- [ ] Verify multiple Components, versions, instances, Subsystems, and users.
- [ ] Verify hostile metadata/resources cannot inject unsafe Admin content.
- [ ] Run focused, full, and representative downstream validation.
- [ ] Complete read-only review and conditional focused re-review.

## ADM-09: Canonical Documentation and Closure

Stage Status:
- Current status: PLANNED
- Owner: all Phase 60 repository and release maintainers
- Entry rule: ADM-08 is DONE.
- Completion rule: Verified behavior is normative, all planning records agree,
  and exact Phase 60 validation and review evidence is recorded.

- [ ] Create/update `docs/design/component-admin.md`.
- [ ] Create/update `docs/spec/component-admin.md`.
- [ ] Record exact executable evidence in normative documents.
- [ ] Mark the implementation note historical and non-normative.
- [ ] Reconcile strategy, phase, checklist, Help/Admin, and affected contracts.
- [ ] Confirm no latest contract exists only in notes or journal.
- [ ] Close Phase 60 with exact validation evidence.
