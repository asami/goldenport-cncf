# Generic Skill Workflow Support

## Position

Generic Skill Workflow Support is a CNCF runtime/application-support layer above the generic Workflow Invocation/Continuation mechanisms.

It MUST NOT redefine Workflow/StateMachine semantics. It projects generic runtime contracts into a form convenient for Skill/AI drivers.

## Generic contracts inherited from Workflow runtime

- WorkflowInvocationContract
- Participant / InvocationBinding
- Continuation identity
- ContextBundle / ContextSnapshot / ContextReference
- CompletionContract / EvidenceContract
- lease / idempotent resume / stale rejection

## Skill-oriented projections

Potential public concepts:

```text
SkillContinuation
SkillWorkOrder
SkillContextView
SkillResult
SkillEvidence
SkillExecutionHint
```

These are projections/profiles over generic contracts, not independent sources of truth.

`SkillExecutionHint` should express model-independent requirements such as work kind, capability, complexity, risk and review policy. Concrete model/provider/reasoning names belong to host dispatch policy, not durable Workflow semantics.

## Cost and context principles

- Do not invoke an AI child task merely to call `advance`, `status`, `submit`, or other control-plane operations.
- Keep Skill payloads compact; return references instead of complete histories/logs/artifacts.
- Allow workers to lazily dereference only required context.
- Normalize worker output into Result/Evidence rather than returning entire worker conversations to parent supervisors.
- Mechanical/deterministic actions remain runtime operations and are not Skill WorkOrders.

## Specializations

`sm-workflow` is the Software Development specialization and may add PLAN/IMPLEMENT/REVIEW, build/test/spec/git closing, development evidence and repository/worktree policies.

Other domains must be able to use Generic Skill Workflow Support without depending on `sm-workflow` or software-development vocabulary.
