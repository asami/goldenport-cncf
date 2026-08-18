# Phase 58 Checklist - Component and SubComponent Composition Foundation

status=planned
phase=[Phase 58 - Component and SubComponent Composition Foundation](phase-58.md)
implementation_note=[Component and SubComponent Architecture Implementation Proposal](../notes/component-subcomponent-architecture-implementation.md)
resource_implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)
planning_journal=[Resource SubComponent Phase Split and Planning (historical Phase 56)](../journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md)

This checklist is the authoritative Phase 58 state ledger after Phase 58
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 57.5 closes the complete Phase 57 series.

## RSC-01: Architecture Proposal, Inventory, and Executable Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy/sbt-cozy, Component Repository, Help, Admin, and sample
  maintainers
- Entry rule: Phase 57.5 is closed.
- Completion rule: The non-normative architecture proposal, existing behavior,
  conflicts, ownership, and exact failing-first acceptance identities are
  recorded.

- [ ] Create the Phase 58 Component/SubComponent architecture proposal in
      `docs/notes` and record its required later promotion to `docs/design`
      and `docs/spec`.
- [ ] Inventory CAR/SAR layouts, runtime manifests, descriptors, integrity
  entries, dependency metadata, expanded artifacts, and development evidence.
- [ ] Inventory Component Repository index, local publication, remote
  retrieval, cache, offline, and release-visibility behavior.
- [ ] Inventory development-directory and packaged resolver precedence.
- [ ] Inventory current Help and Admin physical-resource walking or assumptions.
- [ ] Inventory source/archive equivalence and managed-source collection paths.
- [ ] Fix the distinction between parent Components, independently identifiable
      Subcomponent Components, their non-authoritative information payloads,
      and Subsystems.
- [ ] Fix initial `Documentation` and `SourceCode` Subcomponent Component
      roles and their payload boundaries.
- [ ] Freeze initial executable-child role examples and the separation of role
      from implementation technology.
- [ ] Fix logical release identity separately from physical artifact identity.
- [ ] Fix publication completeness separately from runtime activation.
- [ ] Register failing-first specifications for every Phase 58 acceptance
  group.

Evidence:
- Pending.

## RSC-02: Identity and Composition Model

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component and repository contract maintainers
- Entry rule: RSC-01 is DONE.
- Completion rule: One deterministic parent/child composition model and codec
  represent exact release membership without duplicating Component identity.

- [ ] Define root composition manifest schema and version.
- [ ] Define primary and Subcomponent CAR classes, including Documentation,
      Source, and external-platform roles, and their exact payload boundaries.
- [ ] Define parent Component coordinate, every child Component identity,
      exact logical release, artifact coordinate, role,
      implementation technology, version, digest, signature, requiredness,
      and repository.
- [ ] Define access, disclosure, license, media/profile, and provenance fields.
- [ ] Define canonical logical resource identity separately from physical path.
- [ ] Define forward-compatible unknown-field behavior.
- [ ] Reject duplicate roles/coordinates, cycles, logical-resource conflicts,
      inconsistent parent membership, and incompatible child Components.
- [ ] Reject unsafe paths and malformed digests/signatures.
- [ ] Add codec round-trip, property, hostile-input, and compatibility tests.

Evidence:
- Pending.

## RSC-03: Packaging and Publication Completeness

Stage Status:
- Current status: PLANNED
- Owner: Cozy/sbt-cozy and Component Repository maintainers
- Entry rule: RSC-02 is DONE.
- Completion rule: Fixture Subcomponent CARs and their payloads package
  deterministically and incomplete declared release profiles never become
  repository-visible.

- [ ] Define deterministic archive layout for Subcomponent CARs and their
      payload artifacts without making the payload itself a capability or
      implicit parent dependency.
- [ ] Generate primary composition metadata and subordinate artifact metadata.
- [ ] Bind exact digests/signatures after deterministic packaging.
- [ ] Validate parent, role, coordinate, version, and integrity before upload.
- [ ] Define atomic repository admission and release visibility.
- [ ] Reject missing, duplicate, incompatible, and digest-invalid required
  artifacts.
