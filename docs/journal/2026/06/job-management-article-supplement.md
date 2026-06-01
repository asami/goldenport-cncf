# CNCF Job Management Article Supplement

Date: 2026-06-01
Status: journal note

## Purpose

This note collects article-oriented source material for explaining the current
CNCF Job Management model.

This is not a normative design document. The primary normative reference is:

- [job-management.md](../../../design/job-management.md)

The sections below supplement that primary reference with related information
from configuration, Web/API response, and Phase 22 closure documents.

## Current Status

The current Job Management design is the closed Phase 22 baseline.

The phase dashboard is:

- [phase-22.md](../../../phase/phase-22.md)

`phase-22.md` is not the normative specification. It records that the following
work is complete:

- Command execution policy normalization;
- Job Entity management;
- JCL profile / execution profile diagnostics;
- JobDefinition Entity / binding / execution record policy;
- Task transaction and compensation boundary;
- user Job notification policy;
- Event-based user notification forwarding;
- CompositeQuery boundary for Web page context.

## Command Execution Mode Tokens

Runtime config key:

```text
textus.command.execution-mode
```

Canonical production values:

```text
sync
job-sync
job-async
job-sync-with-async-cont
```

Accepted long alias:

```text
job-sync-with-async-continuation
```

Legacy compatibility inputs:

```text
sync-direct-no-job
sync-job
async-job
```

Deprecated compatibility / test-only modes:

```text
async-job-and-await
sync-job-async-interface
```

These deprecated modes should not be used in new configuration or production
descriptors. New async-interface tests should use `JobAsync` plus an explicit
await helper instead.

Reference:

- [configuration-model.md](../../../design/configuration-model.md)

## Descriptor-Level Transaction Defaults

Descriptor-level `commandExecutionPolicy` can specify transaction semantics.

Current strict defaults:

```text
callerTransactionPolicy = join-caller
eventTransactionRequirement = required
jobTransactionScope = per-task
continuationEventTransactionRequirement = required
```

Meaning:

- synchronous phases join the caller transaction by default;
- Event-driven handlers are required to run in the same active transaction by
  default;
- Job-managed execution uses per-task transaction scope by default;
- async continuation tasks also default to strict Event transaction semantics
  within their own continuation transaction.

Relaxing any of these is an explicit operation design decision.

Examples of relaxation:

```text
eventTransactionRequirement = best-effort
eventTransactionRequirement = ignore
callerTransactionPolicy = new-transaction
jobTransactionScope = whole-job
```

## Response Envelope Contract

Canonical CNCF success / accepted response envelopes reserve `data` for the
business payload.

Top-level roots:

```yaml
data: ...
execution: ...
job: ...
continuation: ...
page: ...
diagnostics: ...
debug: ...
links: ...
```

Semantics:

- `data`
  - business payload only.
- `execution`
  - execution metadata;
  - includes mode, interface, component, service, operation, and job-management
    flags.
- `job`
  - primary Job metadata;
  - carries primary `job.id` when Job-managed execution is used.
- `continuation`
  - residual / async continuation metadata;
  - for `JobSyncWithAsyncCont`, the conceptual mode is:

```text
event-async-same-job-task
```

- `page`
  - pagination metadata.
- `diagnostics`
  - warnings and structured operational diagnostics.
- `debug`
  - debug-only runtime information such as calltree / trace.
- `links`
  - navigation or related-resource links.

Important rule:

```text
result is not a CNCF response-envelope root.
```

`result` is reserved for external protocol adapters such as JSON-RPC or MCP
when the protocol requires that field.

`textus-execution` is no longer a canonical envelope root.

Reference:

- [web-layer.md](../../../design/web-layer.md)

## JobSyncWithAsyncCont External Semantics

`JobSyncWithAsyncCont` returns:

- the primary business result synchronously in `data`;
- the primary Job id in `job.id`;
- continuation intent / metadata under `continuation`.

The returned `job.id` is the tracking root for both:

- the synchronous primary Task;
- later async continuation Tasks.

The async part must be traceable through the same Job's task tree / timeline.
It is not an untracked fire-and-forget thread.

The current design prefers same-Job continuation Tasks, not child Jobs, for the
standard `JobSyncWithAsyncCont` path.

## Phase 22 Semantic Direction

Phase 22 final semantic direction:

- CQRS identifies state-changing operations as Commands.
- Command does not automatically mean async Job execution.
- Runtime cost and application UX determine whether a Command runs
  synchronously or as a Job.
- Job execution is explicit platform behavior.
- Simple CRUD-style Commands remain synchronous by default.
- Job and JobDefinition are runtime/system management entities, not business
  workflow entities.
- Job instance and JobDefinition are distinct.
- Job state and state transitions are part of Job control.
- Task boundaries are the natural place to define transactional success,
  failure, and compensation inside a Job.

## Job Instance And JobDefinition

A Job instance is:

- a concrete execution record;
- projected as a system Job Entity;
- the record of lifecycle, status, result, timeline, and control state;
- one execution lifecycle.

A JobDefinition is:

- a reusable runtime/system definition;
- a separate system Entity;
- a place to describe launch behavior, JCL/profile, binding, version/hash, and
  lifecycle;
- the future location for executable JCL sections such as procedural `flow` and
  Event-driven `events` / `onEvent`.

A Job launched from a JobDefinition should retain definition snapshot metadata
for auditability.

## Notification And Web Context

JobEngine should emit Job lifecycle / recovery events only.

User-visible notification behavior is implemented by Event forwarding rules to
`UserNotificationProvider`, not by embedding direct notification logic into
JobEngine.

Web page context uses a query-only CompositeQuery boundary for display support
data, including:

- notification counts;
- active Job counts;
- unconfirmed Job counts;
- session/page context values.

This keeps server-rendered Web UI from issuing many independent Domain-tier
calls and prevents page rendering from producing Commands or Jobs.

## Article Framing Recommendation

A practical article structure:

1. Problem
   - Long-running or observable execution needs traceability, retry,
     cancellation, and audit.
2. Core distinction
   - Command is a semantic operation.
   - Job is an operational execution-management record.
3. Four execution modes
   - `Sync`
   - `JobSync`
   - `JobAsync`
   - `JobSyncWithAsyncCont`
4. Four-layer runtime model
   - Job
   - Task
   - Transaction
   - Event
5. Key design rule
   - `Task != Transaction`
6. Strict transaction defaults
   - same-transaction Event handling is the default;
   - weakening it is explicit.
7. Response envelope
   - business data in `data`;
   - operational tracking in `execution`, `job`, `continuation`.
8. Operational result
   - Job management makes execution observable, controllable, and recoverable.

## Source Documents

- [job-management.md](../../../design/job-management.md)
- [configuration-model.md](../../../design/configuration-model.md)
- [web-layer.md](../../../design/web-layer.md)
- [phase-22.md](../../../phase/phase-22.md)
