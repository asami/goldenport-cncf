# sm-workflow Execution Routing Feedback into CNCF Phase 80

Date: 2026-10-03
Status: design decision

## Trigger

sm-workflow practical-use planning exposed a generic gap in the Phase 77.2 ExecutionRequirement contract. Reasoning/capability/risk can describe semantic demand, but practical Skill execution also needs to distinguish current-context execution, delegated execution, and review independence without encoding concrete model/provider or ChatGPT/Codex task topology.

## Findings

The application-side assessment remains domain-specific. sm-workflow can classify TRIVIAL, PROGRAMMING / ENGINEERING, and context footprint. Those concepts should not be promoted into CNCF.

The resolved execution concepts are generic:

- whether the current execution participant may execute inline or another context/provider is required;
- whether independence from a producer context is required;
- evidence identifying how and where the work actually executed.

This applies beyond software development and therefore belongs at the CNCF Workflow protocol/runtime boundary.

## Phase decision

Do not create a new CNCF phase. Phase 80 already exists for operationally demonstrated Workflow invocation, participant, placement and context-integration extensions, so it is the correct owner.

A minimum Phase 80 slice is pulled forward to support sm-workflow Phase 5 practical completion: placement, independence, execution-context evidence, and conformance/rejection.

Broader provider capability negotiation, fallback/escalation, context-budget-aware routing and cross-participant generalization remain later Phase 80 refinement driven by sm-workflow Phase 6 operational evidence.

The Phase 77 sequence remains the stable baseline; this is an additive generic extension rather than a second Workflow protocol or a semantic ORCHESTRATION/CONTINUATION mode.
