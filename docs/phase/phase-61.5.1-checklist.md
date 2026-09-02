# Phase 61.5.1 Checklist - Information Downstream Runtime and Consumer Acceptance

status=active
phase=[Phase 61.5.1 - Information Downstream Runtime and Consumer Acceptance](phase-61.5.1.md)
predecessor=[Phase 61.5 Checklist](phase-61.5-checklist.md)
successor=[Phase 61.6 Checklist](phase-61.6-checklist.md)

## IC-07B: Downstream Runtime and Consumer Acceptance

Stage Status:
- Current status: ACTIVE
- Owner: Cozy, CNCF, Textus Knowledge Editor, Textus SIE, and representative
  application maintainers
- Update rule: Update only from accepted IC-07B evidence. Consume the accepted
  IC-07A persisted migration handoff; do not redefine its migration policy.
- Entry rule: Phase 61.5 is CLOSED with its IC-07A release closure accepted.
- Completion rule: Development source and packaged CAR execution use the
  canonical Information model with explicit compatible runtime-coordinate and
  component dependency identity evidence.

- [x] Align current development metadata to CNCF `0.5.3-SNAPSHOT` without
  conflating generator and runtime compatibility. (Slices A/B accepted)
- [x] Normalize generated Scraper dependency identity assertions to the
  canonical namespace/id/version form. (Slice B accepted)
- [ ] Validate Textus Knowledge Editor list/detail/edit/lifecycle flows.
- [x] Validate Textus SIE authority resolution, publication, and
  materialization flows. (Slice B focused specs accepted)
- [ ] Validate book, paper, web-resource, Person, Organization, and textual
  work/edition/series/volume profiles.
- [ ] Validate Tag filtering and local Knowledge materialization.
- [ ] Validate Help/API compatibility for development source and packaged CAR
  execution.
- [ ] Validate packaged CAR/catalog runtime admission with current source
  coordinates and verified checksums.
- [ ] Run focused downstream suites and representative end-to-end smoke tests.
- [ ] Complete the required Step review, conditional review-fix/re-review,
  final Phase review, full validation, and release commit.

Evidence:
- Split source: `D-P61.5-IC07B-SPLIT-001` in Phase 61.5.
- Preserved failing-first receipt: `P61.5-IC07B-VAL-008`, invocation
  `47287-20260901T023104Z`, which identifies the stale SIE Scraper dependency
  assertion to normalize in this Phase.
- `D-P61.5.1-IC07B-VALIDATION-001` records the user-authorized resumption of
  focused validation after an unrelated Cozy Logical UI collision was fixed.
- `D-P61.5.1-IC07B-PACKAGED-CAR-001` authorizes isolated compatible BoK and
  Scraper worktrees plus serialized local-warehouse packaging for the required
  packaged CAR runtime admission; current user worktrees remain preserved.
- `D-P61.5.1-IC07B-PACKAGED-RUNTIME-LAUNCHER-002` authorizes cncf-launcher
  as a mutation repository for a formal prebuilt runtime-bundle input. Its
  pre-existing dirty paths remain outside Phase ownership until the replanned
  manifest freezes the exact implementation boundary.
- `P61.5.1-IC07B-C-PREFLIGHT-001` rejected the legacy packaged smoke before
  any executable task resource was created because it starts SBT internally;
  Slice C now owns a prebuilt/runtime-only equivalent.
- `P61.5.1-IC07B-01C-ACCEPT-001` accepts that prebuilt-only boundary after
  static contract validation and final typed re-review. It is an admission
  prerequisite, not evidence that the isolated packaged runtime has run.
- `P61.5.1-IC07B-01C1-ACCEPT-001` accepts the formal launcher
  `--runtime-bundle-dir` input after its five-scenario focused specification
  passed. It is the only admitted runtime input for the pending packaged
  session.
- `P61.5.1-IC07B-01C2-STATIC-ACCEPT-001` accepts SIE's prebuilt-only local
  launcher-channel composition. C3 supersedes it only for the packaged runtime
  path with a sealed direct launcher closure and offline component cache; C2's
  retained static source input uses local Ivy, not Maven.
- `D-P61.5.1-IC07B-CLOSED-RUNTIME-INPUT-004` records the user-approved four
  mutation/commit repositories, the thin-launcher/jsoup readiness-timeout root
  cause, and the selected sealed-launcher plus offline component-cache design.
  C3 is currently REPLAN with M2 ledger
  `CB-P61.5.1-IC07B-C3-001` through `CB-P61.5.1-IC07B-C3-004`: standalone
  launcher closure, complete cache sealing, symmetric input-root exclusion,
  and semantic executable-probe scenarios. The fresh focused re-review sealed
  those four findings but added `CB-P61.5.1-IC07B-C3-RR2-001`: the runtime
  bundle must reject lib and component.d CAR/SAR symlink escape before runtime
  ownership. The frozen containment repair remains inside the approved C3
  boundary, but the review verdict requires a full Phase review after repair.
  The full C3 review's classpath-JAR, warehouse source/target, and BoK fixture
  findings are now sealed. The resulting focused closure re-review found two
  further local SIE inputs: the runtime-bundle root must undergo task-private
  source/shared-root exclusion, and the project-metadata-derived CAR path must
  be canonically contained by the task-private warehouse. Cycle 2 is limited
  to those two admissions and their static probe scenarios. C3 remains
  unaccepted; no live packaged acceptance is claimed. Cycle 2 passed its
  static probe, syntax/diff gate, CAR lint with no current FAIL, and typed
  focused closure re-review. The next separate gate is task-private local
  package preparation followed by the prebuilt-only packaged-runtime session.
- Transparency note: the prior PLAN agent-use disclosure omitted required
  fields. It is incomplete and is not acceptance, validation, or authority
  evidence for C3.
- `P61.5.1-IC07B-01B-ACCEPT-001` accepts the SIE development metadata and
  representative consumer boundary after final focused validation and typed
  re-review. It does not replace the pending isolated packaged runtime
  admission in Slice C.
