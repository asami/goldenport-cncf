# Phase 70 Checklist - Post-Assembly Component Activation

status=in_progress
current_stage=CA70-01
current_slice=CA70-01A
phase=[Phase 70 - Post-Assembly Component Activation](phase-70.md)
successor=[Phase 70.1 - Protected Component Activation Lifecycle](phase-70.1.md)
contract=[Component Activation Lifecycle Specification](../spec/component-activation-lifecycle.md)

This checklist is the authoritative Phase 70 state ledger after Phase 70
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 70 is a separately
selectable supplier branch after Phase 55 and must not start while another CNCF
Phase is active.

## CA70-01: Contract and Failing-First Evidence

Stage Status:
- Current status: IN_PROGRESS
- Current slice: CA70-01A
- Owner: CNCF Component, Subsystem assembly, server lifecycle, readiness, configuration, and diagnostics maintainers
- Entry rule: Phase 55 is closed and no other CNCF Phase is active.
- Update rule: The Phase status/current-stage/current-slice fields and this checklist change only for evidence-backed stable work-item state changes; no completion may be recorded before independent review and all required evidence.
- Completion rule: API, lifecycle point, modes, ordering, timeout, readiness, failure, cleanup, and diagnostic contracts are frozen.

CA70-01A disposition: FROZEN_CANDIDATE. The normative component activation
specification records the complete R1-R9 contract candidate, including the
public opt-in/context boundary, post-assembly placement, deterministic
once-only ordering, component-boundary-only dependency access, mode isolation,
controlled-test admission, timeout/cancellation, failure/readiness/cleanup,
redaction, compatibility, and consumer-neutral handoff boundaries.

CA70-01B and the protected runtime accumulator are transferred to Phase 70.1.
CA70-01 remains IN_PROGRESS until the retained contract/handoff receives its
own independent review and acceptance.

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

Decision `P70-ACTIVATION-SPLIT-001`: the user selected Phase 70.1 as the
child of the approved `phase-split`. Binding request identity:
`fe4ba4860e492c68867d5690dc5776fe5131d665a16e11801623749c6b66173c`;
resolved-request SHA-256:
`44868d7a2dda29d352489acc3f5a68cdf2041aa2515546635c4835efe517464c`.
The unaccepted runtime source/test accumulator and receipt
`P70-CA70-02A-VAL-014` are transferred to Phase 70.1, with the receipt kept
as validation provenance only. Phase 70 retains CA70-01A's contract/handoff;
it neither accepts the runtime delta nor changes Textus BoK Phase 8 ownership.

- [ ] Inventory component creation, bootstrap, context injection,
      runtime-service binding, server listener/readiness, and shutdown order.
- [ ] Prove that `Component.initialize` occurs before the complete consumer
      dependency graph is safely available.
- [ ] Freeze the opt-in capability/API and prohibit arbitrary configured
      startup Operation dispatch.
- [ ] Freeze managed server, command, client, emulator, and controlled-test
      execution-mode behavior.
- [ ] Freeze deterministic order, once-only identity, timeout/cancellation,
      structured failure, readiness, cleanup, and diagnostic redaction.
- [ ] Record the executable-evidence matrix and consumer handoff boundary that
      Phase 70.1 must re-baseline without treating its runtime evidence as a
      parent acceptance.

Evidence:
- CA70-01A R1-R9 contract candidate:
  `docs/spec/component-activation-lifecycle.md`.
- CA70-01B failing-first executable specification: Transferred to Phase 70.1.

## CA70-02: Runtime Implementation

Stage Status:
- Current status: CLOSED
- Transfer disposition: TRANSFERRED_TO_PHASE_70.1; this is a parent-ledger
  closure, not runtime acceptance.
- Owner: Phase 70.1 protected runtime activation maintainers
- Entry rule: Phase 70.1 re-baselines the transferred accumulator against the
  reviewed parent contract/handoff.
- Completion rule: No parent runtime work remains; the child accepts the
  protected runtime delta through its own compliant review and release route.
- Update rule: Keep this parent stage CLOSED only while the checked transfer
  checklist below and its non-acceptance boundary remain accurate.

- [x] Parent runtime implementation responsibility is relocated to
      [Phase 70.1 Checklist](phase-70.1-checklist.md), ACT70.1-02; this
      records transfer only and does not accept the runtime delta.

Evidence:
- See [Phase 70.1 Checklist](phase-70.1-checklist.md), ACT70.1-02.
- `P70-CA70-02A-VAL-014` is provenance only, not acceptance evidence.

## CA70-03: Managed Runtime and Consumer Acceptance

Stage Status:
- Current status: CLOSED
- Transfer disposition: TRANSFERRED_TO_PHASE_70.1; this is a parent-ledger
  closure, not managed-runtime or consumer acceptance.
- Owner: Phase 70.1 runtime acceptance maintainers; Textus BoK Phase 8 keeps
  its separately owned consumer implementation.
- Entry rule: Phase 70.1 protected runtime work is accepted.
- Completion rule: No parent managed-runtime task remains; the child records
  generic managed-server evidence and the exact separate BoK handoff, without
  editing or accepting BoK source.
- Update rule: Keep this parent stage CLOSED only while the checked transfer
  checklist below and its non-acceptance boundary remain accurate.

- [x] Parent managed-runtime and consumer-acceptance responsibility is
      relocated to [Phase 70.1 Checklist](phase-70.1-checklist.md),
      ACT70.1-03 and ACT70.1-04; this records transfer only and does not
      accept runtime or Textus BoK consumer work.

Evidence:
- See [Phase 70.1 Checklist](phase-70.1-checklist.md), ACT70.1-03 and
  ACT70.1-04.

## CA70-04: Validation and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF contract and supplier-handoff maintainers
- Entry rule: CA70-01A receives independent review.
- Update rule: Update this stage only when the retained contract/handoff
  review and repository evidence reach a stable disposition; do not mark it
  closed while any checklist item remains unchecked.
- Completion rule: Parent contract/handoff evidence converges without accepting
  the transferred Phase 70.1 runtime delta.

- [ ] Complete independent review of the retained lifecycle contract and
      supplier-only Textus BoK handoff.
- [ ] Record the Phase 70.1 transfer boundary and ensure no parent receipt is
      represented as runtime or consumer acceptance.
- [ ] Close the parent documentation boundary only after its required review
      and repository evidence; do not imply Phase 70.1 completion.

Evidence:
- Pending.
