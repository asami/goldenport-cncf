# CompositeQuery v2 Contract

Status: implemented-pending-focused-validation

CompositeQuery v2 composes a declared set of Query requests into one ordered,
bounded aggregate. It is an additive API; `CompositeQueryRequest`,
`NamedQuery`, `CompositeQueryPolicy`, `CompositeQueryResponse`, and the v1
`CompositeQueryEngine.execute` caller contract remain unchanged.

`CompositeQueryEngine.executeV2Blocking(request, targets, cancellation)` is
the compatible v2 facade. It delegates to an explicitly supplied, owning CNCF
`JobEngine`; composition creates neither an executor nor a scheduler and never
runs from an active Job worker.

## Rules

| Rule | Contract |
| --- | --- |
| CQ2-R1 | V1 constructors, defaults, unapply arities, execution behavior, and page consumers remain intact. V2 is additive and adapts only usable ordered values to the legacy response. |
| CQ2-R2 | A v2 request declares branches, targets, dependencies, deadlines, typed fallback, bounded policy, aggregate result, and typed diagnostic records. |
| CQ2-R3 | Version is exactly 2. Admission validates branch IDs, empty or duplicate declarations, references, targets, dependency edges and DAG, numeric bounds, supported fallback response, trace-job aliases, and nested active-job callers before submission. |
| CQ2-R4 | Ready DAG branches are selected in declaration order. `RequireValue` needs a succeeded or fallback predecessor; `AfterCompletion` waits only for terminal completion. Collect-all and explicit required fail-fast preserve every declaration in the result. |
| CQ2-R5 | The supplied JobEngine receives ordinary asynchronous `JobTask` submissions. Logical completion and physical query-body exit are tracked separately: a cancelled or timed-out running body retains its width reservation until it exits, and a body whose canonical durable callback is withheld retains its reservation until canonical settlement or logical timeout/cancel plus body exit. Job admission, cancellation, deadlines, and late completion are synchronized on a caller-thread coordinator monitor; no worker blocks awaiting another worker. |
| CQ2-R6 | Each branch retains caller security and correlation context while binding the chosen target component's scope, runtime, resources, UnitOfWork, response cell, and operation-evaluation buffer. Query-only target authorization remains authoritative. |
| CQ2-R7 | Local and registered Subsystem targets use a strict in-process V2 invocation/reply envelope with matching version, branch, and target IDs. Target aliases are immutable, explicit, non-empty, non-duplicate, and cannot be `local`. |
| CQ2-R8 | Branch terminal outcomes are `Succeeded`, `Fallback`, `Failed`, `Skipped`, `Cancelled`, and `TimedOut`. Fallback is a prevalidated constant response, allowed only for explicit `QueryFailed` or selected branch-local `TimedOut`; authorization, protocol, configuration, cancellation, and response violations never use fallback. |
| CQ2-R9 | Request and branch deadlines include queue time. Cancellation is caller-owned, inherited where available, and may control only captured Job IDs. The entry deadline includes preflight; a branch deadline starts when its coordinator reservation is created before provider submission and is checked again at canonical acceptance. Request expiry seals every incomplete branch without fallback. Terminal outcomes are immutable; already completed results remain. |
| CQ2-R10 | Valid admitted requests return a successful `Consequence` containing a complete ordered aggregate. Aggregate status identifies success, partial optional failure, required failure, cancellation, or timeout. `requiredSucceeded` is distinct from aggregate transport success. |
| CQ2-R11 | Retained values are limited to `Void`, `Json`, `Yaml`, `RecordResponse`, and scalar responses with a canonical UTF-8 size at or below the selected limit. `Http` and `Opaque` responses are rejected. Diagnostics retain only fixed IDs, status/code, timings, flags, and optional Job ID. |
| CQ2-R12 | Ephemeral is the default. Persistent diagnostics use existing Job lifecycle storage with empty input and parameters and `Void` task result; branch request and response payloads, raw conclusions, secrets, and content-bearing calltrees are excluded. A staged value is accepted only through canonical task settlement; an admission failure or withheld canonical callback becomes a safe terminal refusal or deadline, never accepted success. |

## Stable data model

`CompositeQueryV2Policy` defaults to at most four branches admitted in parallel,
30 seconds request duration, 64 branches, 256 KiB per retained response,
collect-all failure treatment, and ephemeral jobs. Validation constrains the
respective maxima to 16, 60 seconds, 64 branches, 256 dependency edges, and
256 KiB. A branch deadline is positive and cannot exceed the request deadline.

The response has exactly one terminal `CompositeQueryV2BranchResult` per
declared branch, in declaration order. A fallback remains visible as a
`Fallback` outcome and preserves the original fixed failure code. The safe
legacy adapter yields outer `Consequence.Failure` when a required result is not
usable; optional failures become bounded legacy diagnostics.

## Executable examples

| Examples | Behavior surface |
| --- | --- |
| E01–E02 | preflight validation, duplicate and cyclic graph rejection |
| E03–E05 | declaration ordering, dependency mode, sequential/parallel semantic equivalence |
| E06–E08 | bounded admission, collect-all, and explicit required fail-fast |
| E09–E12 | fallback eligibility, response bound/type, retained diagnostics, scheduler refusal |
| E13–E20 | cancellation, queued race, deadline, late completion, interruption, nested worker guard, own-ID control |
| E21–E27 | target directory/protocol, target authorization, isolated context cells, trace-job rejection, redaction |
| E28 | legacy adaptation and v1 consumer compatibility |

Remote transport, credential delegation, arbitrary URL routing, Saga behavior,
ordinary response persistence, and presentation composition are outside this
contract.
