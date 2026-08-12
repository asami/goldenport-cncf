# Phase 57.1 AES-01 Action Execution Inventory

status = evidence / non-normative
authority = [Phase 57.1](../phase/phase-57.1.md)
normative_contract = [Action execution semantics](../spec/action-execution-semantics.md)

This note records the admitted execution routes and the current public
consumers for AES-01. It is an evidence inventory, not an implementation
prescription. The normative, testable contract is in the linked
specification. Future migration ownership recorded here belongs to Phase 57.2
AES-04/AES-05; it is not work performed by this inventory.

## Evidence key

Route-resolved Request execution enters
`Subsystem._execute_resolved_operation` (`Subsystem.scala:992-1041`), which
performs selector authorization and operation-evaluation admission before
`ComponentLogic._execute_action`. A public Action route enters
`Subsystem._execute_action_c` (`Subsystem.scala:1701-1726`) or, for an owning
component, `_execute_component_action_c` (`Subsystem.scala:1689-1699`). The
normal in-process response callers are
`Subsystem.executeOperationResponse` (`Subsystem.scala:868-870`),
`Component.execute` (`Component.scala:275-277`),
`CncfRuntime.executeActionResponse` (`CncfRuntime.scala:5499-5503`), and the
embedding `CncfHandle.executeAction` (`CncfRuntime.scala:3842-3846`).

## Admitted route inventory

