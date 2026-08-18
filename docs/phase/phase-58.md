# Phase 58 - Component and SubComponent Composition Foundation

status=planned
planned_at=2026-07-31
depends_on=[Phase 57.5](phase-57.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58 Checklist](phase-58-checklist.md)
implementation_note=[Component and SubComponent Architecture Implementation Proposal](../notes/component-subcomponent-architecture-implementation.md)
resource_implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)
planning_journal=[Resource SubComponent Phase Split and Planning (historical Phase 56)](../journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md)

## Purpose

Implement the identity, registry, packaging, resolution, integrity,
provenance, and lifecycle foundation for one parent Component and its
independently describable Subcomponent Components.

One parent Component remains executable through its primary CAR. Every
declared Subcomponent is itself a Component with its own CAR and parent/role/
implementation membership. This includes `Documentation` and `SourceCode`
Subcomponents: their payloads are information resources, while their CARs
remain independently describable and executable Components.

Phase 58 precedes Component Documentation and AI Knowledge Integration so
later Help, AI, and Component Admin features consume one verified resolver
instead of inventing separate archive, repository, or provenance paths.

## Dependency

Phase 58 begins after Phase 57.5 closes the complete Phase 57 series.

Phase 55 supplies typed configuration/provenance foundations that may be
consumed by resolver policy and diagnostics. Phase 58 must not reopen Phase 55
scope or encode SubComponent selection as an unrelated configuration
authority.

## Journal Deferred-Work Merge

Phase 58 absorbs the development/packaged parity portion of the Phase 53
CS-02C journal deferral only where it concerns parent/Subcomponent Component
description, CAR packaging, resolver provenance, and admitted runtime
resolution. A descriptor-v2 snapshot and declared style/capability metadata
must not create a second authoritative Component identity, activate a child,
or grant a capability.

Generic capability-definition validation, arbitrary Metadata Factory
ComponentStyle contribution, and global catalog policy remain owned by Strategy
9.50. This Phase does not reopen Phase 53 by treating those future extension
contracts as a packaging prerequisite.

Phase 57.1 and Phase 57.2 supply explicit Action execution semantics.
SubComponent composition must not create a second execution-intent or
job-submission policy. CAR admission and description do not imply external
platform deployment.

## Selected Direction

- A parent Component is an executable CAR, never a container-only artifact.
- A root composition registry in the primary CAR declares exact parent/child
  membership, role, implementation technology, coordinate, version, digest,
  requiredness, access policy, and repository evidence.
- Every declared Subcomponent, including Documentation and SourceCode, is an
  independently identifiable Component with its own CAR. Its information
  payload does not by itself grant a capability, authority, or parent runtime
  dependency.
- A Subcomponent CAR exposes its admitted identity, metadata, documentation,
  diagnostics, and MCP-readable information independently; the parent registry
  records structural relationship without granting authority or implicitly
  activating the child.
- Role and implementation technology are distinct. A presentation role, for
  example, may carry React or KMP implementation evidence.
- CNCF admits and describes a CAR. Deployment of a platform-specific child
  artifact remains the explicit responsibility of that target platform.
- A Subsystem is an executable composition of one or more Components. A single
  executable Component may be represented as an implicit single-Component
  Subsystem.
- Repository release visibility is atomic: a complete release is not exposed
  until every required physical artifact is present and valid.
- Publication completeness and runtime activation are separate. Production
  may activate the primary CAR without fetching or activating optional
  Subcomponent CARs.
- One resolver composes embedded, development-directory, expanded artifact,
  local repository, cache, remote repository, and offline-bundle resources.
- Every resolved resource retains logical identity and physical provenance.
- Missing, remote, restricted, unavailable, stale, incompatible, and corrupt
  states remain structurally distinguishable.
- `OperationMode` selects runtime-owned resource-resolution policy and is not
  exposed to Component domain code.
- Develop mode may resolve required development resources automatically;
  production never automatically fetches source.
- Phase 59 Help and AI access and Phase 60 Component Admin consume the same
  resolved resource and provenance APIs.
- Help and Admin must not independently scan CARs, SubComponents, development
  directories, caches, or repositories.

## Scope

