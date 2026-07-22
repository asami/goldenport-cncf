# Phase 48 Checklist - Operation Evaluation and Corpus/Experiment Capture

status=active
phase=[Phase 48 - Operation Evaluation and Corpus/Experiment Capture](phase-48.md)

This checklist is the authoritative Phase 48 state ledger. Only one item may be
active at a time.

## OE-01: Normative Contract

Stage Status:
- Current status: ACTIVE
- Owner: CNCF operation runtime maintainers

- [ ] Define automatic framework capture separately from application-supplied
  supplemental facts.
- [ ] Define automatic facts for every admitted operation separately from
  explicit corpus membership and experiment assignment.
- [ ] Define Corpus and Experiment as separate standard SPI contracts.
- [ ] Define disconnected components as no-op/discard behavior.
- [ ] Define execution correlation, authorization order, terminal capture, and
  failure isolation.
- [ ] Define delivery-context reentrancy suppression for sink-induced
  operations, including explicit cross-sink policy.
- [ ] Define finite delivery timeout, concurrency, queue, byte, saturation,
  overflow, and drop/limitation behavior.
- [ ] Define default payload exclusion and confidentiality policy.
- [ ] Promote the accepted contract into `docs/design` and `docs/spec`.

## OE-02: Capture Model

Stage Status:
- Current status: PENDING

- [ ] Define bounded operation/execution identity and correlation values.
- [ ] Define automatic start and terminal facts.
- [ ] Define corpus candidate, experiment observation, supplemental fact,
  limitation, and delivery-result values.
- [ ] Distinguish framework, application, and provider fact sources.

## OE-03: Standard SPI

Stage Status:
- Current status: PENDING

- [ ] Define provider-neutral Corpus sink SPI.
- [ ] Define provider-neutral Experiment sink SPI.
- [ ] Provide disabled/no-op and deterministic fake implementations.
- [ ] Install traced services at the calling component socket without exposing
  provider implementation types.

## OE-04: Runtime Context and Correlation

Stage Status:
- Current status: PENDING

- [ ] Inherit sink capabilities through `ScopeContext`.
- [ ] Carry immutable operation-scoped correlation through `ExecutionContext`.
- [ ] Preserve correlation across Job execution, retry, resume, and nested
  operation boundaries.
- [ ] Prevent unrelated component instances from sharing supplemental state.

## OE-05: Automatic Operation Chokepoint

Stage Status:
- Current status: PENDING

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
- Current status: PENDING

- [ ] Add protected ActionCall/Behavior DSL helpers for corpus candidates.
- [ ] Add protected ActionCall/Behavior DSL helpers for experiment
  observations and bounded labels/measurements.
- [ ] Route supplemental capture through UnitOfWork and the installed standard
  SPI rather than direct component calls.
- [ ] Enforce byte/count/confidentiality limits and structured failures.

## OE-07: Diagnostics and Observability

Stage Status:
- Current status: PENDING

- [ ] Project delivery status and limitations through execution metadata.
- [ ] Record bounded CallTree and metrics facts without payload duplication.
- [ ] Keep corpus/experiment membership independent of telemetry sampling and
  retention.
- [ ] Preserve canonical `Consequence` diagnostics as the operation outcome.

## OE-08: Executable Evidence

Stage Status:
- Current status: PENDING

- [ ] Cover automatic capture with connected and disconnected sinks.
- [ ] Cover supplemental DSL capture and source attribution.
- [ ] Cover success, failure, timeout, cancellation, retry, resume, nested
  calls, and exactly-once terminal emission.
- [ ] Cover payload safety, sink failure isolation, and component-instance
  isolation.
- [ ] Cover same-sink recursion suppression, explicit cross-sink behavior,
  timeout, queue saturation, overflow, and bounded drop diagnostics.
- [ ] Verify undeclared operations emit only automatic facts and disabled
  sinks perform no provider invocation.

## OE-09: Downstream Handoff

Stage Status:
- Current status: PENDING

- [ ] Validate one offline Textus Corpus adapter.
- [ ] Validate one offline Textus Experiment adapter.
- [ ] Prove CNCF has no downstream implementation dependency.
- [ ] Record separately owned production-provider follow-up work.

## OE-10: Verification and Closure

Stage Status:
- Current status: PENDING

- [ ] Run focused operation, context, SPI, Job, and observability specs.
- [ ] Run `Test/compile` and the full CNCF test suite.
- [ ] Complete review and review-fix cycles with no actionable finding.
- [ ] Update strategy, design, specification, developer guidance, and phase
  evidence.
- [ ] Close Phase 48 only after implementation and downstream acceptance pass.
