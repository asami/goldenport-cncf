# Phase 64 Checklist - CML Composite StateMachine / Workflow Runtime Integration

status=planned
phase=[Phase 64 - CML Composite StateMachine / Workflow Runtime Integration](phase-64.md)

This checklist is the authoritative Phase 64 state ledger after Phase 64
starts. No stage starts before Phase 63.2 closes.

The acceptance path consumes, but never reinterprets or executes ahead of, the
Phase 63.2 post-commit handoff:

```text
CML -> normalize -> generate -> Composite/Workflow contract
Phase 63.2 CommittedTransition -> pure composite derivation / WorkflowInstance contract
Phase 77 -> ABI admission -> ComponentFactory -> Provider/Continuation runtime
```

## SWF-01: StateMachine / Workflow Semantic Inventory

- [ ] Inventory existing CML StateMachine grammar/model/generation/runtime.
- [ ] Inventory current CML Workflow syntax/model, if any.
- [ ] Inventory Phase 14 runtime Workflow concepts.
- [ ] Classify every proposed Workflow concept as:
  - existing StateMachine semantic;
  - general Composite StateMachine semantic;
  - mandatory Workflow specialization; or
  - runtime policy/infrastructure.
- [ ] Reject duplicate Workflow concepts that already exist in StateMachine.
- [ ] Register failing-first cross-repository acceptance specs.

## SWF-02: Composite StateMachine Contract

- [ ] Freeze Composite StateMachine identity/version.
- [ ] Define constituent-machine binding with stable role and machine identity.
- [ ] Distinguish constituent reference/coordination from ownership.
- [ ] Define higher-level composite state/configuration.
- [ ] Define how committed constituent transitions participate in composite
  progression.
- [ ] Reuse existing transition, trigger, predicate, and action/effect contracts
  wherever applicable.
- [ ] Define action identity, constituent/composite provenance, and causal
  order without introducing an executable Action algebra or Provider binding.
- [ ] Define nesting and source-location semantics.

## SWF-03: Composite Rule Admission and Minimal Workflow Specialization

- [ ] Evaluate process-instance identity against general composite instance
  semantics.
- [ ] Evaluate multi-subject correlation against general constituent binding.
- [ ] Evaluate durable waiting/progression against general composite semantics
  and runtime policy.
- [ ] Evaluate pending work, completion/cancellation, and process history.
- [ ] Add only semantics proven mandatory for Workflow and non-generalizable.
- [ ] Document the evidence for every Workflow-only addition.
- [ ] Keep rule evaluation pure. Do not add Provider execution, Operation/Job
  selection, Continuation, or Skill behavior to this stage.

## SWF-04: CML Generated Contract

- [ ] Consume Cozy/SimpleModeler generated Composite StateMachine contract
  metadata.
- [ ] Preserve composite id/version and constituent role/machine refs.
- [ ] Preserve composite state/configuration and transition/trigger identity.
- [ ] Preserve explicit constituent-transition bindings.
- [ ] Preserve Workflow specialization metadata only where required.
- [ ] Preserve source locations and ABI/version metadata.
- [ ] Preserve typed action semantic/compiler-binding metadata for the canonical
  Phase 64.2 `ExecProgram[UnitOfWorkOp, A]` target.
- [ ] Freeze constituent/composite action provenance and causal ordering without
  introducing a second Action algebra, an interpreter, or Provider binding.
- [ ] Leave generated Workflow API/SPI admission and automatic
  `ComponentFactory` bootstrap to Phase 77.
- [ ] Fail unsupported required semantics rather than infer model behavior.

## SWF-05: Pure CNCF Composite Derivation

- [ ] Evaluate only admitted generated composite definitions on the canonical
  pure-derivation path.
- [ ] Observe constituent committed transitions without bypassing Phase 63.2.
- [ ] Advance composite state/configuration deterministically.
- [ ] Ensure further domain mutation re-enters constituent StateMachine
  enforcement.
- [ ] Do not execute Actions, invoke Operations/Jobs, resolve Providers, or
  grow runtime-only Workflow language features ahead of CML and Phase 77.

## SWF-06: WorkflowInstance Persistence Contract

- [ ] Determine the general `CompositeStateMachineInstance` contract.
- [ ] Add `WorkflowInstance` specialization only if required by proven
  Workflow-only semantics.
- [ ] Define durable progression/history and constituent bindings.
- [ ] Define stable logical occurrence/idempotency identities and the SPI
  contract for persistence/recovery.
- [ ] Leave persistence implementation, duplicate delivery, external claim,
  resume, and Operation/Job submission to Phase 77.

## SWF-07: Observability and Compatibility

- [ ] Correlate CML source, composite definition, constituent machine,
  `CommittedTransition`, composite occurrence, and trace.
- [ ] Project higher-level composite state and constituent states separately.
- [ ] Preserve definition identity versus runtime occurrence identity.
- [ ] Preserve normal authorization without privilege amplification.
- [ ] Map Phase 14 raw-event/status-field behavior only through explicit
  compatibility adapters.
- [ ] Keep specialist workflow-engine integration explicit.
- [ ] Leave Provider/interpreter outcomes, Operation/Job traces, Continuation,
  and Skill projection observability to Phase 77.

## SWF-08: CML-First Contract Acceptance

- [ ] Start from a real CML Composite StateMachine containing multiple
  constituent StateMachines.
- [ ] Include a Workflow specialization using primarily the same composite
  structure.
- [ ] Parse/normalize through Cozy and generate through SimpleModeler.
- [ ] Prove the exact Phase 63.2 `CommittedTransition` causes valid composite
  derivation without bypassing constituent StateMachine authority.
- [ ] Prove any Workflow-only element is both mandatory and non-generalizable.
- [ ] Prove contract observability and compatibility.
- [ ] Do not claim `ComponentFactory` bootstrap, Provider/interpreter execution,
  Operation/Job delegation, duplicate handling, crash recovery, Continuation,
  or Skill behavior; Phase 77 proves those executable concerns.
- [ ] Record exact Cozy, generated ABI, and CNCF revisions.
