# Workflow Execution Protocol を CNCF Runtime の基本構造にする

- Date: 2026-09-17
- Status: Design decision

## Decision

CNCF Workflow Runtime は、CML Workflow の基本 Execution Model として Orchestration Protocol と Continuation Protocol の双方を扱う。

- Orchestration: Runtime が Participant を direct invoke する。
- Continuation: Runtime が semantic boundary で suspend し、Continuation を External Driver に返し、Result によって resume する。

Protocol の違いによって Workflow Model を分岐させない。CML generated ABI の WorkflowInvocationContract、ContextBundle、CompletionContract、EvidenceContract、Operation/Result semantics を共有する。

Continuation は durable execution primitive として扱い、WorkflowInstance revision、continuation identity、lease、idempotency、ContextSnapshot、stale-result rejection、history/evidence correlation を Runtime responsibility とする。

Context は canonical Workflow Context と、外部実行に必要な Work Context / Execution Context を分離する。Continuation payload は bounded に保ち、large source/log/artifact は reference で扱う。

将来的には同一 WorkflowRun 内で Operation/Participant ごとに Protocol を混在できるようにする。

## Architecture significance

この構造により、CNCFからAI Runtime/Componentを直接呼べる通常環境ではOrchestrationを使い、Codex/ChatGPT Skillのように外部から駆動する環境ではContinuationを使える。同じWorkflow semanticsとExecutable Specificationを維持したままExecution topologyだけを変更できる。

`sm-workflow` はContinuation側のreference consumer/implementationとして扱い、そこで得たcost/context/durable-resumeの知見をCNCF coreへ一般化する。

## Continuation as Control Overlay

Continuation Protocol の重要な意味は、AI Agent 対応に限定されない。外部の汎用実行環境を CNCF Runtime 内へ取り込むことなく、その実行を Textus Workflow の論理的な制御構造の下に置ける点にある。

従来、Batch/RPA/Agent 等を Textus の制御下で利用しようとすると、scheduler、worker、adapter、external I/O、retry、orchestration など実行能力そのものを Textus/CNCF 側へ実装する方向になりやすかった。

Continuation では、外部 Runtime の実行能力をそのまま利用し、semantic boundary だけを Workflow が保持する。

```text
             Textus Workflow
              control model
                   |
              Continuation
                   |
       +-----------+-----------+
       |           |           |
       v           v           v
     Codex      OpenClaw   Other Runtime
       |           |           |
       +-----------+-----------+
                   |
             Result/Evidence
                   |
                   v
             Textus Workflow
```

Textus が所有するのは Workflow/GoalPhase、Completion Contract、Result acceptance、state transition、retry/escalation policy、continuation identity/revision/idempotency、および execution evidence correlation である。外部 Runtime は Goal 内部の実現方式を所有する。

このため Continuation Protocol は、外部実行環境へ Textus の control semantics を overlay するための protocol と位置付けられる。

```text
External execution capability
          +
Textus deterministic control
          =
controlled external execution
```

この構造により、OpenClaw、Codex、RPA、既存 Batch Runtime、Cloud Job、CI/CD、Human Worker など、Goal -> external execution -> Result/Continuation の契約に適合できる実行主体を、同じ Workflow semantics の下で扱える。

Orchestration Protocol と Continuation Protocol を同一 Workflow Model 上で併用できることも重要である。CNCF Operation のような direct invocation と、OpenClaw/Codex のような external execution を Operation/Participant 単位で選択できるため、Textus は全ての実行能力を自前で所有する必要がない。

この観点では、CNCF Workflow Runtime の役割は「全てを実行する framework」ではなく、「異なる実行環境に対して決定的な control structure を提供する runtime」へ広がる。

## Handoff

詳細は `docs/notes/workflow-execution-protocol-runtime.md` を参照する。次期Workflow runtime phaseでは、Cozy Phase 62 generated ABIをadmissionし、両Protocolの共通runtime primitiveを実装する。
