# Phase 64 Checklist - CML Composite StateMachine / Workflow Runtime Integration

status=planned
phase=[Phase 64 - CML Composite StateMachine / Workflow Runtime Integration](phase-64.md)

This checklist is the authoritative Phase 64 state ledger after Phase 64
starts. No stage starts before Phase 63 closes.

The acceptance path must preserve continuity with Phase 63:

```text
CML -> normalize -> generate -> ComponentFactory -> CNCF runtime
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
- [ ] Define nesting and source-location semantics.

## SWF-03: Minimal Workflow Specialization

- [ ] Evaluate process-instance identity against general composite instance
  semantics.
- [ ] Evaluate multi-subject correlation against general constituent binding.
- [ ] Evaluate durable waiting/progression against general composite semantics
  and runtime policy.
- [ ] Evaluate pending work, completion/cancellation, and process history.
- [ ] Add only semantics proven mandatory for Workflow and non-generalizable.
- [ ] Document the evidence for every Workflow-only addition.

## SWF-04: CML Generation, ABI, and Bootstrap

- [ ] Consume Cozy/SimpleModeler generated Composite StateMachine definitions.
- [ ] Preserve composite id/version and constituent role/machine refs.
- [ ] Preserve composite state/configuration and transition/trigger identity.
- [ ] Preserve explicit constituent-transition bindings.
- [ ] Preserve Workflow specialization metadata only where required.
- [ ] Preserve source locations and ABI/version metadata.
- [ ] Bootstrap generated composite/workflow definitions automatically through
  ComponentFactory.
- [ ] Fail unsupported required semantics rather than infer runtime behavior.

## SWF-05: CNCF Composite Execution

- [ ] Execute only admitted generated composite definitions on the canonical
  path.
- [ ] Observe constituent committed transitions without bypassing Phase 63.
- [ ] Advance composite state/configuration deterministically.
- [ ] Invoke declared Operations through normal CNCF authorization and
  idempotency boundaries.
- [ ] Delegate asynchronous work to JobEngine.
- [ ] Ensure further domain mutation re-enters constituent StateMachine
  enforcement.
- [ ] Do not grow runtime-only Workflow language features ahead of CML.

## SWF-06: Durable Instance / Recovery

- [ ] Determine the general `CompositeStateMachineInstance` contract.
- [ ] Add `WorkflowInstance` specialization only if required by proven
  Workflow-only semantics.
- [ ] Define durable progression/history and constituent bindings.
- [ ] Define concurrency and duplicate-delivery handling.
- [ ] Close the crash window around progression decision and Operation/Job
  submission.
- [ ] Use stable logical occurrence/idempotency identities.
- [ ] Define recovery/resume and version migration.

## SWF-07: Observability and Compatibility

- [ ] Correlate CML source, composite definition, constituent machine,
  `CommittedTransition`, composite occurrence, Operation, Job, and trace.
- [ ] Project higher-level composite state and constituent states separately.
- [ ] Preserve definition identity versus runtime occurrence identity.
- [ ] Preserve normal authorization without privilege amplification.
- [ ] Map Phase 14 raw-event/status-field behavior only through explicit
  compatibility adapters.
- [ ] Keep specialist workflow-engine integration explicit.

## SWF-08: CML-First Cross-Repository Acceptance

- [ ] Start from a real CML Composite StateMachine containing multiple
  constituent StateMachines.
- [ ] Include a Workflow specialization using primarily the same composite
  structure.
- [ ] Parse/normalize through Cozy and generate through SimpleModeler.
- [ ] Bootstrap through ComponentFactory without hand-written canonical runtime
  definitions.
- [ ] Prove constituent committed transitions cause valid composite
  progression.
- [ ] Prove composite execution cannot bypass constituent StateMachine
  authority.
- [ ] Prove any Workflow-only element is both mandatory and non-generalizable.
- [ ] Prove Operation/Job delegation, duplicate handling, crash recovery,
  observability, and compatibility.
- [ ] Record exact Cozy, generated ABI, and CNCF revisions.
