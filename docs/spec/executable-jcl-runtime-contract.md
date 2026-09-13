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

Submitting an active JobDefinition by reference resolves one management record
and captures its definition ID, key, version, revision, hash, source, format,
and normalized profile in the existing Job snapshot. The one submitted Job
executes the captured source plan and retains its original task and
continuation records.

Updating that same active definition through
`job_control.job.update_job_definition` creates the next current revision and,
when the source changes, the next version and hash. A subsequent management
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

This bridge is local to `JobDefinitionEntity`. Canonical key equality is
trimmed-string equality. New records use a versioned, lossless direct ID label:
`v1_<utf16-length-in-hex>_<each-UTF-16-unit-in-four-hex-digits>`. Thus `a-b`
and `a_b` have distinct new IDs while retaining their original normalized keys.

Reads first try the versioned direct ID, then use a store-scoped exact persisted
normalized-key search as a read-only legacy fallback. A complete historic
three-argument `EntityId` cannot be reconstructed from a key because it contains
non-reconstructible timestamp and entropy. At each cache hit, new-ID load, and
legacy-key result, the persisted normalized key must equal the requested normalized
key. A mismatching candidate is never returned. Creation rejects an exact existing
key through either compatible lookup, but admits distinct collision pairs such as
`a-b` and `a_b`; no migration or duplicate repair is performed.

This does not redesign `EntityId`, `UniversalId`, ID-generation context,
EntityStore indexing, or the public ID contract. A general persisted-identity
redesign remains an explicit deferred boundary for later work.

## Out of scope

Restart/recovery mechanism redesign, EventReception policy or subscription
redesign, global event registration, asynchronous or `NewJob` continuation,
external event delivery, engine redesign, and changes to authorization,
persistence, scheduling, retry, timeout, cancellation, compensation,
workflow, transport, or public protocol sources are outside Slice D.

Global EntityId/UniversalId redesign, store-wide key indexing, and identity
migration are outside this executable-JCL contract.
