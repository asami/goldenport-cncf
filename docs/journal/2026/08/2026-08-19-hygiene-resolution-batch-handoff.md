# Hygiene Resolution Batch Handoff

Status: COMPLETE
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cozy
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff.md

## Purpose

Resolve the admitted Cozy archive and executable-specification maintenance
records in one behavior-preserving batch, with one focused review and one Cozy
full-suite gate on the accepted tree.

## Included Hygiene

| ID | Status | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- | --- |
| HYG-P56-002 | RESOLVED | `docs/journal/2026/08/2026-08-07-phase-56-hygiene-follow-up.md` | The direct runtime-catalog `URLConnection` read has no bounded runtime-integration evidence. | HP-001 | Add controlled local runtime evidence while preserving archive-packager behavior. |
| HYG-P56-004 | RESOLVED | `docs/journal/2026/08/2026-08-07-phase-56-hygiene-follow-up.md` | SAR publication uses a system temporary `.sar` path. | HP-002 | Contain only temporary SAR staging under Cozy's repository artifact boundary. |
| HYG-P57.4-002 | RESOLVED | `docs/journal/2026/08/2026-08-14-phase-57.4-hygiene-follow-up.md` | CAR-lint test work is created outside `target/`. | HP-003 | Keep assertions and lint behavior while placing test work under `target/`. |
| HYG-P57.4-003 | RESOLVED | `docs/journal/2026/08/2026-08-14-phase-57.4-hygiene-follow-up.md` | A retained temporary `user.home` aliases `/private/var`, and an article-media latch assertion is unreliable. | HP-004 | Isolate/clean the fixture and stabilize the assertion without changing publication behavior. |

## Frozen Boundary

- Allowed repositories: `cozy` for implementation and `cloud-native-component-framework` for this batch ledger and source-ledger closure.
- Preserve paths: all pre-existing dirty paths outside the exact targets; in particular, Cozy video-review source/spec changes and the Cozy compatibility-naming journal.
- Allowed behavior change: none, except the local temporary staging/work locations and test-fixture lifecycle specified below.
- Prohibited expansion: public API/source compatibility, archive/SAR format, publication semantics, remote publishing, CAR transaction changes, network-client replacement, architecture, schema, persistence, transport, security, lifecycle behavior, and unrelated Hygiene.

## HP-001 — Runtime-catalog URL integration evidence

- Hygiene IDs: HYG-P56-002
- Repository: /Users/asami/src/dev2025/cozy
- Targets: `src/main/scala/cozy/archive/CozyArchivePackager.scala`; `src/test/scala/cozy/CozyArchivePackagerSpec.scala`
- Allowed repair: add/refine a controlled local HTTP-runtime-catalog specification for the existing `URLConnection` path; only make a local compatibility-preserving source correction if that evidence demonstrates a defect.
- Prohibited expansion: descriptor/publication redesign, network-client replacement, external network contact, and archive-path changes outside the evidence seam.
- Focused validation: `sbt --batch "testOnly cozy.CozyArchivePackagerSpec"` through the serialized CNCF SBT route.
- Dependencies: None

## HP-002 — SAR temporary staging containment

- Hygiene IDs: HYG-P56-004
- Repository: /Users/asami/src/dev2025/cozy
- Targets: `src/main/scala/cozy/archive/CozySarPublisher.scala`; `src/test/scala/cozy/CozySarPublisherSpec.scala`
- Allowed repair: create the temporary SAR only under the existing repository-local artifact work boundary, with directly covering evidence for location, naming, cleanup, and preserved output.
- Prohibited expansion: SAR content/coordinates, remote upload, CAR transaction, or broad temporary-directory cleanup.
- Focused validation: `sbt --batch "testOnly cozy.CozySarPublisherSpec"` through the serialized CNCF SBT route.
- Dependencies: HP-001

## HP-003 — CAR-lint fixture containment

- Hygiene IDs: HYG-P57.4-002
- Repository: /Users/asami/src/dev2025/cozy
- Targets: `src/test/scala/cozy/lint/CozyCarLintSpec.scala`
- Allowed repair: place the existing temporary test roots under repository `target/`, retaining all assertions and cleanup behavior.
- Prohibited expansion: CAR lint semantics, archive publication, fixture redesign, or production-source changes.
- Focused validation: `sbt --batch "testOnly cozy.lint.CozyCarLintSpec"` through the serialized CNCF SBT route.
- Dependencies: HP-002

## HP-004 — Article-media fixture and concurrency reliability

- Hygiene IDs: HYG-P57.4-003
- Repository: /Users/asami/src/dev2025/cozy
- Targets: `src/test/scala/cozy/CozyBokProjectSpec.scala`; `src/test/scala/cozy/publication/CozyArticleMediaPublicationOrchestrationSpec.scala`; `src/test/scala/cozy/publication/CozyArticleMediaVideoCommandSpec.scala`
- Allowed repair: use a canonical, repository-contained temporary `user.home` fixture with reliable cleanup and make the recorded bounded latch assertion deterministic.
- Prohibited expansion: article-media production behavior, CAR archive/lint/modeler producer paths, publication semantics, or product-flow rewrites.
- Focused validation: `sbt --batch "testOnly cozy.bok.CozyBokProjectSpec"` and `sbt --batch "testOnly cozy.publication.CozyArticleMediaPublicationOrchestrationSpec cozy.publication.CozyArticleMediaVideoCommandSpec"` through the serialized CNCF SBT route.
- Dependencies: HP-003

## Final Focused Review

- Exact targets: every file listed in HP-001 through HP-004.
- Required checks: each included ID/outcome; whole-target hygiene; preserved runtime, SAR, lint, and publication behavior; package validation evidence; test-fixture isolation/cleanup; consumer compatibility; and scope containment.
- Failure policy: stop without commit; do not run an automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cozy: sbt --batch test` through `/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`.

Run the Cozy suite exactly once on the reviewed tree and stop on failure.

## Completion Contract

- Commit only after the final focused review and final full-validation gate pass.
- Update the HYG-P56-002, HYG-P56-004, HYG-P57.4-002, and HYG-P57.4-003 source records to `RESOLVED` with batch, validation, and acceptance-commit evidence.
- Mark this batch `COMPLETE` only in the accepted committed tree.
- Mark the four superseded legacy task handoffs as historical only; do not alter the excluded public source-compatibility records.

## Closure Evidence

- Final focused review: CLEAN on the exact HP-001 through HP-004 target set.
- Final full validation: Cozy invocation `30861-20260818T224506Z`, 97 suites and 1,337/1,337 tests passed.
- Acceptance commits: reported externally after the dependency-ordered grouped commit succeeds.

## Non-goals

- Public source-compatibility migrations and unrelated legacy Hygiene.
- Any user-owned Cozy video-review work or unrelated working-tree changes.
