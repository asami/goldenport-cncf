# Phase 58.2 Checklist - Subcomponent Packaging and Publication Completeness

status=planned
phase=[Phase 58.2 - Subcomponent Packaging and Publication Completeness](phase-58.2.md)
predecessor=[Phase 58.1](phase-58.1.md)
successor=[Phase 58.3](phase-58.3.md)

## RSC-03: Packaging and Publication Completeness

Stage Status:
- Current status: PLANNED
- Owner: Cozy/sbt-cozy and Component Repository maintainers
- Entry rule: Phase 58.1 RSC-02 is DONE.
- Completion rule: Fixture Subcomponent CARs and their payloads package deterministically and incomplete declared release profiles never become repository-visible.

- [ ] Define deterministic archive layout for Subcomponent CARs and their payload artifacts without making the payload itself a capability or implicit parent dependency.
- [ ] Generate primary composition metadata and subordinate artifact metadata.
- [ ] Bind exact digests/signatures after deterministic packaging.
- [ ] Validate parent, role, coordinate, version, and integrity before upload.
- [ ] Define atomic repository admission and release visibility.
- [ ] Reject missing, duplicate, incompatible, and digest-invalid required artifacts.
- [ ] Define optional versus required relationship behavior without making initial Documentation/SourceCode completeness ambiguous.
- [ ] Preserve local publication and remote publication parity.
- [ ] Build deterministic Documentation/SourceCode and representative executable-child fixture artifacts.
- [ ] Add source/archive and repeated-build equivalence tests.

Evidence:
- Pending.
