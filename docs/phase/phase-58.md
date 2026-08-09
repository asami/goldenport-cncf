# Phase 58 - Component Resource SubComponent Foundation

status=planned
planned_at=2026-07-31
depends_on=[Phase 57.5](phase-57.5.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58 Checklist](phase-58-checklist.md)
implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)
planning_journal=[Resource SubComponent Phase Split and Planning (historical Phase 56)](../journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md)

## Purpose

Implement the repository, composition, resolution, integrity, provenance, and
lifecycle foundation that lets one logical Component release use multiple
physical resource artifacts.

The initial resource roles are:

- `Documentation`; and
- `SourceCode`.

These artifacts are Resource SubComponents. They are not executable
Components, Componentlets, Subsystems, or runtime CAR dependencies.

Phase 58 precedes Component Documentation and AI Knowledge Integration so
later Help, AI, and Component Admin features consume one verified resolver
instead of inventing separate archive, repository, or provenance paths.

## Dependency

Phase 58 begins after Phase 57.5 closes the complete Phase 57 series.

Phase 55 supplies typed configuration/provenance foundations that may be
consumed by resolver policy and diagnostics. Phase 58 must not reopen Phase 55
scope or encode SubComponent selection as an unrelated configuration
authority.

Phase 57.1 and Phase 57.2 supply explicit Action execution semantics. Resource SubComponent
resolution must not create a second execution-intent or job-submission policy.

## Selected Direction

- One logical Component release has one identity and version even when it is
  physically represented by a primary execution CAR plus Resource
  SubComponents.
- A root composition manifest in the primary artifact declares exact
  SubComponent role, coordinate, parent identity, version, digest, required
  relationship, access policy, and repository evidence.
- Documentation and SourceCode are initial closed roles. The model remains
  extension-ready, but arbitrary role registration is not part of Phase 58.
- Resource SubComponents use Component Repository publication, integrity,
  cache, and retrieval services without becoming runtime participants.
- Repository release visibility is atomic: a complete release is not exposed
  until every required physical artifact is present and valid.
- Publication completeness and runtime activation are separate. Production
  may activate the primary CAR without fetching resource artifacts.
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

- Define Resource SubComponent identity, role, parent relationship, logical
  release membership, version, digest, signature, requiredness, access policy,
  media/profile metadata, and repository identity.
- Define the root Component composition manifest without duplicating
  authoritative Component identity.
- Define exact publication completeness and release visibility behavior.
- Define Component Repository index, upload/admission, retrieval, cache,
  offline bundle, and failure behavior for multi-artifact releases.
- Define embedded versus external resource identity and precedence.
- Define development-directory, expanded artifact, local repository, cache,
  remote repository, and offline-bundle resolution.
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
- Extend Cozy/sbt-cozy packaging and repository publication paths for generic
  Resource SubComponent fixtures.
- Provide stable resolver/provenance APIs for Phase 59 Help/AI and Phase 60
  Admin consumers.
- Promote verified architecture and behavior to design/specification before
  closure.

## Boundaries

- Phase 58 does not author User Guides, Reference Manuals, Scaladoc, model
  diagrams, or AI indexes.
- Phase 58 does not implement Textus CBD Support or Textus BoK ingestion.
- Phase 58 does not implement final Help or Component Admin presentation.
- Phase 58 does not make a Resource SubComponent executable.
- Phase 58 does not turn a Resource SubComponent into a Component,
  Componentlet, Subsystem, service provider, or capability provider.
- Phase 58 does not add fine-grained language/media roles beyond the initial
  Documentation and SourceCode roles.
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
| RSC-01 | Inventory and executable contract freeze | Existing CAR, repository, dev-dir, cache, manifest, integrity, source/archive, Help, and Admin assumptions plus failing-first acceptance identities are fixed. | planned |
| RSC-02 | Identity and composition model | Resource SubComponent roles, parent/release identity, root composition manifest, provenance, access, and deterministic codecs are implemented. | planned |
| RSC-03 | Packaging and publication completeness | Cozy/sbt-cozy can package fixture SubComponents and the repository exposes a release only after all required artifacts validate. | planned |
| RSC-04 | Resolver and provenance | Embedded, development, expanded, local, cached, remote, and offline resources resolve through one API without losing physical origin. | planned |
| RSC-05 | Operation-mode and development composition | Develop/Test/Demo/Production policies, precedence, primary-only activation, and structured readiness outcomes are implemented. | planned |
| RSC-06 | Authorization, disclosure, and integrity | Restricted source, signatures/digests, safe paths, repository credentials, and non-leaking diagnostics are enforced. | planned |
| RSC-07 | Lifecycle, concurrency, and observability | Cache reuse, refresh, unload, shutdown, multi-instance, concurrent resolution, CallTree, metrics, and diagnostics are deterministic. | planned |
| RSC-08 | Downstream consumer contract | Stable resolved-resource and provenance APIs are proven with non-presentational Help and Admin consumer fixtures. | planned |
| RSC-09 | End-to-end and cross-repository validation | Packaged, dev-dir, local/remote, offline, restricted, missing, corrupt, and primary-only profiles pass across owning repositories. | planned |
| RSC-10 | Canonical closure | Verified design/specification, strategy, phase, notes, and journal records agree; provisional notes are marked historical. | planned |

## Acceptance

- A root composition manifest represents one logical Component release without
  creating another Component identity.
- Documentation and SourceCode Resource SubComponents bind to one exact parent
  release.
- Repository publication does not expose an incomplete required-artifact set.
- Primary-only production activation succeeds without automatically
  downloading Documentation or SourceCode artifacts.
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
| `/Users/asami/src/dev2025/cloud-native-component-framework` | Composition model, resolver, provenance, policy, lifecycle, downstream consumer API, diagnostics, and executable runtime contract |
| `/Users/asami/src/dev2025/cozy` | Resource SubComponent packaging model, archive layout, validation, publication metadata, and fixture generation |
| `/Users/asami/src/dev2026/sbt-cozy` | Build integration, development evidence, packaging tasks, and local publication workflow |
| Component Repository implementation owners | Atomic logical-release visibility, exact artifact retrieval, cache/repository metadata, and offline bundles |
| selected sample/Component repositories | Embedded, split, restricted, development, production-primary-only, and offline acceptance |

## Planning References

- `docs/notes/component-resource-subcomponent-implementation.md`
- `docs/journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md`
- `docs/journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md`
- `docs/design/component-dependency-loading.md`
- `docs/design/component-factory.md`
- `docs/phase/phase-59.md`
- `docs/phase/phase-60.md`

## Resume Point

After Phase 55 closes, begin RSC-01. Freeze identity, composition,
publication-completeness, resolver, provenance, operation-mode, Help-consumer,
and Admin-consumer acceptance before implementing archive or repository
changes.
