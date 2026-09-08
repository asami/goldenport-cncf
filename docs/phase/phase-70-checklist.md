# Phase 70 Checklist - Post-Assembly Component Activation

status=closed
closed_at=2026-09-08
phase=[Phase 70 - Post-Assembly Component Activation](phase-70.md)
successor=Phase 70.1 - preserved planning files (no acceptance authority)
contract=[Component Activation Lifecycle Specification](../spec/component-activation-lifecycle.md)

This checklist is the authoritative Phase 70 state ledger after Phase 70
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 70 is a separately
selectable supplier branch after Phase 55 and must not start while another CNCF
Phase is active.

## CA70-01: Contract and Failing-First Evidence

Stage Status:
- Current status: DONE
- Completion scope: retained parent contract and supplier handoff only.
- Current slice: CA70-01A
- Owner: CNCF Component, Subsystem assembly, server lifecycle, readiness, configuration, and diagnostics maintainers
- Entry rule: Phase 55 is closed and no other CNCF Phase is active.
- Update rule: The Phase status/current-stage/current-slice fields and this checklist change only for evidence-backed stable work-item state changes; no completion may be recorded before independent review and all required evidence.
- Completion rule: API, lifecycle point, modes, ordering, timeout, readiness, failure, cleanup, and diagnostic contracts are frozen.

CA70-01A disposition: COMPLETE for the retained parent contract and supplier
handoff only. The normative component activation specification records the
complete R1-R9 contract candidate, including the public opt-in/context
boundary, post-assembly placement, deterministic once-only ordering,
component-boundary-only dependency access, mode isolation, controlled-test
admission, timeout/cancellation, failure/readiness/cleanup, redaction,
compatibility, and consumer-neutral handoff boundaries. The independent Step
Review receipt `/private/tmp/cncf-p70-ca7001a-step-review.md` records
`PASS — parent documentation boundary only`; it is not a Phase review, runtime
validation, Phase 70.1 child acceptance, or Textus BoK consumer acceptance.

CA70-01B and the protected runtime accumulator were previously transferred to
Phase 70.1. The transfer is superseded for the ten admitted paths by
`P70-DEC-ACCUMULATOR-ADMISSION-003`; CA70-01 remains complete for the retained
contract and supplier handoff only.

Decision `P70-CA70-01B-FULL-REVIEW-001`: the developer selected
`acceptance-change` after `FULL_REVIEW_REQUIRED`. Before CA70-01B can complete,
its executable evidence must prove per-mode component admission before any
no-activation assertion and recursively inspect public conclusion/diagnostic
exception payloads for raw `Throwable` data and private locators. The next
workflow state is `PARENT_CAPABILITY_CHECK`; no implementation or review is
admitted by this decision record alone.

Decision `P70-CA70-02A-PROTECTED-REPLAN-001`: the developer selected
`replan-protected-step`; decision request identity
`620f487811f034ca0d1094edf86d90438a9d1c71257633da6fa470067b678758` is
resolved as `continue-resolved-decision`. The unaccepted public activation API,
server-startup gate, scheduler/cancellation, cleanup, diagnostics, and tests
are one protected Step accumulator: `P70-ACTIVATION-CORE` / `CA70-02A`.
Completion requires real managed-server failure gating and runtime-visible
redacted diagnostic evidence, plus command/client exclusion. Textus BoK stays
a supplier handoff only; this decision does not permit a Phase 8 source edit,
acceptance commit, or review-gate bypass.

Historical Decision `P70-ACTIVATION-SPLIT-001`: the user selected Phase 70.1
as the child of the approved `phase-split`. Binding request identity:
`fe4ba4860e492c68867d5690dc5776fe5131d665a16e11801623749c6b66173c`;
resolved-request SHA-256:
`44868d7a2dda29d352489acc3f5a68cdf2041aa2515546635c4835efe517464c`.
The prior transfer of the runtime source/test accumulator is superseded for
the ten admitted paths by `P70-DEC-ACCUMULATOR-ADMISSION-003`.
`P70-CA70-02A-VAL-014` remains validation provenance only. Phase 70 retains
the supplier-only Textus BoK handoff and accepts no BoK source or consumer
behavior.

- [x] Inventory component creation, bootstrap, context injection,
      runtime-service binding, server listener/readiness, and shutdown order.
- [x] Prove that `Component.initialize` occurs before the complete consumer
      dependency graph is safely available.
- [x] Freeze the opt-in capability/API and prohibit arbitrary configured
      startup Operation dispatch.
- [x] Freeze managed server, command, client, emulator, and controlled-test
      execution-mode behavior.
- [x] Freeze deterministic order, once-only identity, timeout/cancellation,
      structured failure, readiness, cleanup, and diagnostic redaction.
- [x] Record the executable-evidence matrix and consumer handoff boundary that
      the active Phase 70 accumulator must provide without treating focused
      validation as release or consumer acceptance.

Evidence:
- CA70-01A R1-R9 contract candidate:
  `docs/spec/component-activation-lifecycle.md`.
- CA70-01B failing-first executable specification: admitted to the active
  Phase 70 accumulator.

Decision `P70-DEC-ACCUMULATOR-ADMISSION-003`: the existing
component-activation source and executable-specification accumulator is
re-admitted to Phase 70 under `continue-existing-authority`. Phase 70 owns
these ten paths for `P70-ACTIVATION-CORE` / `CA70-02A`:

