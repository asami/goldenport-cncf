# Operation Evaluation Contract Specification History (2026-07-21)

status=record
updated_at=2026-07-21
tag=operation, evaluation, corpus, experiment, observability, specification-history

## Position of This Record

This journal records the review and specification work performed from:

docs/journal/2026/07/2026-07-21-operation-evaluation-corpus-experiment-observability-handoff.md

It records what was inspected, what was retained, and what was adjusted. It
does not establish runtime requirements. The resulting proposed contract is in:

docs/notes/operation-evaluation-contract-proposed-specification.md

The notes document remains non-normative under
docs/rules/document-lifecycle.md. Stable decisions and binding behavior were
not promoted to docs/design or docs/spec during this work.

## Review Performed

The review compared the handoff with:

- Subsystem operation dispatch and resolved-operation execution;
- ActionEngine execution, commit/abort/dispose, CallTree, metrics, and
  OpenTelemetry handling;
- CmlOperationDefinition metadata;
- ExecutionContext.CncfCore;
- RuntimeContext.ExecutionMetadata;
- current action request/result confidentiality handling;
- current Textus Corpus CML and reference manual;
- current Textus Experiment CML and reference manual;
- CNCF document-lifecycle rules.

No Scala, CML, configuration, or executable specification was changed.

## Findings Retained from the Handoff

The following directions were supported by the inspected code and product
contracts:

- Corpus and experiment membership must be established before operation
  execution rather than reconstructed from telemetry.
- A corpus revision is immutable evaluation input.
- An operation declaration should express eligibility and logical policy, not
  concrete corpus, experiment, provider, model, prompt, or credential values.
- The operation runtime should carry one admitted assignment that component
  logic can read but cannot allocate or mutate.
- Terminal evaluation and observability evidence must be bounded and redacted.
- Capture creates a candidate; it does not automatically add a case to an
  immutable corpus revision.
- Auxiliary capture, observation, or telemetry failure must not rewrite a
  successful business response.
- CNCF core should remain provider-neutral and should not directly depend on
  textus-corpus or textus-experiment.

## Implementation Evidence

### Common operation path

executeOperationResponse is a convenience method that delegates to
executeWithMetadata. The reusable resolved route is
Subsystem._execute_resolved_operation. SPI execution also reaches that method.

ActionEngine.execute owns the action-level execution envelope and already
records sanitized action evidence, CallTree, runtime metrics, and OpenTelemetry
data.

The review therefore treated the common boundary as the resolved-operation
pipeline plus ActionEngine, not as an extension to the public
executeOperationResponse method alone.

### Operation declaration

CmlOperationDefinition currently carries command policy, access,
confidentiality-related fields, relationship bindings, and result fields. It
does not carry an evaluation declaration.

The proposed notes contract recorded evaluation as new optional metadata so
existing generated CARs remain unaffected when no evaluation policy is
present.

### Execution context

ExecutionContext.CncfCore already transports resolved runtime capabilities and
operation execution state. It has no corpus, experiment, or evaluation field.

The proposed notes contract recorded one immutable, operation-scoped
OperationEvaluationContext as the appropriate carrier. It deliberately avoids
placing raw configuration or adapter clients in ExecutionContext.

### Execution metadata

RuntimeContext.ExecutionMetadata currently carries Job, saga, trace, execution,
failure, and optional inline CallTree information. It has no place for an
evaluation admission or recording limitation.

The proposed notes contract recorded a bounded evaluation execution report as
metadata while preserving the canonical OperationResponse.

### Observability

ActionEngine and ObservabilityEngine already sanitize request and response
summaries using operation field confidentiality. This can support bounded
evidence construction.

Those records are subject to sampling, retention, and export failure, so the
review retained the rule that observability is downstream evidence and not the
authority for corpus or experiment membership.

## Textus Contract Check

### Textus Corpus

The current Textus Corpus component owns immutable CorpusRevision and
CorpusCase entities. Cases reference sanitized fixture and expected-evidence
resources. Provider, experiment, execution outcome, and metric data are
explicitly outside the corpus model.

The current component does not yet define a CorpusCandidate entity or
candidate-submission operation. The proposed CNCF contract therefore describes
a provider-neutral candidate sink but does not assume a current Textus Corpus
API. Candidate review and promotion remain a future Textus Corpus adapter
surface.

### Textus Experiment

The current Textus Experiment component owns Experiment, ExperimentArm,
ExperimentRun, and ExperimentObservation. One experiment references one
immutable corpus revision. An arm uses an executionPlanReference rather than
embedding provider/model fields.

Its current reference manual describes reproducible offline comparison and
explicitly says that it does not route production traffic.

The handoff discussed runtime arm allocation and A/B operation execution more
generally. To avoid silently expanding Textus Experiment responsibility, the
proposed first contract was narrowed to an explicit offline assignment supplied
by an experiment orchestrator. Online production allocation was recorded as a
deferred extension.

## Decisions Recorded in the Proposed Notes Contract

The review produced these provisional design choices:

- OperationEvaluationResolver resolves or validates admission before business
  execution.
- OperationEvaluationSink records terminal facts and optionally submits corpus
  candidates.
- Both are provider-neutral ScopeContext capabilities with disabled and fake
  implementations.
- OperationEvaluationContext is immutable and carried by
  ExecutionContext.CncfCore.
- Ordinary, optional, and required evaluation participation have distinct
  failure behavior.
- A missing optional assignment uses the control branch; a missing required
  experiment assignment fails before business execution.
- An unknown admitted variant does not silently fall back to control.
- Job resume and retry preserve the original assignment; retries receive a new
  attempt identity.
- Nested operations do not automatically become duplicate experiment
  observations.
- Business terminal outcome and evaluation-record completeness are separate
  dimensions.
- Stable fact identifiers support idempotent at-least-once sink delivery.
- Telemetry sampling cannot erase an admitted experiment fact.
- Default evidence excludes raw request, response, prompt, provider output,
  credentials, and execution-plan content.

These choices were recorded only in the non-normative notes artifact.

## Differences from the Original Handoff

The resulting notes contract made four material clarifications:

1. The runtime integration point is the common resolved-operation pipeline,
   not executeOperationResponse alone.
2. Initial experiment participation uses explicit offline assignment; online
   production traffic allocation is deferred.
3. Optional capture/admission failure and required experiment-admission failure
   are different. Only the latter prevents business execution.
4. Corpus candidate submission is defined as a generic sink because the
   current Textus Corpus component has no candidate API.

## Artifacts Produced

- docs/notes/operation-evaluation-contract-proposed-specification.md
- docs/journal/2026/07/2026-07-21-operation-evaluation-contract-specification-history.md

The original handoff was left unchanged so the initial proposal and the later
review remain separately traceable.

## Deferred State at End of Review

The work stopped before:

- choosing final CML syntax;
- defining final Scala package and type names;
- selecting synchronous, queue, or outbox delivery;
- adding Textus Corpus candidate APIs;
- adding Textus Experiment adapters;
- defining online allocation;
- modifying runtime code;
- adding executable specifications;
- promoting the proposal to docs/design and docs/spec.
