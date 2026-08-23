# Phase 59.2 Checklist - Component Knowledge and Model Manifest Contract

status=planned
phase=[Phase 59.2 - Component Knowledge and Model Manifest Contract](phase-59.2.md)
predecessor=[Phase 59.1](phase-59.1.md)
successor=[Phase 59.3](phase-59.3.md)

## DOC-02: Knowledge and Model Resource Contracts

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component/CAR contract maintainers
- Update rule: Mark DONE only after codec, validation, and hostile-input
  evidence accepts the frozen manifest contract.
- Entry rule: Phase 59.1 DOC-01B is DONE.
- Completion rule: Versioned knowledge and model manifests bind content to
  Phase 58 resource identity/provenance without physical rediscovery.

- [ ] Consume the Phase 58 composition manifest and ResolvedComponentResources
  or accepted equivalent.
- [ ] Define knowledge/model manifest schema/version and canonical resource
  paths without duplicating Component identity.
- [ ] Define resource identity, kind, role, language, media type, size, digest,
  authority, stability, source, license, disclosure, and provenance.
- [ ] Define contextual framework canonical URL, publication generation,
  document/section identity, and local/online availability independently of
  Component resource identity.
- [ ] Define generated-from and stale-projection detection and bind
  Documentation/SourceCode entries to logical resource identity/provenance.
- [ ] Define portable Entity, Powertype, StateMachine, Value, Datatype,
  relationship, class-diagram, and state-diagram resources.
- [ ] Define framework Documentation Component references without execution
  dependency or independent authoring authority.
- [ ] Define public Directive projection identity/origin/version/authority/
  visibility/digest/redaction metadata.
- [ ] Define public Skill Catalog identity/owner/purpose/trigger/requirements/
  permissions/side-effects/MCP/installation/visibility/digest metadata.
- [ ] Define the stable read-only consumer contract later used by Phase 60.
- [ ] Implement deterministic JSON codec, validation, and explicit
  forward-compatible unknown-field behavior.
- [ ] Reject unsafe paths, duplicate identities, invalid media/role
  combinations, and digest mismatch.
- [ ] Add property-based manifest and hostile-path specifications.

Evidence:
- Pending.
