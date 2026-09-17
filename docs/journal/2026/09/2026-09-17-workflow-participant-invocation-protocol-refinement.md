# Workflow Participant Invocation Protocol refinement

Date: 2026-09-17
Status: Design decision / Phase 80 input

## Decision

CNCF Workflow Runtime は Orchestration / Continuation を WorkflowRun 全体の排他的execution modeとして扱わない。CML generated ABI が宣言する Action/Participant単位の `InvocationBinding` として実行する。

```text
Workflow Runtime
  -> BuildProject       : ORCHESTRATION
  -> RequestApproval    : CONTINUATION -> UI
  -> ReviewChange       : CONTINUATION -> AI
  -> CommitChanges      : ORCHESTRATION
```

ORCHESTRATIONではRuntimeがParticipant/Operationを直接invokeする。CONTINUATIONではRuntimeがContinuationをdurably persist/yieldし、process/threadを保持せずreturnする。後からUI/AI/remote driverがResultをsubmitし、snapshot/contractを検証してresumeする。

## Human UI

Human Approvalを`openDialog`等のRuntime Actionとして実装しない。Runtimeは`RequestApproval : ApprovalContext -> ApprovalDecision`のInvocation Contractを扱う。

UI adapterはContinuationを取得し、schema/presentation metadataから表示を構築し、ApprovalDecisionをsubmitする。UIのsession lifetimeとWorkflowRun lifetimeを分離する。

## Common continuation clients

- Web / Flutter / CLI UI: HUMAN continuation client
- Codex/ChatGPT Skill: AI continuation client / driver
- remote worker/service: remote continuation client

Participantが異なっても、Continuation identity、revision/snapshot、lease、completion/evidence validation、idempotent resumeのruntime機構を共有する。

## Runtime invariant

Continuation client/driverがWorkflow semanticsを所有しない。次のvalid action/stateはWorkflow Runtimeが決める。UI/AIはContinuation Contractに対するResultを返すだけである。

## Phase 80 impact

Phase 80ではAction/Participant単位のbinding dispatch、mixed-protocol workflow、durable human continuation、stale snapshot rejection、UI/AI共通resume contractをruntime acceptanceへ追加する。
