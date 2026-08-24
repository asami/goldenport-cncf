# Phase 59.2.3 Checklist - Public Metadata and Read-Only Consumer Contract

status=in-progress
phase=[Phase 59.2.3 - Public Metadata and Read-Only Consumer Contract](phase-59.2.3.md)
predecessor=[Phase 59.2.2](phase-59.2.2.md)
successor=[Phase 59.3](phase-59.3.md)

## DOC-02D: Public Metadata and Read-Only Consumer Contract

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF Component/CAR contract maintainers
- Update rule: Mark DONE only after public metadata and read-only consumer
  behavior are accepted with deterministic codec and executable evidence.
- Entry rule: Phase 59.2.2 DOC-02C is DONE.
- Completion rule: The complete manifest contract exposes only descriptive,
  read-only evidence and grants no directive, Skill, resolver, or content
  authority.

- [ ] Define public Directive projection identity, origin, version, authority,
  visibility, source digest, and redaction metadata without exposing restricted
  rule content or overriding a mounted directive.
- [ ] Define public Skill Catalog identity, owner, purpose, trigger,
  requirements, permissions, side effects, MCP requirements, installation
  reference, visibility, and digest metadata without installation, activation,
  execution, configuration, or authority grants.
- [ ] Define the stable read-only consumer contract later used by Phase 60
  without resolver, content-read, route, or Admin behavior.
- [ ] Extend deterministic JSON codec/validation and unknown-field handling for
  public metadata and the read-only consumer boundary.
- [ ] Add property-based and hostile-input specifications for redaction,
  visibility, authority, and non-activation/non-installation behavior.

Evidence:
- IN_PROGRESS: DOC-02D-01 implementation is routed under
  D-P59.2-NESTED-SPLIT-NUMBERING-001; mandatory Phase full-review routing is
  protected and no lightweight Step review is prepared by this Step.
