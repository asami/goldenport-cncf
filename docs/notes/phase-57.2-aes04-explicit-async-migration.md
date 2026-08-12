# Phase 57.2 AES-04 Explicit Async Migration

status = active Slice AES-04B / non-normative migration evidence; parent validation and review pending
authority = [Phase 57.2](../phase/phase-57.2.md), [Phase 57.2 Checklist](../phase/phase-57.2-checklist.md)
normative_contract = [Action execution semantics](../spec/action-execution-semantics.md)
inventory_source = [Phase 57.1 AES-01 Action Execution Inventory](phase-57.1-aes01-action-execution-inventory.md)

This note classifies every Job-dependent consumer recorded by the Phase 57.1
inventory. It does not close implementation work. The normative target is the
timeless action-execution specification, especially R6, R7, R10, R11, R12 and
E5, E6, E8.

## Consumer classification

| Phase 57.1 consumer | Classification | Evidence and handoff |
| --- | --- | --- |
| `ComponentLogic.submitJob`; Rule `Job`; workflow/JCL/Job Control | Already explicit | These callers select Job management directly and use `JobEngine` lifecycle APIs. |
| Query trace-Job route | Already explicit | The debug trace setting is the defined, explicit trace exception; it does not alter plain Action behavior. |
| Managed command Job-ID/await/primary-result routes | Already explicit | Managed command policy selects the Job route and consumers retain their selected result timing. |
| EventReception same-Job synchronous task | Already explicit | The selected same-Job synchronous policy calls the Job task path and preserves the bound context. |
| EventReception same-Job asynchronous enqueue | Implemented in AES-04B | `EventReception._dispatch_event_action` now returns immediate `enqueueTaskInJob(...).map(_ => ())` results and stages action-scoped enqueue through `stagePostCommitC`; focused executable-spec evidence remains pending parent validation and review. |
| EventReception new-Job asynchronous submission without action scope | Already explicit | Immediate `JobEngine.submit(...).map(_ => ())` preserves an admission failure as a `Consequence.Failure`; it does not return a Job ID to dispatch. |
| EventReception new-Job asynchronous submission with action scope | Implemented in AES-04B | The action-scoped submission now stages `submit().map(_ => ())` through `stagePostCommitC`; focused executable-spec evidence remains pending parent validation and review. |
| `JobEngine` status/result/primary-result/await/control/read-model/timeline/task-tree APIs | Already explicit | These are Job lifecycle consumers; accepted Job identity and later terminal result remain distinct. |
| `JobControlComponent` / `DefaultJobService` | Already explicit | Job Control is an explicit Job lifecycle/result consumer. Its public response projection remains AES-05 work. |
| Request and in-process invocation callers | AES-04 runtime repair | Their direct `OperationResponse` contract is unchanged. Any real Job-dependent caller requiring Job work must select explicit intent rather than infer it. |
| Service CLI/HTTP wrappers | AES-05 projection work | Underlying invocation remains explicit; CLI/HTTP representation and metadata alignment are projection work. |
| Operation Tool | AES-05 projection work | It transports the underlying response; public direct-versus-Job representation is projection work. |
| `OperationResponseFormatter` | AES-05 projection work | Existing scalar/Job-ID response-shape inference is a projection concern. |
| HTTP server and loopback Job-ID metadata | AES-05 projection work | Header and metadata handoff alignment belongs to public transport projection. |
| Form result extraction/rendering | AES-05 projection work | Form Job metadata and links are public projection work. |
| Static-form admin and Blob/Association/Tag renderers | AES-05 projection work | These direct-record consumers require projection alignment only. |
| CLI debug Job reference | AES-05 projection work | Debug Job presentation is a projection handoff. |
| Help, Describe, and Schema projections | AES-05 projection work | These static/public projections must describe the explicit contract without changing admission. |
| Admin, diagnostics, metrics, and CallTree | AES-05 projection work | Operator-facing result and lifecycle presentation is projection/observability alignment. |

## Consequence-loss boundary

AES-04B implements the two previously inventoried consequence-loss sites:

1. same-Job asynchronous event enqueue in `EventReception._dispatch_event_action`; and
2. action-scope post-commit new-Job event submission in the same method.

The immediate new-Job submission path maps the returned `Consequence` and is
not a consequence-loss site. `UnitOfWork.stagePostCommitC` preserves staged
callback failures, while `lastCommitTermination` records committed transaction
state independently from the returned post-commit result; `RuntimeContext`
uses that termination to retain committed operation-evaluation supplemental
state. No other Phase 57.1 inventoried Job-dependent consumer currently
discards an enqueue or submission `Consequence` at this boundary. This records
implementation evidence only; parent validation, review, and Step completion
remain pending.

## AES-04A executable matrix

`EventReceptionExplicitAsyncContractSpec` records same-Job admission failure,
staged new-Job post-commit failure, committed termination, and successful
context-preserving paths. `UnitOfWorkPostCommitConsequenceSpec` records
structured post-commit callback handoff, deterministic multi-failure handling,
prepare rejection, and committed-with-post-commit-failure lifecycle evidence.
`OperationEvaluationSupplementalDslSpec` records committed transaction state at
the supplemental terminal provider when a legacy callback surfaces a
post-commit failure. These are focused AES-04B evidence changes pending parent
validation and review; this note does not claim Step completion or test pass.