- [ ] Define optional versus required relationship behavior without making
  initial Documentation/SourceCode completeness ambiguous.
- [ ] Preserve local publication and remote publication parity.
- [ ] Build deterministic Documentation/SourceCode and representative
      executable-child fixture artifacts.
- [ ] Add source/archive and repeated-build equivalence tests.

Evidence:
- Pending.

## RSC-04: Resolution, Activation Boundary, and Provenance

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component Repository and runtime loading maintainers
- Entry rule: RSC-03 is DONE.
- Completion rule: Every resource and child form resolves through one API with
  exact logical identity and physical provenance, while discovery, activation,
  and external deployment remain separate.

- [ ] Resolve embedded primary resources.
- [ ] Resolve explicit development-directory resources.
- [ ] Resolve expanded Documentation and Source Subcomponent CARs and their
      payloads.
- [ ] Resolve every Subcomponent registry entry and independent CAR identity
      without implicitly activating a child.
- [ ] Resolve local repository and managed-cache artifacts.
- [ ] Resolve explicitly admitted remote repository artifacts.
- [ ] Resolve offline complete-release bundles.
- [ ] Define deterministic precedence and conflict behavior.
- [ ] Preserve origin kind, repository, artifact, path, digest, access,
  license, and resolution-step provenance.
- [ ] Report local, remote, cached, restricted, unavailable, missing, stale,
  incompatible, and corrupt states separately.
- [ ] Expose one `ResolvedComponentResources` API or accepted equivalent.
- [ ] Define single-Component and multi-Component Subsystem composition and
      diagnostics without treating membership as activation authority.

Evidence:
- Pending.

## RSC-05: Operation-Mode and Development Composition

Stage Status:
- Current status: PLANNED
- Owner: CNCF launcher, runtime, and development resolver maintainers
- Entry rule: RSC-04 is DONE.
- Completion rule: Operation mode selects one runtime-owned composition policy
  without entering Component domain code or deploying platform-specific child
  artifacts.

- [ ] Implement Develop precedence across explicit directory,
  development-local, expanded, local, cache, and remote sources.
- [ ] Define structured development-readiness failure for required missing,
  stale, corrupt, or incompatible resources.
- [ ] Keep Test deterministic with no implicit remote access.
- [ ] Require explicit Demo policy for remote Documentation retrieval.
- [ ] Keep Production primary-only capable.
- [ ] Require explicit child activation and platform-native deployment handoff
      for Subcomponents that carry external-platform artifacts.
- [ ] Prevent automatic Production source resolution, mounting, or fetch.
- [ ] Keep `OperationMode` out of Component implementation APIs.
- [ ] Verify development and packaged parity.

Evidence:
- Pending.

## RSC-06: Authorization, Disclosure, and Integrity

Stage Status:
- Current status: PLANNED
- Owner: CNCF security, repository, and source-policy maintainers
- Entry rule: RSC-05 is DONE.
- Completion rule: Resource and child access is authorized, integrity-checked,
  and non-leaking in every resolution form.

- [ ] Enforce role and resource access policy before content exposure.
- [ ] Represent restricted source without disclosing or indexing it.
- [ ] Verify digest, signature, parent, release, and repository evidence.
- [ ] Reject path traversal, symlink escape, and archive ambiguity.
- [ ] Keep repository credentials and signed access material out of manifests.
- [ ] Keep source content, credentials, host paths, and repository secrets out
  of diagnostics, metrics, and CallTree.
- [ ] Verify authorization cannot be granted by a manifest alone.
- [ ] Verify a parent registry cannot grant Operation authority, MCP access, or
      executable-child activation.
- [ ] Add hostile archive, corrupt cache, unauthorized source, and disclosure
  regression tests.

Evidence:
- Pending.

## RSC-07: Lifecycle, Concurrency, and Observability

