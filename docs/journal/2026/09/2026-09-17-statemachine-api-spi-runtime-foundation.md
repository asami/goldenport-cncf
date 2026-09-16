# StateMachine API/SPI Runtime Foundation

Date: 2026-09-17
Status: Design refinement / Phase 77 input

## Decision

CNCFのWorkflow API/SPI/Continuation runtimeをWorkflow専用層として重複実装せず、StateMachine RuntimeのAPI/SPI機構として一般化する。Workflow RuntimeはこのStateMachine API/SPI runtimeを利用し、Workflow固有のpurpose/use-case/actor/subworkflow等の意味を上位に重ねる。

```text
CNCF StateMachine Runtime
  API invocation
  SPI provider binding
  Action execution
  Completed / Suspended / Failed
  Continuation / resume
  provider admission
        ^
        |
CNCF Workflow Runtime
  process semantics / workflow projection
```

## StateMachine SPI provider model

```text
Required Operation
  -> LocalProvider
  -> ExternalContinuationProvider
  -> TestProvider
```

RuntimeはStateMachineが選択したActionを実行し、providerが外部Resultを必要とする場合のみContinuationとしてsuspendする。Workflow-wide protocol modeは存在しない。

## API/SPI composition

将来assemble/runtime bindingはStateMachine SPI -> APIのlogical connectionとして実装する。

- same Component StateMachine API
- another Component API
- another Workflow/StateMachine API
- Skill provider
- Human/UI provider
- remote provider

transportはbinding/provider layerの責務とし、Action implementationとStateMachine semanticsから分離する。

## Broader reuse

同じStateMachine API/SPI runtimeをWorkflow以外のEntity lifecycle、Job、UI/client StateMachine等にも再利用可能にする。Workflowだけの特殊Continuation engineを作らない。

## Phase 77

直近のreference scenarioはSkill-driven Workflowのままとし、その実装をStateMachine API/SPI runtimeの最初のvertical sliceとして利用する。
