# Operation Evaluation Contract — Proposed Specification

status = proposed, non-normative
date = 2026-07-21
strategy_item = 9.36 Operation Evaluation Contract

Source handoff:
docs/journal/2026/07/2026-07-21-operation-evaluation-corpus-experiment-observability-handoff.md

Promotion annotation (2026-07-23): Phase 48 promoted the accepted direction to
`docs/design/operation-evaluation-capture.md` and
`docs/spec/operation-evaluation-capture.md`. Those normative documents replace
this proposal as the current contract. In particular, an absent evaluation
declaration no longer disables automatic framework capture and does not imply
Corpus membership, declaration-derived candidate capture, or Experiment
assignment. Explicit supplemental candidate submission remains possible under
the normative DSL contract. This historical proposal otherwise remains
unchanged.

## Position

This note specifies a proposed CNCF contract for operation evaluation,
corpus-candidate capture, experiment participation, and observability
correlation.

It is intentionally stored in notes and is therefore non-normative. It does not
change current runtime behavior. After review, stable architectural decisions
belong in docs/design and testable requirements belong in docs/spec, following
docs/rules/document-lifecycle.md.

The first delivery target is deterministic offline evaluation over an immutable
corpus revision. The contract leaves room for a later admitted online
experiment allocator, but production-traffic routing is not part of the first
scope.

## Goals

- Declare operation-level eligibility for evaluation without embedding runtime
  experiment or provider configuration in a CAR.
- Resolve one immutable evaluation admission before operation business logic
  starts.
- Give operation logic read-only access to an admitted variant or execution
  plan without exposing global configuration.
- Produce bounded terminal evaluation facts without changing the canonical
  operation response.
- Preserve corpus, experiment, and observability correlation without treating
  telemetry as the authority for membership.
- Keep CNCF independent of textus-corpus, textus-experiment, Textus AI, and
  particular telemetry providers.
- Support deterministic fake adapters and replayable executable
  specifications.

## Non-Goals

- CNCF does not own corpus revisions, corpus cases, experiments, arms, runs,
  metric aggregation, or acceptance judgments.
- CNCF does not promote a captured candidate into an immutable corpus.
- CNCF does not expose raw observability stores or global experiment
  configuration to CAR logic.
- CNCF does not embed model, prompt, provider, endpoint, credential, or
  tenant-specific identifiers in an operation declaration.
- The first contract does not allocate production traffic between arms.
- Auxiliary recording does not replace the operation response or normal
  Consequence semantics.

## Confirmed Extension Points

The current implementation provides these relevant surfaces:

- CmlOperationDefinition carries generated operation metadata but has no
  evaluation field.
- Subsystem._execute_resolved_operation is the common resolved route used by
  ordinary and SPI operation execution.
- ActionEngine.execute surrounds action authorization, execution,
  commit/abort/dispose, CallTree recording, metrics, and OpenTelemetry export.
- ExecutionContext.CncfCore carries resolved runtime capabilities and has no
  operation-evaluation context.
- RuntimeContext.ExecutionMetadata carries execution and trace metadata and has
  no evaluation report.
- ObservabilityEngine.recordActionExecution already records sanitized action
  evidence, but an observability record is not a durable corpus or experiment
  membership record.

The integration must therefore wrap the resolved operation execution pipeline;
adding behavior only to the public executeOperationResponse convenience method
would miss other routes and would be the wrong abstraction boundary.

## Conceptual Model

~~~text
Operation declaration
  + runtime evaluation policy
  + invocation evaluation context
  + authorization and confidentiality policy
    -> OperationEvaluationAdmission
       -> immutable OperationEvaluationContext
          -> ordinary operation execution
          -> optional declared variant selection
             -> canonical operation outcome
                -> bounded OperationEvaluationFact
                   -> evaluation sink
                   -> corpus candidate sink when admitted
                   -> observability correlation when admitted
~~~

The authoritative direction is:

~~~text
Corpus revision and case
  -> experiment, arm, and run
     -> admitted operation execution
        -> evaluation fact and observability evidence
~~~

The inverse direction is forbidden:

~~~text
telemetry search
  -> inferred corpus membership
  -> inferred experiment membership
~~~

## Operation Declaration

### Candidate CML shape

The following shape is illustrative but its semantics are specified below:

~~~yaml
evaluation:
  corpus:
    capture: candidate
    profile: route-resolution
    outcomes:
      - success
      - failure
    sampling: representative
    redaction: default
  experiment:
    eligible: true
    purpose: route-resolution
    variant-profile: execution-plan
~~~

### Declaration model

A candidate generated model is:

