# Rule Engine and Inference Runtime

## Purpose

CNCF provides an engine-neutral Rule and Inference runtime for domain logic.
It supports constraint, calculation, derivation, decision-table, production,
and inference rules without making CNCF core depend on JESS, RETE, Drools, or
another concrete rule engine.

Rules express domain decisions such as tax calculation, price lookup, tiered
discounts, eligibility, validation, and derived facts. They are not a bypass
for ActionCall, UnitOfWork, authorization, Event, Job, or observability.

## Ownership

- goldenport core owns generic values, `Record`, `Consequence`, and expression
  primitives that do not depend on CNCF runtime semantics.
- CNCF owns rule/inference models, rule evaluation contracts, deterministic
  built-in evaluation, provider SPI, ActionCall/Event/Job action planning, and
  execution diagnostics.
- Providers may implement optimized or external engines behind the CNCF SPI.
  Provider-specific rule languages, execution objects, and payloads must not
  leak through the CNCF public model.

## Rule Model

A `RuleSet` is a named, versioned, immutable evaluation definition. It contains
one or more `Rule` values and declares its input/output fact vocabulary.

The normative foundation-model contract is
`docs/spec/rule-engine-inference-runtime.md`.

- `Fact` is a typed or Record-shaped assertion in a `WorkingMemory`.
- `Rule` has identity, family, priority, conditions, outputs, and explanation
  metadata.
- `Agenda` is the deterministic ordered set of eligible rule activations.
- `RuleEvaluationResult` reports matches, derived facts, calculations,
  validation issues, recommendations, and explanations without side effects.
- `RuleFireResult` reports the admitted action plans and their execution
  outcomes when firing is explicitly requested.

Rule-family semantics are distinct:

- constraint rules return validation issues or structured `Conclusion`
  failures and do not mutate state;
- calculation rules produce values such as tax, totals, prices, or scores;
- derivation and inference rules add explainable facts;
- decision-table rules select rows deterministically from normalized inputs;
- production rules create planned runtime actions only after their conditions
  match.

## Evaluation and Firing

Evaluation is pure with respect to CNCF persistent state. Given the same
RuleSet version, normalized facts, and execution profile, built-in evaluation
MUST produce the same agenda order, selected decision-table rows, derived
facts, values, and explanations.

Firing is separate from evaluation. A production-rule result contains an action
plan, not a direct repository mutation. The runtime admits each action through
the appropriate canonical boundary:

- domain operation through `ActionCall` and UnitOfWork;
- reactive work through Event reception;
- asynchronous work through Job/Task management;
- non-mutating advice as a recommendation output.

Rule actions MUST NOT call EntityStore, repositories, provider runtimes, or
threads directly.

The initial production-plan vocabulary is `Operation`, `Event`, `Job`, and
`Recommendation`. An operation or Job plan identifies an operation selector and
Record-shaped input only; it never retains an `Action`, `ActionCall`, repository
handle, or closure. `RuleActionAdmission` resolves the selector only when a
caller explicitly fires a completed evaluation. Operation plans use the target
component's `ComponentLogic` path, Event plans use `EventBus.publishAuthorized`,
and Job plans use the target component's `JobEngine` with an `ActionTask`.
Recommendations remain evaluation output. Firing stops at the first structured
failure and does not define a cross-plan transaction or workflow; business
process/saga behavior remains outside the baseline.

The built-in decision-table evaluator is a pure selection function. It returns
the selected row, ordered candidate rows, Record-shaped output, and structural
explanation metadata. It does not calculate general expressions, mutate
WorkingMemory, or fire runtime actions; those responsibilities remain separate
Phase 32 work items.

`RuleProgram` admits a decision-table instruction only for a
`RuleFamily.DecisionTable` Rule. Each declared table input column binds to one
immutable `FactId`; the program is invalid when bindings are incomplete,
duplicated, or refer to an empty Fact identifier. Evaluation resolves the bound
facts into normalized decision-table inputs, selects one deterministic row, and
returns a `RuleDecisionTableResult` with table/rule identity, selected-row
identity, Record output, and source Fact identifiers. A missing bound Fact is a
normal no-match condition, not an ambient lookup or an implicit default row.
Decision outputs are deliberately separate from calculation `values`: the
runtime never merges arbitrary table output fields into a shared Record.

