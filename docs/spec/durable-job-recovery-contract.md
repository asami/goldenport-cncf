# Durable Job Recovery Classification Contract

status=normative
phase=JM69-03E
source-authority=`P69.1-JM69-03-RECOVERY-001`

This is the normative contract for the pure, package-internal recovery
classification introduced by `JM69-03E`. It consumes an already authorized
`DurableJobRecord` and six opaque `DurableReplayEvidence` tokens. It creates
no provider, scheduler, resolver, task, Action, ActionEngine, UnitOfWork,
execution context, clock, thread, credential, payload body, result body, or
external effect.

The source record remains governed by
[`durable-job-record-contract.md`](durable-job-record-contract.md). Its V1/V2
codec bytes, migrations, public projection, and record schema are unchanged.
The record is supplied only after the existing durable codec/access boundary
has admitted and authorized it. This classifier does not verify storage or
integrity bytes again, and it does not select a provider.

## Closed decision value

`DurableJobRecoveryDecision` contains exactly:

- an outcome: `terminal-succeeded`, `terminal-failed`,
  `terminal-cancelled`, `resumable`, `retryable`, or
  `recovery-required`;
- a vector of closed, typed reasons; and
- when and only when an otherwise eligible record lacks replay proof, the
  exact ordered vector of failed proof categories.

The categories are always in this order: `idempotency`, `input`,
`definition`, `authorization`, `provider`, and `compatibility`. A token is
failed when it is absent, `null`, or blank. Tokens are opaque: the classifier
does not resolve, inspect, or use them to choose a provider or execute work.

The decision model does not contain a `JobTask`, Action, ActionEngine,
ExecutionContext, provider object, credential, raw input/result body,
ClassLoader, closure, function, or arbitrary object. Terminal outcomes are
facts only; they do not synthesize a live `JobResult`.

## Outcome truth table

| Durable record fact | Replay proof | Classification |
| --- | --- | --- |
| `Succeeded` with succeeded result | not consulted | `terminal-succeeded` |
| `Failed` with failed result | not consulted | `terminal-failed` |
| `Cancelled` with cancelled result | not consulted | `terminal-cancelled` |
| V2 `Submitted`, no `schedule.startedAt`, no `recoveryRequired`, no `nextRetryAt` | all six pass | `resumable` |
| V2 `Submitted`, no `schedule.startedAt`, no `recoveryRequired`, `nextRetryAt` present | all six pass | `retryable` |
| Otherwise eligible V2 submitted or delayed-retry record | any proof missing or blank | `recovery-required`, with the exact failed categories |
| `Running` or `Suspended` | any | `recovery-required` |
| `Submitted` with `schedule.startedAt` present | any | `recovery-required` |
| Non-terminal record with `recoveryRequired = true` | any | `recovery-required` |
| Impossible format/lifecycle/result combination | any | `Consequence.Failure` |

Terminal lifecycle facts take precedence: a valid terminal record remains
terminal and is not converted into replay work. The classifier has no clock;
`nextRetryAt` distinguishes a durable delayed-retry checkpoint from ordinary
unstarted submitted work but does not assert that retry time has arrived.

## Checkpoint ordering

The durable protocol is ordered as follows:

1. Durable submitted or running intent is checkpointed before execution.
2. ActionEngine and UnitOfWork own the eventual commit or abort.
3. Durable terminal outcome is checkpointed only after that result.

An interruption after a start checkpoint and before a terminal checkpoint is
therefore `recovery-required`, even with six complete replay-proof tokens. The
classifier never automatically replays an external effect, dispatches a retry,
or starts UnitOfWork execution.

## Explicit non-goals

This slice adds no provider I/O, new-process rehydration, resolver injection,
scheduled or retry queue dispatch, task reconstruction, recovery control,
store/JobEngine wiring, public management API, codec/schema/migration change,
or Phase 69.2 behavior. Ephemeral work has no durable record and remains
outside this boundary.
