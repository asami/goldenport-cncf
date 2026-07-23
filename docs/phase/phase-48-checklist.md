# Phase 48 Checklist - Operation Evaluation and Corpus/Experiment Capture

status=active
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
- Current status: IN_PROGRESS
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
- [ ] Cover automatic capture with connected and disconnected sinks.
- [ ] Cover supplemental DSL capture and source attribution.
- [ ] Cover success, failure, timeout, cancellation, retry, resume, nested
  calls, and exactly-once terminal emission.
- [ ] Cover payload safety, sink failure isolation, and component-instance
  isolation.
- [ ] Cover same-sink recursion suppression, explicit cross-sink behavior,
  causal context propagation, timeout, queue saturation, overflow, and bounded
  drop diagnostics.
- [ ] Cover supplemental commit release, operation abort, and post-commit
  framework-binding failure without leaking provider calls.
- [ ] Verify undeclared operations emit only automatic facts and disabled
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
  specification findings; OE-08A is ready for its validated release commit.
- OE-08 remains open for the consolidated required-scenario matrix, including
  explicit cross-sink behavior and SPI route evidence.

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

## OE-09: Downstream Handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF and downstream Textus integration maintainers
- Update rule: Mark IN_PROGRESS only after OE-08 closes; mark DONE only when
  every OE-09 checklist item is complete.

- [ ] Validate one offline Textus Corpus adapter.
- [ ] Validate one offline Textus Experiment adapter.
- [ ] Prove CNCF has no downstream implementation dependency.
- [ ] Record separately owned production-provider follow-up work.

## OE-10: Verification and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF phase maintainers
- Update rule: Mark IN_PROGRESS only after OE-09 closes; mark DONE only when
  every OE-10 checklist item and Phase 48 closure criterion is complete.

- [ ] Run focused operation, context, SPI, Job, and observability specs.
- [ ] Run `Test/compile` and the full CNCF test suite.
- [ ] Complete review and review-fix cycles with no actionable finding.
- [ ] Update strategy, design, specification, developer guidance, and phase
  evidence.
- [ ] Close Phase 48 only after implementation and downstream acceptance pass.
