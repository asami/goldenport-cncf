# Phase 64 Scope Reduction for sm-workflow Executable-Specification Path

Date: 2026-09-20
Status: normative planning decision

Phase 64 を sm-workflow Phase 1 の executable specification に必要な最小 Composite StateMachine / Workflow foundation に縮小した。

Phase 64 は constituent composition、composite-state derivation、derived transition、action ordering/provenance、minimal Workflow specialization、minimal WorkflowInstance identity/revision/progression/correlation、および real CML fixture の Phase 77 handoff を所有する。

以下は Phase 64 から外した。

- durable external Continuation / participant protocol;
- public JSON/REST/MCP/Skill protocol;
- Retry/Timeout/Deadline/Timer/Cancellation;
- rich failure/idempotency policy;
- 2PC/compensation/recovery;
- rich Workflow-to-Workflow orchestration;
- remote transport/service discovery;
- advanced operational administration.

Action execution substrate は、完了済み Phase 63.1 の local atomic
commit/rollback contract を Phase 64.2 が直接消費し、Phase 64.2 が
ExecProgram/planner/test interpreter の増分を所有する形へ再整合した。
Phase 64.1 は superseded な planning history として残す。API/SPI、minimum
typed Workflow Protocol、Continuation、Skill projection は Phase 77 が所有する。
2PC/compensation/recovery は Phase 85 が所有する。

さらに advanced Workflow runtime/composition/integration の受け皿として Phase 86 を新設した。Phase 86 は Phase 77 / sm-workflow Phase 1 の前提ではなく、sm-workflow 疎通・実運用 evidence から具体化する。

これにより critical path は機能網羅ではなく executable specification 成立を優先する。
