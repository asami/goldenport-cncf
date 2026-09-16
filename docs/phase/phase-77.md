# Phase 77: Workflow Execution Protocol Runtime

Status: planned

## Goal

Cozy/CML Phase 62 が生成する Workflow ABI を canonical input とし、Orchestration Protocol と Continuation Protocol を同じ Workflow semantics 上で実行できる CNCF runtime primitive を完成させる。

## Upstream dependency

Cozy Phase 62 の completed release と frozen ABI を前提とする。CNCF側で CML を再解析したり、独自 Workflow DSL / parallel schema を作らない。

必要 handoff:

- Workflow ABI version
- real CML fixture
- protocol/binding metadata
- WorkflowInvocationContract
- ContextBundle / ContextReference / ContextSnapshot
- CompletionContract / EvidenceContract
- Continuation / ContinuationResult schema
- stale-result validation contract

## Scope

1. Workflow ABI fail-closed admission。
2. WorkflowInstance identity/revision/history persistence SPI。
3. common WorkflowInvocationContract runtime model。
4. Orchestration Protocol executor。
5. Continuation Protocol suspend/yield/resume primitive。
6. continuation identity/lifecycle と result correlation。
7. lease/ownership/idempotency contract。
8. ContextSnapshot validation と stale result rejection。
9. bounded ContextBundle projection / reference handling。
10. Completion/Evidence contract validation。
11. automatic transition / deterministic Operation / semantic boundary progression integration。
12. Operation/Participant protocol binding admission。
13. same-workflow fixture で Orchestration / Continuation の semantic equivalence を検証。
14. `sm-workflow` consumer handoff を作成。

## Runtime invariants

- Workflow semantics の正本は generated ABI と WorkflowInstance state に置く。
- Protocol は Operation semantics を変更しない。
- Continuation Driver は Workflow semantics owner ではない。
- stale ContinuationResult は fail closed する。
- Result replay は idempotent に扱う。
- Context payload は bounded とし、canonical state / large artifact を無制限複製しない。
- raw shell を Workflow protocol primitive にしない。
- specific AI model/reasoning level を core runtime contract に固定しない。
- Orchestration と Continuation は同じ Completion/Evidence semantics を使う。

## Acceptance

- 同一 generated Workflow fixture が両 Protocol で同じ terminal semantics に到達する。
- Orchestration では Runtime が Participant invocation/result を駆動できる。
- Continuation では process restart を越えて suspend/resume できる SPI/contract が成立する。
- wrong continuation id/revision/context snapshot の result が拒否される。
- duplicate result submission が二重 transition を起こさない。
- ContextReference を用いて large context を payload へ複製せずに実行できる。
- Operation/Participant binding を変更しても StateMachine/Guard/Result semantics が変化しない。
- `sm-workflow` が CNCF 固有型を public skill contract に露出せず consumer として利用できる。

## Non-goals

- default SQLite provider
- sm-workflow public CLI/skill
- AI model dispatch policy
- application-specific Goal/Phase/Step profile
- remote publish/deployment policy

## Planning references

- `docs/notes/workflow-execution-protocol-runtime.md`
- `docs/journal/2026/09/2026-09-17-workflow-execution-protocol-runtime-baseline.md`