~~~scala
final case class CmlOperationEvaluation(
  corpus: Option[CmlCorpusCaptureDeclaration],
  experiment: Option[CmlExperimentEligibilityDeclaration]
)

final case class CmlCorpusCaptureDeclaration(
  capture: CorpusCaptureEligibility,
  profile: String,
  outcomes: Set[OperationTerminalOutcomeKind],
  sampling: String,
  redaction: String
)

final case class CmlExperimentEligibilityDeclaration(
  eligible: Boolean,
  purpose: String,
  variantProfile: String
)
~~~

The exact Scala representation is provisional. The following invariants are
not:

- The declaration expresses eligibility, purpose, and logical policy names.
- The declaration does not activate capture or an experiment by itself.
- profile, sampling, redaction, purpose, and variant-profile are logical names
  resolved by runtime policy.
- The declaration contains no concrete corpus revision, case, experiment, arm,
  run, model, provider, prompt, endpoint, credential, or tenant identifier.
- An absent declaration is equivalent to evaluation disabled for that
  operation.
- Unknown policy names are diagnosed during CAR verification or runtime
  admission; they are never silently treated as a broader policy.

### Compatibility

Adding evaluation metadata must be optional and binary/source compatible with
generated CARs that do not declare it. No existing operation changes behavior
until an effective runtime policy admits evaluation.

## Runtime Policy Resolution

### Inputs

The effective policy is the intersection of:

- the operation declaration;
- runtime and subsystem policy;
- component trust and execution-capability grants;
- subject and operation authorization;
- invocation-supplied evaluation context;
- confidentiality and redaction policy;
- retention and size limits;
- adapter availability.

Component code must not read these inputs directly.

### Resolver

CNCF supplies a provider-neutral resolver:

~~~scala
trait OperationEvaluationResolver {
  def resolve(
    request: OperationEvaluationAdmissionRequest
  ): Consequence[OperationEvaluationAdmission]
}
~~~

The resolver is obtained through ScopeContext and inherited by child scopes.
The runtime scope supplies a disabled resolver by default. Tests may override
it with a deterministic fake.

The resolver may validate an assignment supplied by an offline experiment
orchestrator. A future resolver may allocate an online arm, but that capability
is outside the first scope.

### Resolution timing

Resolution occurs:

1. after route resolution, input normalization, ingress security, and operation
   authorization;
2. before makeOperationRequest and before operation business logic;
3. exactly once for one logical operation attempt;
4. before any variant-dependent provider or component selection.

Authorization precedes external evaluation resolution so an unauthorized
caller cannot use the evaluation adapter as an information side channel.

### Admission modes

~~~scala
sealed trait OperationEvaluationAdmission

case object EvaluationDisabled

final case class EvaluationAdmitted(
  context: OperationEvaluationContext
)

final case class EvaluationUnavailable(
  limitation: OperationEvaluationLimitation
)
~~~

An unavailable optional admission uses the normal control branch and records a
bounded limitation. An unavailable required experiment invocation fails before
business execution because running it without its declared population and arm
would create invalid experiment evidence.

The requirement is runtime/invocation policy, not an operation declaration:

~~~scala
enum EvaluationParticipationRequirement {
  case Optional
  case Required
}
~~~

## Evaluation Context

### Carrier

ExecutionContext.CncfCore gains one resolved value:

~~~scala
operationEvaluation: OperationEvaluationContext =
  OperationEvaluationContext.disabled
~~~

The context is immutable and operation-scoped. It contains resolved values, not
configuration or mutable service clients.

### Candidate model

~~~scala
final case class OperationEvaluationContext(
  mode: OperationEvaluationMode,
  correlation: Option[OperationEvaluationCorrelation],
  assignment: Option[OperationEvaluationAssignment],
  capture: OperationCapturePolicy,
  requirement: EvaluationParticipationRequirement
)

final case class OperationEvaluationCorrelation(
  corpusRevisionId: Option[String],
  corpusCaseId: Option[String],
  experimentId: Option[String],
  experimentArmId: Option[String],
  experimentRunId: Option[String],
  evaluationExecutionId: String,
  evaluationAttemptId: String
)

final case class OperationEvaluationAssignment(
  variantKey: String,
  executionPlanReference: Option[String]
)
~~~

Identifiers are opaque CNCF correlation values. CNCF does not load or mutate
the corresponding Textus entities.

### Correlation invariants

- An experiment arm requires an experiment and an experiment run.
- An experiment run requires exactly one corpus revision.
- An experiment case execution requires a corpus case belonging to the
  admitted revision.
- An assignment is fixed before execution and cannot change during an attempt.
- Retries retain revision, case, experiment, arm, run, execution, and variant
  identity but receive a distinct attempt identity.
