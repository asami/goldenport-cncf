# Operation Evaluation and Capture

Status: normative design

## Purpose

CNCF provides one provider-neutral boundary for recording bounded operation
execution facts and for correlating explicitly admitted Corpus and Experiment
work. Framework capture is automatic. Applications add domain-specific facts
only through the protected internal DSL.

The boundary preserves normal operation execution. Corpus and Experiment are
consumers of execution facts; they are not alternate operation runtimes and do
not own CNCF operation outcomes.

## Terminology

- **automatic fact**: a bounded framework-owned fact about an authorized CNCF
  operation attempt;
- **supplemental fact**: bounded application-owned domain evidence submitted
  through the ActionCall/Behavior internal DSL;
- **evaluation declaration**: optional operation metadata that permits
  explicit corpus membership, candidate capture, experiment assignment, or
  another evaluation policy;
- **evaluation admission**: the immutable result of applying a declared
  membership or assignment policy before business execution;
- **execution correlation**: the operation, invocation, attempt, parent, Job,
  Task, and optional admitted Corpus/Experiment identities shared by facts for
  one execution;
- **sink**: the provider-neutral Corpus or Experiment SPI capability installed
  at the calling component socket;
- **delivery context**: framework state identifying sinks currently receiving
  facts, used to prevent recursive delivery;
- **delivery limitation**: a bounded structured description of evidence that
  was omitted, dropped, timed out, or otherwise not delivered.

Automatic capture and evaluation admission are separate concepts. An
operation does not need an evaluation declaration to produce automatic facts.
An automatic fact does not create corpus membership, a corpus candidate,
experiment assignment, an arm, or a measurement.

## Ownership

CNCF owns:

- the automatic capture chokepoint;
- provider-neutral correlation and fact contracts;
- optional evaluation declaration and admission contracts;
- Corpus and Experiment SPI contracts;
- disabled/no-op and deterministic fake sink behavior;
- protected supplemental capture DSL operations;
- delivery limits, reentrancy control, failure isolation, and safe diagnostics;
- propagation through CNCF Job, Task, retry, resume, and nested operation
  execution.

The Corpus component owns:

- candidate persistence and review;
- deduplication, labeling, confirmation, and promotion;
- immutable corpus revisions and cases;
- corpus retention and deletion policy.

The Experiment component owns:

- experiment definitions, arms, runs, and observations;
- assignment validation and experiment lifecycle;
- metric aggregation, acceptance evidence, and reports;
- experiment retention and deletion policy.

Applications own the meaning of their supplemental labels, measurements, and
domain evidence. Applications do not own framework execution facts and MUST
NOT call a Corpus or Experiment provider directly.

CNCF core has no dependency on a Textus Corpus, Textus Experiment, AI,
telemetry, or storage implementation.

## Capture Surfaces

### Automatic Framework Capture

Every resolved and authorized operation attempt enters the framework capture
boundary. This includes ordinary protocol, HTTP, CLI, internal component API,
SPI operation, Job worker, and query-only execution paths.

When a corresponding sink is installed, CNCF produces:

- one bounded start fact after authorization and before business execution;
- one bounded terminal fact after the canonical operation outcome and
  framework-owned response bindings are known.

The terminal fact classifies the existing operation outcome. It does not
replace, wrap, or reinterpret the canonical `Consequence` or business
response.

When no sink is installed, CNCF selects a no-op capability. No provider is
called, no external service is required, and operation behavior is unchanged.

### Explicit Evaluation Admission

Corpus membership, corpus candidate capture, experiment participation,
assignment, arm identity, and measurements require explicit declaration or
supplemental input. Runtime policy resolves those declarations after operation
authorization and before any assignment-dependent business execution.

An optional unavailable admission uses the normal control path and records a
bounded limitation. A required unavailable admission fails before business
execution. Such a failure belongs to the declared admission policy; it is not
caused by automatic sink recording.

An admitted assignment is immutable for one logical execution. Component code
may read only its admitted logical variant or execution-plan reference. It may
not allocate an arm, inspect global experiment state, or substitute an
undeclared assignment.

### Operation Declaration Model