The built-in RE-04 evaluator adds a deliberately restricted expression AST for
exact decimal calculation, boolean constraint predicates, and forward
derivation. It is an engine-neutral compatibility baseline rather than an
embedded scripting language: no arbitrary code, reflection, provider call,
repository call, or action can appear in an expression. Calculation results and
reported constraint violations are evaluation values. Forward derivation may
only add immutable Facts to the returned WorkingMemory, with Rule/Fact-only
provenance. It reaches a bounded fixed point in canonical Rule order and does
not persist or fire anything.

## SPI

`RuleEngine` evaluates RuleSets and returns explainable evaluation results.
`InferenceEngine` performs inference-oriented evaluation over supplied facts.
Both SPI contracts receive CNCF `ExecutionContext` and return `Consequence`.
The built-in deterministic evaluator is the compatibility baseline. A provider
selection changes implementation only; it does not change RuleSet identity,
action-boundary rules, or structured diagnostic behavior.

The two contracts deliberately have different semantic breadth.
`RuleEngine.evaluate` performs the complete pure RuleProgram evaluation;
`InferenceEngine.infer` performs derivation-only forward inference and does not
apply calculation or constraint behavior. Both are installed at consumer
`SpiSocket`s by the common `SpiResolver`. This keeps a component's dependency
on the CNCF-owned model while allowing a provider to replace the built-in
evaluator without leaking provider request, response, or failure types.

## Configuration and Projection

RuleSets use a restricted Record-shaped descriptor representation at the
subsystem root. The canonical key is `ruleSets`; `rule_sets` and `rule-sets`
are accepted input aliases. The component-instance `rules` field remains
component configuration and is not interpreted as a RuleSet declaration.

The initial descriptor decoder admits only RuleSet identity/version, declared
input/output fact names, Rule identity/family/priority, declarative condition
and output Records, and explanation metadata. It does not decode expressions,
decision-table rows, executable `RuleProgram` instructions, or action plans.
Those values remain typed component/runtime construction inputs until a later
explicit declarative-language slice fixes their syntax and validation. A
descriptor MUST NOT embed arbitrary host-language code.

`DescribeProjection` and `SchemaProjection` expose declared subsystem
RuleSets as read-only `ruleSets` metadata. `RuleProjection.projectEvaluation`
projects an in-memory evaluation as agenda entries, explanations, calculation
names, decision-table/selected-row identities, constraint summaries, derived-
Fact provenance, and planned action kinds. Neither projection serializes raw
Fact values, calculation or decision output values, action parameters/Event
payloads, provider raw payloads, or secrets.

## Diagnostics

Rule evaluation is observed at the consumer-side Rule SPI socket. `RuleEngine`
and `InferenceEngine` calls create one `spi:rule-engine.evaluate` or
`spi:inference-engine.infer` CallTree span in the calling component context;
providers do not add a duplicate wrapper span. The span and its
`spi.invocation` metric contain only RuleSet identity/version, agenda,
explanation, and planned-action counts, provider/socket selection metadata, and
structured success or `Conclusion` diagnostic information.

Rule firing is observed once at `RuleActionAdmission` as `rule:fire` with
`calltree_kind=rule`. The `rule.execution` runtime metric records firing
operation, RuleSet identity, outcome, elapsed time, and a
`ConclusionDiagnostics`-derived diagnostic key for failures. Firing traces and
`RuleProjection.projectFiring` may identify planned-action count, outcome count,
Event dispatch count, and submitted Job identifiers. They do not serialize
WorkingMemory values, calculation values, action parameters, Event payloads,
recommendation details, provider raw payloads, or secrets.

Nested ActionCall, Event, Job, and Task observability remains canonical; rule
tracing does not replace those boundaries. ActionCall CallTree request and
response summaries are structural by default, so a rule-fired operation cannot
reintroduce plan parameter values into the enclosing trace.

## Non-Goals

- JESS compatibility, RETE optimization, Drools integration, and other
  concrete external engines;
- business-process execution, BPMN, human-task workflow, or general
  orchestration;
- a rule authoring UI;
- untrusted arbitrary-code execution or sandboxing beyond existing CAR
  capability boundaries;
- replacing authorization policy with rules.
