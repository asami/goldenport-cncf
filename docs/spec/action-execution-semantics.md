# Action Execution Semantics

Status: normative static specification

Action execution is defined by its admitted route, operation kind, explicit
Job intent, and shared execution lifecycle. This document is authoritative for
observable behavior. Architectural context is described non-normatively in
`docs/design/execution-model.md` and
`docs/design/component-internal-execution-model.md`.

## Route scope and admission (R1)

This specification covers public execution of plain `Action`, `QueryAction`,
`CommandAction`, operation-definition-classified generic `Action`, explicit
Job/async routes, and event continuation/direct routes. It defines observable
responses, Job creation, context, authorization, UnitOfWork, observability,
and failure boundaries without prescribing private implementation shape.

A route-resolved selector operation MUST pass Subsystem selector
authorization and operation-evaluation admission before
`ComponentLogic._execute_action` (`Subsystem.scala:992-1041`,
`1701-1726`). A no-route embedding compatibility invocation is admitted
through the owning component fallback in `_execute_component_action_c` or the
no-route branch of `_prepare_component_operation_task`
(`Subsystem.scala:1209-1219`, `1689-1699`). That compatibility invocation MUST
NOT pass Subsystem selector authorization or operation-evaluation capture, and
MUST NOT invent a Subsystem route or operation-evaluation identity. It MUST
still execute the bound `ActionCall` through normal `ActionEngine.execute`
authorization and lifecycle. `ComponentLogic.createActionCall` binds the
ordinary ActionCall execution selector from the component and request fields
(`ComponentLogic.scala:70-80`).

## Operation-kind selection (R2)

When an operation definition is present, its `kind` MUST be resolved before
the runtime Action subtype, including conflicts. A definition `QUERY` MUST
select the query route even for a `CommandAction`, and a definition `COMMAND`
MUST select the command route even for a `QueryAction`. Only when no
recognized definition kind is available may runtime subtype select
`QueryAction` or `CommandAction`; otherwise the plain Action route applies.

## Plain direct execution (R3)

A route-resolved plain, unclassified `Action` (not a `QueryAction` or
`CommandAction`, and without a recognized operation-definition kind) MUST
execute its bound `ActionCall` synchronously through the shared Action
execution boundary and MUST return the exact `OperationResponse` produced by
that call. It MUST NOT create an implicit Job or return a Job ID, including
when debug `traceJob` is enabled. A no-route embedding compatibility
invocation that reaches the plain route has the same direct-response and
no-implicit-Job requirements.

Every direct route MUST preserve the exact `OperationResponse` returned by the
bound ActionCall. It MUST NOT replace that response with a Job ID, wrapper,
polling token, or reconstructed payload. Only an explicit Job route described
by R6 may create a Job.

## Query execution and explicit trace (R4)

`QueryAction` and a generic Action classified as `QUERY` MUST execute directly
by default and MUST return the exact direct `OperationResponse`. When the
existing explicit debug `traceJob` setting is enabled, the query route MUST
create a persistent synchronous Job, await it, and return the same response
while retaining Job and calltree diagnostics. The trace setting MUST NOT
change a plain Action route into a Job route (R3).

## Command policy and response modes (R5)

For a concrete `CommandAction`, the effective command policy MUST be resolved
in this order:

1. `ctx.framework.commandExecutionMode`, mapped through
   `CommandExecutionPolicy.fromLegacyMode`;
2. the operation definition's valid typed `commandExecutionPolicy`;
3. valid legacy operation `execution` metadata, mapped through
   `fromLegacyExecution`; and
4. the concrete action's `commandExecutionMode`, mapped through
   `fromLegacyMode`.

When the command+SCRIPT compatibility predicate at
`ComponentLogic.scala:552-556` is true, step 4 MUST be replaced by
`CommandExecutionPolicy.default`. For a generic Action classified as
`COMMAND`, the order MUST be framework override, operation-definition typed
policy, valid legacy `execution` metadata, then
`CommandExecutionPolicy.default`; there is no runtime `CommandAction` policy
to consult. An invalid or absent typed policy MUST NOT erase valid legacy
execution metadata.

With `managedByJob = false`, a command MUST return the exact direct response.
With `managedByJob = true`, an asynchronous interface MUST return a Job-ID
`OperationResponse`, a synchronous interface MUST await the Job result, and a
synchronous `asyncContinuation` MUST return the primary Job result.

## Explicit Job management and input (R6)

