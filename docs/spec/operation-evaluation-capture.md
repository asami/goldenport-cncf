# Operation Evaluation and Corpus/Experiment Capture

Status: normative static contract

## Scope

This specification defines the observable CNCF contract for automatic
operation capture, explicit Corpus/Experiment admission, supplemental
application facts, sink delivery, correlation, confidentiality, and failure
isolation. OE-02 defines the concrete provider-neutral Scala capture values and
CML declaration syntax. Later Phase 48 stages define SPI signatures and runtime
policy.

## Automatic Capture Contract

1. Every operation attempt that has completed route resolution, framework
   normalization, ingress security resolution, and operation authorization
   MUST enter the automatic capture boundary.
2. Automatic capture MUST cover ordinary protocol, HTTP, CLI, internal
   component API, SPI operation, Job worker, and query-only execution paths.
3. If a corresponding sink is installed, CNCF MUST create one bounded start
   fact before business execution and one bounded terminal fact after the
   canonical outcome and framework response bindings are known.
4. An operation declaration MUST NOT be required for automatic capture.
5. An automatic fact MUST NOT establish corpus membership, submit a corpus
   candidate, assign an experiment or arm, or create a measurement.
6. If no sink is installed, the no-op capability MUST invoke no provider and
   MUST preserve operation behavior.
7. Authorization denial MUST occur before external evaluation admission and
   sink invocation. Pre-authorization failures MUST remain ordinary routing,
   validation, or authorization diagnostics rather than execution facts.

## Explicit Admission Contract

Corpus membership, candidate capture, experiment participation, assignment,
arm identity, and measurements MUST be enabled only by explicit operation,
runtime, or invocation policy.

- Admission MUST occur after authorization and before assignment-dependent
  operation request construction or business execution.
- One admitted assignment MUST be immutable for one logical execution.
- A required unavailable or invalid admission MUST fail before business
  execution with a structured `Consequence`.
- An optional unavailable admission MUST use the normal control path and MAY
  report a bounded limitation.
- An unknown admitted variant MUST NOT silently fall back to control.
- Operation code MUST NOT allocate or mutate Corpus/Experiment lifecycle
  state.

Automatic facts remain eligible when an authorized invocation has no explicit
admission. Conversely, an admitted membership or assignment does not permit
raw payload capture.

## Supplemental Fact Contract

Applications MUST submit supplemental Corpus or Experiment information only
through protected ActionCall/Behavior internal DSL operations.

The protected operation names are
`operation_evaluation_label`, `operation_evaluation_measurement`,
`corpus_candidate`, and `experiment_observation`. The first two construct
bounded typed values. The latter two MUST emit
`UnitOfWorkOp.StageOperationEvaluationSupplemental`; they MUST NOT expose or
invoke a provider service directly.

The DSL MUST:

- use the current immutable execution correlation;
- identify the source as application-owned;
- stage an immutable delivery intent in the active UnitOfWork;
- partition staged, committed, released, and discarded intents by operation
  attempt when multiple Tasks, retries, or nested operations share a UnitOfWork;
- transfer a committed intent to a framework-owned post-commit capture buffer
  without invoking the external sink as a transaction participant;
- release the intent to the installed standard SPI capability only after the
  complete canonical operation and framework-owned response bindings succeed;
- discard staged or buffered intents on abort, rollback, commit failure,
  cancellation, timeout, or later framework-binding failure;
- apply authorization, confidentiality, count, byte, timeout, and delivery
  policies;
- preserve the canonical `Consequence` semantics of the enclosing operation;
- reject direct provider handles and unbounded values.

Supplemental delivery after canonical success MUST NOT reopen the committed
transaction or change its outcome. Failure-operation evidence is supplied by
the automatic terminal fact; the ordinary in-operation DSL MUST NOT publish a
supplemental fact from an aborted operation.

Framework-owned automatic facts MUST remain distinguishable from application
supplemental facts and provider-owned delivery diagnostics.

## Terminal Outcome Contract

The terminal fact MUST classify the canonical operation result as success,
failure, timeout, or cancellation without replacing that result.

- Framework-owned response and association/image bindings MUST complete before
  terminal evidence is constructed.
- One logical attempt MUST produce at most one terminal automatic fact for each
  installed sink.
- Retried attempts MUST retain logical execution and assignment correlation
  while receiving distinct attempt and fact identities.
- Stable fact identity MUST permit idempotent handling of repeated provider
  delivery.
- Sink, telemetry, metrics, and diagnostic failures MUST NOT convert business
  success into failure or replace an existing business failure.

An explicit required pre-execution admission failure remains operation failure
because the operation was not allowed to run without its declared assignment.
That rule does not make terminal sink recording authoritative.

## Nested and Job Execution Contract

- Job submission, worker execution, retry, and resume MUST preserve the safe
  logical correlation required to attribute automatic and supplemental facts.
- Serialized Job state MUST contain only framework-owned bounded identifiers
  and logical references, never provider handles or raw payloads.
- A nested operation MUST produce its own automatic facts after its own
  authorization.
