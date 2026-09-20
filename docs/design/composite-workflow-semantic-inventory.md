# Composite StateMachine / Workflow Semantic Inventory

Status: accepted SWF-01 semantic inventory for Phase 64
Date: 2026-09-20

## Authority and boundary

This document is the authoritative SWF-01 inventory for Phase 64. It classifies
each required concern exactly once as StateMachine reuse, Composite-general,
minimal Workflow-only, or runtime policy/later ownership. The Phase 64 document
and checklist remain the closure authority; this inventory does not close any
checklist item by itself.

Phase 64 consumes two accepted upstream contracts. It consumes the Phase 63.1
local atomic-execution contract and the Phase 63.2 `CommittedTransition` only
after the constituent commit. Phase 64 does not reopen, reinterpret, or execute
ahead of either contract. The downstream semantic handoff is the existing
`ExecProgram` boundary for logical action composition, followed by Phase 64.2
planning/interpreter/production alignment and the Phase 77 consumer boundary.

## Exclusive four-way inventory

Every concern in this table belongs to exactly one classification. An excluded
claim is a boundary, not an unrecorded implementation task.

| Classification | Required concerns | Immediate owner | Upstream boundary | Downstream boundary | Excluded implementation claim |
| --- | --- | --- | --- | --- | --- |
| **StateMachine reuse** | Constituent transition selection; guards and transition identity; deterministic ordering; candidate/effect planning; the committed-transition input boundary. | Existing StateMachine semantics and Phase 63.1 for selection/candidate/local atomic execution; Phase 63.2 for the post-commit `CommittedTransition` envelope. Phase 64 consumes these inputs. | Cozy's typed generated StateMachine contract, Phase 63.1 local atomic execution, and Phase 63.2's after-commit envelope. | Composite derivation consumes the committed input; Phase 64.2 may plan and interpret the resulting `ExecProgram`. | No second selector, direct constituent-state mutation, raw event/status inference, pre-commit Workflow trigger, or reopening of Phase 63.1/63.2. |
| **Composite-general** | Stable constituent role/machine identity; configuration schema; pure deterministic composite-state derivation; fail-closed unmapped/ambiguous outcomes; derived-transition identity; sequential causation; constituent/composite action ordering and provenance through the existing `ExecProgram` boundary. | Phase 64 owns the minimum consumer semantic boundary. Cozy owns the producer's generated model semantics; CNCF consumes the typed contract without reparsing CML. | Cozy-generated typed semantic contract plus the committed constituent transition supplied by Phase 63.2. | Phase 64.2 owns planner/test-interpreter/production alignment; Phase 77 consumes the semantic handoff for its API/SPI and runtime boundary. | No CML grammar or normalization, new `ActionOp` algebra, provider execution, durable runtime behavior, generated API/SPI admission, or general-purpose Workflow engine. |
| **Minimal Workflow-only** | Profile/definition identity; non-durable instance identity; current lifecycle/progression meaning; correlation/causation only where Composite StateMachine semantics cannot represent it. | Phase 64 owns the minimum Workflow semantic specialization and its typed handoff; Cozy remains the producer of any CML-declared profile metadata. | CML-first generated profile/definition metadata and the Phase 64 composite binding from a post-commit `CommittedTransition`. | Phase 77 owns the durable WorkflowInstance/API/SPI/Continuation consumer contract; `sm-workflow` supplies later application payloads. | No independently durable persistence, revision/history, suspension, continuation/resume, provider behavior, public protocol, or Workflow-only concept representable as Composite StateMachine semantics. |
| **Runtime policy or later ownership** | CML grammar/normalization and producer semantics (Cozy); `ExecProgram` planning, test interpreter, and production alignment (Phase 64.2); generated API/SPI admission, `ComponentFactory` bootstrap, Provider/Action execution, durable persistence/revision/history, protocol, and continuation/resume (Phase 77); 2PC, compensation, and recovery (Phase 85). | The named owner in parentheses owns each concern. None is implemented by this SWF-01 slice. | The accepted generated typed contract, existing `ExecProgram` boundary, and Phase 63.1/63.2 inputs are consumed as-is. | Each named later owner receives only the contract assigned to it; Phase 77 receives the minimum Phase 64 semantic handoff. | No syntax invention, planner/interpreter implementation, bootstrap/provider execution, durable behavior, public protocol, 2PC, compensation, recovery, or deployment/runtime expansion here. |

## Non-negotiable semantic fence

- Workflow is a Composite StateMachine specialization/profile, not a second
  language or engine.
- Constituent machines retain transition authority. No action executes before
  the commit whose `CommittedTransition` causally enables it.
- `ExecProgram` remains the shared executable boundary. This inventory does not
  introduce a parallel action algebra.
- Semantic identity is distinct from durable storage and runtime policy. The
  non-durable Workflow identity in the third row does not pre-admit Phase 77
  persistence, revision/history, continuation, or protocol behavior.
- Unmapped or ambiguous generated composite configuration fails closed; this
  inventory does not authorize a priority fallback or inferred meaning.

## Handoff conclusion

SWF-01 is complete as a classification boundary when the Phase 64 closure
authority accepts this inventory. The Phase 64 document and checklist retain
closure authority, while this record is the authoritative semantic inventory
for SWF-01 and the reference for SWF-02 through SWF-08.

## References

- [Phase 64](../phase/phase-64.md) and [Phase 64 Checklist](../phase/phase-64-checklist.md)
- [Phase 63.1](../phase/phase-63.1.md) and [Phase 63.2](../phase/phase-63.2.md)
- [UnitOfWork Program Planning](unitofwork-program-planning.md)
- [StateMachine Boundary Contract](statemachine-boundary-contract.md)
- [Composite StateMachine / Workflow Runtime Provisional Specification](../notes/statemachine-workflow-alignment-provisional-specification.md)
- [StateMachine / Composite StateMachine / Workflow Runtime v1 Decisions](../notes/statemachine-composite-runtime-v1-decisions.md)