Job creation MUST occur only when a caller explicitly selects Job management,
including `submitJob`, `JobSubmitOption`, a managed command policy, a rule
`Job`, workflow/JCL/Job Control, or an event async policy. Job lifecycle,
status, result, primary result, await, control, retry, persistence, and task
read models MUST be provided by `JobEngine`.

`JobSubmitOption` controls Job persistence, run mode, priority, scheduling,
debug/profile metadata, and optional `input`. A `JobSubmitOption.input` value
is an optional `JobInput` carrier. The nested `JobInput.retentionPolicy`
controls cleanup (`DeleteOnCompletion`, `Ttl`, or `Keep`) and is not selected
directly by `JobSubmitOption`. A direct route MUST NOT materialize Job input or
Job read-model state merely because a request contains input-shaped fields.

Explicit Job intent alone MAY create or continue Job work. No response shape,
ambient context, debug setting, or inferred downstream need MAY create Job work
when an explicit Job intent is absent. Unsupported or ambiguous execution
intent MUST fail deterministically as a structured `Consequence.Failure`.

## Event continuation and dispatch (R7)

Synchronous event dispatch MUST execute through the dispatcher and bound action
context. Same-Job and asynchronous policies MUST use their explicitly selected
Job task or enqueue paths. Direct and same-Job synchronous dispatch MUST
preserve the action result internally and report dispatch success. Same-Job
asynchronous dispatch MUST enqueue in the existing Job, and new-Job
asynchronous dispatch MUST submit an explicitly configured Job. Event policy,
transaction relation, saga/correlation lineage, and authorization MUST remain
in force.

An immediate same-Job enqueue or new-Job submission admission failure MUST be
returned as `Consequence.Failure`; it MUST NOT be converted to dispatch
success, `Unit`, or a Job ID. When an admitted asynchronous enqueue or
submission is deferred to post-commit processing, its callback MUST return its
structured `Consequence` to UnitOfWork commit processing. A callback failure is
an observable post-commit dispatch failure.

## Authorization distinctions (R8)

The following authorization cases MUST remain observably distinct:

1. **Subsystem selector authorization/evaluation rejection.** A route-resolved
   admission rejection occurs before `ComponentLogic._execute_action`; no
   ActionCall starts and the admission/evaluation failure is structured (R1).
2. **Normal `ActionCall.authorize` rejection.** `ActionEngine.execute` performs
   authorization in its pre-execution `authresult`
   (`ActionEngine.scala:68-88`, `404-412`). The rejection MUST be returned as
   a structured failure and recorded at the failure calltree boundary. It MUST
   NOT commit `ActionEvent.authorizationFailed` and MUST NOT emit normal
   `observe_enter`/`observe_leave` for an action that did not start.
3. **Isolated `ActionEngine.executeAuthorized` rejection.** Its denied branch
   MAY commit `ActionEvent.authorizationFailed` before returning the security
   failure (`ActionEngine.scala:332-354`). This event/commit behavior MUST NOT
   be presented as the normal admitted runtime path in item 2.

## Execution context and scope (R9)

An ActionCall MUST execute with its explicitly bound immutable
`ExecutionContext`, including security, runtime facilities, observability,
route-resolved operation identity when present, and UnitOfWork binding.
Component execution MUST preserve the component action child scope. No-route
embedding MUST preserve the supplied context without fabricating a Subsystem
route or operation-evaluation identity; its ActionCall still binds the
ordinary execution selector described in R1. Event continuation MUST preserve
or derive context, Job context, transaction relation, and correlation/saga
lineage according to the event policy (R7).

## UnitOfWork and terminal lifecycle (R10)

The shared Action execution boundary MUST commit the bound UnitOfWork on
successful completion, abort it on action failure, and dispose terminal
runtime resources. Direct and Job-managed routing MUST NOT bypass ActionCall
DSL, ActionCall authorization, managed datastore lease, or terminal cleanup.
When a post-commit callback fails, the primary transaction remains committed:
the failure is a post-commit dispatch failure, not a transaction abort or
rollback. Terminal resource and observability evidence MUST distinguish
committed-with-post-commit-failure from aborted termination and MUST NOT claim
rollback.

## Observability and diagnostics (R11)

Action execution MUST preserve existing tracing, calltree, metrics, logging,
operation-evaluation diagnostics where the route is resolved, and runtime
execution metadata. A started action MUST have exactly one enter and one leave;
selector/evaluation or ActionCall authorization rejection MUST have no
started-action observation pair. Job-specific calltree and Job
debug/persistence records MUST be produced only for explicit Job routes,
including the explicit query trace-Job exception (R4, R6).

