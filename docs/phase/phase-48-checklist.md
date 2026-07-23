# Phase 48 Checklist - Operation Evaluation and Corpus/Experiment Capture

status=closed
phase=[Phase 48 - Operation Evaluation and Corpus/Experiment Capture](phase-48.md)

This checklist is the authoritative Phase 48 state ledger. Only one stage may
be `IN_PROGRESS` at a time.

## OE-01: Normative Contract

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Closed by the normative design/spec promotion recorded below.

- [x] Define automatic framework capture separately from application-supplied
  supplemental facts.
- [x] Define automatic facts for every resolved and authorized operation
  attempt separately from explicit corpus membership and experiment
  assignment.
- [x] Define Corpus and Experiment as separate standard SPI contracts.
- [x] Define disconnected components as no-op/discard behavior.
- [x] Define execution correlation, authorization order, terminal capture, and
  failure isolation.
- [x] Define delivery-context reentrancy suppression for sink-induced
  operations, including explicit cross-sink policy.
- [x] Define finite delivery timeout, concurrency, queue, byte, saturation,
  overflow, and drop/limitation behavior.
- [x] Define default payload exclusion and confidentiality policy.
- [x] Promote the accepted contract into `docs/design` and `docs/spec`.

Evidence:
- `docs/design/operation-evaluation-capture.md`
- `docs/spec/operation-evaluation-capture.md`

## OE-02: Capture Model

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark DONE only when the typed model and executable model specs
  satisfy every OE-02 checklist item.

- [x] Define bounded operation/execution identity and correlation values.
- [x] Define automatic start and terminal facts.
- [x] Define corpus candidate, experiment observation, supplemental fact,
  limitation, and delivery-result values.
- [x] Distinguish framework, application, and provider fact sources.

Evidence:
- `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationModel.scala`
- `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationModelSpec.scala`
- `src/test/scala/org/goldenport/cncf/projection/GeneratedHelpProjectionSpec.scala`
- Kaleidox `OperationEvaluationModelSpec`
- Cozy `ModelerServiceOperationSpec` evaluation generation scenario
- Cozy `cozy/operation-evaluation-contract` scripted Scala 3 compilation

## OE-03: Standard SPI

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-02 closes; mark DONE only when
  every OE-03 checklist item is complete.

- [x] Define provider-neutral Corpus sink SPI.
- [x] Define provider-neutral Experiment sink SPI.
- [x] Provide disabled/no-op and deterministic fake implementations.
- [x] Install traced services at the calling component socket without exposing
  provider implementation types.

Evidence:
- `src/main/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSink.scala`
- `src/test/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSinkSpec.scala`
- Focused OE-03 executable specifications: 35 tests passed.
- CNCF `Test/compile` and diff validation passed.

## OE-04: Runtime Context and Correlation

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-03 closes; mark DONE only when
  every OE-04 checklist item is complete.

- [x] Inherit sink capabilities through `ScopeContext`.
- [x] Carry immutable operation-scoped correlation through `ExecutionContext`.
- [x] Carry logical active-sink delivery context through context rebinding,
  scheduler handoff, Job/Task submission, retry, and sink-induced async calls.
- [x] Preserve correlation across Job execution, retry, resume, and nested
  operation boundaries.
- [x] Prevent unrelated component instances from sharing supplemental state.

Evidence:
- `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationContext.scala`
- `src/test/scala/org/goldenport/cncf/context/OperationEvaluationContextSpec.scala`
- `src/test/scala/org/goldenport/cncf/job/OperationEvaluationJobContextSpec.scala`
- Focused OE-04 executable specifications: 9 tests passed.
- CNCF `Test/compile` and diff validation passed.

## OE-05: Automatic Operation Chokepoint

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-04 closes; mark DONE only when
  every OE-05 checklist item is complete.

- [x] Capture only after operation resolution and authorization.
- [x] Capture the bounded framework fact without requiring operation-local
  evaluation declaration metadata.
- [x] Record framework-owned structural start facts before business execution.
- [x] Record one terminal success/failure/timeout/cancellation fact after
  canonical response bindings are known.
- [x] Prove no-op sinks perform no provider invocation.
- [x] Prove sink failure cannot change the business outcome.
- [x] Suppress delivery back to the same sink when sink handling invokes a CNCF
  operation.
- [x] Bound stalled and saturated sinks without indefinite operation delay.