| Route | Concrete admission and execution evidence | Observable response / Job contract | Security, context, UoW, observability, and failure consumers |
| --- | --- | --- | --- |
| Route-resolved plain, unclassified `Action` | `_execute_resolved_operation` and `_execute_action_c` perform `_authorize_operation` and `_resolve_operation_evaluation_admission` before `ComponentLogic._execute_action` (`Subsystem.scala:992-1041`, `1701-1726`). With no operation-definition kind and neither `QueryAction` nor `CommandAction`, the final fallback synchronously runs `taskdecorator(task).run(scopedctx).result` (`ComponentLogic.scala:162-175`). | The decorated task returns the exact bound `ActionCall` `OperationResponse` with no implicit Job or Job-ID scalar, including when `traceJob` is enabled. | This route has selector authorization/evaluation admission, an action child scope, `ActionTask`, `ActionEngine`, UnitOfWork finalization, calltree/trace/metrics/diagnostics, and structured `Consequence` failure boundaries. |
| Route-resolved `QueryAction` | Runtime fallback after operation-definition kind resolution reaches `_execute_query_action` (`ComponentLogic.scala:162-170`). Query Job tasks default to `Ephemeral` (`JobEngine.scala:350-353`). | Without `traceJob`, `task.run(ctx).result` returns the exact direct response (`ComponentLogic.scala:199-215`). With the existing debug setting, a `Persistent`/`Sync` Job with `executionNotes = Vector("debug trace query")` is submitted, noted, awaited, and its response returned (`ComponentLogic.scala:203-212`). | The direct path uses `ActionTask` -> `ActionEngine.execute`; the trace exception retains Job/calltree diagnostics. Failures remain structured `Consequence.Failure` values. |
| Route-resolved generic `Action` classified by operation definition as `QUERY` | `_resolve_operation_kind` reads the matching `CmlOperationDefinition.kind` (`ComponentLogic.scala:265-279`); `Some(Query)` is selected before runtime subtype (`ComponentLogic.scala:162-170`). `_execute_resolved_operation` admits generic Actions (`Subsystem.scala:1017-1029`). | Same direct response and explicit trace-Job exception as `QueryAction`; the definition kind wins even if the runtime Action subtype conflicts. | Same selector authorization/evaluation, explicit context/scope, ActionEngine, UoW, observation, diagnostics, and structured-failure boundaries. |
| Route-resolved `CommandAction` | Runtime branch reaches `_execute_command_action` (`ComponentLogic.scala:165-172`). For a concrete `CommandAction`, `_effective_command_execution_policy` is implemented at the current lines `281-301` (framework override, operation policy, then concrete action fallback, with the command+SCRIPT exception). | `managedByJob = false` runs the decorated task directly. A managed policy submits `Sync` or `Async`; an async interface returns a Job-ID scalar, synchronous interface awaits, and synchronous `asyncContinuation` consumes the primary result (`ComponentLogic.scala:217-263`). `responseJobId` is noted by `_note_job_response` (`ComponentLogic.scala:606-613`). | Job options carry the selected input/policy metadata. The ActionTask still invokes ActionEngine and the shared UoW/context lifecycle. Direct failures remain `Consequence.Failure`; awaited/primary Job failures are converted from `JobResult.Failure`. |
| Route-resolved generic `Action` classified by operation definition as `COMMAND` | `_resolve_operation_kind` selects `Some(Command)` before runtime subtype (`ComponentLogic.scala:162-166`), then `_execute_command_action` is used. For a generic Action, policy resolution is framework override -> operation policy -> `CommandExecutionPolicy.default` (`ComponentLogic.scala:223-231`). | The default is direct. A valid typed `commandExecutionPolicy` takes precedence over valid legacy `execution` metadata; managed policies use the Command Job-ID/await/primary-result modes above. | Same route security, context, scope, UoW, ActionEngine, observability, diagnostics, and structured failure boundaries. |
| No-route component embedding / ad-hoc Action compatibility | `Subsystem._execute_component_action_c` has a separate `_resolve_route` `None` branch that calls `component.logic._execute_action(action, context, identity)` (`Subsystem.scala:1689-1699`). `_prepare_component_operation_task` likewise preserves an unclassified/no-route task as `task -> context` (`Subsystem.scala:1209-1219`); `executeEventContinuationAction` runs that prepared task (`ComponentLogic.scala:179-197`). | This compatibility route bypasses Subsystem selector authorization and operation-evaluation capture; it does not invent a Subsystem resolved route or operation-evaluation identity. `ComponentLogic.createActionCall` still binds the ordinary ActionCall execution invocation selector from `component.componentId.name`, `action.request.service`, and `action.request.operation` (`ComponentLogic.scala:70-80`). Its identity-decorated plain fallback synchronously returns the exact bound `ActionCall` `OperationResponse` with no implicit Job or Job-ID scalar. | ActionCall authorization and the shared `ActionEngine.execute` lifecycle still apply, including explicit context, UoW, observation, and structured failures. `OperationEvaluationAutomaticCaptureSpec.scala:374-385` verifies an already resolved ad-hoc Action returns `success` while the operation-evaluation sink remains empty. |
| Explicit Job / async submission | `ComponentLogic.submitJob` delegates directly to `JobEngine.submit` (`ComponentLogic.scala:558-566`). Production callers include `RuleActionPlan.Job` / `RuleActionAdmission._admit_c`, workflow/JCL/Job Control, and event reception. `JobEngine` exposes submit/status/result/primary-result/await/control/read-model/timeline/task-tree APIs (`JobEngine.scala:825-870`). | A caller-selected Job returns or records a Job ID or consumes a Job-managed result. `JobSubmitOption` selects persistence, run mode, scheduling, priority, debug/profile metadata, and optional `input`; the nested `JobInput.retentionPolicy` controls input cleanup (`JobEngine.scala:390-400`, `461-478`). `awaitResult` and `getPrimaryResult` expose `JobResult.Success(OperationResponse)` or `JobResult.Failure(Conclusion)`. | `ActionTask.run` creates the bound ActionCall and invokes `ActionEngine.execute` (`JobEngine.scala:356-381`); JobEngine owns scheduling, persistence/read-model, retry, cancellation, task outcomes, and Job observability. These are not implicit plain-Action fallbacks. |
| Event continuation, direct and Job-managed | `OperationRequestActionDispatcher` invokes `ComponentLogic.executeEventContinuationAction` (`OperationRequestActionDispatcher.scala:40-51`). `EventReception._dispatch_event_action` selects direct dispatch, same-Job sync, same-Job async enqueue, or new-Job async across `EventReception.scala:1796-1900`. New-Job async constructs `JobSubmitOption` and task at `1863-1885`, calls `engine.submit` at `1886`, and either stages submission post-commit (`1887-1891`) or maps immediate submission to `Unit` (`1892-1894`). In the staged callback the returned `Consequence` is discarded (`val _ = submit()`); same-Job async likewise discards `enqueue()`'s returned `Consequence`, so the outer dispatch can report `Unit` before rejection is surfaced. | Direct/same-Job sync preserves the action result internally and reports dispatch success. Same-Job async enqueues an explicit task but currently loses the enqueue failure in the async branch. New-Job async may return `Unit` when submission is staged; it does not promise a Job ID to that caller, and a staged submission rejection is not currently surfaced or recorded by the outer dispatch. A caller that receives an accepted async Job ID may observe it before terminal worker outcome. | Event authorization, transaction attributes, saga/correlation lineage, and dispatch security remain in force. Immediate (non-staged) Job submission/admission failure is a `Consequence.Failure`; later task failure becomes terminal `JobResult.Failure` (`JobEngine.scala:1597-1617`), while scheduler-worker failure settles a failed Job (`JobEngine.scala:1352-1360`, `1390-1402`). The staged callback and same-Job async loss are current AES-04 repair obligations. |