## Failure semantics and asynchronous timing (R12)

Expected direct-action execution failures MUST remain structured
`Consequence.Failure(Conclusion)` values. A Job enqueue, submission, or
admission failure, including invalid `JobSubmitOption`, unavailable JobEngine,
or rejected policy, MUST remain a failure and MUST NOT be represented as a
successful `Unit` or Job-ID response. This applies both to immediate paths and
to the structured result reported by post-commit processing.

After a true asynchronous Job is accepted, an asynchronous interface MAY
return a successful Job ID before terminal worker outcome is known. A later
worker failure MUST settle as terminal `JobResult.Failure` and MUST be exposed
by Job status/result consumers. Await and primary-result routes MUST surface
that failure synchronously. No enqueue, submission, or admission failure may
be converted into a successful `Unit` or Job-ID response.

## Stable executable examples

The following examples are stable traceability anchors. Each example names the
governing rules that define its expected behavior.

### E1 — Route-resolved plain Action (R1, R2, R3, R9, R10, R11, R12)

Given a route-resolved plain unclassified Action, when it executes with and
without debug `traceJob`, then it returns the direct `OperationResponse`,
preserves the shared lifecycle, and leaves no implicit Job.

### E2 — No-route component embedding (R1, R3, R9)

Given an already resolved no-route component Action, when it executes, then it
preserves direct ActionCall execution and shared context without selector
admission or automatic operation-evaluation capture.

### E3 — Query and trace-Job behavior (R2, R4, R11)

Given a `QueryAction` or generic `QUERY` Action, when it executes by default or
with explicit `traceJob`, then the default is direct and the trace case awaits
a persistent synchronous Job and returns the same response.

### E4 — Command policy and response mode (R2, R5, R6)

Given a concrete or generic command and its policy metadata, when the effective
policy is resolved, then precedence is exact and direct, Job-ID, await, and
primary-result responses follow the selected mode.

### E5 — Explicit Job lifecycle (R6, R9, R10, R11, R12)

Given explicit Job intent and a `JobSubmitOption`, when the Job is submitted,
then the selected persistence, run mode, input-retention policy, context,
observability, lifecycle, and terminal failure result are preserved. Rejected
admission returns `Consequence.Failure`, while an accepted Job may return its
ID before a later terminal `JobResult.Failure`.

### E6 — Event continuation timing (R7, R9, R10, R12)

Given synchronous, same-Job, or new-Job event continuation, when the selected
dispatch path runs, then authorization, context, transaction relation, and
correlation are preserved; enqueue and submission admission failures remain
structured; and an admitted post-commit failure is observed without claiming a
transaction rollback.

### E7 — Authorization boundaries (R1, R8, R11)

Given selector rejection, normal ActionCall authorization rejection, or
isolated `executeAuthorized` rejection, when each path is evaluated, then its
failure, event-commit, and observation behavior remains distinct.

### E8 — Shared direct and Job-managed lifecycle (R3, R6, R9, R10, R11, R12)

Given either a direct route or an explicit Job-managed route, when the action
executes, then both use the same ActionCall, authorization, UnitOfWork,
observability, and structured-failure boundaries while retaining their distinct
response timing. A post-commit dispatch failure retains committed transaction
evidence and distinct terminal observability.

## Related executable specifications

The following non-normative references identify executable specifications that
exercise portions of these rules:

* `src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala`
  covers operation-definition classification and query trace behavior (R2,
  R4).
* `src/test/scala/org/goldenport/cncf/component/ComponentLogicCommandScriptExecutionModeSpec.scala`
  and `src/test/scala/org/goldenport/cncf/job/JobCommandSyncAndTaskFirstSpec.scala`
  cover command policy and Job response modes (R5, R6).
* `src/test/scala/org/goldenport/cncf/job/JobPrimaryResultSpec.scala` and
  `JobQueryReadModelSpec.scala` cover Job result, primary-result, and read-model
  behavior (R6, R12).
* `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala`
  and `OperationEvaluationJobCaptureSpec.scala` cover route-resolved
  evaluation context, continuation, calltree, and Job correlation, including
  the no-route compatibility case (R1, R9, R11).
* `src/test/scala/org/goldenport/cncf/action/ActionEngineAuthorizationFailureCommitSpec.scala`
  and the isolated `executeAuthorized` cases in
  `ActionEngineObservationSpec.scala` cover isolated authorization event-commit
  and observation behavior only (R8, R11).
