# Phase 58 Checklist - Component and SubComponent Contract Freeze

status=in_progress
phase=[Phase 58 - Component and SubComponent Contract Freeze](phase-58.md)
predecessor=[Phase 57.5](phase-57.5.md)
successor=[Phase 58.1](phase-58.1.md)
implementation_note=[Component and SubComponent Architecture Implementation Proposal](../notes/component-subcomponent-architecture-implementation.md)
resource_implementation_note=[Component Resource SubComponent Implementation Proposal](../notes/component-resource-subcomponent-implementation.md)

This checklist owns RSC-01 only. RSC-02 through RSC-10 moved exactly once to
Phase 58.1 through Phase 58.9 under approved split `D-58-SPLIT`.

## RSC-01: Architecture Proposal, Inventory, and Executable Contract Freeze

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF, Cozy/sbt-cozy, Component Repository, Help, Admin, and sample maintainers
- Update rule: Record RSC01-A1 and RSC01-B implementation evidence as it is produced; treat the Step as accepted only after its review and commit evidence are recorded. The RSC01-B registration item is implementation evidence subject to this existing review/commit acceptance rule.
- Entry rule: Phase 57.5 is closed.
- Completion rule: RSC01-A records the non-normative architecture proposal, existing behavior, conflicts, ownership, and frozen invariants; RSC01-B separately records exact failing-first acceptance identities.

- [x] Create the Phase 58 Component/SubComponent architecture proposal in `docs/notes` and record its required later promotion to `docs/design` and `docs/spec`.
- [x] Inventory CAR/SAR layouts, runtime manifests, descriptors, integrity entries, dependency metadata, expanded artifacts, and development evidence.
- [x] Inventory Component Repository index, local publication, remote retrieval, cache, offline, and release-visibility behavior.
- [x] Inventory development-directory and packaged resolver precedence.
- [x] Inventory current Help and Admin physical-resource walking or assumptions.
- [x] Inventory source/archive equivalence and managed-source collection paths.
- [x] Fix the distinction between parent Components, independently identifiable Subcomponent Components, their non-authoritative information payloads, and Subsystems.
- [x] Fix initial `Documentation` and `SourceCode` Subcomponent Component roles and their payload boundaries.
- [x] Freeze initial executable-child role examples and the separation of role from implementation technology.
- [x] Fix logical release identity separately from physical artifact identity.
- [x] Fix publication completeness separately from runtime activation.
- [x] RSC01-B: Register exact failing-first specifications and acceptance identities for every Phase 58-series acceptance group (implementation evidence; subject to the existing review/commit acceptance rule).

Evidence:
- Main handoff: [`component-subcomponent-architecture-implementation.md`](../notes/component-subcomponent-architecture-implementation.md)
- RSC01-B acceptance registry: [`phase-58-rsc01b-failing-first-acceptance-registry.md`](../notes/phase-58-rsc01b-failing-first-acceptance-registry.md)
- Design history and ledgers: [`2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md`](../journal/2026/08/2026-08-20-phase-58-rsc01-component-subcomponent-contract-freeze.md)
