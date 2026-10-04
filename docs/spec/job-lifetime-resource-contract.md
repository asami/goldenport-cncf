# Job Lifetime Resource Contract

status=normative-partial-implementation
phase=69.7
slice=JM69-09B3B3C2A-and-C2B2B1-and-C2B2B2
implementation_acceptance=C2A-focused-validated-C2B2B1-focused-validated-independent-review-Step-acceptance-commit-pending

This contract defines cumulative per-Job runtime paid credits owned by one
InMemoryJobEngine State. It supplements the existing retained/pending/executing,
input, page, wait, native Job storage and owned Blob resource contracts. C2A
implementation and executable specifications passed actual VAL-003 focused
validation; independent review, Step acceptance and commit remain pending.
[C2B1 native provider authority](durable-job-lifetime-quota-contract.md) is
VAL-005 validated with independent incremental conformance PASS. Root A also
retains VAL-005/independent conformance PASS. Native C2B2B1 live runtime passed focused validation; independent Slice
conformance, Step acceptance and commit remain pending; B2 fresh-State recovery
is authored with fresh validation/review pending, and every Phase69.7/original Phase69
obligation remain required.

## R1 Policy and shared ownership

The package-internal `JobLifetimePolicy` has inclusive positive lower-only
limits: charged task slots **8192**, history credits **524288**, and lifetime
admissions **16384**. Values above defaults, zero and negative values refuse.
Binding is permitted only before State's first resource attachment. No public
constructor, case-class Product, signed schema or provider contract changes.

One State owns one lifetime ledger under its existing monitor. Every owner sees
the same paid counters, outstanding normal dispatch tickets and companion
claims. The counter methods call no clock, task, provider, event sink or engine;
they acquire no other monitor. Existing lock order remains State then the
original resource budget. Numeric snapshots expose only charged task, history,
admission and remaining prepaid Cancel counters.

## R2 Paid costs and monotonicity

| Actual admission | Task slots | History credits | Admissions | Prepaid Cancel |
| --- | ---: | ---: | ---: | ---: |
| Root or Retry with n tasks | 2n | 32+64n | 1 | 1 |
| Same-Job Sync or queued Async child | 2 | 96 | 1 | 1 |
| Suspend or Resume Apply | 0 | 32 | 1 | 0 |
| Public annotation batch with m new notes | 0 | 32+m | 1 | 0 |
| Idempotent Replay | 0 | 0 | 0 | 0 |
| Cancel Apply | 0 | 0 | 0 | consumes one prepaid allowance |

Initial root execution notes add their exact row count to the root's history
cost. Retry retains the old notes without paying their cost again. Empty roots
still pay 32 history credits, one admission and one Cancel allowance. Normal
work reserves its possible companion even when no compensation is ultimately
needed. Ordinary full 1024-task base batches fit the defaults.

Published task, history and admission credits stay occupied after completion,
queue removal, cancellation, retry-state clear, scheduling/dispatch failure,
shutdown and another owner attachment. No refund, reset, history eviction,
clipping, suppression or increment-and-undo admission is permitted. Consuming a
Cancel allowance changes only that safety counter; each later accepted Retry
adds its own allowance.

## R3 Pure preparation and publication

New-root preparation is a bounded pure candidate before original resource
reservation, Job ID, clock, task getter or native admission. The ledger is
published immediately after successful original native admission and before
record, event, scope, queue or execution publication. Explicit original native
`AdmissionRefused` discards only the unpublished candidate and releases its
original resource lease. Original refusal observer, ID, clock and native attempt
facts remain visible. Failures after native admission retain published credits.

For an existing Job, lifetime and original physical capacity are checked under
the same State monitor. Pure physical capacity refusal leaves the lifetime
candidate uncommitted; lifetime refusal leaves the physical budget untouched.
Accepted credits commit before Task ID, clock, metadata getter, native write,
record/history/event, scope, queue, timer or task effects. Nested Sync may reuse
its thread's physical execution lease while independently paying every child
admission. Another thread cannot borrow that lease. Queued work carries its
original resource envelope; dispatch and canceled removal never pay or refund
lifetime credits again.

## R4 Normal, compensation and closure dispatch

A paid batch reserves n normal and n companion slots. A normal Task ID is
associated with its prepaid slot under State before its first task getter,
metadata/native start or run. Forced queued IDs retain that same association.

Compensation claims one original normal Task ID atomically before the original
compensation/action-reference getter, new companion ID, clock, native/history or
run. Competing root, child and engine selectors execute that companion once.
Losing selectors add no callback, ID or group marker. Missing and failed
compensation still mark recovery-required and retain their original outcome,
notes and timeline. Exhausted expansion capacity cannot skip owed normal
settlement, compensation or prepaid Cancel.