- Identifiers from different admissions must never be merged by matching
  telemetry attributes.
- Empty or partial experiment identity that violates these invariants is
  rejected during admission.

### Nested operations

Evaluation membership is operation-scoped.

- Trace and ordinary observability correlation may propagate to a nested
  operation.
- Corpus-case and experiment membership do not automatically make a nested
  operation a second evaluation observation.
- A nested component or SPI operation participates only when its own operation
  declaration and runtime policy admit it.
- Runtime policy may explicitly propagate the same assignment as an execution
  condition, but it must preserve the parent/child relationship and must not
  create duplicate corpus membership.

### Jobs and retries

When an admitted operation becomes asynchronous, the immutable evaluation
context is serialized into framework-owned Job execution metadata. A resumed or
retried Job must not reallocate its arm.

The serialized form contains identifiers and safe logical references only. It
does not contain prompts, model credentials, raw inputs, or mutable adapter
handles.

## Component Internal DSL

Operation logic receives read-only access through protected CNCF methods:

~~~scala
protected final def evaluation_assignment:
  Option[OperationEvaluationAssignment]

protected final def evaluation_variant:
  Option[String]

protected final def evaluation_execution_plan:
  Option[String]
~~~

The final API may expose only the first method and derive the convenience
methods.

Component logic may:

- use the admitted variant or execution-plan reference;
- use the control/default branch when no assignment exists;
- record domain evidence through a separately admitted bounded evidence API.

Component logic may not:

- allocate or change an arm;
- query global experiment or corpus configuration;
- select an undeclared experiment purpose;
- infer an assignment from trace, Job, request, tenant, or random values;
- write experiment observations directly;
- replace the control branch for an ordinary non-experiment invocation.

An admitted assignment with an unknown or unsupported variant is a
pre-execution admission/configuration failure. It must not silently fall back
to control and contaminate an experiment comparison.

## Execution Pipeline

The proposed common wrapper is OperationEvaluationRunner around the resolved
operation path.

~~~text
resolve route and normalize request
  -> authorize operation
  -> resolve evaluation admission
  -> create operation-scoped ExecutionContext
  -> construct and execute Action
  -> apply canonical association/image bindings
  -> classify canonical terminal outcome
  -> build bounded evaluation fact
  -> submit admitted facts and candidate
  -> attach safe evaluation report to execution metadata
  -> return the original operation response or failure
~~~

Terminal fact construction must observe the canonical response after
framework-owned response bindings. Action-level CallTree details may be
referenced, but they are not the canonical response and do not define
membership.

The wrapper must cover ordinary protocol, HTTP, CLI, Job, and SPI entry routes
that converge on resolved operation execution. It must not be implemented only
in one presentation adapter.

## Outcome Model

Business execution and evaluation recording are separate dimensions.

~~~scala
enum OperationTerminalOutcomeKind {
  case Success
  case Failure
  case TimedOut
  case Cancelled
}

enum EvaluationRecordStatus {
  case Complete
  case Limited
  case Unavailable
}
~~~

Provider- or application-specific quality judgments such as accepted, repaired,
confirmed, escalated, or rejected remain Textus Experiment observations. CNCF
does not add them to the generic terminal outcome.

An operation may also return an application limitation in its normal response.
That remains application data. EvaluationRecordStatus describes completeness
of the auxiliary evaluation evidence and must not overwrite the business
outcome.

## Evaluation Fact

### Candidate model

~~~scala
final case class OperationEvaluationFact(
  factId: String,
  operation: OperationEvaluationOperationIdentity,
  correlation: Option[OperationEvaluationCorrelation],
  assignment: Option[OperationEvaluationAssignment],
  outcome: OperationTerminalOutcomeKind,
  recordStatus: EvaluationRecordStatus,
  requestEvidence: EvaluationEvidence,
  responseEvidence: EvaluationEvidence,
  limitations: Vector[OperationEvaluationLimitation],
  traceId: Option[String],
  executionId: Option[String],
  startedAt: Instant,
  endedAt: Instant
)
~~~

The fact is bounded and provider-neutral. factId is stable for an attempt so a
sink can process at-least-once delivery idempotently.

### Evidence

EvaluationEvidence is one of:

- absent;
- a redacted bounded structural summary;
- a digest with an explicitly scoped evidence reference;
- an adapter-owned sanitized evidence reference.

Raw request and response publication is disabled by default. An evidence
reference is not an unrestricted filesystem path, URL, or provider payload.

The effective redaction policy uses the existing operation request/result field
confidentiality model and may only tighten it.

## Corpus Candidate Contract

