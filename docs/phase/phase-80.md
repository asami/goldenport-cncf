# Phase 80: Extended Workflow Invocation and Integration Runtime

Status: planned

## Goal

Use the StateMachine API/SPI Runtime and first Skill-driven Workflow vertical slice completed in the Phase 77 -> 77.1 -> 77.2 sequence as a stable foundation. Add Workflow invocation, participant, and context integration extensions whose need has been demonstrated in operation.

Phase 80 is not a prerequisite for `sm-workflow Phase 1`. sm-workflow can begin implementing executable specifications when it receives the CNCF Phase 77.2 consumer handoff.

## Dependency

- CNCF Phase 77.2 closed and consumer handoff frozen, after Phases 77 and 77.1.
- Cozy Phase 62 generated Workflow ABI.
- Do not redefine the Phase 77 sequence's StateMachine API/SPI, `ActionExecution = Completed | Suspended | Failed`, durable Continuation/resume, or deterministic progression semantics.

## Baseline inherited from the Phase 77 sequence

Phase 80 does not count the following as new implementation work.

- Workflow ABI admission / ComponentFactory discovery.
- independent WorkflowInstance persistence contract.
- StateMachine Provided API / Required SPI provider runtime.
- Completed / Suspended / Failed handling.
- durable Continuation creation and typed resume.
- stale / duplicate result rejection.
- bounded deterministic `advance`.
- Generic Skill projection of semantic suspended SPI operations.
- minimum typed Start/Continuation/WorkOrder/Terminal protocol, including the
  initial abstract reasoning vocabulary, `MinimalPresentation`, and fail-closed
  Skill/Codex JSON encoding.
- deterministic test provider.
- `sm-workflow` consumer handoff.

These are completion conditions of the Phase 77 sequence, with Phase 77.2 as their final owner.

## Extension candidates

Phase 80 uses operational evidence to add only the extensions needed on the Phase 77 sequence foundation.

1. richer `WorkflowInvocationContract` projection.
2. bounded `ContextBundle / ContextReference / ContextSnapshot` ergonomics.
3. richer Completion / Evidence projection and validation.
4. AI / Human / remote participant integration using the same Phase 77.1 Continuation identity.
5. UI/client continuation retrieval and resumption ergonomics.
6. provider/invocation placement policy that remains outside Workflow semantics.
7. richer Generic Skill Workflow metadata beyond Phase 77.2's model-independent capability / complexity / risk / review hints.
8. future local/remote transport or Workflow-to-Workflow integration preparation where justified.

## Normative compatibility rule

There is no Workflow-wide Orchestration/Continuation mode and no semantic
`InvocationBinding = ORCHESTRATION | CONTINUATION` switch.

Direct/local execution and external continuation are provider/runtime placement choices for an admitted Required SPI operation. They do not change StateMachine / Workflow semantics.

Any historical Phase 80 addendum that describes a semantic ORCHESTRATION/CONTINUATION binding is superseded by the Phase 77 sequence and this document.

## Scheduling

Phase 80 is deliberately outside the critical path for `sm-workflow Phase 1`.

```text
Cozy Phase 62 (closed)
        ↓
CNCF Phase 77 -> Phase 77.1 -> Phase 77.2
        ↓
sm-workflow Phase 1 executable specification

        ↓ operational feedback

CNCF Phase 80 extended invocation/integration runtime
```

Phase 80 should be refined from the Phase 1 / skill connectivity work rather than implemented speculatively.

## Acceptance direction

Acceptance criteria are to be frozen when concrete extension candidates are selected from operational evidence. At minimum, all selected extensions must preserve Phase 77-sequence semantics and existing sm-workflow executable specifications.

## Non-goals

- Reimplementing the Phase 77 sequence.
- Blocking sm-workflow Phase 1.
- Creating a second Workflow DSL or Workflow-specific parallel StateMachine runtime.
- Semantic protocol-mode switches.
- Retry / Timeout and later runtime-control facilities already assigned to dedicated follow-up phases.
- sm-workflow application-specific Goal/Phase/Step policy.
