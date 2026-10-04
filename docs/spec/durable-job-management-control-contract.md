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

## Phase 69.7 B3B3B wait admission and lifetime

After policy authorization, the request, command, option and mode must be
non-null, and every Sync or Async option timeout must be **0..86400000ms
inclusive**. `awaitResult` admits the same range before record lookup. Zero
means immediate/no wait; negative durations are refused rather than clamped.
Invalid shape/range or a NonFatal initial clock/deadline failure returns fixed
`Consequence.argumentInvalid("JobEngine wait request refused")`, without input,
JobId or provider text. Denied requests retain their original policy failure
before all shape, clock, capacity and existence observation. Public defaults
remain Async/3000ms/10ms; `pollMillis` remains an ignored compatibility field.

Sync computes one deadline and reserves an opaque wait lease before any record
guard, Retry, cancellation scope, queue, timeline or event change. The admitted
deadline/lease is carried into the original private transition/response path.
Async validates shape/range without a new clock/deadline or wait lease.
Every await and Sync call conservatively holds one slot for its entire call,
including missing, immediate, completed and replay outcomes. The independent
wait budget is **64 slots per shared State**, across all engine owners, without
changing public resource policy, State constructors or existing job counters.
Admission follows engine-state-to-budget lock order; body and timer callbacks
execute outside admission/budget monitors. Quiesce or owner close refuses new
waits. Original reads, Async cancellation, guards and replay facts remain.

Unfinished waits use only the existing injected/system one-shot timer and clock.
Zero/completed waits create no registration. Remaining Duration is bounded to
24h before nanosecond conversion; a throwing, backward-over-bound or extreme
admitted clock returns fixed resource unavailable after attempting its own
registration close. No polling, scheduler grammar or host clock substitution
is introduced.

The lease becomes ambiguous before external schedule and before physical close.
Only a non-null returned registration acknowledges scheduling; only successful
close acknowledges disposal. NonFatal schedule exception/null registration or
close failure returns `JobEngine resource capacity unavailable` and retains the
occupied ghost. Close failure overrides Completed/TimedOut. Fatal/interruption
propagates without laundering pre-effect ambiguity. Owner replacement/shutdown
never resets ghosts, probes provider closure or shuts down an injected shared
timer. Acknowledged completion/timeout/interruption closes only its own timer
and releases exactly once; interruption retains the thread flag.

Quiesce signals without claiming admitted waits terminated. They stay charged
until actual completion, timeout, interruption or local force-cancel and
acknowledged close. Existing local forceCancel/shutdown wake-ups remain local
to their owner. Thread termination requires actual bounded join evidence.
Only shape/range/deadline/capacity refusal guarantees zero control mutation:
operational timer failure after admitted Sync Retry may return unavailable while
the original authoritative Submitted work, queue, events and scope remain.
There is no rollback or claim of zero effects for that case.

[JobWaitResourceBoundsSpec](../../src/test/scala/org/goldenport/cncf/job/JobWaitResourceBoundsSpec.scala)
authors WT1..WT12 with actual failed/native checkpoint facts, shared owners,
64 physical waits and a last-slot race, fault timers, close acknowledgement gates,
bounded physical joins, hostile clocks and at least 24 seeded finite actual
sequences. Every named behavior has adjacent Given/When/Then and direct should
assertions. Exact 43-suite validation, independent review and commit are pending.

## R4 Ownership exclusions

Phase 69.1 exclusively owns durable recovery, snapshot integrity, provider,
and startup-recovery behavior. Phase 69.7 exclusively owns retention,
expiry, cleanup, deletion, operations, and maintenance behavior. This contract
adds no recovery or retention command, durable schema or migration, provider
call, raw-query compatibility change, Help, HTTP, CLI, JCL, or user-experience
surface.
