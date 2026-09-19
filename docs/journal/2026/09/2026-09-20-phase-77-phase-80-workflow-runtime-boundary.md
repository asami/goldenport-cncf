# Phase 77 / Phase 80 Workflow Runtime Boundary

Date: 2026-09-20
Status: normative planning clarification

## Decision

CNCF Phase 77 is the minimum complete Workflow runtime foundation and the direct prerequisite for sm-workflow Phase 1. Phase 77 must close with a stable sm-workflow consumer handoff.

Phase 80 is not a prerequisite for sm-workflow. It is a post-Phase-77 extension phase whose concrete scope is refined from sm-workflow executable-specification and skill connectivity feedback.

## Phase 77 owns

- generated ABI admission and ComponentFactory discovery
- StateMachine Provided API / Required SPI runtime
- ActionExecution Completed / Suspended / Failed
- independent durable WorkflowInstance contract
- durable Continuation and typed resume
- stale/duplicate rejection
- deterministic bounded advance
- Generic Skill projection of semantic suspended SPI operations
- deterministic test provider
- real Cozy fixture acceptance
- sm-workflow consumer handoff

## Phase 80 may extend

- richer invocation/context/evidence projections
- AI/Human/remote participant integration
- UI/client continuation ergonomics
- provider placement and transport integration
- additional Generic Skill metadata
- later integration facilities justified by operational evidence

## Superseded idea

Workflow-wide Orchestration/Continuation modes and semantic Action/Participant InvocationBinding switches are not part of the normative architecture. Local/direct versus external continuation is a provider/runtime placement concern beneath the same admitted StateMachine semantics.

This boundary keeps sm-workflow Phase 1 on the shortest path: Cozy 62 -> CNCF 77 -> sm-workflow executable specification.