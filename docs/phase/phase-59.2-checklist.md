# Phase 59.2 Checklist - Base Component Knowledge Manifest Contract

status=closed
phase=[Phase 59.2 - Base Component Knowledge Manifest Contract](phase-59.2.md)
predecessor=[Phase 59.1](phase-59.1.md)
successor=[Phase 59.2.1](phase-59.2.1.md)

## DOC-02: Knowledge and Model Resource Contracts

Stage Status:
- Current status: CLOSED
- Owner: CNCF Component/CAR contract maintainers
- Update rule: CLOSED only in the distinct Phase release commit after the
  final full-suite receipt, the mandatory full review, and every accepted
  closure record are bound to this checklist.
- Entry rule: Phase 59.1 DOC-01B is DONE.
- Completion rule: DOC-02A base manifest behavior binds content to Phase 58
  resource identity/provenance without physical rediscovery. The remaining
  DOC-02 requirements are owned by Phases 59.2.1 through 59.2.3.

- [x] Consume the Phase 58 composition manifest and ResolvedComponentResources
  or accepted equivalent.
- [x] Define knowledge/model manifest schema/version and canonical resource
  paths without duplicating Component identity.
- [x] Define resource identity, kind, role, language, media type, size, digest,
  authority, stability, source, license, disclosure, and provenance.
- [x] Bind Documentation and SourceCode entries to Phase 58 logical resource
  identity/provenance.
- [x] Implement deterministic JSON codec, validation, and explicit
  forward-compatible unknown-field behavior.
- [x] Reject unsafe paths, duplicate identities, invalid media/role
  combinations, and digest mismatch.
- [x] Add property-based manifest and hostile-path specifications.

Evidence:
- DOC-02A Step commit: `d86dfc54305427be9e49b18a30d0f2a4721513d7`.
- Historical 2026-08-24 focused run:
  `/var/folders/vx/f3wcxbgx0hbgwfjw3ly2v7lm0000gn/T/cncf-sbt-logs/48908-20260823T222015Z.log`
  completed three suites with 26 tests succeeded and 0 failed.
  `ComponentKnowledgeManifestSpec` contributed six scenarios; the other two
  suites were Phase 58 resource-binding companions.
- Mandatory Phase full review: `CB-P592-001` and `CB-P592-002` were admitted;
  the bounded closure batch closed `CB-P592-002`, and the one user-authorized
  exceptional repair closed the remaining lowercase `credentialtoken` alias.
  The final focused re-review found no Current Phase Blocker and no need for a
  further full review.
- Current focused validation receipt:
  `/var/folders/vx/f3wcxbgx0hbgwfjw3ly2v7lm0000gn/T/cncf-sbt-logs/41694-20260824T013617Z.summary.json`
  records three completed suites, 26 succeeded tests, zero failures, no
  compilation failure, and a released serial SBT lock.
- Final full-suite receipt and the distinct Phase release commit are bound to
  `phase59.2-clb-6169a9ee53e67b52ceeacdcd6682d553a84cb3b06cd6a053ac4a03a3cf77ea65`.
- Accepted Hygiene: none. Accepted Development Candidate: `DEV-P592-001`,
  persisted in the canonical Phase 59.2 Development Candidate journal.

Overall checklist status is `closed` in the distinct release commit. Phase
59.2.1 remains planned and is not started by this closure.
