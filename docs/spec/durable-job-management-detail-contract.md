# Durable Job Management Detail Contract

status=normative
phase=JM69-04B
source-authority=`PHASE-69.2 / JM69-04 / JM69-04B`

This contract defines additive, canonical exact-job management reads. It does
not alter the raw `JobEngine` read methods, durable-record format, recovery,
control, storage-provider, retention, or transport contracts.

## R1 Detail and result vocabulary

`queryManagementDetail` returns a `JobManagementDetail` only for an authorized
job. Its shape is the 04A `JobManagementSummary`, a bounded retry summary, the
existing `JobResultSummary`, and Task and timeline counts. It contains no
submitter identity, debug data, `JobInput`, payload body or reference, full
`OperationResponse`, result body, calltree, provider, or transport value.

`queryManagementResult` returns a closed `JobManagementResult`: `Available`
contains only the existing live `JobResult`; `Pending` carries the existing
result summary before a final live result exists; and `UnavailableAfterRestart`
carries a retained terminal result summary when a fresh engine has only a
durable terminal projection. The latter never deserializes, recreates, loads,
resolves, verifies remotely, or exposes an external result/payload body or
reference.

## R2 Authorization and exact reads

Every management read locates a candidate using the same live-before-terminal
durable precedence as 04A, then authorizes that candidate through
`JobQueryPolicy` before calculating returnable detail, result, Task, timeline,
tree, Task-detail, or count data. Missing and denied jobs both return a
successful empty option at this facade.

The additive exact reads are `queryManagementDetail`, `queryManagementResult`,
`queryManagementTasks`, `queryManagementTimeline`,
`queryManagementTaskExecutionTree`, and `queryManagementTaskDetail`. They all
accept `JobQueryPolicy` and `ExecutionContext`; the raw `query`, `getResult`,
`queryTasks`, `queryTimeline`, `queryTaskExecutionTree`, and `queryTaskDetail`
remain compatibility methods with unchanged behavior.

## R3 Bounded pages and terminal source

Management Task and timeline pages accept offsets no lower than zero and limits
in `1..100`. Invalid pagination fails before a job candidate is located. Tree
and Task-detail reads are exact reads of the already-authorized job.

Live state is the sole source for an available result. When live state is absent
and an authorized terminal durable projection is the candidate after a fresh
restart, its retained terminal facts supply only the
`UnavailableAfterRestart` summary outcome. Phase 69.1 remains the durable
snapshot integrity and metadata-verification boundary.

## R4 Exclusions

This contract adds no payload/blob resolver, provider integration, external
storage operation, remote verification, download, signing, retention policy,
durable migration, recovery behavior, lifecycle write, management control,
protocol, Help, HTTP, CLI, JCL, task-local calltree body, raw event history, or
user-experience surface.
