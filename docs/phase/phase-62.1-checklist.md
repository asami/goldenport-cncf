# Phase 62.1 Checklist - ArtScene-driven Progressive Static Web Client Integration

status=planned
phase=[Phase 62.1](phase-62.1.md)

This checklist is the authoritative Phase 62.1 state ledger after the Phase
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 62 must be closed
before PSI-01 starts.

## PSI-01: Baseline Handoff and Gap Taxonomy

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web and ArtScene Web maintainers
- Entry rule: Phase 62 is closed and its consumable artifact identity is fixed.
- Completion rule: Every observed gap has one non-overlapping owner class.

- [ ] Freeze the Phase 62 artifact, public JavaScript API, asset path, CSRF
  projection/header, error envelope, and compatibility identity.
- [ ] Define `phase-62-defect`, `reusable-extension`, `artscene-local`, and
  `future-candidate` gap classes.
- [ ] Define evidence required to promote an ArtScene observation into CNCF.
- [ ] Keep Phase 62 defects outside new-capability accounting.
- [ ] Register failing-first producer and consumer acceptance identities.

Evidence:
- Pending.

## PSI-02: ArtScene Consumer Trial

Stage Status:
- Current status: PLANNED
- Owner: ArtScene Web maintainers with CNCF Web support
- Entry rule: PSI-01 is DONE and ArtScene Phase 13C is active.
- Completion rule: Real ArtScene flows exercise the baseline and every gap is
  reproducible and classified.

- [ ] Adopt the CNCF facade and REST v1 execution for Timeline and List query
  refresh.
- [ ] Adopt the same REST Operation execution contract for review and
  facility-subscription mutations.
- [ ] Use Form API only where dynamic Web input definition or optional admission
  validation is demonstrably required.
- [ ] Verify no initial REST/Form API hydration is introduced.
- [ ] Verify normal links/forms and PRG remain usable without JavaScript.
- [ ] Exercise authorization, CSRF rejection, structured operation failure,
  browser network failure, abort, retry, and rapid repeated actions.
- [ ] Record the smallest reproducible contract gap without ArtScene semantics
  in the proposed CNCF surface.

Evidence:
- Pending.

## PSI-03: Bounded Reusable Extensions

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web client and HTTP maintainers
- Entry rule: PSI-02 has admitted at least one reusable extension; otherwise
  mark this stage not required with evidence.
- Completion rule: Every admitted extension is generic, stable, and does not
  take ownership of DOM, page, or domain behavior.

- [ ] Extend only the necessary Form API adaptation, REST execution, request
  encoding, response decoding, structured error, cancellation, or asset
  packaging contracts.
- [ ] Preserve caller-owned `AbortSignal` and request-generation coordination.
- [ ] Keep token and diagnostic values out of exceptions, URLs, history, logs,
  metrics, CallTree, and ordinary UI messages.
- [ ] Preserve same-origin and unsafe-method security rules.
- [ ] Keep direct Form API execution POST compatibility-only and out of new
  ArtScene code.
- [ ] Add executable public-contract and malformed-input specifications.

Evidence:
- Pending.

## PSI-04: Producer/Consumer Compatibility

Stage Status:
- Current status: PLANNED
- Owner: CNCF packaging and ArtScene CAR maintainers
- Entry rule: PSI-03 is DONE or explicitly not required.
- Completion rule: Development and packaged consumers use the same published
  contract without source-project coupling.

- [ ] Verify the CNCF-owned fixture against the final client contract.
- [ ] Publish the required CNCF development artifact through the normal local
  workflow.
- [ ] Verify ArtScene development-directory consumption.
- [ ] Verify ArtScene packaged-CAR asset resolution and execution.
- [ ] Record exact producer/consumer versions and artifact identities.

Evidence:
- Pending.

## PSI-05: Real Browser and Failure Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF and ArtScene integration maintainers
- Entry rule: PSI-04 is DONE.
- Completion rule: Browser evidence proves both enhancement and fallback paths
  under success, concurrency, security rejection, and failure.

- [ ] Verify localized meaningful first HTML and zero initial API fan-out.
- [ ] Verify source-only local filtering and bounded explicit-action requests.
- [ ] Verify latest-request-wins under reordered responses.
- [ ] Verify review/follow double-submit control, failure recovery, and retry.
- [ ] Verify direct link, reload, back/forward, and JavaScript-disabled fallback.
- [ ] Verify structured public errors and diagnostic/token non-leakage.
- [ ] Verify authorization and Operation policy remain server-authoritative.

Evidence:
- Pending.

## PSI-06: Promotion and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web documentation and ArtScene planning maintainers
- Entry rule: PSI-05 is DONE.
- Completion rule: Verified contracts, ownership, evidence, and downstream
  gates agree and no mandatory gap remains ownerless.

- [ ] Promote stable CNCF contracts into design/spec/developer guidance.
- [ ] Update ArtScene Phase 13 evidence to the final producer contract.
- [ ] Relocate optional Island/runtime candidates to strategy item 9.21.
- [ ] Confirm ArtScene-specific behavior remains outside CNCF.
- [ ] Run focused, full, packaged, naming, executable-specification, review, and
  diff-check gates selected by the implementation plan.
- [ ] Close Phase 62.1 before ArtScene Phase 13 closes when a mandatory
  reusable extension was admitted.

Evidence:
- Pending.
