# Phase 84: Goal-Oriented Workflow Iteration Runtime

Status: planned

## Goal

Retry と Goal-oriented Iteration を runtime / history / diagnostics 上で区別し、AI / Human participant の意味的反復を支援する。

## Scope

- NeedsInput / NeedsRevision / Rejected 等の semantic outcome
- review -> revision -> review 等の iteration
- termination / escalation の必要最小限の runtime support
- RetryPolicy との明確な分離

専用 Iteration runtime abstraction は通常 StateMachine transition で不足することが実運用 evidence から確認された場合に限定する。
