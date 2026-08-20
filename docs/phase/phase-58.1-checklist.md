# Phase 58.1 Checklist - Component Identity and Composition Codec

status=done
phase=[Phase 58.1 - Component Identity and Composition Codec](phase-58.1.md)
predecessor=[Phase 58](phase-58.md)
successor=[Phase 58.2](phase-58.2.md)

## RSC-02: Identity and Composition Model

Stage Status:
- Current status: DONE
- Owner: CNCF Component and repository contract maintainers
- Entry rule: Phase 58 RSC-01 is DONE.
- Completion rule: One deterministic parent/child composition model and codec represent exact release membership without duplicating Component identity.

- [x] Define root composition manifest schema and version.
- [x] Define primary and Subcomponent CAR classes, including Documentation, Source, and external-platform roles, and their exact payload boundaries.
- [x] Define parent Component coordinate, every child Component identity, exact logical release, artifact coordinate, role, implementation technology, version, digest, signature, requiredness, and repository.
- [x] Define access, disclosure, license, media/profile, and provenance fields.
- [x] Define canonical logical resource identity separately from physical path.
- [x] Define forward-compatible unknown-field behavior.
- [x] Reject duplicate roles/coordinates, cycles, logical-resource conflicts, inconsistent parent membership, and incompatible child Components.
- [x] Reject unsafe paths and malformed digests/signatures.
- [x] Add codec round-trip, property, hostile-input, and compatibility tests.

Evidence:
- Step commit `bcbdd159a4b80e7783939463a550ba90ebbbf6c7` accepted the focused
  RSC-02 implementation.
- The mandatory Phase review finding on duplicate child identity across logical
  releases was repaired by `F-58.1-RSC02-003`; focused closure review sealed
  PASS and the codec suite passed 33 tests with no failures.
- The final serialized `sbt --batch test` suite is the release-commit gate for
  this frozen Phase tree; no commit is created unless it passes.