The 32 closure credits plus 64 per normal/companion pair cover engine timeline
and execution-note additions: scheduling/due markers, task and transaction
start/outcome, compensation/missing/recovery-required, cancellation, exhaustion,
dead-letter/poison, worker/native-checkpoint and entity-sync diagnostics. All
prior task models, definitions, notes and timeline facts remain complete and in
order. Both retry record updates merge fields into the latest protected record
after markers, preserving priorities, due times, origin, attempts and labels.
This cardinality proof bounds neither arbitrary user memory/text, callback
business events, calltree allocation, external bytes nor process RAM.

## R5 Controls, Retry and annotation

Authorization, original shape/deadline/identity/checkpoint/transition precedence
and shared Sync wait capacity remain intact. Only an actual Apply pays. Explicit
Retry reserves its complete base batch and original root pending lease before
new scope, clock, record, history, event or queue. Automatic Immediate/Delayed
Retry pays the complete batch before new timer/queue/task effects. Capacity
refusal uses the original failure's exhausted/dead-letter settlement; prior
failed-task effects and RetryLater clock decisions remain visible.

Public `annotateJob` retains Unit and missing-Job no-op behavior. For an existing
Job its note vector and all elements must be nonnull and bounded. Before clock,
map/debug mutation or entity synchronization, it pays 32+m history credits and
one admission. Invalid or exhausted admission throws exactly
`IllegalArgumentException("JobEngine lifetime annotation refused")`, preserving
prior facts and counters. Accepted notes append in order; empty batches still
pay. A running user's prior effects remain visible and the exception follows
original failed settlement. The existing fixed profile note
`jcl declared profile attached` is added at most once under State, using the
prepaid root/seed closure allowance; duplicate attachment adds no row.

## R6 Complete bootstrap

Preserve original engine/input validation precedence. Before any resource seed,
attached flag, owner, executor, clock, getter, provider or timer, scan bounded
complete contents of both State maps across every status. Bound map discovery
by maxretainedjobs plus one sentinel, and collection cardinality before
traversal/allocation. Tasks, models, definitions, timeline and notes must have
nonnull containers and counting elements; their footprints must fit policy.
Build the whole pure lifetime candidate, then run original resource seed, then
infallibly commit the verified candidate under the same monitor. Refusal leaves
both maps and existing budgets unchanged and throws the existing fixed
`IllegalArgumentException("JobEngine resource bootstrap refused")`.

For an unbound, locally unaccounted Job, let k be normal-model count, t total-model count, d
definition count, and h actual timeline plus note rows. Historical task floor is
max(2k,t,d), history floor max(h,64k), and admission floor
max(1,ceil(h/32),k). Submitted/Running live base work additionally reserves its
2n/32+64n/one admission before dispatch. Terminal records receive no prospective
batch until accepted Retry/child admission. Seeds retain one bounded safety
Cancel allowance. Existing compensation status/relation seeds occupied claims
without a task getter.

Duplicate map IDs use componentwise maximum, preserving both representations.
An existing State ledger is never seeded or admitted again: current bounded
structure must fit its exact already-paid counters and associated IDs/claims;
outstanding tickets, claims and physical pending work survive unchanged.
Incompatible external structure refuses instead of inventing credits. No input
sanitizer or map truncation participates in bootstrap.
Native-bound jobs instead follow R8: authenticate the exact acknowledged Seed,
including zero Cancel credits and unused normals, and replace its diagnostic
mirror. Local floors and componentwise maxima never produce native authority.

## R7 Native runtime and terminal authority

After original source/load/classifier/eligible-port and reconstruction
shape/identity/input checks, validate the bounded live batch and signed
task/timeline/diagnostic/retry footprint before quota reads or runtime effects.
Checked twice the intrinsically executed normal count is an obligation lower
bound, not prepaid credit. Pending declaration IDs without execution, association,
task events or compensation obligations are definitions, not normal associations.
Refuse locally knowable collisions/quiesce and require the explicit runtime
bridge to own the identical supplied store before native authentication.

Read-only recovery authenticates already Published V2 authority using that store,
candidate access, caller recovery context and complete selected policy. The full
signed snapshot and provider revision must equal admitted current proof. Compare
actual descriptor/history obligations, native-associated/executed normal IDs,
task-event membership and companion original/claims to its exact Seed. A live
uncancelled batch fits actual remaining normals; a cancelled safety-only batch
need not have dispatch capacity. Missing/Pending/corrupt/V1/foreign-owner/policy
or stale proof refuses. Eligible source/native load/port effects remain visible.

Preparation mutates neither State nor bridge nor native quota. After safe record
construction and original physical staging, one atomic bridge adoption precedes
State owner/Seed publication, physical commit, insertion and due registration.
Recovery performs zero root/Work/Retry payment and no floor/maximum import.

