# Phase 64 - Minimal Composite StateMachine / Workflow Foundation

status=planned
planned_at=2026-08-12
revised_at=2026-09-20
depends_on=[Phase 63](phase-63.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 64 Checklist](phase-64-checklist.md)

## Purpose

Provide the minimum Composite StateMachine and Workflow semantics required by CNCF Phase 77 and the sm-workflow Phase 1 executable-specification path.

Workflow remains a specialization/profile of Composite StateMachine. Phase 64 does not build a general-purpose workflow engine or advanced operational runtime.

```text
StateMachine
  -> Composite StateMachine
       -> minimal Workflow specialization
            -> Phase 77 API/SPI + typed protocol
                 -> sm-workflow Phase 1 executable specification
```

## Boundary

Phase 64 owns model/runtime semantics that are necessary before the API/SPI and external Continuation layer can exist:

- constituent StateMachine composition with stable role/identity;
- constituent configuration and deterministic composite-state derivation;
- derived composite-transition identity and causal correlation;
- deterministic ordering/provenance of constituent and composite actions;
- minimal Workflow specialization only where Composite StateMachine semantics are insufficient;
- minimal WorkflowDefinition/WorkflowInstance semantic identity and progression model needed by the later Phase 77 durable store contract;
- CML-generated definition admission/bootstrap and end-to-end fixture evidence.

Phase 64 does not own rich operational workflow facilities. Those are later phases.

## Selected direction

- Reuse StateMachine semantics before adding Workflow-only concepts.
- Composite business state is derived from constituent configuration where possible; do not persist a duplicate mutable business status merely for convenience.
- Constituent StateMachines retain local transition authority.
- Composite transition occurrence is causally correlated to the committed constituent transition/configuration change that caused it.
- Constituent and composite actions use one typed executable-program model and preserve deterministic causal ordering.
- CML/Cozy owns model semantics and generated contracts; CNCF consumes them without reconstructing meaning from names.
- Workflow-specific concepts require evidence that they cannot be represented cleanly as general Composite StateMachine semantics.
- Existing StateMachine hierarchy/history semantics are consumed when available; Phase 64 does not implement missing hierarchy/history runtime features.

## Minimal Workflow specialization

Phase 64 freezes only the minimum process identity/progression concepts required by Phase 77:

```text
WorkflowDefinitionIdentity
WorkflowInstanceIdentity
WorkflowLifecycle
CurrentProgression
Correlation / Causation
```

This is a semantic/runtime model, not yet the independently durable WorkflowInstance store. Phase 77 owns persisted revision/history, suspension state, Continuation identity, ContextSnapshot, replay/stale protection, and the public typed protocol model/encodings.

## Action execution boundary

Phase 64 defines how constituent/composite logical actions compose and where execution attaches, but detailed execution substrate is split:

- Phase 64.1: local UnitOfWork atomic commit/rollback foundation.
- Phase 64.2: ExecProgram planning, deterministic test/simulation interpreter, and production alignment.

Phase 64 must not absorb 2PC, compensation, recovery, Retry/Timeout, or external participant protocol work.

## Work stack

| ID | Outcome | Status |
| --- | --- | --- |
| SWF-01 | Inventory StateMachine / Composite StateMachine / Workflow concepts and classify each as reused, composite-general, minimal Workflow-only, or runtime-policy. | planned |
| SWF-02 | Freeze constituent role/identity, configuration schema, deterministic composite-state derivation and derived-transition identity. | planned |
| SWF-03 | Admit generated composite rules and fail closed for ambiguous/unsupported runtime configuration. | planned |
| SWF-04 | Freeze typed constituent/composite action composition, ordering and provenance, delegating execution substrate to 64.1/64.2. | planned |
| SWF-05 | Freeze minimal Workflow specialization and semantic WorkflowDefinition/WorkflowInstance identity, lifecycle/progression and correlation contract; durable revision/history/store semantics are Phase 77. | planned |
| SWF-06 | Admit CML-generated Composite StateMachine/Workflow definitions through ComponentFactory without CML reparsing or handwritten canonical definitions. | planned |
| SWF-07 | Prove one real CML fixture through constituent transition -> derived composite transition -> action program boundary -> minimal Workflow progression. | planned |
| SWF-08 | Freeze exact Phase 77 handoff and explicitly record deferred advanced facilities. | planned |

## Acceptance

- One CML Composite StateMachine/Workflow source yields one deterministic typed generated runtime definition.
- Composite StateMachine coordinates multiple constituent StateMachines without erasing their identity or transition authority.
- Composite state is deterministically derived from admitted constituent configuration/rules where declared.
- Ambiguous or unsupported configurations fail closed.
- Derived composite transition is correlated to its causal committed constituent transition/configuration change.
- Constituent/composite actions preserve deterministic order and provenance and use the shared typed executable-program boundary.
- Minimal Workflow definition/instance identity, lifecycle/progression and correlation can be represented without introducing a second Workflow language/runtime; durable revision/history/store semantics remain Phase 77.
- No external participant/Skill/Continuation protocol is required to close Phase 64.
- A real CML fixture reaches the handoff required by Phase 77 using deterministic testable execution boundaries.
- Phase 77 can add API/SPI, durable Continuation, typed protocol Value Objects and Skill projection without changing Phase 64 semantics.

## Explicitly deferred

The following are not Phase 64 completion requirements:

- independently durable WorkflowInstance persistence, revision/history, retention and lease policy;
- durable external participant Continuation/resume;
- public Workflow JSON/REST/MCP/Skill protocol;
- AI/Human participant invocation and UI continuation;
- Retry / Timeout / Deadline / Timer / Cancellation;
- rich FailurePolicy and general idempotency/duplicate-protection policy;
- 2PC / distributed atomic transaction;
- compensation / RecoveryRequired / manual recovery;
- Workflow-to-Workflow orchestration/composition runtime;
- remote transport/service discovery/deployment binding;
- advanced operational observability/administration.

## Dependencies and follow-up

Phase 64 begins after Phase 63 closes. If the post-Phase-63 gap review identifies missing hierarchical-state/shallow-history runtime support, that dedicated StateMachine follow-up must be completed before Phase 64 relies on those semantics.

After Phase 64, 64.1 and 64.2 provide the minimal execution/test substrate. Phase 77 then owns the independently durable WorkflowInstance persistence SPI (identity + persisted revision/history/current suspension), StateMachine API/SPI provider runtime, ActionExecution, bounded advance, Continuation/resume, typed Workflow protocol Value Objects/JSON encoding, Generic Skill projection and sm-workflow consumer handoff.

Advanced Workflow runtime capabilities are owned by dedicated later phases.

## References

- [Phase 64 Checklist](phase-64-checklist.md)
- [Phase 63](phase-63.md)
- [Phase 64.1](phase-64.1.md)
- [Phase 64.2](phase-64.2.md)
- [Phase 77](phase-77.md)
- [StateMachine / Workflow alignment](../notes/statemachine-workflow-alignment-provisional-specification.md)