- `docs/phase/phase-70.md`;
- `docs/phase/phase-70-checklist.md`;
- `docs/spec/component-activation-lifecycle.md`;
- `src/main/scala/org/goldenport/cncf/component/ComponentActivation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`;
- `src/main/scala/org/goldenport/cncf/cli/ServerOperation.scala`;
- `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceClientPart.scala`;
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`;
- `src/test/scala/org/goldenport/cncf/component/ComponentActivationLifecycleSpec.scala`; and
- `src/test/scala/org/goldenport/cncf/cli/ServerOperationActivationSpec.scala`.

The earlier Phase 70.1 transfer is superseded for these ten paths. The
untracked Phase 70.1 planning files remain preserved and have no source,
implementation, validation, or acceptance authority. CA70-02A is the sole
active non-terminal slice. R1--R9 are
unchanged, and no Textus BoK source or consumer behavior is accepted.

Decision `P70-DEC-R3-LIFECYCLE-DISPATCH-004`: the verified direct answer
`追加する` selected `add-lifecycle-dispatch-path`. The canonical Server branch
in `CncfRuntimeInstanceLifecyclePart._run` is therefore within the ten-path
accumulator: it gates construction/dispatch of `ServerOperation` on activation
after completed assembly, preserves the direct `startServer` compatibility
path, and removes the duplicate post-dispatch gate. This adds no HTTP-server,
Textus BoK, or public-API authority.

## CA70-02: Runtime Implementation

Stage Status:
- Current status: DONE
- Transfer disposition: CLOSED in Phase 70 under the re-admitted accumulator;
  the earlier Phase 70.1 transfer remains superseded for the ten admitted
  paths, and one same-boundary internal Http4s test-adapter path was added by
  the accepted closure repair.
- Owner: Phase 70 protected runtime activation maintainers
- Entry rule: The admitted accumulator is implemented against the reviewed
  R1--R9 contract and its focused executable specifications.
- Completion rule: The active source/test accumulator and required evidence
  converge through the Phase 70 review and release route; no Textus BoK source
  or consumer behavior is accepted here.
- Update rule: Keep this parent stage closed only while the verified runtime
  evidence and release closure record remain accurate.

- [x] Complete the re-admitted ten-path protected runtime activation
      accumulator and the same-boundary internal Http4s test-adapter repair
      under `P70-ACTIVATION-CORE` / `CA70-02A`, preserving R1--R9 and its
      server-only, cleanup, once-only, mode, controlled-test, and redaction
      invariants.

Evidence:
- The preserved untracked Phase 70.1 planning files have no acceptance
  authority for this accumulator.
- `P70-CA70-02A-VAL-014` is provenance only, not acceptance or release
  evidence.
- `P70-CA70-02A-VAL-SBR2-002` passed 16 focused lifecycle and managed-server
  tests, and the independent SBR2 focused closure review has no findings.

## CA70-03: Managed Runtime and Consumer Acceptance

Stage Status:
- Current status: CLOSED
- Transfer disposition: HISTORICAL_TRANSFER_SUPERSEDED for the ten admitted
  paths; preserved planning files have no current acceptance authority.
- Owner: Phase 70 managed-runtime acceptance maintainers; Textus BoK Phase 8
  keeps its separately owned consumer implementation.
- Entry rule: The active Phase 70 accumulator provides the required managed
  runtime evidence.
- Completion rule: Record managed-server evidence and the exact separate BoK
  handoff without editing or accepting BoK source or consumer behavior.
- Update rule: Keep this parent stage CLOSED only while the checked transfer
  checklist below and its non-acceptance boundary remain accurate.

- [x] Record the historical Phase 70.1 planning transfer as superseded for
      the ten admitted paths; it grants no source, validation, or acceptance
      authority and does not accept Textus BoK consumer work.

Evidence:
- Preserved untracked Phase 70.1 planning files remain outside the current
  acceptance boundary.

## CA70-04: Validation and Closure

Stage Status:
- Current status: DONE
- Current slice: none
- Owner: CNCF contract and supplier-handoff maintainers
- Entry rule: CA70-01A receives independent review.
- Update rule: Keep this stage closed only after the retained contract/handoff,
  the active accumulator, independent review, and final release evidence reach
  a stable disposition; do not mark it closed while any checklist item remains
  unchecked.
- Completion rule: Parent contract/handoff and active accumulator evidence
  converge without accepting Textus BoK source or consumer behavior.

- [x] Complete independent review of the retained lifecycle contract and
      supplier-only Textus BoK handoff.
- [x] Record the superseded Phase 70.1 transfer boundary and ensure no parent
      receipt is represented as Textus BoK source or consumer acceptance.
- [x] Close the parent documentation boundary for this CA70-04A slice only
      after its required review and repository evidence; do not imply Phase
      70.1 completion.

Evidence:
- Independent Step Review receipt:
  `/private/tmp/cncf-p70-ca7001a-step-review.md` — PASS for the parent
  documentation boundary only. The receipt is not runtime validation or
  Textus BoK consumer acceptance. `P70-CA70-02A-VAL-014` remains provenance
  only.
- Full Phase review `P70-FULL-REVIEW-001` admitted only `CPB-P70-001`; Cycle 1
  removed the nine links to uncommitted Phase 70.1 artifacts, and the accepted
  focused re-review closed that blocker.
- The SBR2 focused receipt `P70-CA70-02A-VAL-SBR2-002` passed 16 tests and its
  independent re-review closed the three remaining runtime lifecycle and
  readiness blockers. The release manifest binds the final full-suite receipt.
  Textus BoK source and consumer acceptance remain excluded.
