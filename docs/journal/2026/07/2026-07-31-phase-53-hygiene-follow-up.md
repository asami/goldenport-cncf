# Phase 53 Hygiene Follow-up Ledger

Date: 2026-07-31

Status: open

This non-normative ledger preserves phase-external maintenance findings. It
does not change Phase 53 contracts or completion criteria.

## HYG-P53-001 — ArtScene CML nominal string wrappers

- Status: OPEN
- Discovery: Phase 53 CAR lint, 2026-07-31
- Repository/path: `textus-art-scene`, `src/main/cozy/textus-art-scene.cml`,
  nominal-wrapper warnings around lines 1839–2133
- Evidence: CAR lint reports 29 nominal scalar wrappers represented as plain
  strings.
- Risk: warning noise weakens datatype-contract review.
- Boundary: a dedicated ArtScene CML datatype-contract hygiene group; it is a
  model redesign outside the CS-02 catalog handoff.

## HYG-P53-002 — Phase 52 closure status metadata

- Status: OPEN
- Discovery: Phase 53 CS-02B focused re-review, 2026-07-31
- Repository/path: `cloud-native-component-framework`,
  `docs/phase/phase-52.md` and `docs/phase/phase-52-checklist.md`
- Evidence: both front-matter status values are `in_progress` although their
  closing narratives say `CLOSED`; the checklist also embeds prose in a
  `Current status` field.
- Risk: future phase selection can reopen or misclassify a closed Phase 52.
- Boundary: a standalone Phase 52 documentation-normalization task, with no
  behavior or Phase 53 scope change.

## HYG-P53-003 — CAR fixture component-name coordinate alias

- Status: OPEN
- Discovery: Phase 53 CS-05C focused re-review, 2026-08-01
- Repository/path: `cloud-native-component-framework`,
  `src/test/scala/org/goldenport/cncf/component/testutil/CarArchiveFixture.scala`
- Evidence: fixture coordinate extraction recognizes serialized `component`
  and nested `component.name`, but not the accepted `componentName` alias. A
  fixture with distinct display `name` and `componentName` can therefore emit
  CAR runtime/ABI evidence that runtime admission rejects.
- Risk: tests can silently create structurally inconsistent CAR fixtures.
- Boundary: CNCF test-fixture hygiene. Extend coordinate extraction and add a
  runtime-admission regression separately; CS-05C uses canonical `component`
  evidence and does not change fixture infrastructure.

## HYG-P53-004 — Legacy HTTP executable-specification prose normalization

- Status: OPEN (user-authorized defer, 2026-08-02)
- Discovery: PM-53-01 focused re-review, 2026-08-02
- Repository/path: `cloud-native-component-framework`,
  `src/test/scala/org/goldenport/cncf/http/AuthenticationWebSessionSpec.scala`
  and `src/test/scala/org/goldenport/cncf/http/Http4sHttpServerDispatchSpec.scala`
- Evidence: PM-53-01 behavior and all associated focused tests pass, but a
  whole-file review identified remaining scenario-specific Given/When/Then
  prose placement and private/local naming normalization in legacy HTTP specs.
- Risk: executable-specification readability and traceability debt only; no
  mode-admission behavior, security result, or validation result is affected.
- Boundary: a dedicated HTTP executable-specification hygiene task. Do not
  reopen PM-53-01 or change the Subsystem user-mode contract.
