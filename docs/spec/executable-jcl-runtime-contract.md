# Executable JCL Runtime Contract — Slices JM69-05A/B/C/D

This specification defines the accepted executable JCL boundary for Phase
69.3. Slice JM69-05A parses and semantically compiles a closed model, Slice
JM69-05B submits its bounded finite flow through the existing Job/Task bridge,
Slice JM69-05C publishes declared events and runs explicit local
continuations, and Slice JM69-05D proves immutable JobDefinition snapshots
through the public management/submission surfaces. The contract does not add
dynamic selectors, branching, parallelism, looping, waiting, or scripting.

## Accepted root

Executable sections are legal only under the canonical `job:` root when its
`target` contains `action`. The legacy `jobs:` batch root and a workflow target
may remain valid for non-executable JCL, but reject any executable section.

Every parsed or directly constructed `JobDefinition` has exactly one semantic
target before dispatch: either a non-empty `target.action`, or a workflow with
non-empty `definition` and `registration`. Both-target and no-target values
fail semantic compilation. Action and Workflow submission both compile this
same invariant before selecting their existing execution authority.

`profile` and `profile.eventChain` are diagnostics-only. They are parsed and
retained without affecting compilation control flow.

## Closed grammar

`flow` has exactly `steps`:

```yaml
flow:
  steps:
    - id: step-id
      action: component.service.operation
      parameters:
        key: value
```

Each step has exactly `id` and `action`, plus optional string `parameters`.
Step IDs and actions are non-empty, IDs are unique, `root` is reserved, and no
more than 32 steps are accepted.

`events` has exactly `emit`:

```yaml
events:
  emit:
    - id: event-id
      after: step-id
      name: EventName
      kind: domain
      persistent: true
```

Each emission has exactly `id`, `after`, and `name`, plus optional `kind` and
`persistent`. IDs and names are non-empty and IDs are unique. `after` is either
`root` or an accepted flow-step ID. Emission count is bounded at 32.

`onEvent` has exactly `handlers`:

```yaml
onEvent:
  handlers:
    - id: handler-id
      event: EventName
      action: component.service.operation
      parameters:
        key: value
```

Each handler has exactly `id`, `event`, and `action`, plus optional string
`parameters`. IDs and actions are non-empty and IDs are unique. `event` must
name one emitted event, and declaration order is retained. Handler count is
bounded at 32. Accepted handlers compile to explicit same-Job continuation
values.

## Pre-execution rejection

Unknown keys, duplicate IDs, unknown Event or `after` references, missing or
empty executable sections, invalid scalar types, and over-limit counts are
rejected deterministically before a submit operation. Common text fields accept
only non-empty source strings, and `events.emit.persistent` accepts only a
source Boolean; numbers, textual booleans, collections, nulls, and arbitrary
objects are rejected. XML rejects every `DOCTYPE`, external general or
parameter entity, external DTD, external schema, and XInclude construct before
JCL conversion, without dereferencing files, URLs, classpath resources, network
endpoints, or entity content. HOCON rejects heuristic, file, URL, and classpath
`include` forms and unresolved substitutions without calling `resolve`. Branch,
condition/when, loop, parallel, join, wait, retry, timeout, cancellation,
`NewJob`, dynamic selectors, scripts, bodies, and other unsupported workflow
forms are not part of this grammar and are rejected. No parser fallback
preserves these forms as inert executable data.

YAML source preflight uses SnakeYAML `SafeConstructor` with bounded loader
options: at most 50 collection aliases, no recursive keys, nesting depth at
most 50, and at most 3 MiB of code points. Tagged construction is rejected
before `RecordSourceLoader` receives a value; ordinary untagged mapping and
sequence source shapes remain accepted.

## Ownership boundary

JM69-05A owns typed parsing, deterministic normalized records, cross-field
semantic validation, and the `JobSemanticPlan`. JM69-05B owns bounded runtime
sequencing through existing Job/Task and Action authorities. JM69-05C owns the
local JobControl adapter that connects compiled emissions to existing EventBus,
EventEngine persistence, and JobEngine same-Job execution. JM69-05D owns the
public-surface proof that a submitted Job retains its immutable JobDefinition
snapshot after a later definition update. EventReception policy and
subscriptions remain independent authorities.

## Slice B bounded runtime bridge

For a canonical action target, the compiled plan is submitted as exactly one
existing Job. Its ordered task list contains the root action first, followed by
one existing `ActionTask` for each declared finite flow step. Every selector is
resolved through `_resolve_target_action`, and every task is prepared through
`_prepare_operation_task` with the caller's original `ExecutionContext` before
the single `JobEngine.submit` call. The root prepared context remains the Job
context; per-flow prepared-context return values are not chained or exposed.

