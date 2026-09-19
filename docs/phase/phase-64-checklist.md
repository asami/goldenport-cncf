# Phase 64 Checklist - Minimal Composite StateMachine / Workflow Foundation

status=planned
phase=[Phase 64](phase-64.md)

Phase 64 closes only the minimum Composite StateMachine / Workflow foundation required by Phase 77 and sm-workflow Phase 1.

## SWF-01 Semantic inventory
- [ ] Classify required concepts as StateMachine reuse, Composite-general, minimal Workflow-only, or runtime-policy.
- [ ] Reject speculative advanced Workflow features from this Phase.

## SWF-02 Composite identity and derivation
- [ ] Freeze constituent role/identity and configuration schema.
- [ ] Freeze deterministic composite-state derivation.
- [ ] Freeze derived composite-transition identity and causal correlation.

## SWF-03 Admission
- [ ] Consume generated CML contracts without reparsing CML.
- [ ] Fail closed for ambiguous/unsupported composite configuration.
- [ ] Preserve source/model identity.

## SWF-04 Action composition
- [ ] Preserve constituent/composite action ordering and provenance.
- [ ] Use the shared typed executable-program boundary.
- [ ] Delegate local atomic execution to 64.1 and planner/interpreter/testability to 64.2.

## SWF-05 Minimal Workflow specialization
- [ ] Freeze semantic WorkflowDefinitionIdentity / WorkflowInstanceIdentity.
- [ ] Freeze lifecycle/current progression and correlation/causation semantics needed by Phase 77.
- [ ] Leave independently durable revision/history/store/suspension semantics to Phase 77.
- [ ] Add no Workflow-only concept that can be represented as Composite StateMachine semantics.

## SWF-06 ComponentFactory admission
- [ ] Admit generated Composite StateMachine/Workflow definitions through ComponentFactory.
- [ ] Do not accept handwritten definitions as canonical end-to-end evidence.

## SWF-07 Real fixture
- [ ] Prove constituent committed transition -> derived composite transition.
- [ ] Prove typed lower/upper action composition and deterministic test boundary.
- [ ] Prove minimal Workflow progression required for Phase 77 handoff.

## SWF-08 Handoff and deferral
- [ ] Freeze exact Phase 77 consumer handoff.
- [ ] Record that durable WorkflowInstance revision/history/store, ActionExecution, Continuation/resume, API/SPI provider runtime, typed protocol Value Objects/JSON encoding and Skill projection are Phase 77 concerns.
- [ ] Record Retry/Timeout and other runtime-control features as later phases.
- [ ] Record 2PC/compensation/recovery as Phase 85.
- [ ] Record Workflow-to-Workflow orchestration and advanced integration as later work.
- [ ] If Phase 63 closure reveals hierarchy/history runtime gaps, require the dedicated follow-up before relying on those semantics.

## Closure

Phase 64 is complete when the generated Composite StateMachine / minimal Workflow semantics and real fixture are sufficient for 64.1/64.2 execution/test foundations and the Phase 77 handoff, without implementing advanced operational Workflow facilities.
