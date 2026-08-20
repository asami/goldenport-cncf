# Phase 58.1 Checklist - Component Identity and Composition Codec

status=planned
phase=[Phase 58.1 - Component Identity and Composition Codec](phase-58.1.md)
predecessor=[Phase 58](phase-58.md)
successor=[Phase 58.2](phase-58.2.md)

## RSC-02: Identity and Composition Model

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component and repository contract maintainers
- Entry rule: Phase 58 RSC-01 is DONE.
- Completion rule: One deterministic parent/child composition model and codec represent exact release membership without duplicating Component identity.

- [ ] Define root composition manifest schema and version.
- [ ] Define primary and Subcomponent CAR classes, including Documentation, Source, and external-platform roles, and their exact payload boundaries.
- [ ] Define parent Component coordinate, every child Component identity, exact logical release, artifact coordinate, role, implementation technology, version, digest, signature, requiredness, and repository.
- [ ] Define access, disclosure, license, media/profile, and provenance fields.
- [ ] Define canonical logical resource identity separately from physical path.
- [ ] Define forward-compatible unknown-field behavior.
- [ ] Reject duplicate roles/coordinates, cycles, logical-resource conflicts, inconsistent parent membership, and incompatible child Components.
- [ ] Reject unsafe paths and malformed digests/signatures.
- [ ] Add codec round-trip, property, hostile-input, and compatibility tests.

Evidence:
- Pending.