Evidence:
- `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationActionTask.scala`
- `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationDeliveryRuntime.scala`
- `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala`
- `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationDeliveryRuntimeSpec.scala`
- Focused OE-05 implementation specs: 31 tests passed.
- Internal dispatch and Job settlement regression set: 40 tests passed across
  operation capture, delivery bounds, runtime context, Job correlation,
  Service validation, Rule admission, and Workflow execution, with two
  pre-existing pending Service property placeholders.
- Event reception regression set: 36 tests passed, including same-transaction,
  same-Job, asynchronous continuation, rollback, and controlled scheduling.
- Review fixes cover mandatory resolved-route identity, internal
  ComponentLogic/Rule/Workflow/JCL task preparation, settlement-safe terminal
  classification, capture-bookkeeping isolation, decorated Job metadata,
  compensation terminal completion, per-Task cancellation classification,
  post-authorization request-construction failure, Event continuation capture,
  pre-Task query-only and Job admission rejection, and interruption-safe
  auxiliary observation. Prepared cross-component tasks now bind capture to the
  target component Action scope, preserving inherited security and Job context
  while preventing delivery to the caller component sink.
- The full-suite regression fix preserves already resolved ad-hoc
  `ComponentLogic` Actions outside the operation-route boundary while keeping
  resolvable internal component API calls on the authorization and capture
  chokepoint. The focused command/query/capture regression set passes 48 tests.
- Prepared tasks retain their target component scope across detached
  compensation execution, so ComponentLogic and JCL compensation facts cannot
  leak into the primary caller sink. The expanded OE-05, JCL, command,
  operation-semantics, Service, Rule, Workflow, and Event regression set passes
  103 tests with two pre-existing Service placeholders pending.
- Fresh post-fix re-review found no actionable behavior, naming,
  executable-specification, or documentation finding. The exact staged
  snapshot passed the full CNCF suite: 331 suites completed, 2,307 tests
  succeeded, and no test failed.

## OE-06: Supplemental Internal DSL

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-05 closes; mark DONE only when
  every OE-06 checklist item is complete.

- [x] Add protected ActionCall/Behavior DSL helpers for corpus candidates.
- [x] Add protected ActionCall/Behavior DSL helpers for experiment
  observations and bounded labels/measurements.
- [x] Route supplemental capture through UnitOfWork and the installed standard
  SPI rather than direct component calls.
- [x] Stage supplemental intents in UnitOfWork, retain them in a framework
  post-commit buffer, and release them only after canonical operation success.
- [x] Discard supplemental intents on abort, rollback, commit failure,
  cancellation, timeout, and later framework-binding failure.
- [x] Enforce byte/count/confidentiality limits and structured failures.

Evidence:
- `BehaviorOperationEvaluationPart` provides typed protected helpers for
  labels, measurements, corpus candidates, and experiment observations.
- `StageOperationEvaluationSupplemental` routes immutable intents through the
  UnitOfWork interpreter into an attempt-partitioned bounded buffer.
- Commit marks only the current attempt; canonical successful terminal
  delivery releases that attempt, while failure, timeout, cancellation,
  commit failure, and framework-binding failure discard it.
- Executable specifications cover Functional and Procedure ActionCalls,
  multiple Tasks in one Job, retry, nested operations, interruption/fatal
  propagation, cleanup failure, confidentiality, count/byte limits, and
  generated attempt isolation.
- The expanded focused regression set passes 55 tests across five suites.
- Fresh post-fix review found no actionable behavior, naming,
  executable-specification, or documentation finding.
- The exact staged snapshot passed the full CNCF suite: 332 suites completed,
  2,324 tests succeeded, and no test failed.

## OE-07: Diagnostics and Observability

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-06 closes; mark DONE only when
  every OE-07 checklist item is complete.

- [x] Project delivery status and limitations through execution metadata.
- [x] Record bounded CallTree and metrics facts without payload duplication.
- [x] Keep corpus/experiment provider-owned assignment state independent of
  telemetry sampling, retention loss, and observer failure.
- [x] Preserve canonical `Consequence` diagnostics as the operation outcome.

Implementation Evidence:
- `OperationEvaluationExecutionReport` retains at most 32 payload-safe
  delivery diagnostics in `RuntimeContext.ExecutionMetadata` and counts
  omitted diagnostics.
