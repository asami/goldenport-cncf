# Phase 80: Extended Workflow Invocation and Integration Runtime

Status: planned

Checklist: [Phase 80 delivery ledger](phase-80-checklist.md)

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
6. generic execution placement requirement/evidence, initially INLINE / DELEGATED as provider/runtime placement rather than StateMachine semantics.
7. generic execution independence requirement and execution-context identity/evidence sufficient to prove producer/reviewer separation.
8. richer Generic Skill Workflow metadata beyond Phase 77.2's model-independent capability / risk / review hints.
9. Requirement -> ExecutionEvidence conformance/admission for selected placement and independence constraints.
10. future local/remote transport or Workflow-to-Workflow integration preparation where justified.
11. minimum execution-interface capability and dispatch integration selected below for sm-workflow Phase 9; richer negotiation, context-budget-aware routing and participant generalization remain later candidates. Application escalation policy belongs to sm-workflow.

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

## Minimum slice for sm-workflow Phase 5

Operational feedback from sm-workflow requires a small Phase 80 slice before sm-workflow Phase 5 can close practical execution routing:

- extend generic ExecutionRequirement with model/provider-independent placement and independence requirements;
- extend ExecutionEvidence with the actual execution placement/context identity needed to validate those requirements;
- preserve concrete model/provider/effort as host/runtime mapping and evidence only;
- allow INLINE execution by the current participant without a child AI invocation;
- allow REQUIRED independence to reject a completion that cannot demonstrate a distinct permitted execution context.

This minimum slice must remain application-neutral. TRIVIAL, PROGRAMMING / ENGINEERING, and software-development context-footprint classification remain sm-workflow policy, not CNCF vocabulary.

## Acceptance direction

For the minimum slice, prove JSON/typed round-trip compatibility, INLINE and DELEGATED evidence, independence conformance/rejection, and backward-compatible defaults for existing Phase 77.2 consumers. Later Phase 80 work may add richer capability negotiation, fallback/escalation, context/resource policy, and participant integration based on sm-workflow Phase 6 evidence.

All selected extensions must preserve Phase 77-sequence StateMachine semantics and existing sm-workflow executable specifications.

## Selected delivery: AI execution provider interface (2026-10-30)

Planning source: [architecture journal](https://github.com/asami/goldenport-cncf/blob/main/docs/journal/2026/10/2026-10-30-ai-execution-provider-interface-phase80-phase9.md).
Consumer: [sm-workflow Phase 9](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-9.md).
Existing contract: [Workflow execution disposition and observation](../spec/workflow-execution-disposition.md).

Deliver a bounded review route first: supplied diff, specification and relevant
validation evidence -> selected AI executor -> typed review result -> existing
completion/Admission. This is the next selected integration slice, not a claim
that the earlier minimum slice or provider connectivity is already complete.

### Delivery ownership

| Boundary | Owner and planned deliverable |
| --- | --- |
| WorkOrder, Continuation, result, execution Evidence | CNCF; extend existing generic contracts only for demonstrated gaps |
| Classification, model-selection policy, convergence, Takeover | sm-workflow Phase 9 |
| Effectful dispatch connection | Owning execution runtime through existing Required-SPI/UoW; connect the selected adapter and return observations to the pure Harness |
| Actual API invocation | textus-ai-runtime or an available equivalent execution adapter |
| Agent request/result transport | Execution environment's Continuation adapter, using existing issuance and one-shot completion |

`WorkflowExecutionHarness` currently selects and records observations; it invokes
no provider. The journal's dispatch direction is a target connection, not a
description of an already effectful Harness. Keep that pure boundary and identify
the actual invoking runtime and adapter entry point in the implementation handoff.

### Main-route-first sequence

| Slice | Work | Observable exit / handoff |
| --- | --- | --- |
| P80-A: contract inventory | Compare existing Harness, Required-SPI/UoW, registry/profile metadata, Continuation and Evidence contracts; reconcile earlier minimum-slice evidence. Promote the selected interface contract into spec/design before code. | A bounded gap map and concrete caller -> dispatcher -> adapter -> completion path; existing accepted behavior is reused. |
| P80-B: minimum connected dispatch | Represent API Direct / Agent Continuation capability using existing metadata where possible; connect one bounded review requirement to the two adapter interfaces. | Executable generic scenarios return typed results/actual execution observations through the existing completion path; hand usable producer interfaces to Phase 9 R1 without waiting for all Phase 80 candidates. |
| P80-C: result/lifecycle acceptance | Exercise outcome distinctions, one-shot result handling, context independence and restart/result collection at the existing boundaries. | Focused producer evidence and exact supported/unsupported capabilities accompany the Phase 9 handoff. Real provider connection is qualified by Phase 9 R1; mocks alone do not establish it. |

### Acceptance constraints for the selected slices

- API Direct / Agent Continuation describe interface capability, independently
  of INLINE / DELEGATED placement and OPTIONAL / REQUIRED independence. Do not
  add a Workflow semantic mode or assume every combination is supported.
- Provider interchange requires the capabilities of the actual work. The first
  review uses supplied inputs and a typed result; API inference alone does not
  establish repository exploration, editing or patch-application capability.
- Preserve COMPLETED, DECLINED, BLOCKED, UNAVAILABLE and FAILED, including
  FAILED/UncertainExecution. A proven nonstart and a lost response after start
  must remain distinguishable. Uncertainty does not authorize blind replay or
  Takeover; reuse existing retained-result/recovery handling.
- Preserve logical work identity and history across successive assignments.
  Each issued Continuation is completed at most once; duplicate/late results
  cannot complete a replacement assignment. New assignments use existing
  issuance and correlation, not reuse of an already completed Continuation.
- The existing Harness has a finite primary/alternate selection contract.
  Application Takeover must not silently turn it into an unbounded retry loop.
  Identify any genuinely missing generic continuation capability for Phase 9 R2;
  keep convergence and the shared repair budget in sm-workflow.
- Independent review receives the diff, specification and necessary validation
  context without the implementer's conversation. A distinct permitted execution
  context may use the same model; model change alone does not prove independence.
- Record actual interface, provider/profile, execution context, issued work and
  result correlation using existing Evidence. Missing usage remains unknown.
  Selection alone does not establish invocation or semantic acceptance.
- Git/SBT, publication and command authority remain with their existing runtime
  owners. No adapter gains free-form command authority through this work.

### Scope and preservation

Complete the selected checklist items with linked executable evidence; reconcile
earlier minimum-slice obligations against retained evidence before Phase closure.
Unselected extension candidates do not become additional completion gates.
All Dot/Codex/OpenClaw adapters, mandatory model benchmarks and Local LLM setup
are outside this first delivery. Do not add a second recovery protocol, repair
ledger, management hashes, repeated internal checks or AI monitoring calls.

This planning update changes no live Workflow, Session, issued request, profile,
accepted result, history, repair count or validation result. Producer integration
does not require restarting existing consumers or rerunning successful work to
reconstruct evidence.

## Non-goals

- Reimplementing the Phase 77 sequence.
- Blocking sm-workflow Phase 1.
- Creating a second Workflow DSL or Workflow-specific parallel StateMachine runtime.
- Semantic protocol-mode switches.
- Retry / Timeout and later runtime-control facilities already assigned to dedicated follow-up phases.
- sm-workflow application-specific Goal/Phase/Step policy.
