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
- Current status: NEXT
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-03 closes; mark DONE only when
  every OE-04 checklist item is complete.

- [ ] Inherit sink capabilities through `ScopeContext`.
- [ ] Carry immutable operation-scoped correlation through `ExecutionContext`.
- [ ] Carry logical active-sink delivery context through context rebinding,
  scheduler handoff, Job/Task submission, retry, and sink-induced async calls.
- [ ] Preserve correlation across Job execution, retry, resume, and nested
  operation boundaries.
- [ ] Prevent unrelated component instances from sharing supplemental state.

## OE-05: Automatic Operation Chokepoint

Stage Status:
- Current status: OPEN
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-04 closes; mark DONE only when
  every OE-05 checklist item is complete.

- [ ] Capture only after operation resolution and authorization.
- [ ] Capture the bounded framework fact without requiring operation-local
  evaluation declaration metadata.
- [ ] Record framework-owned structural start facts before business execution.
- [ ] Record one terminal success/failure/timeout/cancellation fact after
  canonical response bindings are known.
- [ ] Prove no-op sinks perform no provider invocation.
- [ ] Prove sink failure cannot change the business outcome.
- [ ] Suppress delivery back to the same sink when sink handling invokes a CNCF
  operation.
- [ ] Bound stalled and saturated sinks without indefinite operation delay.

## OE-06: Supplemental Internal DSL

Stage Status:
- Current status: OPEN
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-05 closes; mark DONE only when
  every OE-06 checklist item is complete.

- [ ] Add protected ActionCall/Behavior DSL helpers for corpus candidates.
- [ ] Add protected ActionCall/Behavior DSL helpers for experiment
  observations and bounded labels/measurements.
- [ ] Route supplemental capture through UnitOfWork and the installed standard
  SPI rather than direct component calls.
- [ ] Stage supplemental intents in UnitOfWork, retain them in a framework
  post-commit buffer, and release them only after canonical operation success.
- [ ] Discard supplemental intents on abort, rollback, commit failure,
  cancellation, timeout, and later framework-binding failure.
- [ ] Enforce byte/count/confidentiality limits and structured failures.

## OE-07: Diagnostics and Observability

Stage Status:
- Current status: OPEN
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-06 closes; mark DONE only when
  every OE-07 checklist item is complete.

- [ ] Project delivery status and limitations through execution metadata.
- [ ] Record bounded CallTree and metrics facts without payload duplication.
- [ ] Keep corpus/experiment membership independent of telemetry sampling and
  retention.
- [ ] Preserve canonical `Consequence` diagnostics as the operation outcome.

## OE-08: Executable Evidence

Stage Status:
- Current status: OPEN
- Owner: CNCF operation runtime maintainers
- Update rule: Mark IN_PROGRESS only after OE-07 closes; mark DONE only when
  every OE-08 checklist item is complete.

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