Automatic capture produces a candidate fact:

~~~text
admitted terminal operation fact
  -> CorpusCandidate
  -> external normalization, redaction, deduplication, labeling, and review
  -> separately published immutable corpus revision
~~~

A candidate contains:

- a stable candidate id;
- operation identity and logical capture profile;
- terminal outcome and selection reason;
- sanitized request/response evidence or references;
- safe execution correlation;
- redaction and schema-version identity;
- creation time and source digest.

A candidate does not contain:

- corpus revision membership;
- an expected answer accepted without review;
- provider credentials or raw provider payloads;
- unrestricted customer identity;
- a mutable reference to runtime state.

CNCF submits the candidate to a provider-neutral sink. textus-corpus owns any
future candidate registry and the review/promotion lifecycle. The current
textus-corpus contract publishes reviewed immutable revisions and cases; the
candidate API is a separate future adapter surface and is not assumed to exist
today.

## Experiment Contract

The first integration uses an explicit offline assignment supplied by the
experiment orchestrator:

~~~text
one immutable corpus revision
  -> one experiment run
     -> one corpus case and arm assignment
        -> admitted operation execution
           -> terminal evaluation fact
              -> Textus Experiment observation
~~~

Each arm varies only its declared execution-plan reference. The operation sees
the admitted variant or plan reference, not provider/model/prompt/credential
fields.

CNCF does not create or aggregate Experiment, ExperimentArm, ExperimentRun, or
ExperimentObservation entities. An adapter maps the generic fact to
textus-experiment recordObservation input.

Online randomization, traffic percentages, sticky user allocation, exposure
logging, and production experiment routing are deferred. If added later, the
resolver must still produce the same immutable admission before execution.

## Recorder and Sink

~~~scala
trait OperationEvaluationSink {
  def record(
    fact: OperationEvaluationFact
  ): Consequence[Unit]

  def submitCandidate(
    candidate: CorpusCandidate
  ): Consequence[Unit]
}
~~~

The sink is resolved through ScopeContext. CNCF provides disabled and fake
implementations. Product adapters live outside CNCF core or behind a
provider-neutral SPI.

Delivery semantics:

- fact and candidate identifiers are stable and idempotent;
- implementations may use at-least-once delivery;
- retry and buffering policy is runtime-owned;
- telemetry sampling does not sample away an admitted experiment fact;
- a sink cannot modify the operation response;
- sink content is bounded before adapter invocation.

An outbox or durable queue may be introduced later. It is an implementation
choice as long as failure isolation, idempotency, and attribution are retained.

## Failure Isolation

| Condition | Operation behavior | Evaluation behavior |
| --- | --- | --- |
| Evaluation disabled | Execute control/default branch | No resolver side effect beyond disabled resolution; no fact or candidate |
| Optional resolver unavailable | Execute control/default branch | Record bounded limitation when possible |
| Required experiment admission unavailable | Fail before business execution | No invalid experiment execution |
| Unknown admitted variant | Fail before business execution | Attribute configuration/admission failure |
| Business operation failure | Preserve original Consequence failure | Emit failure fact when admitted |
| Timeout or cancellation | Preserve original terminal semantics | Emit corresponding fact when admitted |
| Fact/candidate sink failure after execution | Preserve business response/failure | Mark evaluation report limited/unavailable |
| Telemetry export failure | Preserve business response/failure | Evaluation membership remains authoritative elsewhere |

Capture and observation failure must never convert an otherwise successful
business result into failure. Required pre-execution admission is different:
without a valid assignment, the requested experiment execution has not begun.

## Execution Metadata

RuntimeContext.ExecutionMetadata gains a bounded optional report:

~~~scala
evaluation: Option[OperationEvaluationExecutionReport]
~~~

The report contains:

- mode and admitted/disabled/unavailable state;
- safe correlation ids allowed by publication policy;
- terminal outcome;
- record status;
- limitation codes;
- fact/candidate ids when available.

It contains no raw request, response, prompt, provider output, credentials, or
unrestricted identifiers. executeOperationResponse continues returning only
the canonical OperationResponse; metadata-aware routes may expose a sanitized
projection according to existing policy.

## Observability

Safe candidate attributes are:

~~~text
evaluation.mode
evaluation.purpose
evaluation.profile
evaluation.fact_id
evaluation.record_status
evaluation.outcome
corpus.revision_id
corpus.case_id
experiment.id
experiment.arm_id
experiment.run_id
evaluation.execution_id
evaluation.attempt_id
evaluation.variant
~~~

Every attribute remains subject to authorization, confidentiality, redaction,
retention, and publication policy. Even opaque identifiers may be suppressed
when their linkage is sensitive.