Lifetime refusal reports original closed `Refused` without changing native
revision, timestamps, maps, resources, IDs or physical timers. Accepted recovery
retains original input sanitization, native identity/timestamps/priority and due
behavior. Duplicate runtime/terminal identities cannot overwrite/reset a ledger.
Terminal registration checks its signed footprint and exact native authority,
then retains only closed projection, exact Seed and original quota/access/context
owner with bridge=None, after retained physical capacity. It has no executable
record/task, timer, pending work or write capability. That None remains
authoritative after later bridge configuration; lifetimeSnapshot authenticates
the original native owner. Duplicate/capacity refusal preserves signed/native
bytes and publishes no Seed or fact.

C2A local floors prove current unbound runtime safety; unchanged canonical
bytes/revision/timestamps cannot prove unused historical credits across restart.
Authored C2B2B2 instead uses the separate exact native persisted lifetime proof.
Runtime ownership does not replace any real two-process or downstream acceptance.

## Executable scenarios and current evidence

[JobLifetimeAdmissionSpec](../../src/test/scala/org/goldenport/cncf/job/JobLifetimeAdmissionSpec.scala)
authors LS1..LS11; [JobLifetimeConcurrencySpec](../../src/test/scala/org/goldenport/cncf/job/JobLifetimeConcurrencySpec.scala)
authors LC1..LC6; [JobLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobLifetimeRecoverySpec.scala)
authors LR1..LR6. Each uses adjacent Given/When/Then and direct should assertions
on real engine behavior, and at least 24 fixed-seed finite action checks. Shared
owners, physical manual timers, counted tasks/IDs/clocks/EventStore, bounded
joined race actors and signed standard EntityStore provide concrete facts.
Cleanup is in finally; original 47 Specs and preceding validated slices remain
preserved. Actual `PHASE-69.7-JM69-09B3B3C2A-VAL-003` discovered50 suites and
passed433 tests, zero failures, zero compiler warnings and one existing
SqliteDataStore pending example. Its immutable receipt SHA256 is
`b6dc2aea3c4ef0ccfea78d10ef1b79ec4874c5047583aa41710ae66425401f37`.
Independent Step review, acceptance and commit remain pending.

Preceding C1 actual receipt `PHASE-69.7-JM69-09B3B3C1-VAL-003` discovered 47
suites and passed 410 tests, zero failures, zero compiler warnings, with one
existing SqliteDataStore pending example. C1 independent Step review, acceptance
and commit remain pending. C2A's actual focused receipt is recorded above.

C2B1 authors a separate native Pending/Published singleton with exact paid/unused
credits, normal IDs, once-only claims and prepaid Cancel intent. Its new LQ1..LQ16
SQLite Spec and24 fixed-seed finite sequences retain VAL-005/independent incremental PASS.
The held encoding cost is4096 bytes per entry plus4096 per charged slot; two
slots per normal cover normal/claim IDs before they exist. Provider B1 adds no
runtime caller. C2B2B1 now authors native bound child/control/Retry/annotation/
association/claim and same-State bootstrap. Authored B2 binds fresh-State
runtime/terminal recovery, including legacy/missing authority handling and
original source/port precedence. It honors persisted Cancel intent and original
root-candidate discard without refunding native evidence; fresh validation and
independent review remain pending.
No silent historical floor import can supply restart authority. Real process and
actual CBD acceptance remain mandatory after integration.

Canonical `HYG-PH697-SQL-SIZE-01` remains separate maintenance. C2B, remaining
JM69-09C/D, every JM69-10 real two-process and CBD Support fixture, predecessor
ancestry/obligation audit, aggregate final validation, independent reviews,
version evidence and local/release commits remain mandatory. JM69-09 and Phase
69.7 remain IN_PROGRESS; JM69-10 remains OPEN; no aggregate criterion is closed.

## R8 Native binding and exact same-State restoration

For acknowledged native jobs, persisted quota proof owns every paid operation;
the State budget is a diagnostic mirror replaced by exact acknowledged Seed.
The private State-body binding retains quota/access, admitted root context,
owning Bridge, shared cancellation scope and closed refusal bookkeeping under
the existing State monitor. Unbound jobs retain the local R1..R7 branch and
its conservative floors for local bootstrap. Same-State native attachment validates the entire pure
footprint first, then one authenticated snapshot per unique bound job, accepts
native supersets of live IDs/claims, and restores unused normals and zero Cancel
credits exactly. No native componentwise maximum or historical floor is used.

