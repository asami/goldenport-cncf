# Phase 58.3 Checklist - Resource Resolution, Activation Boundary, and Provenance

status=planned
phase=[Phase 58.3 - Resource Resolution, Activation Boundary, and Provenance](phase-58.3.md)
predecessor=[Phase 58.2](phase-58.2.md)
successor=[Phase 58.4](phase-58.4.md)

## RSC-04: Resolution, Activation Boundary, and Provenance

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component Repository and runtime loading maintainers
- Entry rule: Phase 58.2 RSC-03 is DONE.
- Completion rule: Every resource and child form resolves through one API with exact logical identity and physical provenance, while discovery, activation, and external deployment remain separate.

- [ ] Resolve embedded primary resources.
- [ ] Resolve explicit development-directory resources.
- [ ] Resolve expanded Documentation and Source Subcomponent CARs and their payloads.
- [ ] Resolve every Subcomponent registry entry and independent CAR identity without implicitly activating a child.
- [ ] Resolve local repository and managed-cache artifacts.
- [ ] Resolve explicitly admitted remote repository artifacts.
- [ ] Resolve offline complete-release bundles.
- [ ] Define deterministic precedence and conflict behavior.
- [ ] Preserve origin kind, repository, artifact, path, digest, access, license, and resolution-step provenance.
- [ ] Report local, remote, cached, restricted, unavailable, missing, stale, incompatible, and corrupt states separately.
- [ ] Expose one `ResolvedComponentResources` API or accepted equivalent.
- [ ] Define single-Component and multi-Component Subsystem composition and diagnostics without treating membership as activation authority.

Evidence:
- Pending.
