# Durable Job Management Control Contract

status=normative
phase=JM69-04C
source-authority=`PHASE-69.2 / JM69-04 / JM69-04C`

This contract defines the existing public `JobEngine.control` lifecycle
boundary. It makes replay behavior explicit without adding a recovery,
retention, provider, durable-record, or transport control surface.

## R1 Commands and response vocabulary

`JobControlCommand` is closed to `Cancel`, `Retry`, `Suspend`, and `Resume`.
`JobControlResponse` returns the requested `JobId`, the current status, the
existing optional synchronous `OperationResponse`, the existing async marker,
and additive `changed`. `changed=true` means this invocation applied a lifecycle
transition or scheduled retry work; `changed=false` means it accepted an
idempotent replay and made no control-side state, event, timeline, queue, or
cancellation-scope change.

## R2 State guards and replays

| Command | Applied source states | Applied target | Idempotent current state |
| --- | --- | --- | --- |
| `Cancel` | `Submitted`, `Running`, `Suspended` | `Cancelled` | `Cancelled` |
| `Suspend` | `Submitted`, `Running` | `Suspended` | `Suspended` |
| `Resume` | `Suspended` | `Running` | `Running` |
| `Retry` | `Failed`, `Cancelled` | `Submitted` and one retry work item | `Submitted`, `Running` |

All other command/state combinations retain the structured guard failure. In
particular, replay does not make terminal success, an unrelated command, or a
recovery-required refusal into a new lifecycle policy. An accepted replay
returns the current status without appending control evidence, replacing a
cancellation scope, mutating the record, or enqueueing retry work. A
synchronous replay completes immediately and does not wait for or alter
existing work.

## R3 Authorization and current source

`JobControlPolicy.authorize` is evaluated before any job-record lookup or
observable status, result, timeline, task, diagnostics, recovery, or retention
observation. A denied existing job is unchanged and follows the same policy
failure path as a denied supplied id that is missing; control does not disclose
existence.

After authorization, the current live `JobRecord` supplies the control status
and state guard. The existing raw reads and management read contracts retain
their own source-precedence rules; this control contract adds no new query or
durable projection source.

## R4 Ownership exclusions

Phase 69.1 exclusively owns durable recovery, snapshot integrity, provider,
and startup-recovery behavior. Phase 69.7 exclusively owns retention,
expiry, cleanup, deletion, operations, and maintenance behavior. This contract
adds no recovery or retention command, durable schema or migration, provider
call, raw-query compatibility change, Help, HTTP, CLI, JCL, or user-experience
surface.
