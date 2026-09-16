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

## Handoff

詳細は `docs/notes/workflow-execution-protocol-runtime.md` を参照する。次期Workflow runtime phaseでは、Cozy Phase 62 generated ABIをadmissionし、両Protocolの共通runtime primitiveを実装する。
