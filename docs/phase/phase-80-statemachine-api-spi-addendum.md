# Phase 80 Addendum: StateMachine API/SPI Runtime Foundation

Status: planned / normative refinement

## Goal

Phase 80のWorkflow SPI/Continuation runtimeをStateMachine API/SPI runtimeの最初のvertical sliceとして実装し、Workflow専用の並行機構を作らない。

## Requirements

- StateMachine Runtime dispatches Provided API operations and resolves Required SPI providers.
- StateMachine Action execution handles Completed/Suspended/Failed typed outcomes.
- External SPI provider execution persists durable Continuation and supports typed resume.
- Provider binding supports at least local/direct, external continuation and deterministic test forms at the contract level.
- Workflow Runtime consumes/projects StateMachine API/SPI rather than duplicating it.
- Generic Skill Workflow Support projects suspended external StateMachine/Workflow SPI operations into Skill commands/WorkOrders.
- Runtime identity/correlation/provider contracts remain extensible to future assemble API/SPI binding and local/REST transport selection.

## Acceptance

Implement the current Skill-driven Software Development Workflow scenario through the generic StateMachine foundation: internal deterministic actions execute locally, semantic Review SPI suspends to the Skill provider, typed result resumes the StateMachine, and closing completes without Workflow-specific protocol mode logic.

## Follow-up

Full assemble binding, Workflow-to-Workflow generated proxy, REST Workflow connector and UI/client StateMachine integration remain later phases after this foundation is stable.