Operation-definition kind precedes runtime subtype, including conflicts. For a
concrete `CommandAction`, the exact policy lookup is framework command override
(`ctx.framework.commandExecutionMode`) -> typed operation policy -> valid legacy
`definition.execution` metadata -> concrete action's legacy/default mode, with
the command+SCRIPT compatibility combination selecting
`CommandExecutionPolicy.default` before the concrete action fallback
(`ComponentLogic.scala:281-301`, `_is_command_script_combo` at `552-556`). A
generic Action classified as `COMMAND` has no concrete action fallback and ends
at the default shown above (`ComponentLogic.scala:223-231`).

## Public consumer and handoff inventory

This table records current consumers and observable handoffs. It does not
choose the Phase 57.2 implementation. The final column is the explicitly
assigned migration owner.

| Surface | Current consumer / caller | Current observable behavior and handoff contract | Migration owner |
| --- | --- | --- | --- |
| Request / in-process | `Subsystem.executeOperationResponse` (`Subsystem.scala:868-870`), `Service._invoke` (`Service.scala:29-41`), `Component.execute`, `CncfRuntime.executeActionResponse`, and `CncfHandle.executeAction` | In-process callers receive the `OperationResponse` unchanged. Request routing and formatting are separate; a Job-ID scalar is meaningful only when the caller explicitly selected a Job-producing route. | AES-04: migrate real Job-dependent callers to explicit asynchronous intent. |
| Service CLI/HTTP wrappers | Public `Service.invokeCli` builds a protocol request, calls `invokeRequest`, and formats the response as a string (`Service.scala:45-50`); `Service.invokeHttp` converts an HTTP request, calls `invokeRequest`, and converts the response to HTTP (`Service.scala:52-59`). Both delegate through `_invoke` to `Subsystem.executeOperationResponse` (`Service.scala:29-41`). | The underlying `OperationResponse` contract is preserved before CLI string or HTTP response projection; `invokeHttp` currently reports its request-conversion `notImplemented` failure. | AES-04: keep Job-dependent service callers explicit. AES-05: align CLI/HTTP response and Job metadata projection. |
| Operation tool | `DefaultOperationToolService._Invocation._execute_c` builds a `Request` and calls `subsystem.executeOperationResponse(request, executioncontext)` (`OperationToolSource.scala:253-281`), then wraps the response in `OperationToolResult` after the result-size check. | The tool returns the underlying direct or explicitly Job-managed `OperationResponse` unchanged inside `OperationToolResult`; admission and maximum-result failures remain structured. | AES-04: make asynchronous intent explicit for Job-dependent tools. AES-05: align operation-tool result projection with direct-versus-Job metadata. |
| `OperationResponseFormatter` | `OperationResponseFormatter.toResponse` (`OperationResponseFormatter.scala:28-67`) | Formats HTTP/record/scalar responses. For envelope output, `_is_job_id` parses a scalar with `JobId.parse` (`OperationResponseFormatter.scala:158-170`, `332-335`); `_job_record` prefers `responseJobId`, then `debugJobId`, then a Job-ID-looking scalar (`297-312`). Thus current envelope shape and Job metadata use response-shape/Job-ID inference. | AES-05: align Request/transport projection so direct responses and explicit Job metadata are not confused by heuristic inference. |
| HTTP and loopback | `Http4sHttpServer._with_job_id_header` and `_to_http_response_with_metadata` (`Http4sHttpServer.scala:7373-7462`, `7531-7539`); `LoopbackHttpDriver._execute` (`LoopbackHttpDriver.scala:61-70`) | When runtime metadata has `responseJobId` or `debugJobId`, HTTP and loopback responses add/replace `X-Textus-Job-Id`. The HTTP adapter applies this on binary, structured-error, and ordinary response paths; loopback mirrors the metadata handoff. | AES-05: align explicit accepted-Job metadata and direct response handling. |
| Form result extraction/rendering | `FormResultMetadata.fromHttpResponse` / `fromBody` (`FormResultMetadata.scala:96-105`), JSON path extraction (`149-198`), scalar `cncf-job-` extraction (`207-208`), and `toTemplateValues` (`36-57`); `StaticFormAppRendererFormResultPart.execution_debug_panel` (`StaticFormAppRendererFormResultPart.scala:274-307`) | Form rendering exposes `result.job.id` and status from body metadata, accepts several nested `jobId` spellings, and renders application/system Job links from the extracted or execution metadata. `Http4sHttpServer._annotate_application_job` only annotates when body Job ID matches `responseJobId` (`Http4sHttpServer.scala:3730-3749`). | AES-05: align Form result Job acceptance/result fields with explicit transport metadata. |
| Static-form admin and Blob/Association/Tag renderers | `StaticFormAppRendererComponentAdminPart.admin_operation_response` calls `subsystem.executeOperationResponse(request).toOption` (`StaticFormAppRendererComponentAdminPart.scala:1458-1464`). `StaticFormAppRendererBlobTagPart.blob_admin_record`, `admin_association_record`, and `admin_tag_record` call the same API and require `OperationResponse.RecordResponse` (`StaticFormAppRendererBlobTagPart.scala:598-635`). | These admin UI helpers consume direct record responses and convert operation failures or unexpected response shapes into `Option`/structured `Consequence` results; they do not infer a Job from a record. | AES-05: align admin/form projection with explicit execution-mode and Job metadata contracts. |
| CLI debug Job reference | `CncfRuntime._print_debug_job_reference` (`CncfRuntime.scala:5980-6000`), called after command/HTTP execution (`CncfRuntime.scala:3934-3935`, `5442-5443`) | CLI prints a debug Job link from `metadata.debugJobId`, or from `OperationResponse.Http` header `X-Textus-Job-Id`; it does not treat every generic scalar as a debug reference. | AES-05: align CLI presentation with explicit Job metadata and direct scalar responses. |
| Help projection | `HelpProjection.projectModel` (`HelpProjection.scala:21-55`) | Produces HelpModel selectors, component children, and descriptive details. It is a documentation/projection consumer and currently does not consume execution metadata or infer a Job result. | AES-05: align Help projection with the admitted direct-versus-Job contract. |
| Describe projection | `DescribeProjection.project` (`DescribeProjection.scala:16-24`, operation metadata `63-82`) | Produces static subsystem/component/operation records, including command execution policy, effective mode, and policy source. It does not consume a runtime Job ID or terminal outcome. | AES-05: align Describe projection fields with explicit execution mode metadata. |
| Schema projection | `SchemaProjection.project` (`SchemaProjection.scala:16-27`, operation metadata `52-68`) | Produces schema records and operation definitions, including command execution policy/mode/source and parameters. It does not infer a runtime Job from a response. | AES-05: align Schema projection with explicit execution mode metadata. |
| Admin / diagnostics / CallTree | `AdminComponent` registers execution calltree/history/diagnostics operations (`AdminComponent.scala:196-207`, definitions `662-725`); calls return `RecordResponse` (`1427-1497`). The diagnostics record names workflow/event/job authoritative selectors (`1505-1640`). | Admin execution records are direct operation responses. Diagnostics hands operators to Job Control for final Job lineage and async failure disposition; it does not itself turn a direct response into a Job. | AES-05: align Admin, diagnostics, metrics, and CallTree handoffs. |
| Job Control | `JobControlComponent.JobService` (`JobControlComponent.scala:92-120`), `DefaultJobService` status/history/calltree/result/await (`439-520`), and `_job_result_response` (`1891-1903`) | Job Control is the explicit Job consumer: status/history/calltree/task details are Job read models; `get_job_result` returns the Job result/failure and `await_job_result` returns the successful `OperationResponse` or propagates `JobResult.Failure`. | AES-04: keep Job-dependent callers explicit; AES-05: align public Job Control projection/result fields. |

