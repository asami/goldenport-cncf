# Phase 81: Workflow Retry and Timeout Runtime

Status: planned

## Goal

Cozy が生成する Retry / Timeout metadata を admission し、sm-workflow を含む consumer が一時的 failure と hanging invocation に耐えられる最小 execution safety を提供する。

## Scope

- maximum attempts
- fixed retry delay
- durable attempt count
- Action / Participant execution timeout
- retry exhaustion / timeout outcome
- restart-safe execution state
- late completion が二重 transition を起こさない最小 protection
- status/history/diagnostics evidence

## Dependency

Phase 80 Workflow Execution Protocol Runtime と Cozy Retry/Timeout ABI extension。

## Non-goals

Deadline、general Timer / Wait、Cancellation、rich FailurePolicy、general idempotency framework、backoff/jitter、dedicated Iteration semantics。
