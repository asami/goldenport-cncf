# Phase 70 Checklist - Post-Assembly Component Activation

status=planned
phase=[Phase 70 - Post-Assembly Component Activation](phase-70.md)

This checklist is the authoritative Phase 70 state ledger after Phase 70
starts. Only one stage may be `IN_PROGRESS` at a time. Phase 70 is a separately
selectable supplier branch after Phase 55 and must not start while another CNCF
Phase is active.

## CA70-01: Contract and Failing-First Evidence

Stage Status:
- Current status: OPEN
- Owner: CNCF Component, Subsystem assembly, server lifecycle, readiness, configuration, and diagnostics maintainers
- Entry rule: Phase 55 is closed and no other CNCF Phase is active.
- Completion rule: API, lifecycle point, modes, ordering, timeout, readiness, failure, cleanup, and diagnostic contracts are frozen.

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
- [ ] Register failing-first executable specifications for every acceptance
      group and the Textus BoK consumer handoff.

Evidence:
- Pending.

## CA70-02: Runtime Implementation

Stage Status:
- Current status: OPEN
- Owner: CNCF Component/Subsystem/runtime maintainers
- Entry rule: CA70-01 is DONE.
- Completion rule: The accepted activation contract is implemented without changing non-opt-in or direct-command behavior.

- [ ] Add the typed opt-in component activation capability and context.
- [ ] Invoke activation only after complete component bootstrap, injection, and
      runtime-service binding.
- [ ] Gate managed server readiness and ordinary application traffic on
      successful required activation, without assuming whether the host binds
      its listener before or after activation.
- [ ] Execute activating components exactly once in deterministic order.
- [ ] Apply bounded timeout/cancellation and reject detached background
      completion as startup success.
- [ ] Route failure through structured diagnostics and ordinary Subsystem
      cleanup without leaking secrets or private locators.
- [ ] Keep direct command/client modes activation-free and require explicit
      controlled-test admission for test/emulator activation.
- [ ] Preserve existing shutdown, datastore, Job, Event, Workflow,
      configuration, user-mode, and authorization contracts.

Evidence:
- Pending.

## CA70-03: Managed Runtime and Consumer Acceptance

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime plus Textus BoK consumer maintainers
- Entry rule: CA70-02 is DONE.
- Completion rule: Generic server evidence and the Textus BoK consumer prove readiness-safe activation end to end.

- [ ] Prove no-opt-in compatibility and one-/multi-component successful
      activation with exact ordering and duration evidence.
- [ ] Prove public component dependency access during activation through normal
      CNCF selectors/APIs.
- [ ] Prove failed and timed-out activation prevent readiness and clean owned
      runtime resources exactly once.
- [ ] Prove command/client exclusion and explicitly admitted controlled-test
      execution.
- [ ] Run the Textus BoK Phase 8 startup-bootstrap fixture with SIE and verify
      complete publication before readiness plus invalid-metadata failure.

Evidence:
- Pending.

## CA70-04: Validation and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF release maintainers
- Entry rule: CA70-03 is DONE.
- Completion rule: Validation, documentation, review, downstream evidence, version, and release commit converge.

- [ ] Run focused and full serialized CNCF validation and representative
      downstream Textus BoK validation.
- [ ] Promote the lifecycle/readiness contract into stable design/specification
      and operator/developer guidance.
- [ ] Complete independent review and bounded repair/re-review when required.
- [ ] Record exact Textus BoK consumer handoff, artifact/version identity, and
      release evidence.
- [ ] Create the Phase release commit and mark all Phase/checklist/strategy
      ledgers closed only after every acceptance condition passes.

Evidence:
- Pending.