The root receives exactly `job.parameters`. Each flow step receives those
parameters merged with its own parameters, with the step value taking
precedence. The Job retains the existing `jcl.target.action` annotation and,
when flow is present, records declared IDs in the stable
`jcl.flow.step.ids` annotation and an execution note in the same order. Root
compensation, request summary, persistence, profile, definition snapshot,
await-result response, authorization preparation, and all existing JobEngine
lifecycle semantics remain unchanged.

`events` and `onEvent` are compiled-but-not-executed in Slice B. Their runtime
bridge is defined in Slice C below.

## Slice C event and continuation bridge

Before the one `JobEngine.submit` call, every declared `onEvent` handler is
resolved and prepared through the same `_resolve_target_action` and
`_prepare_operation_task` paths. Its parameters are `job.parameters` merged
with handler parameters, with handler values taking precedence. Prepared
continuations are associated by declared event name and retain declaration
order. They are not appended to the initial Job task list.

Only a source task with a matching emission is wrapped, and the wrapper first
delegates the prepared task unchanged. On `TaskSucceeded`, each matching
emission is processed in declaration order. The wrapper creates a
`ReceptionDomainEvent` with the declared name and kind (or `domain-event` when
kind is absent), an empty payload, and the source task's `ExecutionContext`
clock time. It includes stable `jcl.event.id` and `jcl.event.after` attributes,
standard `cncf.context.jobId`, `cncf.context.taskId`,
`cncf.context.correlationId`, and `cncf.context.causationId` attributes when
those values exist, and source subsystem/component/action attributes only from
the current task context or prepared delegate metadata.

The event is published through the existing `EventBus.publishRuntime` with
`EventPublishOption(persistent = emission.persistent.getOrElse(false))` and
the current source-task context. A local continuation is admitted only after
that publication succeeds. Each matching continuation then runs synchronously
through `JobEngine.runTaskInJobSync` with the current Job ID and source-task
context. JobEngine creates the child task record, source-task parent relation,
lifecycle checkpoints, cancellation scope, and execution context. No
`submit`, enqueue, `NewJob`, direct action-engine call, EventBus registration,
or mutable EventReception state is used by JCL.

The source wrapper returns the original successful response only when all
publication and matching continuation calls succeed. A source `TaskFailed`
performs no emission or continuation. A publication or continuation failure is
returned as the source wrapper's `TaskFailed` result, leaving JobEngine
failure, retry, cancellation, and compensation behavior authoritative.

When emissions or continuations are present, Job debug metadata records their
declared IDs in declaration order as `jcl.event.ids` and
`jcl.continuation.ids`, with corresponding execution notes. Dynamic payloads
are never recorded as Job debug metadata.

## Lineage, replay, and duplicate ownership

Each accepted event carries the current source task's job, task, correlation,
and causation context. Causation is the current JobContext causation ID,
falling back to the source action ID and then the correlation ID. Same-Job
continuation records use the source task as parent and preserve handler order.

One completed source-task execution produces each matching emission once and
each matching declared continuation once. A whole-Job retry or replay is
existing JobEngine behavior and may execute the source again, re-emit its
declared event, and re-run its continuation. JCL adds no global duplicate
delivery suppression; receiver idempotency and external delivery
deduplication remain Event-contract responsibilities. Static pre-existing
EventBus/EventReception consumers receive the same published event
independently of JCL local continuations.

## Slice D immutable JobDefinition snapshot

Submitting an active JobDefinition by reference resolves one stored typed Entity
and captures its opaque saved definition ID, key, source, format, and normalized
profile in the existing Job snapshot. EntityStore owns the revision used for its
internal conditional save; there is no domain version, revision, or hash in the
definition or submitted snapshot. The one submitted Job
executes the captured source plan and retains its original task and
continuation records.

Updating that same active definition through
`job_control.job.update_job_definition` replaces the current source under the
same stored identity and ordinary EntityStore lifecycle. A subsequent management
read exposes the new source, but the already submitted Job remains succeeded
with its original snapshot, task count, task lineage, and execution trace; it
does not execute nodes introduced by the update. Future submissions may use the
new current definition.

This proof uses only `job_control` create, submit-by-reference, update, and
management-read operations. It does not submit a task directly to JobEngine,
publish or register on EventBus directly, mutate EventReception, or call the
action engine directly. The existing JobEngine, EventBus/EventEngine,
EventReception, restart, retry, cancellation, and durable task-position
semantics remain authoritative; Slice D introduces no new bypass or recovery
mechanism.

## JobDefinition direct-ID compatibility bridge

