# Workflow Participant Invocation Runtime

## Runtime model

CNCF Workflow Runtime は CML generated `WorkflowInvocationContract` を admissionし、ActionごとのInvocationBindingを実行する。

```text
ORCHESTRATION
  -> invoke typed Operation/Participant
  -> collect Result/Evidence
  -> validate
  -> continue

CONTINUATION
  -> create/persist Continuation
  -> return/yield
  -> receive Result later
  -> validate identity/revision/snapshot/schema/evidence
  -> resume
```

同一Workflow内で両方式を混在できる。

## Durable Continuation runtime

Continuation runtimeは少なくとも次を共通提供する。

- continuation identity and WorkflowRun correlation
- expected revision / ContextSnapshot
- lease/claim where needed
- ContextBundle/reference resolution boundary
- typed result validation
- CompletionContract / EvidenceContract validation
- idempotent result submission
- stale/duplicate/expired continuation rejection
- history/evidence recording

## UI boundary

Human interactionはContinuationの標準利用例とする。Workflow RuntimeはUIを直接制御しない。

UI adapter/client:

1. pending HUMAN continuationを取得する。
2. input/result schema、context、optional presentation hintsをprojectionする。
3. user choice/inputを取得する。
4. typed Resultとしてsubmitする。

これによりWeb/Flutter/CLI等で同じWorkflow semanticsを利用でき、UI sessionを跨いだ長時間approvalも可能になる。

## AI boundary

AI Skill/Agentも同じContinuation runtimeを利用する。AIを直接invokeできるhostではORCHESTRATION bindingも利用できるため、AI-specific Workflow semanticsを作らない。

## Deterministic action

Continuationを利用するWorkflowでも、build/test/git等のtyped deterministic OperationはORCHESTRATIONで直接実行できる。Continuation Protocolは全Actionを外へyieldすることを意味しない。