- `OperationEvaluationDeliveryRuntime` attempts one completed
  `operation-evaluation:delivery` CallTree mark and one
  `operation-evaluation.delivery` runtime metric for each attempted installed
  sink delivery.
- Provider-worker CallTree collection is detached from the caller stack, and
  runtime metric labels retain all bounded limitation kinds and diagnostic
  keys.
- Delivery diagnostics omit fact identity, execution correlation, provider
  instance, evidence payload, `Conclusion.display`, and diagnostic facets.
- Executable specifications cover nested report aggregation, bounded typed
  fact/diagnostic values, timeout-safe CallTree behavior, complete limitation
  metrics, and provider-owned assignment independence from observer loss.
- The OE-07 focused regression set passes 69 tests across eight suites, and
  `Test/compile` plus `git diff --check` pass.
- Read-only review, review-fix, and clean re-review passed.

## OE-08: Executable Evidence

Stage Status:
- Current status: DONE
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-07 closes; mark DONE only when
  every OE-08 checklist item is complete.

- [x] Resolve declared optional/required evaluation admission after
  authorization and before canonical Request construction, or before
  `ActionCall` business execution when a framework path already owns an
  assignment-independent Action.
- [x] Expose immutable admitted assignment through protected component
  accessors without exposing the resolver/provider.
- [x] Preserve admitted assignment through context rebinding, Job retry, and
  resume while preventing implicit nested-operation inheritance.
- [x] Cover automatic capture with connected and disconnected sinks.
- [x] Cover supplemental DSL capture and source attribution.
- [x] Cover success, failure, timeout, cancellation, retry, resume, nested
  calls, and exactly-once terminal emission.
- [x] Cover payload safety, sink failure isolation, and component-instance
  isolation.
- [x] Cover same-sink recursion suppression, explicit cross-sink behavior,
  causal context propagation, timeout, queue saturation, overflow, and bounded
  drop diagnostics.
- [x] Cover supplemental commit release, operation abort, and post-commit
  framework-binding failure without leaking provider calls.
- [x] Verify undeclared operations emit only automatic facts and disabled
  sinks perform no provider invocation.

Implementation Progress:
- `OperationEvaluationResolver` is a provider-neutral runtime capability
  inherited through `ScopeContext`; declaration-free operations do not invoke
  it and its disabled implementation returns bounded unavailability.
- Optional unavailability follows the control path and records a bounded
  payload-safe admission diagnostic. Required unavailability is recorded as a
  rejected admission and invalid/incomplete admission is recorded as a failed
  admission; both fail before business execution while retaining the
  authorized automatic attempt.
- Admitted Corpus/Experiment correlations and logical assignment are immutable
  operation context. Protected application access exposes only variant and
  execution-plan references.
- Resolver-returned failures and thrown resolver exceptions retain their
  structured `Conclusion`, while execution reports retain only bounded
  structural diagnostic keys rather than application-owned diagnostic
  payloads.
- Empty, undeclared, incomplete, repeated, and post-attempt admissions fail
  without replacing immutable assignment or attempt-correlation state.
- Canonical Request execution admits before operation request construction.
  Direct/prepared Action execution admits after authorization and before
  `ActionCall` business behavior because the Action is already constructed.
- `OperationEvaluationAdmissionSpec`, `OperationEvaluationContextSpec`,
  `OperationEvaluationJobContextSpec`, and
  `OperationEvaluationAutomaticCaptureSpec`, together with the complete
  capture/delivery/supplemental/SPI regression set, pass 83 focused tests
  across nine suites.
- Review-fix corrected the public direct Action path to preserve the
  component-owned `ScopeContext` resolver/sink capabilities instead of
  replacing them with a generic ingress context.
- The focused suites and `Test/compile` pass after review-fix. A fresh
  read-only re-review found no actionable behavioral, naming, or executable-
  specification findings; OE-08A closed in release commit `c7bec615`.
- OE-08B adds immutable subsystem-owned directional cross-sink routes with
  default deny and a finite active-sink depth. Same-sink recursion remains
  forbidden regardless of route configuration.
- Automatic delivery and caller-side standard SPI wrappers use the same policy
  decision. Context rebinding and Job retry/resume retain the policy and active
  sink ancestry.
- OE-08B focused validation passes 49 tests across context, Job, automatic
  capture, bounded delivery, and standard SPI suites; `Test/compile` passes.