- A nested operation MUST NOT inherit Corpus membership or Experiment
  assignment unless an explicit policy admits that operation.
- Parent and child facts MUST retain bounded parent correlation.

## Standard SPI Contract

Corpus and Experiment MUST be separate provider-neutral standard SPI
contracts. Each service MUST be installed at the calling component socket.

- The Corpus contract name MUST be `corpus-evaluation-sink` and MUST expose
  `recordStart(OperationEvaluationStartFact)`,
  `recordTerminal(OperationEvaluationTerminalFact)`, and
  `submitCandidate(CorpusCandidateFact)`.
- The Experiment contract name MUST be `experiment-evaluation-sink` and MUST
  expose `recordStart(OperationEvaluationStartFact)`,
  `recordTerminal(OperationEvaluationTerminalFact)`, and
  `submitObservation(ExperimentObservationFact)`.
- Every operation MUST return
  `Consequence[OperationEvaluationDeliveryResult]` without replacing its input
  fact or the enclosing operation outcome.
- For an installed provider, the caller-side wrapper MUST derive the result
  fact identity from the submitted fact and the sink identity from the resolved
  socket/provider binding. A provider-returned fact or sink identity MUST NOT
  override those framework-owned values.

- Component code MUST depend only on the provider-neutral contract.
- CNCF core MUST NOT depend on Textus Corpus, Textus Experiment, AI, telemetry,
  or provider storage implementations.
- Optional missing providers MUST leave the socket uninstalled while its
  accessor resolves to a disabled/no-op service.
- A disabled service MUST call no provider and MUST return a bounded
  `discarded` delivery result with an `unavailable` limitation.
- Deterministic fake services MUST support executable specifications without
  external services, network access, credentials, or provider libraries, MUST
  preserve invocation order, and MUST return deterministic `delivered`
  results.
- Installed service invocation MUST follow the standard caller-side CNCF SPI
  trace contract without recording fact payloads.
- Sink traces MAY contain contract, operation, fact kind, delivery status, and
  limitation count. They MUST NOT contain fact identity, execution correlation,
  summary, labels, measurements, or provider payloads.
- A sink failure trace or metric MUST retain only structural status and
  diagnostic key. It MUST NOT retain `Conclusion.display` or diagnostic facets.

## Delivery Contract

Every automatic and supplemental delivery path MUST have finite configured or
framework-default bounds for timeout, concurrency, queued item count, item
bytes, aggregate queued bytes, and saturation/overflow handling.

- Saturation MUST reject or drop according to an explicit bounded policy.
- Timeout, rejection, overflow, and provider failure MUST produce a bounded
  delivery result or limitation.
- Delivery MUST NOT wait indefinitely.
- Delivery retry, buffering, or at-least-once handling MUST preserve stable fact
  identity.
- Delivery diagnostics MUST NOT become Corpus/Experiment membership evidence.
- Every attempted installed-sink delivery MUST attempt to append one safe
  diagnostic to `RuntimeContext.ExecutionMetadata` through the best-effort
  delivery observer. A full report MUST increment its omitted count and MUST
  retain at most 32 diagnostics.
- An execution delivery diagnostic MUST contain only operation identity, typed
  fact kind/source, sink contract/socket/provider component, delivery status,
  limitation kinds, and bounded structured diagnostic keys.
- An execution delivery diagnostic MUST NOT contain fact identity, execution
  correlation, provider instance, membership, assignment, summary, labels,
  measurements, provider payload, `Conclusion.display`, or diagnostic facets.
- The delivery boundary MUST attempt to emit a completed payload-safe
  `operation-evaluation:delivery` CallTree mark when CallTree is enabled and a
  payload-safe `operation-evaluation.delivery` runtime metric point.
- Provider execution MUST NOT keep a caller CallTree frame open across its
  finite timeout. Provider-worker CallTree collection is detached from the
  caller stack; the caller-side completed delivery mark is authoritative.
- Runtime metrics MUST retain all bounded limitation kinds and diagnostic keys
  for one delivery rather than selecting only the first value.
- Execution metadata, CallTree, and metric recording MUST be best-effort and
  MUST NOT gate provider delivery, replace a delivery result, or change the
  canonical operation `Consequence`.
- The delivery observer MUST receive only the bounded delivery diagnostic and
  MUST NOT receive the original fact or provider payload.
- Observer sampling, disabling, retention loss, report omission, or failure
  MUST NOT create, remove, reconstruct, or otherwise affect provider-owned
  Corpus membership or Experiment assignment.

## Declaration and Typed Model Contract

The canonical operation declaration MUST use the `evaluation.corpus` and
`evaluation.experiment` metadata roots.

- Corpus `capture` MUST be `candidate`.
- Admission MUST be `optional` or `required`.
- Corpus outcomes MUST be selected from `success`, `failure`, `timeout`, and
  `cancellation`.
- Logical profile, purpose, sampling, redaction, and variant-profile names MUST
  be bounded and normalized.
- The declaration MUST NOT contain concrete revision, case, experiment, arm,
  run, provider, model, credential, tenant, or payload values.
- An absent declaration MUST remain eligible for automatic structural facts and
  MUST NOT establish membership or assignment.