- At RSC-01, create and freeze the non-normative Component/SubComponent
  architecture proposal in `docs/notes`, then derive failing-first acceptance
  identities from it.
- Define parent Component, independently identifiable Subcomponent Component,
  and Subsystem identity and relationship rules, including Documentation and
  Source roles.
- Define a root Component composition registry without duplicating
  authoritative Component identity or granting runtime authority.
- Define role separately from implementation technology, parent/child release
  membership, version, digest, signature, requiredness, access policy,
  media/profile metadata, and repository identity.
- Define exact publication completeness and release visibility behavior.
- Define Component Repository index, upload/admission, retrieval, cache,
  offline bundle, and failure behavior for multi-artifact releases.
- Define embedded versus external payload identity and precedence, and
  Subcomponent CAR discovery separately from child activation.
- Define development-directory, expanded artifact, local repository, cache,
  remote repository, and offline-bundle resolution.
- Prove development and packaged parity for the admitted parent/Subcomponent
  descriptor, declared style/capability metadata, registry, and provenance
  surfaces; reject a mismatch rather than inventing a generic capability
  registry.
- Preserve physical origin, repository, path, digest, authorization, license,
  disclosure, and resolution-step provenance.
- Define deterministic duplicate, conflict, stale, corrupt, missing,
  incompatible, restricted, and unavailable outcomes.
- Define operation-mode resolution policy for Develop, Test, Demo, and
  Production without adding Component mode branches.
- Define primary-only activation independently from knowledge/source
  availability.
- Define load, cache reuse, refresh, unload, shutdown, multi-instance, and
  concurrent-resolution behavior.
- Define bounded diagnostics and metrics without exposing source content,
  credentials, repository secrets, or host paths.
- Provide fake/in-memory repository and resolver fixtures plus packaged,
  development-directory, offline, restricted, and corrupt acceptance cases.
- Extend Cozy/sbt-cozy packaging and repository publication paths for
  Documentation, Source, and external-platform Subcomponent CAR fixtures.
- Provide stable resolver/provenance APIs for Phase 59 Help/AI and Phase 60
  Admin consumers.
- Promote verified architecture and behavior to design/specification before
  closure.

## Boundaries

- Phase 58 does not author User Guides, Reference Manuals, Scaladoc, model
  diagrams, or AI indexes.
- Phase 58 does not implement Textus CBD Support or Textus BoK ingestion.
- Phase 58 does not implement final Help or Component Admin presentation.
- Phase 58 does not let a Documentation/Source payload grant a capability,
  Operation authority, or implicit parent runtime dependency merely because
  its owning Subcomponent CAR is a Component.
- Phase 58 does not implement a platform-specific deployment engine for React,
  KMP, batch, cloud-function, or other child artifacts.
- Phase 58 does not allow a parent registry to activate a child, grant
  Operation authority, grant MCP access, or disclose restricted content.
- Phase 58 does not use Maven documentation classifiers as the Component
  resource contract.
- Phase 58 does not allow a manifest to grant Operation authority, capability,
  source disclosure, or MCP readiness.
- Phase 58 does not treat ClassLoader loading as the resource-artifact
  lifecycle.
- Phase 58 does not package arbitrary `target` state. Source content policy and
  managed-source normalization are consumed by Phase 59.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| RSC-01 | Proposal and executable contract freeze | A non-normative `docs/notes` architecture proposal, existing CAR/repository facts, ownership, and failing-first acceptance identities are frozen. | planned |