## Authorization and failure distinctions

The current implementation has three different authorization cases:

1. **Subsystem selector authorization/evaluation rejection.** Route-resolved
   `_execute_action_c` calls `_authorize_operation` and then operation-
   evaluation admission before calling `ComponentLogic._execute_action`
   (`Subsystem.scala:1701-1725`). Rejection is recorded as an admission
   failure; no ActionCall is started.
2. **ActionCall authorization rejection in normal `ActionEngine.execute`.**
   `ActionEngine.execute` calls `security_authorize` inside the pre-execution
   `authresult` (`ActionEngine.scala:68-88`, `404-412`). Failure records the
   pre-execution calltree/structured failure and returns a failure; the normal
   path does not commit `ActionEvent.authorizationFailed`. This behavior is
   source-evidenced at those lines but has no dedicated executable
   specification; adding normal-path rejection coverage is an AES-02
   executable coverage obligation. The isolated authorization/observation
   specifications listed below do not cover this normal `execute` route.
3. **Isolated `ActionEngine.executeAuthorized`.** Its denied branch constructs
   and commits `ActionEvent.authorizationFailed` before returning the security
   failure (`ActionEngine.scala:332-354`). The method has no production caller
   in the current source; its behavior is covered only by dedicated
   `ActionEngineAuthorizationFailureCommitSpec` and isolated
   `ActionEngineObservationSpec` cases. It must not be described as normal
   admitted runtime behavior.

