# Workflow SPI and Action Suspension Runtime

Date: 2026-09-17
Status: Design refinement / Phase 77 input

## Refinement

CNCF Workflow RuntimeはOrchestration/Continuation modeを切り替えるのではなく、StateMachineが選択したActionを実行し、そのActionExecution結果を処理する。

```text
ActionExecution
  Completed(Result)
  Suspended(Continuation)
  Failed(Error)
```

`advance`はActionを順次実行し、CompletedならResultをStateMachineへ返してtransitionを進める。SuspendedならContinuationとWorkflowRun stateをdurably persistしてcallerへ返す。resumeではtyped Resultとsnapshotを検証し、元ActionのcompletionとしてStateMachineへResultを返して進行を再開する。

## Workflow SPI

外部providerを必要とするtyped Action/Operation群をWorkflow Required Interface、すなわちWorkflow SPIとして扱う。

```text
Workflow
  Provided API: start / advance / resume / status
  Required SPI: RequestApproval / ReviewChange / CaptureMaterial / ...
```

同じRequired Operationに対してruntime-local provider、external continuation provider、test providerをbinding可能にする。Provider placementはWorkflow semanticsを変更しない。

Continuationはexternal SPI providerとのdurable invocation mechanismであり、Workflow modeではない。

## External specification

CML generated ABIからWorkflow SPI specificationをprojectionし、CNCFはprovider compatibility/admissionを検証できるようにする。input/result、Context、Completion、Evidence、capability等を型付きcontractとして扱う。

将来はSPI specからSkill command projection、UI binding、MCP adapter、test provider等を構築できる。

## Phase 77

Phase 77ではSkill-driven reference scenarioを優先し、ActionExecution、suspend/resume、Workflow SPI admission/projection、direct/test/external provider bindingのruntime foundationを閉じる。UI Workflow/Flutter generationは後続とする。
