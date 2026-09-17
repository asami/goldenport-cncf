# Workflow Execution Protocol Runtime

- Status: Design baseline
- Date: 2026-09-17

## Purpose

CNCF Workflow Runtime は CML/Cozy が生成する同一 Workflow semantics に対して、Orchestration Protocol と Continuation Protocol の双方を実行できる Runtime contract を提供する。

```text
CML Workflow ABI
  Workflow / StateMachine / Invocation Contract
                    |
              CNCF Runtime
                    |
          +---------+---------+
          |                   |
          v                   v
 Orchestration Runtime   Continuation Runtime
```

## Orchestration Protocol

Runtime が Participant を直接 invoke し、Result/Evidence を受理して Workflow を継続する。

Participant は Component Operation、AI Runtime、Service adapter、Human Task adapter 等になり得る。

## Continuation Protocol

Runtime は semantic boundary で WorkflowInstance を suspend し、versioned Continuation を返す。External Driver が作業を実行し、ContinuationResult を submit/resume する。

Runtime は continuation identity、workflow revision、lease/idempotency、ContextSnapshot、Completion/Evidence contract を検証してから Result を受理する。

## Common invocation semantics

両 Protocol は CML generated ABI の `WorkflowInvocationContract` を共有する。

```text
WorkflowInvocationContract
  operation
  ContextBundle
  CompletionContract
  EvidenceContract
  ExecutionRequirements
```

Protocol は delivery mechanism であり、Operation semantics を再定義しない。

## Context handling

Runtime は Workflow Context の canonical state を保持し、Invocation/Continuation には作業に必要な Work Context と reference/snapshot を投影する。

- Workflow Context: durable canonical state
- Work Context: target/input/constraints/completion/evidence references
- Execution Context: host/executor 側の一時情報

ContextBundle は bounded payload と reference を基本とする。Runtime は ContextSnapshot を照合し、stale ContinuationResult を fail closed する。

## Durable continuation

Continuation Protocol は process restart を越えて resume 可能な durable execution として扱う。

Runtime responsibility:

- WorkflowInstance identity/revision/history
- continuation identity/lifecycle
- lease/ownership
- idempotent result submission
- stale detection
- suspend/resume
- evidence correlation
- recovery

具体 datastore は port/provider とし、public Workflow semantics に固定しない。

## Mixed binding

将来的に Operation / Participant ごとの Protocol binding を許可する。

```text
Workflow
  Component Operation -> ORCHESTRATION
  AI Review           -> CONTINUATION
  Build/Test          -> ORCHESTRATION
  Human Approval      -> CONTINUATION
```

Runtime は generated binding を admission し、同一 WorkflowRun 内で semantics を保って実行する。

## sm-workflow relation

`sm-workflow` の advance/Continuation/WorkOrder/Decision/WAIT/revision/lease/idempotency/SQLite の実運用知見を Continuation Runtime の reference consumer として利用する。

CNCF は `sm-workflow` 固有の Goal/Phase/Skill protocol を core に取り込まず、共通 Workflow Execution Protocol の runtime primitive を提供する。
