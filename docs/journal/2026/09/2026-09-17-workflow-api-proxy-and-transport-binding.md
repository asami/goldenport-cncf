# Workflow API Proxy and Transport Binding

Date: 2026-09-17
Status: Design refinement / future runtime work

## Goal

Caller WorkflowのActionから別Workflowを通常のtyped APIとして呼び出し、Workflow間接続、Continuation/correlation、local/remote transportをAction implementationから隠蔽する。

```text
Caller Action
  -> generated Workflow API/proxy
  -> CNCF Workflow Binding
       -> Local provider
       -> REST provider
  -> Callee Workflow Runtime
```

## Runtime responsibilities

CNCFは将来的にCML generated Workflow interface metadataからcaller-side proxyを構成/生成できるruntime contractを提供する。

Workflow API/proxyはlogical operationとtyped input/resultを公開し、内部で次を扱う。

- target Workflow resolution
- WorkflowRun/call correlation
- durable suspend/resume
- result completion
- timeout/cancel policy where declared
- transport/provider selection
- error/failure mapping
- context/evidence propagation where contractually required

Caller Actionはこれらを直接実装しない。

## Provider bindings

### LocalWorkflowBinding

同一runtime/process/component topologyで利用可能な場合、typed runtime/method invocationで接続する。

### RestWorkflowBinding

remote deploymentでは同じlogical Workflow APIをREST adapter/proxyで接続する。URL、credentials、service discovery、retry transport policyはruntime/deployment configurationから解決する。

Action codeはlocal/RESTの切替で変更しない。

## Durable call semantics

Workflow間callは通常のRPCより長時間になり得る。calleeがHuman/AI continuationでsuspendしても、caller-side Workflow APIはlogical callとしてcorrelationを保持し、callee result到着時にcaller Workflowを再開できるようにする。

`WorkflowCall[A]` 等のprogramming abstractionは後続設計候補とする。

## Current boundary

Phase 80の直近ターゲットはSkill-driven Workflow SPI/Continuation runtimeである。Workflow API proxy、local/REST connectorは後続Phaseに分離する。ただしPhase 80 runtime identity/correlation/provider modelは将来のtransport-neutral Workflow connectionを阻害しないようにする。
