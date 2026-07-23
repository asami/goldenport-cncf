# Phase 48 - Operation Evaluation and Corpus/Experiment Capture

status=closed
started_at=2026-07-23
closed_at=2026-07-24
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 48 Checklist](phase-48-checklist.md)

## Purpose

Provide a provider-neutral operation-evaluation boundary that automatically
captures safe execution facts and delivers them to connected Corpus and
Experiment components through standard SPI contracts. Application code may add
bounded domain-specific facts through the internal DSL, but it does not
reimplement framework execution capture.

## Selected Direction

- The common resolved-operation execution path is the automatic capture
  chokepoint.
- Every admitted and authorized operation produces the bounded automatic fact
  when a corresponding sink is installed; operation-local declarations are not
  required for this framework fact.
- CNCF captures structural operation, execution, correlation, terminal outcome,
  timing, Job/Task, and structured diagnostic facts without capturing raw
  business payloads by default.
- Corpus and Experiment integrations are separate provider-neutral standard
  SPI contracts installed at the calling component socket.
- Missing Corpus or Experiment components select no-op sinks. Normal operation
  behavior is unchanged and captured facts are discarded.
- Applications use protected internal DSL operations only for additional
  bounded corpus candidates, experiment measurements, labels, and domain
  context.
- Automatically captured and application-supplied facts share one admitted
  execution correlation but remain distinguishable by source.
- Corpus membership, candidate payload, experiment assignment, arm identity,
  and measurements remain explicit declarations or supplemental facts. An
  automatic execution fact alone does not establish membership.
- Corpus and Experiment components own persistence, candidate promotion,
  revision/run lifecycle, aggregation, and acceptance policy.

## Scope

- Normative design and static specification for automatic operation capture,
  standard SPI delivery, and application-supplied evaluation facts.
- Typed provider-neutral execution correlation, terminal fact, limitation,
  delivery result, corpus candidate, experiment observation, and supplemental
  fact models.
- Disabled/no-op, deterministic fake, and installed Corpus/Experiment SPI
  implementations.
- Bounded sink delivery with finite timeout, concurrency, queue, byte, and
  overflow/drop behavior that cannot indefinitely delay an operation.
- Runtime capability inheritance through `ScopeContext` and immutable
  operation-scoped correlation through `ExecutionContext`.
- Integration at the common resolved-operation path after authorization and
  before/after business execution as appropriate.
- Protected ActionCall/Behavior internal DSL helpers for bounded supplemental
  capture.
- Payload-safe CallTree, metrics, diagnostics, retry/resume identity, and
  failure-isolation behavior.
- One offline downstream handoff using Textus Corpus and Textus Experiment
  adapters without adding those dependencies to CNCF core.

## Boundaries

- CNCF does not own corpus revisions, experiment definitions, arms, runs,
  aggregation, acceptance, or promotion policy.
- CNCF core has no dependency on `textus-corpus`, `textus-experiment`, Textus
  AI, or a telemetry backend.
- Automatic capture does not include raw requests, responses, prompts, model
  output, credentials, provider payloads, or unrestricted identifiers.
- Observability records are not reconstructed later to infer corpus or
  experiment membership.
- Sink failure cannot rewrite a completed business result. Explicit
  pre-execution required admission, if supported, remains a separate declared
  operation policy.
- Sink delivery carries an evaluation-delivery context. An operation invoked
  while delivering to one sink cannot emit automatic or supplemental facts
  back to that same sink. Cross-sink forwarding, if admitted, remains explicit
  and bounded rather than recursive by default.
- Automatic and supplemental delivery has finite timeout, concurrency, queue,
  and byte limits. Saturation or overflow produces a bounded delivery
  limitation/drop outcome and cannot block normal operation execution
  indefinitely.
- Application supplemental facts pass through ActionCall/UnitOfWork and do not
  bypass authorization, confidentiality, limits, or observability.
- This phase does not implement online traffic allocation, feature flags,
  experimentation UI, or automatic corpus candidate promotion.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| OE-01 | Normative contract | Design/spec fix automatic capture, SPI ownership, no-op behavior, and supplemental DSL boundaries. | done |
| OE-02 | Capture model | Typed automatic and supplemental facts have bounded stable identities and confidentiality rules. | done |
| OE-03 | Standard SPI | Corpus and Experiment sink contracts provide disabled, fake, and installed behavior. | done |
| OE-04 | Runtime context | Scope and execution contexts carry immutable sink/correlation state across normal and Job execution. | done |
| OE-05 | Automatic chokepoint | The common operation path captures authorized start and terminal facts exactly once. | done |
| OE-06 | Internal DSL | Applications can add bounded corpus/experiment facts without calling providers directly. | done |
| OE-07 | Diagnostics | Delivery status, limitation, CallTree, and metrics remain bounded, payload-safe, timeout-safe, and non-authoritative. | done |
| OE-08 | Executable evidence | Specs cover admission, no-op, installed, success, failure, timeout, cancellation, retry, nested execution, and confidentiality. | done |
| OE-09 | Downstream handoff | Textus-owned fake/development adapters prove the standard SPI without reversing dependencies. | done |
| OE-10 | Verification and closure | Full validation, review, documentation, and closure evidence are complete. | done |

## Acceptance

- An ordinary authorized operation automatically produces one bounded start
  correlation and one terminal execution fact without application code.
- Connected Corpus and Experiment sinks receive the same stable operation
  correlation and source-attributed facts.
- With neither component connected, the operation behaves exactly as before
  and no provider call is attempted.
- An operation without evaluation declarations still emits the automatic fact
  to installed sinks, but it does not acquire corpus membership or experiment
  assignment.
- Application code can add bounded supplemental facts through the internal DSL
  while automatic facts remain present and distinguishable.
- Success, failure, timeout, cancellation, Job retry, and resume retain stable
  correlation and do not duplicate terminal facts.
- Sink failure or diagnostic failure cannot replace the canonical operation
  `Consequence` or business response.
- Sink-induced operation calls cannot recursively invoke the same sink.
- A stalled or saturated sink is bounded by delivery limits and cannot stall
  the operation indefinitely.
- Default capture exposes no raw business or confidential payload.
- Normal executable specifications require no external service, network,
  credentials, or Textus implementation dependency.

## Planning References

- `docs/notes/operation-evaluation-contract-proposed-specification.md`
- `docs/journal/2026/07/2026-07-21-operation-evaluation-corpus-experiment-observability-handoff.md`
- `docs/journal/2026/07/2026-07-21-operation-evaluation-contract-specification-history.md`