An operation declares logical evaluation policy through `evaluation` metadata.
The canonical CML shape is:

```yaml
evaluation:
  corpus:
    capture: candidate
    profile: route-resolution
    admission: optional
    outcomes: [success, failure]
    sampling: representative
    redaction: default
  experiment:
    eligible: true
    purpose: route-resolution
    admission: optional
    variant-profile: execution-plan
```

`capture` is `candidate`. `admission` is `optional` or `required`. Corpus
outcomes are selected from `success`, `failure`, `timeout`, and
`cancellation`. Profile, purpose, sampling, redaction, and variant-profile are
bounded logical names. Concrete Corpus revision/case, Experiment/arm/run,
provider, model, credential, tenant, or payload values do not belong to CML
operation metadata.

The declaration is additive operation metadata and is projected through help,
describe, and schema surfaces. Its absence does not disable automatic capture.
Runtime admission resolves concrete external references in a later execution
stage.

### Typed Capture Model

The provider-neutral model distinguishes logical execution, attempt, fact, and
supplemental-intent identities. These are opaque CNCF IDs generated through
the execution `IdGenerationContext`. Provider-owned Corpus revision/case and
Experiment/arm/run identities are separate bounded opaque references and are
never parsed as CNCF identifiers.

One immutable correlation value contains operation identity, logical
execution, attempt, optional parent execution, ExecutionContext, Job, Task,
trace, observability correlation, and admitted Corpus/Experiment references.
An Experiment run requires both an arm and an immutable Corpus revision. A
retry retains logical execution and admitted assignment while receiving a new
attempt identity.

Capture facts identify their source as `framework`, `application`, or
`provider`:

- framework start and terminal facts contain structural execution data only;
- application Corpus candidates and Experiment observations contain bounded
  summaries, labels, and measurements;
- provider delivery results contain sink identity, status, and bounded
  limitations only.

Every fact and provider delivery result carries existing `DataConfidentiality`
metadata. Text and external reference limits are UTF-8 byte limits.
Measurements use at most 34 decimal digits and a scale from -128 through 128.
Delivery results contain at most 16 limitations. Terminal failures use the existing
`ConclusionDiagnostics.Classification`; facts do not add application detail
codes or derive classification from display text.

### Supplemental Application Capture

Applications submit additional corpus candidates, experiment observations,
labels, measurements, or domain context through protected ActionCall/Behavior
internal DSL operations. The DSL routes submission through UnitOfWork and the
installed standard sink capability.

The canonical protected helper vocabulary is:

- `operation_evaluation_label`;
- `operation_evaluation_measurement`;
- `corpus_candidate`;
- `experiment_observation`.

The label and measurement helpers construct bounded typed values in the
`ExecUowM` program. Candidate and observation helpers construct an immutable
application-owned fact and emit
`UnitOfWorkOp.StageOperationEvaluationSupplemental`. They do not return or
accept a provider handle, sink, post-commit callback, or mutable buffer.

The DSL stages an immutable supplemental-delivery intent in the active
UnitOfWork. A successful UnitOfWork commit transfers that intent to a
framework-owned post-commit capture buffer; it does not invoke the external
sink as a transaction participant. CNCF releases buffered intents only after
the complete canonical operation, including framework-owned response and
association bindings, succeeds. Abort, rollback, commit failure, cancellation,
timeout, or a later framework-binding failure discards the intents. The
automatic terminal fact remains the evidence for an unsuccessful operation.

Once released, supplemental delivery is auxiliary and cannot reopen the
committed transaction or change the canonical operation outcome. A later
contract may add an explicit post-terminal application-evidence surface, but
the ordinary in-operation DSL does not publish facts from an aborted operation.

The UnitOfWork buffer is retained across framework ExecutionContext rebinding
and partitions its state by operation attempt. The current ActionCall context
remains the authority for constructing the fact. Commit, release, and discard
target one attempt without changing evidence for another Task, retry, or nested
operation that shares the UnitOfWork. Rebinding does not duplicate, release, or
discard buffered intents.

Supplemental facts:

- share the admitted execution correlation;
- identify their source as application-owned;
- remain distinguishable from automatic framework facts;
- obey authorization, confidentiality, count, byte, delivery, and
  observability policies;
- cannot bypass the normal CNCF execution chokepoints.

## Execution Order

The normative order is:

```text
resolve route and normalize framework input
  -> resolve ingress security
  -> authorize the operation
  -> establish automatic capture correlation
  -> resolve any explicitly declared evaluation admission
  -> construct and execute ActionCall/Behavior through UnitOfWork
  -> retain committed supplemental intents in the post-commit capture buffer
  -> apply framework-owned response and association bindings
  -> determine the canonical terminal outcome
  -> construct bounded automatic terminal facts
  -> release supplemental intents only for canonical success
  -> deliver eligible facts under bounded sink policy
  -> expose safe delivery diagnostics
  -> return the unchanged canonical outcome
```

Authorization denial occurs before external admission or sink invocation. It
is not an operation execution fact. Request normalization failures that occur
before operation authorization remain ordinary request-validation diagnostics.

The implementation MAY factor the chokepoint around existing runtime methods,
but no presentation adapter or special query path may bypass the contract.

## Correlation and Execution Lifecycles

Correlation is operation-scoped and immutable. It distinguishes one logical
execution from its attempts and preserves parent/child relationships without
parsing opaque CNCF identifiers.

- A retry retains logical execution and admitted assignment identity but has a
  distinct attempt identity.
- A resumed Job restores framework-owned correlation without storing provider
  handles or raw payloads.
- A nested operation receives its own automatic facts and parent correlation.
- Corpus membership and experiment assignment do not propagate to a nested
  operation unless an explicit policy admits that operation.
- A logical attempt produces at most one terminal automatic fact per sink.
- Stable fact identity permits idempotent at-least-once provider delivery.

Observability trace identity may correlate records, but observability sampling
or retention cannot create, remove, or reconstruct Corpus/Experiment
membership.

## SPI Boundary

Corpus and Experiment are separate standard SPI contracts. A calling
component receives the selected provider-neutral service through its own
socket. Provider implementation types and provider configuration do not enter
component code or the execution context.

The standard contracts are:

| Contract | Operations |
| --- | --- |
| `corpus-evaluation-sink` | `recordStart`, `recordTerminal`, `submitCandidate` |
| `experiment-evaluation-sink` | `recordStart`, `recordTerminal`, `submitObservation` |

`recordStart` and `recordTerminal` accept only framework-owned automatic fact
types. `submitCandidate` accepts only `CorpusCandidateFact`, and
`submitObservation` accepts only `ExperimentObservationFact`. Each operation
returns `Consequence[OperationEvaluationDeliveryResult]`; a provider cannot
replace the submitted fact, correlation, or canonical operation result. The
caller-side wrapper reconstructs the result fact identity from the submitted
fact and the sink identity from the resolved socket/provider binding. Provider
status, bounded limitations, and confidentiality remain provider output, but a
provider-supplied fact or sink identity is not authoritative.

The disabled implementation is the default when an optional component is not
connected. The socket remains observably uninstalled, but its accessor returns
the disabled capability rather than throwing. The disabled capability invokes
no provider and returns a bounded `discarded` result with an `unavailable`
limitation. Deterministic fake implementations retain submitted facts in call
order and return deterministic `delivered` results; they are the
executable-specification surface. Production adapters live outside CNCF core.

SPI invocation remains observable at the calling component boundary according
to the standard CNCF SPI trace contract. A provider implemented by CNCF
operations also retains normal operation tracing. Capture-specific diagnostics
must not duplicate payloads in either trace. The caller-side wrapper may record
contract, operation, fact kind, delivery status, and limitation count. It does
not record fact identity, execution correlation, summary, labels,
measurements, or provider payloads. Sink failure tracing is structural: it
retains status and diagnostic key but omits `Conclusion.display` and diagnostic
facets from both CallTree and dashboard metrics.

## Delivery and Reentrancy

Automatic and supplemental delivery is auxiliary unless an explicit
pre-execution admission policy says otherwise. Delivery failure cannot turn a
completed business success into failure or replace an existing business
failure.

