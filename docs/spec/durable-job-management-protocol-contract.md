# Durable Job Management Protocol Contract

status=normative
phase=JM69-04D
source-authority=`PHASE-69.2 / JM69-04 / JM69-04D`

This contract projects the accepted management engine contracts through the
additive `job_control` protocol. It is the sole source for Help/meta,
Record/JSON, OpenAPI/REST, and CLI resolution; it creates no adapter-local
query, control, state cache, or lifecycle authority.

## R1 Additive operations and engine map

The `job_control.job` service adds only these read operations:

| Operation | Engine facade |
| --- | --- |
| `list_management_jobs` | `queryPage` |
| `get_management_job_detail` | `queryManagementDetail` |
| `get_management_job_result` | `queryManagementResult` |
| `list_management_job_tasks` | `queryManagementTasks` |
| `list_management_job_timeline` | `queryManagementTimeline` |
| `get_management_job_task_execution_tree` | `queryManagementTaskExecutionTree` |
| `get_management_job_task_detail` | `queryManagementTaskDetail` |

Exact reads use `id`; task detail additionally uses `taskId`. Task and
timeline pages use `id`, `offset`, and `limit`. The list uses only
`persistentOnly`, `status`, `origin`, `limit`, and opaque `cursor`. Engine
validation, authorization-before-observation, cursor binding, invalid-cursor,
expired-snapshot, and missing/denied semantics remain authoritative.

The existing raw job operations retain their names and behavior. These new
operations never call raw query, visible query, result, task, timeline, or tree
facades.

## R2 Bounded Record vocabulary

A management summary contains only `job-id`, `status`, `persistence`,
`origin`, `created-at`, `updated-at`, and `scheduled-start-at`. A detail
contains that summary plus bounded retry facts, `result-summary`, `task-count`,
and `timeline-count`.

Summary and detail do not expose submitter, subject, session, input, payload,
full response, calltree, raw event history, task body, debug, provider, or
diagnostic data. Task, timeline, tree, and task-detail responses are bounded
projections of their respective authorized management facade values.

`get_management_job_result` is closed: `availability` is `available`,
`pending`, or `unavailable-after-restart`, together with `result-summary`.
Only `available` includes `result-value`, which is the current live result.
The other states neither reconstruct nor expose a result or payload body or
reference.

Missing and denied exact management reads both map to the same ordinary
operation-not-found outcome.

## R3 Controls and generated projections

`job_control.job_admin` exposes `cancel_job`, `retry_job`, `suspend_job`, and
`resume_job`. Each calls its one `JobAdminService` delegate, which delegates
once to the existing `JobEngine.control` boundary. Every control Record has
`job-id`, `status`, `async`, `response`, and `changed`. `changed=true` is an
applied transition; `changed=false` is an accepted no-effect replay, as defined
by the 04C control contract.

The seven management reads opt in to generated OpenAPI `GET`; the four control
operations opt in to generated OpenAPI `POST`. The closed opt-in marker is
interpreted only by the generic OpenAPI projector. Every unmarked protocol
operation preserves its existing inferred method behavior. The same protocol
definitions remain the generated Help/meta, Record/JSON, REST, and CLI source.

## R4 Exclusions

Phase 69.1 exclusively owns recovery, durable integrity, provider, and startup
behavior. Phase 69.7 exclusively owns retention, expiry, cleanup, deletion,
operations, and maintenance. This projection adds none of those operations,
nor durable format/migration, HTTP-server, CLI-adapter, JCL, or UX changes.