Execution, attempt, fact, and supplemental-intent identities MUST be distinct
opaque CNCF identifiers. Corpus and Experiment references MUST remain bounded
opaque provider-owned references. CNCF MUST NOT parse one identity family as
another.

An Experiment run correlation MUST contain an Experiment, arm, and immutable
Corpus revision. Retry MUST retain logical execution and admitted assignment
identity while using a distinct attempt identity.

Every fact MUST identify one source: `framework`, `application`, or `provider`.
Every fact and provider delivery result MUST carry `DataConfidentiality`.
Bounded text and reference limits MUST be evaluated in UTF-8 bytes.
Measurements MUST contain at most 34 decimal digits with scale from -128
through 128. A delivery result MUST contain at most 16 limitations. Automatic facts MUST NOT expose raw request,
response, prompt, generated content, credential, provider payload, or mutable
handle values.

Terminal failure diagnostics MUST use the existing structured
`ConclusionDiagnostics.Classification`. Capture facts MUST NOT introduce an
application detail code, a component-local error taxonomy, or display-message
classification.

Application Corpus candidates and Experiment observations MUST use bounded
summary, label, and measurement collections. Provider delivery results MUST
contain only logical sink identity, status, and bounded limitations.

## Reentrancy Contract

The runtime MUST carry an immutable delivery context identifying the logical
sink identities active in the current causal execution. A logical identity
MUST include the SPI contract, calling socket, and selected provider
component/instance and MUST NOT depend on service object identity.

- A sink-induced operation MUST NOT deliver automatic or supplemental facts
  recursively to the same active sink.
- CNCF MUST preserve the active-sink context through synchronous nesting,
  execution-context rebinding, scheduler handoff, Job/Task submission, retry,
  and asynchronous operation invocation through a CNCF-provided caller
  capability.
- A provider MUST NOT clear, replace, or forge the delivery context.
- Same-sink suppression MUST preserve normal execution of the induced
  operation.
- Cross-sink forwarding MUST be disabled unless an explicit policy permits it.
- Permitted cross-sink forwarding MUST remain bounded by depth, timeout,
  concurrency, queue, and byte policies.
- Suppression or rejection MUST produce safe structural diagnostics without
  payload content.

## Confidentiality Contract

Default automatic facts and diagnostics MUST NOT contain:

- request or response payloads;
- prompts, messages, generated content, or provider output;
- credentials, tokens, session values, or transport headers;
- unrestricted user, tenant, or customer identifiers;
- physical paths, endpoints, provider configuration, or runtime handles;
- execution-plan content.

Default facts MAY contain normalized operation identity, safe execution and
Job/Task correlation, timing classification, source, terminal outcome,
structured diagnostic classification, and delivery status.

An explicit evidence policy MAY admit only a bounded redacted summary, digest,
or logical evidence reference. It MUST respect existing operation-field
confidentiality and MUST NOT broaden access.

## Authority Contract

- `Consequence` is authoritative for operation outcome.
- Corpus owns candidate and immutable revision/case membership.
- Experiment owns assignments, arms, runs, observations, and acceptance.
- Observability owns neither operation outcome nor membership.
- Telemetry sampling, loss, retention, or replay MUST NOT create, erase, or
  reconstruct Corpus/Experiment membership.

## Required Executable Scenarios

Later Phase 48 executable specifications MUST prove the following matrix:

| Scenario | Required evidence |
| --- | --- |
| No sinks connected | Operation result is unchanged and no provider is called. |
| Sinks connected, no declaration | Start and terminal facts are delivered; no membership or assignment is created. |
| Authorization denied | No admission or sink call occurs. |
| Explicit optional admission unavailable | Control execution continues with a bounded limitation. |
| Explicit required admission unavailable | Business execution does not start and a structured failure is returned. |
| Success, failure, timeout, cancellation | Canonical outcome is preserved and one matching terminal fact is attempted. |
| Sink failure or timeout | Business outcome is unchanged and bounded delivery diagnostics are available. |
| Retry and Job resume | Logical correlation is retained; attempt/fact identity is distinct and idempotent. |
| Nested operation | Child automatic facts have parent correlation but no implicit membership inheritance. |
| Same-sink recursion | Recursive delivery is suppressed while the induced operation keeps its normal semantics. |
| Queue/byte saturation | Delivery is bounded and reports rejection/drop without indefinite waiting. |
| Supplemental DSL | Application facts share correlation, carry application source, and pass through UnitOfWork/SPI. |
| Supplemental operation abort | Staged intents are discarded and no supplemental provider call occurs. |
| Supplemental success with later binding failure | Buffered intents are discarded because canonical operation success was not reached. |
| Supplemental canonical success | Committed intents are released after response bindings without reopening the transaction. |
| Confidential payload | Raw payload and restricted metadata are absent from facts, traces, metrics, and diagnostics. |
| Query-only and SPI routes | Both use the same capture semantics as ordinary operation execution. |

Executable tests are the final behavioral authority. This static contract does
not permit a narrower implementation simply because one current dispatcher
path is easier to intercept.
