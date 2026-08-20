# Phase 58.3 Checklist - Resource Resolution, Activation Boundary, and Provenance

status=done
phase=[Phase 58.3 - Resource Resolution, Activation Boundary, and Provenance](phase-58.3.md)
predecessor=[Phase 58.2](phase-58.2.md)
successor=[Phase 58.4](phase-58.4.md)

## RSC-04: Resolution, Activation Boundary, and Provenance

Stage Status:
- Current status: DONE
- Owner: CNCF Component Repository and runtime loading maintainers
- Entry rule: Phase 58.2 RSC-03 is DONE.
- Completion rule: Every resource and child form resolves through one API with exact logical identity and physical provenance, while discovery, activation, and external deployment remain separate.
- Update rule: Mark DONE only after the mandatory Phase final review finds no Current Phase Blocker and the Phase release commit succeeds.

- [x] Resolve embedded primary resources.
- [x] Resolve explicit development-directory resources.
- [x] Resolve expanded Documentation and Source Subcomponent CARs and their payloads.
- [x] Resolve every Subcomponent registry entry and independent CAR identity without implicitly activating a child.
- [x] Resolve local repository and managed-cache artifacts.
- [x] Resolve explicitly admitted remote repository artifacts.
- [x] Resolve offline complete-release bundles.
- [x] Define deterministic precedence and conflict behavior.
- [x] Preserve origin kind, repository, artifact, path, digest, access, license, and resolution-step provenance.
- [x] Report local, remote, cached, restricted, unavailable, missing, stale, incompatible, and corrupt states separately.
- [x] Expose one `ResolvedComponentResources` API or accepted equivalent.
- [x] Define single-Component and multi-Component Subsystem composition and diagnostics without treating membership as activation authority.

Evidence:
- RSC-04 was accepted in Step commit
  `995e82fc4e65b6cb0437a617b607bc0dbb28dcb4` (`Implement RSC-04 component
  resource resolution`). Terra xhigh full review returned
  `CPB-P58.3-001` (terminal same-source selection), `CPB-P58.3-002` (direct
  integrity state validation), and `CPB-P58.3-003` (active ScalaCheck property
  coverage); one Closure Fix Batch repaired all three and focused re-review
  returned `SEALED_PASS`. Accepted post-fix focused accumulator
  `45210-20260820T201957Z` covered `ComponentSubcomponentCompositionCodecSpec`
  and `ResolvedComponentResourcesSpec`, with 48 succeeded / 0 failed / 2
  suites. The final frozen-tree `sbt --batch test` remains pending as the
  Phase release Commit Manifest gate and is not claimed as passed; this
  checklist is committed as DONE only if that gate and the release commit
  succeed.