## Async failure and input evidence

`JobEngine.submit` validates admission and returns a failed `Consequence`
without a successful Job ID when admission is rejected (`JobEngine.scala:1038-
1049`). After an async Job is accepted, the caller may receive a successful Job
ID before worker execution has a terminal result. A later `TaskFailed` is
settled as `JobResult.Failure` (`JobEngine.scala:1597-1617`), and await/primary-
result consumers surface that failure synchronously. Event reception with an
action scope can return `Unit` after staging the submission post-commit rather
than returning a Job ID (`EventReception.scala:1886-1894`).

`JobSubmitOption.input` is only the optional carrier. Input retention is read
from the nested `JobInput.retentionPolicy`, including the component's
`cncf.job.input.retention` request parameter (`ComponentLogic.scala:493-528`),
and cleanup is evaluated by `JobInput.shouldCleanup` (`JobEngine.scala:461-
478`).

## Current implementation evidence and follow-up

The final unclassified fallback at `ComponentLogic.scala:174` synchronously
runs `taskdecorator(task).run(scopedctx).result`. Therefore the route-resolved
decorator and the no-route identity decorator both retain their existing Action
child scope, ActionEngine/ActionCall, UnitOfWork, authorization, observability,
and structured-failure boundaries while returning the exact bound
`OperationResponse` without creating an implicit Job. The no-route branch still
intentionally bypasses Subsystem selector authorization and operation-evaluation
capture, while `ComponentLogic.createActionCall` binds its ordinary ActionCall
execution invocation selector from component and request fields. AES-03 closes
this runtime repair and activates its executable matrix; AES-04 and AES-05
follow-up ownership remains unchanged below.

## Evidence references

* Normative execution lifecycle: `docs/design/execution-model.md` and
  `docs/design/component-internal-execution-model.md`.
* Existing operation-kind and trace-job executable specifications:
  `src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala`.
* Existing command policy executable specifications:
  `src/test/scala/org/goldenport/cncf/component/ComponentLogicCommandScriptExecutionModeSpec.scala`
  and `src/test/scala/org/goldenport/cncf/job/JobCommandSyncAndTaskFirstSpec.scala`.
* Existing Job lifecycle/read-model specifications:
  `src/test/scala/org/goldenport/cncf/job/JobPrimaryResultSpec.scala` and
  `JobQueryReadModelSpec.scala`.
* Existing event continuation and operation-evaluation specifications:
  `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala`
  and `OperationEvaluationJobCaptureSpec.scala`.
* Existing isolated authorization/event-commit specifications:
  `src/test/scala/org/goldenport/cncf/action/ActionEngineAuthorizationFailureCommitSpec.scala`
  and the isolated `executeAuthorized` cases in
  `ActionEngineObservationSpec.scala`. These do not provide executable coverage
  for normal `ActionEngine.execute` -> `ActionCall.authorize` rejection; that
  gap is recorded above as an AES-02 obligation.
