# Phase 80: Extended Workflow Invocation and Integration Runtime

Status: planned

## Goal

Phase 77 で完成した StateMachine API/SPI Runtime と最初の Skill-driven Workflow vertical slice を安定した基盤として、実運用から必要性が確認された Workflow invocation / participant / context integration の拡張機能を追加する。

Phase 80 は `sm-workflow Phase 1` の前提ではない。sm-workflow は CNCF Phase 77 の consumer handoff を受けた時点で executable specification の実装を開始できる。

## Dependency

- CNCF Phase 77 closed and consumer handoff frozen.
- Cozy Phase 62 generated Workflow ABI.
- Phase 77 の StateMachine API/SPI、`ActionExecution = Completed | Suspended | Failed`、durable Continuation/resume、deterministic progression semantics を再定義しない。

## Baseline inherited from Phase 77

Phase 80 では次を新規実装項目として数えない。

- Workflow ABI admission / ComponentFactory discovery.
- independent WorkflowInstance persistence contract.
- StateMachine Provided API / Required SPI provider runtime.
- Completed / Suspended / Failed handling.
- durable Continuation creation and typed resume.
- stale / duplicate result rejection.
- bounded deterministic `advance`.
- Generic Skill projection of semantic suspended SPI operations.
- deterministic test provider.
- `sm-workflow` consumer handoff.

これらは Phase 77 の completion condition である。

## Extension candidates

Phase 80 は実運用 evidence を入力として、Phase 77 の foundation 上に必要なものだけを追加する。

1. richer `WorkflowInvocationContract` projection.
2. bounded `ContextBundle / ContextReference / ContextSnapshot` ergonomics.
3. richer Completion / Evidence projection and validation.
4. AI / Human / remote participant integration using the same Phase 77 Continuation identity.
5. UI/client continuation retrieval and resumption ergonomics.
6. provider/invocation placement policy that remains outside Workflow semantics.
7. additional Generic Skill Workflow metadata such as model-independent capability / complexity / risk / review hints.
8. future local/remote transport or Workflow-to-Workflow integration preparation where justified.

## Normative compatibility rule

There is no Workflow-wide Orchestration/Continuation mode and no semantic
`InvocationBinding = ORCHESTRATION | CONTINUATION` switch.

Direct/local execution and external continuation are provider/runtime placement choices for an admitted Required SPI operation. They do not change StateMachine / Workflow semantics.

Any historical Phase 80 addendum that describes a semantic ORCHESTRATION/CONTINUATION binding is superseded by Phase 77 and this document.

## Scheduling

Phase 80 is deliberately outside the critical path for `sm-workflow Phase 1`.

```text
Cozy Phase 62 (closed)
        ↓
CNCF Phase 77
        ↓
sm-workflow Phase 1 executable specification

        ↓ operational feedback

CNCF Phase 80 extended invocation/integration runtime
```

Phase 80 should be refined from the Phase 1 / skill connectivity work rather than implemented speculatively.

## Acceptance direction

Acceptance criteria are to be frozen when concrete extension candidates are selected from operational evidence. At minimum, all selected extensions must preserve Phase 77 semantics and existing sm-workflow executable specifications.

## Non-goals

- Reimplementing Phase 77.
- Blocking sm-workflow Phase 1.
- Creating a second Workflow DSL or Workflow-specific parallel StateMachine runtime.
- Semantic protocol-mode switches.
- Retry / Timeout and later runtime-control facilities already assigned to dedicated follow-up phases.
- sm-workflow application-specific Goal/Phase/Step policy.
