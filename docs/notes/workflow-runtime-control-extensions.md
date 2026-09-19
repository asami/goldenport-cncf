# Workflow Runtime Control Extensions

## Purpose

CNCF StateMachine / Workflow runtime に実用制御を段階的に追加する。Cozy generated ABI を canonical input とし、CNCF 独自 Workflow DSL を作らない。

## Principles

- StateMachine core の State / Transition / Event / Guard / Action / Context を維持する。
- Retry counter、deadline 等の execution data は durable context / execution state として保持する。
- Retry は技術的再実行、Iteration は Goal-oriented semantic loop として分離する。
- sm-workflow は consumer であり、使用しない将来機能を認識する必要はない。

## Extension groups

1. Operational minimum: Retry + Timeout。
2. Scheduling/lifecycle: Deadline、Timer / Wait、Cancellation、runtime-internal suspension/resumption。
3. Failure/execution safety: classification、FailurePolicy、idempotency / duplicate protection。
4. Goal-oriented iteration: NeedsInput / NeedsRevision / Rejected 等の semantic outcome と iteration。

Retry 初期版は maximum attempts + fixed delay。Timeout 初期版は Action / Participant invocation execution timeout。高度な backoff 等は後続へ送る。

既存 Phase 80 の Workflow Execution Protocol Runtime を基盤として拡張し、同 Phase 自体の scope は膨らませない。