Stage Status:
- Current status: PLANNED
- Owner: CNCF runtime, repository, cache, and observability maintainers
- Entry rule: RSC-06 is DONE.
- Completion rule: Resource and child lifecycle and concurrent resolution are
  bounded, idempotent, observable, and safe.

- [ ] Define cache reuse, refresh, invalidation, and stale detection.
- [ ] Define load, release, unload, and shutdown ownership.
- [ ] Verify multiple Component instances share immutable artifacts safely.
- [ ] Verify concurrent resolution does not duplicate or partially publish
  cache entries.
- [ ] Preserve actual terminal outcomes across cancellation and refresh races.
- [ ] Add bounded resolver CallTree nodes and metrics.
- [ ] Add structured repository/resolver diagnostics without sensitive data.
- [ ] Verify failure of one resource does not corrupt unrelated releases.

Evidence:
- Pending.

## RSC-08: Downstream Consumer Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF resolver, Help, and Component Admin maintainers
- Entry rule: RSC-07 is DONE.
- Completion rule: Help and Admin fixtures consume the same resolved resources,
  child identities, and provenance without physical-artifact scans.

- [ ] Define read-only resource inventory and content-access APIs.
- [ ] Define safe availability, role, identity, version, digest, source, and
  provenance projections.
- [ ] Prove a Help fixture resolves Documentation through the common API.
- [ ] Prove an Admin fixture displays Documentation/SourceCode state through
      the same API.
- [ ] Prove Help and Admin distinguish parent, Documentation/Source,
      external-platform, and other Subcomponent Component identities plus
      Subsystem identities.
- [ ] Prove Help and Admin report identical physical availability and integrity.
- [ ] Prevent Help/Admin from walking CAR, SubComponent, cache, repository, or
  development directories independently.
- [ ] Preserve authorization differences between inventory visibility and
  content access.

Evidence:
- Pending.

## RSC-09: End-to-End and Cross-Repository Validation

Stage Status:
- Current status: PLANNED
- Owner: all Phase 58 repository maintainers
- Entry rule: RSC-08 is DONE.
- Completion rule: Every representative profile and affected repository passes
  focused and full validation.

- [ ] Verify embedded-only small Component.
- [ ] Verify parent plus Documentation and Source Subcomponent CAR fixtures.
- [ ] Verify parent plus independently describable external-platform
      Subcomponent CAR fixtures.
- [ ] Verify explicit external-platform deployment handoff with no CNCF
      deployment fallback.
- [ ] Verify restricted-source release.
- [ ] Verify development-directory override and provenance.
- [ ] Verify local, cached, remote, and offline resolution.
- [ ] Verify production primary-only activation with repository offline.
- [ ] Verify missing, incompatible, duplicate, unsafe, stale, and corrupt
  failures.
- [ ] Verify load/unload, multi-instance, restart, and concurrency.
- [ ] Run focused CNCF, Cozy/sbt-cozy, and repository suites.
- [ ] Run full tests in every changed code repository.
- [ ] Run representative downstream CAR lint and packaging checks.

Evidence:
- Pending.

## RSC-10: Canonical Closure

Stage Status:
- Current status: PLANNED
- Owner: all Phase 58 documentation owners
- Entry rule: RSC-09 is DONE.
- Completion rule: Canonical documents describe verified behavior and no
  current planning record contradicts it.

- [ ] Promote verified architecture to
      `docs/design/component-subcomponent-architecture.md`.
- [ ] Promote normative behavior to
      `docs/spec/component-subcomponent-architecture.md`.
- [ ] Mark the implementation note historical and point it to final
  design/specification.
- [ ] Update Phase 59 Documentation/AI and Phase 60 Admin entry contracts.
- [ ] Update strategy and phase evidence.
- [ ] Run `git diff --check` and documentation link checks.
- [ ] Complete read-only review and admitted review fixes.
- [ ] Close Phase 58 only after exact validation evidence is recorded.

Evidence:
- Pending.