- Review-fix organized the two modified large specifications into semantic
  subsections. Fresh read-only re-review found no actionable behavioral,
  naming, executable-specification, or documentation finding.
- Release validation passed the full CNCF suite: 333 suites completed, 2,354
  tests succeeded, no test failed, 2 were canceled, 1 was ignored, and 59
  remained pending.
- OE-08C closes the consolidated required-scenario matrix. Successful
  query-only dispatch and generic `SpiInvoker` dispatch now prove the same
  automatic start/terminal semantics as ordinary operation execution.
- Retry evidence proves one start/terminal pair per attempt, distinct attempt
  and fact identities, stable logical execution/Job correlation, and
  supplemental release from only the successful attempt.
- A persistent delayed-retry Job now crosses the canonical automatic-capture
  wrapper before and after `JobEngine` rehydration, proving one distinct
  start/terminal pair for each resumed attempt.
- Repeated provider delivery of the same immutable fact returns the same fact
  identity, providing the stable deduplication key required for idempotent
  at-least-once handling.
- Canonical supplemental success is observed on the already committed
  UnitOfWork after response binding and terminal capture; no replacement
  transaction is opened for provider release.
- The consolidated OE-08C matrix passes 91 tests across seven suites; CNCF
  `Test/compile` and diff validation pass.
- The isolated OE-08C staged snapshot passes the full CNCF suite: 333 suites
  completed, 2,353 tests succeeded, no test failed, 2 were canceled, 1 was
  ignored, and 59 remained pending.

OE-08 Required Scenario Evidence Matrix:

| Required scenario | Executable evidence |
| --- | --- |
| No sinks connected | `OperationEvaluationAutomaticCaptureSpec`: disconnected optional sink preserves the result; `OperationEvaluationSinkSpec`: disabled sockets invoke no provider. |
| Sinks connected, no declaration | `OperationEvaluationAdmissionSpec` E1: resolver remains unused, automatic facts are emitted, and membership/assignment remain absent. |
| Authorization denied | `OperationEvaluationAdmissionSpec` E6 and `OperationEvaluationAutomaticCaptureSpec`: no resolver or sink invocation. |
| Optional/required admission | `OperationEvaluationAdmissionSpec` E2, E3, and E14 cover bounded optional control and required pre-business rejection. |
| Success/failure/timeout/cancellation | `OperationEvaluationAutomaticCaptureSpec` preserves each canonical outcome and one terminal per attempt. |
| Sink failure/timeout and saturation | `OperationEvaluationAutomaticCaptureSpec` and `OperationEvaluationDeliveryRuntimeSpec` preserve business outcome with bounded diagnostics. |
| Retry and Job resume | `OperationEvaluationJobContextSpec` runs canonical automatic capture across `JobEngine` rehydration; `OperationEvaluationSupplementalDslSpec` preserves logical correlation with distinct attempt, Task, and fact identities. |
| Nested operation | `OperationEvaluationAutomaticCaptureSpec` and `OperationEvaluationSupplementalDslSpec` preserve parent correlation without implicit membership inheritance. |
| Same-sink and cross-sink delivery | `OperationEvaluationAutomaticCaptureSpec`, `OperationEvaluationContextSpec`, and `OperationEvaluationSinkSpec` prove suppression, directional allowlisting, and finite depth. |
| Supplemental DSL lifecycle | `OperationEvaluationSupplementalDslSpec` proves application source, UnitOfWork staging, post-terminal committed release, abort/binding-failure discard, and attempt isolation. |
| Confidential payload | `OperationEvaluationModelSpec`, `OperationEvaluationDeliveryRuntimeSpec`, `OperationEvaluationAdmissionSpec`, and `OperationEvaluationSinkSpec` exclude payloads from facts, reports, traces, metrics, and diagnostics. |
| Query-only and generic SPI routes | `OperationEvaluationAutomaticCaptureSpec` and `SpiInvokerSpec` prove canonical capture parity and authorization ordering. |

OE-08A Modified Scala File Compliance Ledger:

| File | Naming | Executable specification | Validation | Commit |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/action/ActionCallOperationEvaluationPart.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/context/RuntimeContext.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/context/ScopeContext.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAdmission.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationContext.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationModel.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` | whole-file pass | not a spec | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/test/scala/org/goldenport/cncf/context/OperationEvaluationContextSpec.scala` | whole-file pass | whole-file pass | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/test/scala/org/goldenport/cncf/job/OperationEvaluationJobContextSpec.scala` | whole-file pass | whole-file pass | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAdmissionSpec.scala` | whole-file pass | whole-file pass | 83 focused tests; `Test/compile` | OE-08A release commit |
| `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationModelSpec.scala` | whole-file pass | whole-file pass | 83 focused tests; `Test/compile` | OE-08A release commit |

