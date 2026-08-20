# Phase 58.2 Checklist - Subcomponent Packaging and Publication Completeness

status=done
phase=[Phase 58.2 - Subcomponent Packaging and Publication Completeness](phase-58.2.md)
predecessor=[Phase 58.1](phase-58.1.md)
successor=[Phase 58.3](phase-58.3.md)

## RSC-03: Packaging and Publication Completeness

Stage Status:
- Current status: DONE
- Owner: Cozy/sbt-cozy and Component Repository maintainers
- Update rule: Mark DONE only after the mandatory Phase final review finds no Current Phase Blocker and the Phase release commit succeeds.
- Entry rule: Phase 58.1 RSC-02 is DONE.
- Completion rule: Fixture Subcomponent CARs and their payloads package deterministically and incomplete declared release profiles never become repository-visible.

- [x] Define deterministic archive layout for Subcomponent CARs and their payload artifacts without making the payload itself a capability or implicit parent dependency.
- [x] Generate primary composition metadata and subordinate artifact metadata.
- [x] Bind exact digests/signatures after deterministic packaging.
- [x] Validate parent, role, coordinate, version, and integrity before upload.
- [x] Define atomic repository admission and release visibility.
- [x] Reject missing, duplicate, incompatible, and digest-invalid required artifacts.
- [x] Define optional versus required relationship behavior without making initial Documentation/SourceCode completeness ambiguous.
- [x] Preserve local publication and remote publication parity.
- [x] Build deterministic Documentation/SourceCode and representative external-platform presentation child fixture artifacts.
- [x] Add source/archive and repeated-build equivalence tests.

Evidence:
- Deterministic archive, composition, integrity, and atomic-rejection scenarios are covered by the accepted RSC-03 specs and fixture. Accepted Step commits are Cozy `456451b6ff059fd875701dc600631acb5161a347` with development coordinate `0.3.3-SNAPSHOT`, and sbt-cozy `4a70d586ae9192609fec470a183dd1bc9f1ca9cd`. Initial review blocker `CB-RSC03-001` (stale fixture fallback) was repaired and focused re-review sealed `PASS`. Final frozen-tree suites passed: Cozy test 1,347 passed / 0 failed / 8 canceled Docker opt-in; sbt-cozy test 144 passed / 0 failed / 5 canceled deferred external classpath; and sbt-cozy scripted 9/9 passed. Evidence covers isolated warehouse/local-remote parity only; actual remote publication is not claimed. The post-closure-fix full-suite gate runs in the Phase release commit; this checklist is committed as DONE only when that gate and the release commit succeed.