| RSC-02 | Identity and composition model | Parent, Documentation/Source, external-platform, and other Subcomponent Component relationships plus deterministic registry codecs are implemented. | planned |
| RSC-03 | Packaging and publication completeness | Cozy/sbt-cozy can package representative Documentation, Source, and external-platform child CAR fixtures and the repository exposes complete admitted release profiles only. | planned |
| RSC-04 | Resolution, activation boundary, and provenance | Payloads and Subcomponent CARs resolve through one provenance model while child discovery, child activation, and external deployment remain distinct. | planned |
| RSC-05 | Operation-mode and development composition | Develop/Test/Demo/Production policies, precedence, primary-only activation, child readiness, and structured outcomes are implemented. | planned |
| RSC-06 | Authorization, disclosure, and integrity | Restricted source, signatures/digests, safe paths, repository credentials, and non-leaking diagnostics are enforced. | planned |
| RSC-07 | Lifecycle, concurrency, and observability | Cache reuse, refresh, unload, shutdown, multi-instance, concurrent resolution, CallTree, metrics, and diagnostics are deterministic. | planned |
| RSC-08 | Downstream consumer contract | Stable resolved-resource and provenance APIs are proven with non-presentational Help and Admin consumer fixtures. | planned |
| RSC-09 | End-to-end and cross-repository validation | Packaged, dev-dir, local/remote, offline, restricted, missing, corrupt, and primary-only profiles pass across owning repositories. | planned |
| RSC-10 | Canonical closure | Verified design/specification, strategy, and phase records agree; provisional notes are marked historical and a closure journal records the promotion. | planned |

## Acceptance

- A parent Component remains an executable CAR and its registry represents
  declared Subcomponent Component membership without duplicating Component
  identity.
- Documentation and SourceCode Subcomponents have their own Component identity
  and CAR, bind to one exact parent release, and expose only admitted
  information/operations rather than implicit parent runtime dependencies.
- Every Subcomponent Component has an exact parent relationship, role,
  implementation technology, integrity evidence, and no implicit activation
  or external deployment.
- Repository publication does not expose an incomplete required-artifact set.
- Primary-only production activation succeeds without automatically
  downloading or activating Documentation or SourceCode Subcomponent CARs.
- Develop resolution follows deterministic precedence and retains exact
  provenance for every winning resource.
- Test resolution is deterministic and performs no implicit remote access.
- Demo remote Documentation access requires explicit policy.
- Production never automatically resolves, mounts, or fetches source.
- Restricted source remains represented without being disclosed or indexed.
- Missing, remote, restricted, unavailable, stale, incompatible, corrupt, and
  local states are not collapsed into generic absence.
- Duplicate logical resource identities fail deterministically.
- Unsafe paths, digest mismatch, parent mismatch, and incompatible release
  membership are rejected.
- Offline complete-release bundles resolve without network access.
- Repeated and concurrent resolution does not duplicate cache state or erase
  provenance.
- Help and Admin consumer fixtures receive the same resolved identity,
  availability, and provenance without scanning physical artifacts directly.
- Runtime execution does not depend on build, rendering, source-generation, or
  repository tooling.
- Final verified contracts are promoted to `docs/design` and `docs/spec`.

## Repository Responsibilities

| Repository | Responsibility |
| --- | --- |
| `/Users/asami/src/dev2025/cloud-native-component-framework` | Parent/child composition model, resolver, provenance, activation boundary, policy, lifecycle, downstream consumer API, diagnostics, and executable runtime contract |
| `/Users/asami/src/dev2025/cozy` | Subcomponent CAR packaging model, archive layout, validation, publication metadata, and Documentation/Source/external-platform fixture generation |
| `/Users/asami/src/dev2026/sbt-cozy` | Build integration, development evidence, packaging tasks, and local publication workflow for representative profiles |
| Component Repository implementation owners | Atomic logical-release visibility, exact artifact retrieval, cache/repository metadata, and offline bundles |
| selected sample/Component repositories | Embedded, split, restricted, development, production-primary-only, and offline acceptance |

## Planning References

- `docs/notes/component-subcomponent-architecture-implementation.md`
- `docs/notes/component-resource-subcomponent-implementation.md`
- `docs/journal/2026/07/2026-07-31-phase-53-cs02c-catalog-handoff-and-selection-admission.md`
- `docs/journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md`
- `docs/journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md`
- `docs/design/component-dependency-loading.md`
- `docs/design/component-factory.md`
- `docs/phase/phase-59.md`
- `docs/phase/phase-60.md`

## Resume Point

After Phase 57.5 closes, begin RSC-01 by completing the non-normative notes
proposal and freezing parent/child identity, role/payload versus
execution-platform classification, registry, publication-completeness, activation boundary,
resolver, provenance, operation-mode, Help-consumer, and Admin-consumer
acceptance before implementing archive or repository changes.