Do not publish:

- raw inputs, outputs, prompts, or expected answers;
- model/provider configuration;
- credentials, account identity, or endpoints;
- unrestricted customer identifiers;
- complete execution plans;
- unbounded errors or stack traces.

Observability may carry admitted correlation for diagnosis. It is downstream
evidence and is not used to reconstruct corpus or experiment authority.

## Determinism

Deterministic evaluation requires:

- immutable corpus revision and case identity;
- immutable experiment, arm, run, variant, and execution-plan assignment;
- a stable runtime execution profile where the comparison requires it;
- assignment before business execution;
- no arm reallocation on Job resume or retry;
- explicit attempt identity;
- deterministic fake resolver and sink behavior;
- stable redaction and fact schema versions.

The operation's own nondeterministic behavior is not hidden. It is an execution
condition or measured variable and must be attributed in the resulting
evidence.

## Authorization and Confidentiality

Three checks remain distinct:

~~~text
subject authorization
  -> may the caller invoke the operation?

component evaluation capability
  -> may this CAR participate in capture or experiment execution?

evaluation admission
  -> is this concrete policy, corpus case, run, and assignment valid now?
~~~

All applicable checks must pass. A declaration is a request for eligibility,
not a grant.

Capture policy must use operation field confidentiality and may impose stricter
redaction. It must not weaken a confidential field to public.

## Executable Specification Targets

The promoted specification must prove at least:

1. An operation without an evaluation declaration behaves exactly as before.
2. A disabled policy invokes neither fact nor candidate sink.
3. Authorization occurs before external evaluation resolution.
4. One admitted assignment is fixed before makeOperationRequest and business
   execution.
5. An ordinary invocation without assignment uses the control/default branch.
6. An unavailable optional resolver uses control and records a limitation.
7. An unavailable required assignment fails before business execution.
8. An unknown admitted variant never silently uses control.
9. SPI, HTTP, CLI, and ordinary protocol paths share the resolved-operation
   evaluation wrapper.
10. Nested operations do not accidentally create duplicate experiment
    observations.
11. Job resume preserves assignment and retry changes only attempt identity.
12. Success, failure, timeout, and cancellation retain original operation
    semantics.
13. Response bindings complete before canonical terminal evidence is built.
14. Sink failure cannot convert business success into failure.
15. Stable fact ids make repeated sink delivery idempotent.
16. Telemetry sampling does not erase admitted experiment facts.
17. Correlation IDs appear only when publication policy permits.
18. Raw request, response, prompt, provider output, credentials, and execution
    plans are absent from default facts and observability.
19. Candidate capture never mutates or claims membership in an immutable corpus
    revision.
20. Fake adapters prove all behavior without Textus services, telemetry
    backends, AI providers, or credentials.

## Proposed Implementation Slices

1. Define declaration, admission, context, outcome, fact, limitation, and
   execution-report types.
2. Add optional evaluation metadata to CmlOperationDefinition and generated
   operation projection.
3. Add disabled/fake resolver and sink capabilities to ScopeContext.
4. Carry immutable OperationEvaluationContext in ExecutionContext.CncfCore.
5. Wrap the common resolved operation pipeline and attach terminal reports to
   RuntimeContext.ExecutionMetadata.
6. Add deterministic executable specifications for admission, branching,
   failure isolation, nested calls, Jobs, and confidentiality.
7. Add provider-neutral SPI adapters for Textus Corpus candidate submission and
   Textus Experiment observation recording.
8. Validate an offline corpus-case/arm execution end to end.
9. Promote reviewed decisions to docs/design and behavior to docs/spec.

## Deferred Questions

- Final CML syntax and generated constructor compatibility strategy.
- Exact package names and value wrappers for opaque identifiers.
- Whether fact delivery initially uses synchronous fake/no-op sinks, an
  asynchronous queue, or an outbox.
- How external acceptance evidence is joined to the generic terminal fact.
- Whether a retained evidence reference uses Blob, Information, or another
  bounded CNCF resource.
- Administration and help projections for effective evaluation policy.
- Production online allocation and exposure logging.
- Retention and deletion policy for evaluation facts and candidates.

## Related Documents

- docs/journal/2026/07/2026-07-21-operation-evaluation-corpus-experiment-observability-handoff.md
- docs/rules/document-lifecycle.md
- docs/notes/execution-determinism-capability-design.md
- docs/notes/scope-context-design.md
- docs/notes/unitofwork-guideline.md
- docs/notes/car-capability-sandbox-design.md
- textus-corpus/src/main/cozy/textus-corpus.cml
- textus-experiment/src/main/cozy/textus-experiment.cml