The current [lightweight lifecycle contract](job-definition-lifecycle-contract.md)
owns this boundary. Canonical key equality is trimmed-string equality. The key
remains a key; creation persists one typed Entity with an opaque saved ID.
Store-scoped exact-key search finds that Entity, and `loadDetached` uses its
actual stored ID. Updates retain that identity. There is no key encoding,
key-derived ID, digest, companion integrity field, or correspondence table.
Distinct keys such as `a-b` and `a_b` remain distinct. EntityStore revision is
an internal conditional-save token, not a second JobDefinition domain control.

The earlier Phase 69.3 key-encoded direct-ID description is superseded by this
canonical lightweight lifecycle. No general EntityId/UniversalId redesign,
identity migration, or restored governance system is introduced here.

## Out of scope

Restart/recovery mechanism redesign, EventReception policy or subscription
redesign, global event registration, asynchronous or `NewJob` continuation,
external event delivery, engine redesign, and changes to authorization,
persistence, scheduling, retry, timeout, cancellation, compensation,
workflow, transport, or public protocol sources are outside Slice D.

Global EntityId/UniversalId redesign, store-wide key indexing, and identity
migration are outside this executable-JCL contract.

## JM69-10C9 native metadata and process companion

status=selected_validation_complete
manifest=PHASE-69.7-JM69-10C9-implementation01@3
validation_status=REP006-and-ACC001-passed
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [JX1..JX9 process companion](job-cross-process-acceptance-contract.md#jcl-native-process-companion)
and [seven-scenario executable specification](../../src/test/scala/org/goldenport/cncf/job/JobJclCrossProcessAcceptanceSpec.scala)
extend the existing runtime contract with genuine public finite-flow execution
and independently fresh native readers. A JCL-private prepared-task decorator
uses site-selected defaults: root submission is Root, finite flow is Child,
and same-Job continuation is Continuation. A nonempty delegate relation remains
authoritative. Missing transaction role/scope use Own/PerTask. All original
action/target/operation/parameters/persistence/compensation metadata, run,
canonical outcome, admission failure, and prepared context remain delegated;
`OperationEvaluationActionTask` remains the actual callback owner and
`JclEventAwareTask` remains outside the decorator.

The first actual task has no parent and Root/reference None. Each later finite
task has its immediately preceding actual task ID as both parent and Child
reference. Same-Job continuation retains its actual source-task parent. The
engine's explicit compensation relation/parent override remains authoritative.
TaskStart/Outcome checkpoints assert the genuine accumulated prefix, and the
terminal record has all N distinct tasks in declaration order. The C9-local
evidence transformation calls the unchanged addressed shared pair and copies
only descriptor kind to Action; it does not rewrite models or relax projection.

For a stored definition, the writer publicly creates/gets active A, submits its
reference, completes A, then publicly updates the same ID to B and retires it.
The fresh reader loads the real engine-created JobEntity submitted source A and
the current typed JobDefinition B/Retired under the native lease. Separately it
authenticates the original signed definition metadata and cross-matches saved
ID/key/format. That signed envelope contains no source A and does not
authenticate the JCL body. Its fixed legacy metadata fields are not lightweight
definition controls. No new hash, source Blob, schema, seeded JobEntity, or
writer-content authority is introduced. Fresh durable public query/management
surfaces remain body-free and preserve foreign-caller refusal/concealment.

The [Oct6 journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-cross-process.md)
retains C9-SIGNED-DEFINITION-SOURCE-01 and C9-FINITE-FLOW-ROOT-PARENT-02 plus the
Manifest3 decisions. At initial authoring selected6REP/129ACC were planned,
with no C9 validation result; the current selected results below supersede that status. Actual event/continuation process acceptance is C10 and remains OPEN;
whole-JM69-10 independent M2/commit, actual CBD provider/Entity/completed full
Review/fresh recovery, remaining security/started-work decision, aggregate full
validation, sole original full Phase review, and local release remain OPEN.

## Current C9 selected validation — 2026-10-06

Initial `authored_validation_pending` / `planned-not-run` remains authoring
history and is superseded by `status=selected_validation_complete` and
`validation_status=REP006-and-ACC001-passed`. Current
`review_status=original-wholeStep-protected-M2-pending` and
`acceptance_status=not-accepted` remain unchanged.

Actual [REP006 representative result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-representative06-result.json) completed6 suites/60 tests,
60 succeeded, aborted0/failed0/canceled0/ignored0/pending0/compilerwarnings0.
Actual [ACC001 accumulator result](../../.codex-workflow/phases/PHASE-69.7/JM69-10C9-accumulator01-result.json) completed129 suites/1057 tests,
1057 succeeded, aborted0/failed0/canceled0/ignored0/compilerwarnings0, plus
only the original `SqliteDataStoreSpec` pending1. Both typed readers, SBT and
wrappers exited0; both execution permissions were consumed and actual
`lock=released` was verified by the parent. These are selected-validation
results, with no full-repository, whole-Step or full-Phase acceptance inferred.

JX6 retains four typed pre-execution refusals and complete unchanged native
baselines/effects. The original malformed-YAML refusal retains `ParserException`,
the `expected the node content` diagnostic and exact `Taxonomy(System,Corrupted)`
(System-Corrupted). Draft/Retired exact messages and the unsupported exact-key
message retain `argumentInvalid`. This records existing branch-specific public
consequences; it introduces no parser/taxonomy policy and weakens no negative
assertion. Source A/current B, separate signed metadata, JX1..JX9, privacy,
no-reader-replay, cleanup and the ordinary seeded min24 property remain unchanged.

Stage remains IN_PROGRESS; JM69-09 DONE, JM69-10 OPEN and Phase69.7 IN_PROGRESS.
All original wider acceptance obligations above remain open, including actual
C10 processes, security/positive started-work decision, actual CBD full Review
and independent fresh recovery, compatibility/guidance promotion, protected
whole-JM10 M2/local acceptance commit, predecessor reciprocal deferred-full,
aggregate full CNCF/downstream validation, sole epoch1 full Phase review and
final local release. Original history, allowances, counters and HYG records
remain; selected validation neither resets them nor records review acceptance.

## JM69-10C10 event and continuation process companion

status=authored_validation_pending
manifest=PHASE-69.7-JM69-10C10-implementation01@1
validation_status=planned-not-run
review_status=original-wholeStep-protected-M2-pending
acceptance_status=not-accepted

The [EC1..EC9 companion](job-cross-process-acceptance-contract.md#jcl-event-and-continuation-native-process-companion)
and [nine-scenario executable specification](../../src/test/scala/org/goldenport/cncf/job/JobJclEventCrossProcessAcceptanceSpec.scala)
author actual sequential JVM consumers of the existing Slice C bridge. Real
public inline JobControl Action submission executes root/source commands,
publishes through native EventBus and runs matching local handlers. One static
observer is registered by fixture setup; JCL subscriptions remain unchanged.
Root emission keeps the root scalar; flow emission keeps the source scalar.
Handlers inherit the root parameter map with their own overrides.

Independent Action/Root/Child/Continuation and Own/PerTask selectors validate
actual native models before genuine keyed pairs. Root has no parent; the flow
Child references root; each Continuation references the actual emitting source,
never the previous handler. Progressive boundary vectors preserve the source
Running/Pending during nested handlers. For T=base+N, terminal revision4+2*T
and all T Committed descriptors match exact live order. Dynamic native tickets
yield paid2*T/(32+64*T+32*N+notes)/(1+N)/(1+N), notes3 for root or4 for flow,
remaining0, exact normal IDs and no claims/cancel claim.

Command attempts/UoW termination, post-commit effects and source wrapper
outcome are separate. SourceFailure retains its original argumentInvalid with
no emission. DispatchFailure retains typed event.dispatch and its previous
observer cause after store append; HandlerFailure stops at its first failing
continuation. A wrapper failure does not erase successful delegate commits.
These failures deliberately use Ephemeral Jobs and prove empty durable Job
storage rather than inventing a signed failed recovery contract.

Original ReceptionDomainEvent and actual writer EventRecords prove exact
emission/lineage/context/time/order and persistent option. The existing
Subsystem EventStore is in-memory: each fresh reader's selected eventcount is0,
including after persistent writer events. There is no EventStore durability,
replay, global deduplication or external delivery claim. Persistent readers
restore exact signed terminal/payment facts and actual native inline JobEntity
debug metadata, preserving UnavailableAfterRestart and foreign concealment,
unchanged native rows and zero execution/source/pair/port/Blob/publication/
SQL/Entity-write effects.

All nine leaves and ordinary seed69710102L/min24 generated invocations retain
adjacent GWT, actual sequential processes, bounded receipts and full resource
closure. The [journal](../journal/2026/10/2026-10-06-phase-69.7-job-jcl-event-cross-process.md)
records selected8REP/130ACC as planned-not-run. Original C9 evidence, histories,
allowances and the three [stable Hygiene follow-ups](../journal/2026/10/2026-10-01-phase-69.7-hygiene-follow-up.md)
remain. Stage C10 IN_PROGRESS, JM69-09 DONE, JM69-10 OPEN and Phase69.7
IN_PROGRESS; original whole-Step M2, remaining security/started-work/CBD,
compatibility/promotion, aggregate/full-Phase validation/review and release are
still OPEN. No production runtime contract is changed by this authoring.