Native association precedes metadata/body and compensation folds every selector
before callbacks. Ambiguous association/claim withholds normal work and terminal
settlement across owners without refund or readback inference. Uncertain original
claim selectors cannot be reissued; other owed claims, annotations and prepaid
safety remain eligible. Known false is an effect-free skip. The actual terminal
boundary validates normal compensation aggregates separately from complete
actual companion descriptors. Missing with no declared action still requires
completed committed Succeeded normal proof, nonblank failure, no companion and
both recoveryRequired flags.

Native Work and distinct Retry pay after provisional physical reservation;
annotation and Suspend/Resume pay before mutation. Replay pays nothing. Cancel
loss applies no local safety in that call; fresh known intent applies original
safety once. Accepted Retry alone replaces the shared scope and resets intent.
Delayed Retry computes time after payment and keeps paid ghosts on overflow or
timer failure, with original failure and actual-attempt closure. NRT-A..K/P1..P3
passed current runtime VAL-003 focused validation; independent Slice
conformance, Step acceptance and commit remain pending. Fresh-State B2 validation/review and every later
operational/full-Phase acceptance obligation remain mandatory.

Current runtime evidence `PHASE-69.7-JM69-09B3B3C2B2B1-VAL-003` records
54 completed / 0 aborted suites, 477 run / 477 succeeded / 0 failed tests,
zero compiler warnings and one existing SqliteDataStore pending example.
Independent incremental Slice conformance, independent Step acceptance and
commits remain pending. Fresh B2 validation/review, C/D, JM69-10 actual
two-process/CBD Support and original full Phase obligations remain open.
See the [runtime validation chronology](../journal/2026/10/2026-10-02-phase-69.7-native-lifetime-runtime.md).

## R9 Authored recovery scenarios and genuine fixture arithmetic

[JobDurableLifetimeRecoverySpec](../../src/test/scala/org/goldenport/cncf/job/JobDurableLifetimeRecoverySpec.scala)
authors NR1..NR24: real SQLite close/reopen and exact authorized ownership;
read-only registration and specific pure/native/local refusals; persisted Cancel
and actual stale/lost settlement; N-relative normal checkpoints with immutable
closed history; recovery Retry/control exclusions; and one bounded adoption race.
NR23 uses Seed6970933641, minSuccessfulTests24 and finite shrinking actions.
Its independent oracle is 2*n charged tasks, 32+64*n history, one admission,
one prepaid Cancel before intent (zero after it), exact remaining/IDs/claims.
No production floor/payment function supplies expectations.

Original local-only LR1/LR2 retain duplicate maxima, due admission and malformed
map boundaries. Native LR3..LR6 positive fixtures instead reserveRoot, create,
publishRoot and capture exact acknowledgements before registration; only actual
Work/association/claim/Cancel and ordinary signed checkpoints add later facts.

| Scenario | Genuine paid/native prerequisite and preserved assertion |
| --- | --- |
| LR3 | Policy4/192/2, root2/96/1/1 then Work1 yields4/192/2/2, remaining2; one actual due association leaves1. Signed5 at policy tasks4 and rows161 at history160 remain pure refusals. |
| LR4 | Root count2 pays4/160/1/1; two actual associations and terminal checkpoint yield remaining0/two IDs. Negative executed count2 at tasks3 rejects checked obligation4 before quota reads. |
| LR5 | Complete Own/PerTask root executes one complete child through public runTaskInJobSync while Running/Pending;2/96/1/1 becomes4/192/2/2, remaining0/two IDs/seven actual checkpoints. Owner attachment preserves history and all ten last-credit refusal observations. |
| LR6 | Original Seed6970933541/min24/shrinking uses genuine root plus actual Work for exact independent footprint capacity; one-history-short branch independently refuses without effects. |

LR3 positive/LR5/LR6 use the owned complete-metadata LifetimeTask and the exact
legacy ten-counter adapter; pure negatives keep their original legacy Task and
defaults. LR5/IR5/accepted IR6 add ordered signed named Absent declarations
before publication/source capture while retaining original transient payloads,
legacy SHA/TTL/timestamps, sanitizer and signed retention checks. Invalid IR6
branches remain direct signed/pure input refusals. Runtime/terminal/resource
fixtures likewise use genuine Published authority; the closed deep2048 terminal
uses root1024 plus Work1024, paid4096/131136/2/2 and actual associations.

This is authored behavior coverage with fresh validation, independent review,
Step acceptance and commit pending. Original independent whole-Step obligations
CPB-PH697-NRT-DISPATCH-REFUSAL-01 and CPB-PH697-NRT-CANCEL-SETTLEMENT-01 remain
closure obligations. HYG-PH697-SQL-SIZE-01 and
HYG-PH697-ROOTA-VALIDATION-PROJECTION-01 remain separate maintenance; authored
B2 does not close them or any Phase criterion.