OE-08B Modified Scala File Compliance Ledger:

| File | Naming | Executable specification | Validation | Commit |
| --- | --- | --- | --- | --- |
| `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/context/ScopeContext.scala` | whole-file pass; inherited `observability_Context` is a required parent API override | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationContext.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationDeliveryRuntime.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationModel.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSink.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala` | whole-file pass | not a spec | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/test/scala/org/goldenport/cncf/context/OperationEvaluationContextSpec.scala` | whole-file pass | whole-file pass after semantic subsection review-fix | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/test/scala/org/goldenport/cncf/job/OperationEvaluationJobContextSpec.scala` | whole-file pass | whole-file pass | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala` | whole-file pass | whole-file pass | 49 focused tests; `Test/compile` | OE-08B release commit |
| `src/test/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSinkSpec.scala` | whole-file pass | whole-file pass after semantic subsection review-fix | 49 focused tests; `Test/compile` | OE-08B release commit |

OE-08C Modified Scala File Compliance Ledger:

| File | Naming | Executable specification | Validation | Commit |
| --- | --- | --- | --- | --- |
| `src/test/scala/org/goldenport/cncf/job/OperationEvaluationJobContextSpec.scala` | whole-file pass | whole-file pass | 91 focused tests; full suite; `Test/compile` | OE-08C release commit |
| `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationAutomaticCaptureSpec.scala` | whole-file pass | whole-file pass | 91 focused tests; full suite; `Test/compile` | OE-08C release commit |
| `src/test/scala/org/goldenport/cncf/operation/evaluation/OperationEvaluationSupplementalDslSpec.scala` | whole-file pass | whole-file pass | 91 focused tests; full suite; `Test/compile` | OE-08C release commit |
| `src/test/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationSinkSpec.scala` | whole-file pass | whole-file pass | 91 focused tests; full suite; `Test/compile` | OE-08C release commit |
| `src/test/scala/org/goldenport/cncf/spi/SpiInvokerSpec.scala` | whole-file pass | whole-file pass | 91 focused tests; full suite; `Test/compile` | OE-08C release commit |

## OE-09: Downstream Handoff

Stage Status:
- Current status: DONE
- Owner: CNCF and downstream Textus integration maintainers
- Update rule: Mark IN_PROGRESS only after OE-08 closes; mark DONE only when
  every OE-09 checklist item is complete.

- [x] Validate one offline Textus Corpus adapter.
- [x] Validate one offline Textus Experiment adapter.
- [x] Prove CNCF has no downstream implementation dependency.
- [x] Record separately owned production-provider follow-up work.

Closure evidence:

- `textus-corpus` owns a bounded, ordered, idempotent, non-persistent
  `OfflineCorpusEvaluationSinkAdapter`; its actual primary CAR component
  exposes the standard SPI provider.
- `textus-experiment` owns the corresponding
  `OfflineExperimentEvaluationSinkAdapter`; its actual primary CAR component
  exposes the standard SPI provider.
- CNCF's non-default-discovered
  `OperationEvaluationDownstreamHandoffSpec` loads both compiled downstream
  class directories through an isolated runtime class loader. It proves the
  actual Textus-owned adapters preserve one execution/attempt across corpus
  revision/case and experiment/arm/run correlation without adding either
  downstream implementation to CNCF's compile or production dependencies.
- Downstream README contracts state that persistent candidate promotion,
  observation persistence, retention, and production provider activation are
  separately owned follow-up work.
- CNCF remains provider-neutral; the dependency check must show no
  `org.simplemodeling.textus.corpus` or
  `org.simplemodeling.textus.experiment` implementation import in CNCF
  production sources before OE-09 closes.
- REVIEW_FIX validation on 2026-07-24 covers four Corpus adapter executable
  specifications, four Experiment adapter executable specifications,
  resolver-level installation through each actual primary CAR component,
  rejection of explicit non-offline selection modes, `Test/compile` in both
  downstream repositories, and one explicit cross-repository acceptance
  specification. No cross-repository Scala test dependency or local Corpus
  publication is required.
