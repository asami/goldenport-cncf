# Operation Evaluation, Corpus, Experiment, and Observability Handoff (2026-07-21)

status=handoff
updated_at=2026-07-21
tag=operation, evaluation, corpus, experiment, observability, execution-context

## Position of This Record

This journal records a framework-extension direction for corpus-first AI and
operation evaluation. It is non-normative. It does not add a CNCF operation
property, change `executeOperationResponse`, or establish a dependency on
`textus-corpus` or `textus-experiment`.

Before adoption, the selected contract must be promoted into CNCF design and
static specification documents and supported by deterministic executable
specifications.

## Driver

CNCF already performs common work around an operation execution, including
execution context management and observability. Textus applications need to
evaluate selected operations continuously, including A/B comparisons of AI
profiles, models, prompts, providers, and component configurations.

A weak design reconstructs evaluation data later from observability records.
That loses deterministic input identity when traces are sampled, redacted,
expired, incomplete, or mixed across configuration changes. Textus should make
the relationship explicit from the beginning:

```text
Corpus revision / case
  -> Experiment / arm / run
     -> operation execution
        -> observability and evaluation observations
```

`CorpusRevision` is immutable evaluation input. `Experiment` fixes the corpus
revision, comparison arms, metrics, and execution conditions. Observability is
downstream runtime evidence; it must never be the authority from which corpus
membership or experiment membership is inferred.

## Proposed Operation Declaration

An operation should be able to declare that it is eligible for corpus capture
and experiment execution. The declaration expresses intent and policy class;
it does not embed a tenant-specific corpus ID, experiment ID, secret, model,
or provider configuration in the CAR.

Illustrative shape only:

```yaml
evaluation:
  corpus:
    capture: candidate
    profile: route-resolution
    outcomes: [success, failure]
    sampling: representative
    redaction: default
  experiment:
    eligible: true
    purpose: route-resolution
```

The runtime configuration binds these logical names to a concrete collection
policy, corpus registry, experiment, arm-allocation policy, and authorization.
An application can therefore use the same operation in normal mode, capture
mode, or an admitted A/B experiment without changing its business logic.

## Proposed Runtime Boundary

The common execution path around `executeOperationResponse` should own the
integration. A CAR operation implementation remains responsible only for its
business request and response.

1. Before execution, the runtime resolves whether capture or experiment
   participation is admitted for the operation and execution context.
2. If an experiment is admitted, it resolves a stable experiment, arm, and run
   assignment before invoking the operation.
3. The operation executes normally through the existing common pipeline. It
   may read the admitted assignment from its execution context and select an
   implementation branch, such as an AI profile, prompt revision, downstream
   component operation, or algorithm. The operation does not allocate an arm,
   change the experiment, or infer a branch from raw telemetry.
4. At a terminal outcome, the runtime emits bounded, normalized evaluation and
   observability facts. It may submit a `CorpusCandidate`, but it does not
   mutate an immutable corpus revision.
5. The response remains the operation's canonical response. Capture,
   experiment recording, and observability failures become attributable
   limitations and must not silently change a successful business outcome.

An operation that branches by arm must retain a control/default branch for an
execution with no admitted experiment assignment. It may expose only the
declared arm behavior; runtime policy remains the authority for whether that
branch is admitted for a particular execution.

The runtime should attach correlation metadata to admitted observability facts:

```text
corpusRevisionId
corpusCaseId
experimentId
experimentArmId
experimentRunId
```

The metadata must be subject to the existing confidentiality, redaction,
authorization, retention, and CallTree publication policies. It must not
contain raw inputs, outputs, prompt content, credentials, or unrestricted
customer identifiers.

## Corpus Capture Lifecycle

Automatic capture must create a candidate rather than automatically treating a
trace as an evaluation case:

```text
operation result / feedback / failure observation
  -> CorpusCandidate
  -> normalization, redaction, deduplication, labeling, validation
  -> approved immutable CorpusRevision
```

Candidate selection may prioritize failures, user corrections, high latency or
cost, low-quality outcomes, representative sampling, and large A/B deltas.
Promotion is owned by `textus-corpus` policy and is separate from CNCF's
operation execution responsibility.

## A/B Experiment Semantics

One experiment references exactly one immutable corpus revision for a
comparison. Each arm varies only declared dimensions, such as an AI profile,
model, prompt revision, provider, or component configuration. Each run records
the assigned arm before execution, and each observation refers both to its
corpus case and experiment run.

This permits deterministic comparisons of quality, latency, cost, failure
rate, and explicit limitations. A new corpus case or changed expected result
creates a new corpus revision; an experiment never rewrites its input corpus.

## Required Executable Evidence

Before this direction becomes available, CNCF should demonstrate:

1. an operation declaration can be resolved to an effective runtime capture
   and experiment policy without exposing a component to global configuration;
2. a disabled policy produces no capture or experiment side effect;
3. an admitted experiment assigns one stable arm before operation execution;
4. an operation can select a declared implementation branch only from the
   pre-assigned admitted arm, while an execution without an assignment uses its
   control/default branch;
5. terminal success, failure, timeout, cancellation, and limitation outcomes
   retain their original operation semantics while producing bounded,
   attributable evaluation facts where admitted;
6. observability facts retain the declared corpus/experiment correlation IDs
   without requiring raw payload publication;
7. capture failure cannot convert an otherwise successful operation response
   into a failure; and
8. deterministic fake corpus/experiment/observability adapters prove the
   behavior without live providers, telemetry backends, or AI credentials.

## Non-Goals

- No direct `textus-corpus` or `textus-experiment` dependency in CAR business
  logic.
- No generic operation access to raw observability records or global telemetry
  configuration.
- No automatic promotion of production traces into immutable evaluation corpus
  revisions without policy-controlled validation.
- No provider-, model-, prompt-, or application-specific type in CNCF core.
- No change to canonical operation response semantics when auxiliary capture or
  observation handling fails.

## Promotion Order

1. Audit the existing operation-definition metadata and execution-context /
   `executeOperationResponse` extension points.
2. Define provider-neutral operation evaluation declaration and effective
   runtime-policy contracts.
3. Define typed, bounded correlation and candidate-capture facts at the
   execution boundary.
4. Add deterministic executable specifications for policy resolution, arm
   assignment, terminal outcome handling, correlation, and failure isolation.
5. Let `textus-corpus` own candidate promotion and immutable corpus revision
   management, and let `textus-experiment` own experiment/arm/run aggregation.
6. Migrate application operations only after the common mechanism is proven.
