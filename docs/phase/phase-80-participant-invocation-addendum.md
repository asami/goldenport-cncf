# Phase 80 Addendum: Participant Invocation Runtime

Status: planned / normative addendum to Phase 80

## Goal

CML Workflow ABIのAction/Participant単位 `InvocationBinding` をruntimeで実行し、ORCHESTRATIONとCONTINUATIONを同一Workflow内で安全に混在させる。

## Scope

- ORCHESTRATION typed direct invocation
- CONTINUATION durable yield/resume
- shared WorkflowInvocationContract admission
- ContextBundle / ContextSnapshot handling
- Completion/Evidence validation
- Human UI continuation
- AI continuation
- mixed-binding progression

## Acceptance

- Build/Test/Commit等をORCHESTRATIONで実行しながら、途中のHuman ApprovalだけCONTINUATIONとしてyieldできる。
- Continuation yield時にWorkflow execution thread/processを保持しない。
- UI clientを終了・再起動してもpending continuationを取得しResultをsubmitできる。
- stale ContextSnapshot resultをfail closedする。
- duplicate result submitをidempotent contractに従って処理する。
- Human UIとAI Skillが同じContinuation identity/resume infrastructureを利用できる。
- protocol/binding差でWorkflow StateMachine semanticsを複製しない。
- Continuation workflowであってもdeterministic/direct Actionを通常通りorchestrateできる。