- The explicit downstream acceptance command is:

  ```sh
  sbt --batch \
    -Dtextus.corpus.classes=/path/to/textus-corpus/target/scala-3.3.8/classes \
    -Dtextus.experiment.classes=/path/to/textus-experiment/target/scala-3.3.8/classes \
    "Test / runMain org.scalatest.tools.Runner -s org.goldenport.cncf.spi.evaluation.OperationEvaluationDownstreamHandoffSpec -o"
  ```

OE-09 Modified Scala File Compliance Ledger:

| Repository / file | Naming | Executable specification | Validation | Commit |
| --- | --- | --- | --- | --- |
| `textus-corpus/src/main/scala/org/simplemodeling/textus/corpus/evaluation/OfflineCorpusEvaluationSinkAdapter.scala` | whole-file pass | not a spec | focused adapter spec; `Test/compile` | OE-09 release commit |
| `textus-corpus/src/test/scala/org/simplemodeling/textus/corpus/evaluation/OfflineCorpusEvaluationSinkAdapterSpec.scala` | whole-file pass | Given/When/Then plus generated-capacity property pass | 4 tests; `Test/compile` | OE-09 release commit |
| `textus-corpus/src/main/scala/org/simplemodeling/textus/corpus/impl/ComponentFactory.scala` | whole-file pass | not a spec | actual primary component provider resolution | OE-09 release commit |
| `textus-experiment/src/main/scala/org/simplemodeling/textus/experiment/evaluation/OfflineExperimentEvaluationSinkAdapter.scala` | whole-file pass | not a spec | focused adapter spec; `Test/compile` | OE-09 release commit |
| `textus-experiment/src/test/scala/org/simplemodeling/textus/experiment/evaluation/OfflineExperimentEvaluationSinkAdapterSpec.scala` | whole-file pass | Given/When/Then plus generated-capacity property pass | 4 tests; `Test/compile` | OE-09 release commit |
| `textus-experiment/src/main/scala/org/simplemodeling/textus/experiment/impl/ComponentFactory.scala` | whole-file pass | not a spec | actual primary component provider resolution | OE-09 release commit |
| `src/test/scala/org/goldenport/cncf/spi/evaluation/OperationEvaluationDownstreamHandoffSpec.scala` | whole-file pass | Given/When/Then cross-repository acceptance | 1 explicit acceptance test | OE-09 release commit |

## OE-10: Verification and Closure

Stage Status:
- Current status: DONE
- Owner: CNCF phase maintainers
- Update rule: Mark IN_PROGRESS only after OE-09 closes; mark DONE only when
  every OE-10 checklist item and Phase 48 closure criterion is complete.

- [x] Run focused operation, context, SPI, Job, and observability specs.
- [x] Run `Test/compile` and the full CNCF test suite.
- [x] Complete review and review-fix cycles with no actionable finding.
- [x] Update strategy, design, specification, developer guidance, and phase
  evidence.
- [x] Close Phase 48 only after implementation and downstream acceptance pass.

Closure evidence:

- Focused operation-evaluation, context, SPI, Job, observability, and
  downstream adapter executable specifications passed throughout OE-02 through
  OE-09; the OE-08 compliance ledger records the focused 91-test evidence.
- `sbt --batch test` passed on 2026-07-24 in CNCF with 2,358 successful tests,
  zero failures, two canceled tests, one ignored test, and 59 pending tests.
- `sbt --batch test` passed on 2026-07-24 in `textus-corpus` with seven
  successful tests and in `textus-experiment` with ten successful tests.
- The explicitly invoked, non-default-discovered
  `OperationEvaluationDownstreamHandoffSpec` passed with both downstream
  compiled class directories and actual Textus-owned providers.
- The final OE-09 review-fix and fresh read-only re-review completed with no
  actionable findings before the three validated release commits:
  `68389bf` (`textus-corpus`), `6c4f827` (`textus-experiment`), and
  `47901c1c` (CNCF).
- Normative and developer-facing contracts are recorded in
  `docs/design/operation-evaluation-capture.md`,
  `docs/spec/operation-evaluation-capture.md`, and
  `docs/notes/operation-evaluation-contract-proposed-specification.md`.
- Phase 48 closes with persistent provider operation, candidate promotion,
  experiment acceptance/reporting, and production provider activation still
  owned by downstream Textus components.