Every installed delivery policy has finite:

- invocation timeout;
- concurrency;
- queued item count;
- item and aggregate byte size;
- saturation and overflow behavior.

A timeout, queue rejection, overflow, provider failure, or unsupported content
produces a bounded delivery result or limitation. It cannot wait indefinitely
or expose the provider failure as the canonical operation outcome.

The runtime projects each attempted installed-sink delivery into a bounded
`OperationEvaluationExecutionReport` attached to
`RuntimeContext.ExecutionMetadata`. The report retains at most 32 delivery
diagnostics and counts additional omitted diagnostics. Each retained
diagnostic contains only:

- bounded operation identity;
- fact kind and framework/application source;
- sink contract, socket component, and provider component;
- delivery status;
- bounded limitation kinds;
- bounded structured diagnostic keys derived from provider failures.

The report does not retain fact identity, execution correlation, provider
instance, Corpus membership, Experiment assignment, evidence payload, or
`Conclusion.display`. It is runtime metadata, not a replacement for the
canonical response envelope or the provider-owned evaluation state.

The same delivery boundary records a completed CallTree mark named
`operation-evaluation:delivery` and runtime metrics under
`operation-evaluation.delivery`. These projections use the same safe
structural fields as the execution report. Metric labels retain all bounded
`limitation_kinds` and `diagnostic_keys`. Provider work runs with caller
CallTree collection detached, so timeout cannot corrupt a shared caller stack.
Projection uses a best-effort delivery observer and occurs independently from
provider delivery; sampling, retention loss, omission, or failure of execution
metadata, CallTree, or metrics cannot suppress delivery, alter its result, or
change provider-owned membership/assignment state.
The observer receives the bounded delivery diagnostic, not the original fact
or provider payload.

The delivery context records the logical identity of each sink currently being
invoked. Sink identity is based on the contract, calling socket, and selected
provider component/instance; it is not object identity. If sink delivery
invokes another CNCF operation, automatic and supplemental delivery back to
that same sink is suppressed. Delivery to a different sink is allowed only by
explicit cross-sink policy and remains subject to the same bounds.

The context is causal rather than thread-local. CNCF propagates it through
synchronous nested calls, context rebinding, scheduler handoff, Job/Task
submission, retry, and any asynchronous operation invocation made with the
sink's CNCF-provided caller capability. A provider cannot clear or replace the
context. A later independent external ingress is a new authorized root and is
not represented as continuation of the completed sink delivery.

Asynchronous buffering, an outbox, or at-least-once retry may be selected by a
later runtime policy, but those mechanisms do not weaken these invariants.

## Confidentiality

Default automatic facts contain structural metadata only:

- normalized operation identity;
- execution, attempt, parent, Job, and Task correlation where admitted;
- source and timing classification;
- terminal outcome and structured diagnostic classification;
- bounded delivery status.

Default facts exclude:

- raw requests and responses;
- prompts, messages, generated content, and provider output;
- credentials, tokens, session values, and transport headers;
- unrestricted subject, tenant, or customer identifiers;
- filesystem paths, provider endpoints, provider configuration, and mutable
  handles;
- execution-plan content and application payloads.

An explicit evidence policy may admit a redacted summary, digest, or bounded
logical reference. It may only tighten the existing operation confidentiality
model, never weaken it. Delivery diagnostics and CallTree attributes follow
the same exclusion policy.

## Relationship to Observability

Operation evaluation and observability share safe correlation but have
different authority.

- `Consequence` remains the authoritative operation outcome.
- Corpus and Experiment components remain authoritative for membership and
  lifecycle state.
- Observability records describe execution and delivery behavior only.
- Metrics may aggregate delivery outcomes but cannot be replayed as missing
  facts or used to infer membership.

## Deferred Scope

This contract does not define production traffic allocation, sticky-user
assignment, feature flags, online arm randomization, experiment UI, automatic
candidate promotion, provider-specific schemas, or a durable outbox protocol.
SPI method signatures, configuration defaults, and remaining runtime policy
are fixed by later Phase 48 stages under this design. The typed capture values
and CML declaration syntax are normative from OE-02 onward.
