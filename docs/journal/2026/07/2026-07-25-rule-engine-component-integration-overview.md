# Rule Engine and Component Integration Overview Discussion

Date: 2026-07-25

Status: recorded

## Context

Phase 32 closed the CNCF Rule Engine and Inference Runtime foundation. A
developer-facing infographic was started to show the current feature state, but
the first version placed explicit Rule action admission in the same primary
flow as normal evaluation. That presentation made `fire` look like the required
last step of application-driven rule evaluation.

The discussion separated three concerns that had been combined:

1. the capabilities of the Rule Engine itself;
2. the path by which the Component Framework activates and consumes rules; and
3. concrete application use cases composed from those mechanisms.

## Three-Axis Usage Model

A rule use case is described by three independent axes:

- **Activation**: when and by whom evaluation starts;
- **Rule Family**: what kind of decision is evaluated; and
- **Outcome**: how the evaluation result is interpreted.

Action admission is a fourth, optional post-evaluation step. It is not part of
ordinary rule evaluation.

### Activation

- `Explicit`: application logic invokes a Rule Engine socket.
- `Lifecycle-bound`: an Operation, Entity, UnitOfWork, or Event lifecycle hook
  invokes evaluation automatically.
- `Reactive`: an Event, timer, scheduler, monitor, or Job completion invokes
  evaluation.

### Rule Family

- `Constraint`
- `Calculation`
- `DecisionTable`
- `Derivation`
- `Production`

### Evaluation Outcome

- return calculation or decision values;
- report or reject a constraint;
- return derived Facts;
- return an immutable ActionPlan; or
- return structural explanations.

### Optional Action Admission

A Production Rule may return Operation, Event, Job, or Recommendation plans.
Only an explicit call to `RuleActionAdmission` admits those plans to CNCF
runtime boundaries. Evaluation itself never fires them.

## Current Implementation Position

The Phase 32 Rule Engine foundation is available:

- typed, immutable `RuleSet`, `RuleProgram`, and `WorkingMemory`;
- deterministic Constraint, Calculation, DecisionTable, Derivation, and
  Production evaluation;
- `RuleEngine` and `InferenceEngine` provider-neutral SPI sockets;
- explainable, payload-safe evaluation results; and
- explicit ActionPlan admission through ActionCall, authorized EventBus, and
  JobEngine boundaries.

Application logic can explicitly build Facts and call
`RuleEngineSocket.evaluate`. That is the completed primary use path.

The following Component Framework bindings are not yet implemented:

- automatic RuleSet/RuleProgram selection at Operation or Entity lifecycle
  boundaries;
- automatic runtime-value-to-Fact projection;
- lifecycle Constraint report/reject enforcement;
- Event, timer, scheduler, monitor, or Job-completion activation;
- continuous stateful sessions, temporal windows, deduplication, and cooldown.

Generated datatype/parameter validation is currently separate from
`RuleConstraint`; the existence of Constraint evaluation does not imply
automatic validation interception.

## Comparison With Common Integration Models

The current explicit path resembles stateless embedded decision engines:
application code supplies facts and consumes a result.

The evaluation/admission split also resembles a policy decision point and
policy enforcement point boundary: the engine decides, while the framework
applies or rejects the outcome.

Spring/Bean Validation-style automatic constraints require a framework
interceptor or lifecycle binding. CNCF has the Constraint evaluator but not
that binding.

Stateful production systems and CEP engines retain facts or event windows and
react continuously. CNCF intentionally keeps the Phase 32 evaluator immutable
and request-scoped. Monitoring, scheduling, and event-stream ownership belong
outside the Rule Engine and should activate it through an explicit framework
binding.

## Documentation Decision

Developer overviews are separated as follows:

```text
docs/overview/mechanisms/rules/
  overview.svg
  rule-engine.svg
  component-rule-integration.svg

docs/overview/use-cases/rules/
  overview.svg
  application-rule-evaluation.svg
  lifecycle-rule-constraint.svg
  monitored-condition-event.svg
```

Both viewpoints require `overview.svg` as the primary one-canvas view.
The remaining graphics are optional theme-specific detail.

`rule-engine.svg` ends at `RuleEvaluationResult`. It contains no framework
activation or action firing path.

`component-rule-integration.svg` shows Activation, binding and Fact projection,
Rule Engine invocation, outcome binding, and optional ActionPlan admission. It
also distinguishes available and planned integration points.

The three use-case graphics show one explicit available path and two partial
paths:

- application-driven evaluation: available;
- lifecycle-bound Constraint: evaluator available, automatic binding planned;
- monitored condition to Event: production planning/admission available,
  monitoring and reactive binding planned.

The overviews are explanatory and non-normative. Design, specification, Phase
32 acceptance evidence, and Executable Specifications remain authoritative.

## Final Overview Policy

The discussion established a repository-wide overview convention:

- group graphics by field under both `mechanisms/` and `use-cases/`;
- require one `overview.svg` in every populated field directory;
- use that single graphic as the primary at-a-glance current-state view; and
- add theme-specific graphics only when a lifecycle or mechanism cannot remain
  readable inside the overview.

The Rule graphics therefore move under `mechanisms/rules/` and
`use-cases/rules/`. Each directory has one consolidated overview while the
existing focused graphics remain optional detail.

## References

- `docs/design/rule-engine-inference-runtime.md`
- `docs/spec/rule-engine-inference-runtime.md`
- `docs/phase/phase-32.md`
- `docs/phase/phase-32-checklist.md`
