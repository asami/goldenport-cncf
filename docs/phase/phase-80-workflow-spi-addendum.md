# Phase 80 Addendum: Workflow SPI and Suspended Action Runtime

Status: planned / normative refinement

## Requirements

- Runtime consumes the next Action selected by admitted Workflow/StateMachine semantics.
- Handle typed `ActionExecution = Completed | Suspended | Failed` or equivalent.
- `advance` drains Completed actions without creating Skill/AI turns and returns on Suspended/terminal/policy boundaries.
- Persist Suspended Continuation with WorkflowRun identity, expected revision/ContextSnapshot, completion/evidence contract and idempotency data.
- `resume` validates typed Result and stale/duplicate conditions before completing the suspended Action.
- Admit/project Workflow SPI required operations from the generated CML ABI.
- Support local/direct, external-continuation, and deterministic test provider bindings without duplicating Workflow semantics.
- Generic Skill Workflow Support projects external suspended SPI operations to Skill commands/WorkOrders.
- Do not expose internal deterministic actions as Skill WorkOrders merely because the overall Workflow is driven from a Skill.

## Acceptance

A reference Workflow must execute internal build/test actions, suspend on an external review SPI operation, resume with a typed ReviewResult, execute internal closing/commit actions, and reach terminal state with no orchestration/continuation mode switch in the Workflow definition.
