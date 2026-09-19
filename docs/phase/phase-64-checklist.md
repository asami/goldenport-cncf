# Phase 64 Checklist - Minimal Composite StateMachine / Workflow Foundation

status=planned
phase=[Phase 64](phase-64.md)

Phase 64 closes only the minimum Composite StateMachine / Workflow foundation required by Phase 77 and sm-workflow Phase 1. No stage starts before Phase 63.2 closes.

The acceptance path consumes, but never reinterprets or executes ahead of, the Phase 63.2 post-commit handoff:

```text
CML -> normalize -> generate -> Composite/Workflow semantic contract
Phase 63.2 CommittedTransition -> composite derivation / WorkflowInstance contract
Phase 77 -> ABI admission -> ComponentFactory -> Provider/Continuation runtime
```

## SWF-01 Semantic inventory
- [ ] Classify required concepts as StateMachine reuse, Composite-general, minimal Workflow-only, or runtime-policy.
- [ ] Reject speculative advanced Workflow features from this Phase.

## SWF-02 Composite identity and derivation
- [ ] Freeze constituent role/identity and configuration schema.
- [ ] Freeze deterministic composite-state derivation.
- [ ] Freeze derived composite-transition identity and causal correlation.
- [ ] Consume the Phase 63.2 `CommittedTransition` only as the post-commit constituent-transition input.

## SWF-03 Admission
- [ ] Consume generated CML contracts without reparsing CML.
- [ ] Fail closed for ambiguous/unsupported composite configuration.
- [ ] Preserve source/model identity.
- [ ] Preserve the typed generated semantic contract without generated API/SPI admission or `ComponentFactory` bootstrap; those are Phase 77 concerns.

## SWF-04 Action composition
- [ ] Preserve constituent/composite action ordering and provenance.
- [ ] Use the shared typed executable-program boundary.
- [ ] Delegate local atomic execution to 64.1 and planner/interpreter/testability to 64.2.

## SWF-05 Minimal Workflow specialization
- [ ] Freeze semantic WorkflowDefinitionIdentity / WorkflowInstanceIdentity.
- [ ] Freeze lifecycle/current progression and correlation/causation semantics needed by Phase 77.
- [ ] Leave independently durable revision/history/store/suspension semantics to Phase 77.
- [ ] Add no Workflow-only concept that can be represented as Composite StateMachine semantics.

## SWF-06 Generated semantic-contract handoff
- [ ] Freeze a CML-first generated Composite StateMachine/Workflow semantic-contract handoff without CML reparsing.
- [ ] Leave generated API/SPI admission and `ComponentFactory` bootstrap to Phase 77.
- [ ] Do not accept handwritten definitions as canonical end-to-end evidence.

## SWF-07 Real fixture
- [ ] Prove Phase 63.2 committed constituent transition -> derived composite transition.
- [ ] Prove typed lower/upper action composition and deterministic test boundary.
- [ ] Prove minimal Workflow progression required for Phase 77 handoff.

## SWF-08 Handoff and deferral
- [ ] Freeze exact Phase 77 consumer handoff.
- [ ] Record that generated API/SPI admission, ActionExecution, Provider runtime, Continuation/resume, the minimum schema-versioned fail-closed Continuation JSON wire contract, and the minimum Skill projection required by the sm-workflow vertical slice are Phase 77 concerns.
- [ ] Record broad generic Workflow protocol/JSON surfaces, parent/child Workflow composition, and orchestration extensions as later-phase concerns.
- [ ] Record Retry/Timeout and other runtime-control features as later phases.
- [ ] Record 2PC/compensation/recovery as Phase 85.
- [ ] Record Workflow-to-Workflow orchestration and advanced integration as later work.
- [ ] If Phase 63.2 closure reveals hierarchy/history runtime gaps, require the dedicated follow-up before relying on those semantics.

## Closure

Phase 64 is complete when the generated Composite StateMachine / minimal Workflow semantics and real fixture are sufficient for 64.1/64.2 execution/test foundations and the Phase 77 handoff, without implementing advanced operational Workflow facilities.
