# Rule Engine and Inference Runtime Foundation

Status: normative foundation contract

## Scope

This specification fixes the engine-neutral value-model, decision-table, and
restricted built-in evaluation contracts introduced by Phase 32 RE-02 through
RE-04. It covers RuleSet identity, immutable fact storage, agenda ordering,
evaluation explanation structure, deterministic decision-table selection, and
pure calculation/constraint/derivation evaluation and the first production
action-admission contract. It does not specify general host-language expression
evaluation or arbitrary executable-code embedding.

## RuleSet Identity And Ordering

A `RuleSet` has a non-empty `RuleSetId`, a non-empty version, and one or more
uniquely identified Rules. It declares uniquely named input and output fact
vocabularies.

Rule declaration order is not semantic. The canonical rule order is:

1. descending numeric Rule priority;
2. ascending `RuleId` when priorities are equal.

Duplicate or empty Rule identifiers, RuleSet identity values, or declared fact
vocabulary names are invalid input. CNCF MUST return a structured
`Consequence` failure and MUST NOT choose an implicit winner.

## Facts And WorkingMemory

A `Fact` has a stable `FactId`, a fact name, a scalar or Record-shaped value,
optional type information, attributes, and provenance. Provenance may identify
asserted, imported, calculated, or derived origin and references only stable
Rule/Fact identifiers.

`WorkingMemory` is immutable. A successful factory construction requires
unique, non-empty Fact identifiers and non-empty Fact names. Its canonical
order is ascending `FactId`. Replacing a Fact through `putC` retains the same
Fact identity, restores canonical order, and returns a structured
`Consequence` failure when the replacement is invalid.

## Agenda

An `Agenda` contains eligible `RuleActivation` values. An activation has a
stable activation identifier, Rule identifier, Rule priority, matched Fact
identifiers, and optional explanation.

Agenda ordering is deterministic:

1. descending Rule priority;
2. ascending `RuleId` when priorities are equal;
3. ascending activation identifier when Rule identifiers are equal.

Duplicate or empty activation identifiers and empty Rule identifiers are
invalid input. A factory failure is represented as `Consequence`, not an
implicit ordering or replacement decision.

## Explanations And Evaluation Results

`RuleExplanation` refers to Rules and Facts by stable identifiers. Its source
and result Fact references are normalized in ascending identifier order. An
immutable `RuleEvaluationResult` combines RuleSet identity, WorkingMemory,
Agenda, normalized explanations, and computed value metadata.

Explanations are structural diagnostic values. They must not require raw input
Fact values. Projection/redaction policy and rule evaluation behavior are
specified by later Phase 32 work items.

## Decision Tables

A `DecisionTable` has a non-empty identifier, one or more uniquely named input
columns, and one or more uniquely identified rows. A row has a priority,
column conditions, a Record-shaped output, and optional explanation metadata.
Row declaration order is not semantic.

The initial condition vocabulary is intentionally small:

- `Any`: the column does not constrain the row;
- `Equals`: the supplied Rule Fact value must equal the declared value; and
- `NumberRange`: a numeric value must be in the half-open interval
  `[minimumInclusive, maximumExclusive)`. Either bound may be absent.

`NumberRange` accepts `BigDecimal`, `java.math.BigDecimal`, and integral JVM or
Scala number values. Floating-point values and text-to-number coercion are not
accepted in this contract, so price and tax outcomes are not influenced by
binary floating-point conversion or locale parsing.

Missing input for a constraining condition means that row does not match. A
missing input for `Any` remains valid. Invalid table definitions, unknown
condition columns, duplicate identifiers, or an invalid numeric range return a
structured `Consequence` failure.

All matching rows are reported in canonical selection order:

1. descending row priority;
2. descending specificity, where each non-`Any` condition contributes one;
3. ascending row identifier.

The first candidate is the selected row. A no-match evaluation succeeds with
no selected row and an empty candidate list. It is not an error and must not
fall back to declaration order.

## Restricted Calculation Expressions

The built-in evaluator accepts a closed `RuleExpression` AST. It is not a
host-language evaluator and has no reflection, function calls, I/O, provider
access, repository access, or action nodes.

The initial vocabulary is:

- literal Rule Fact values and `FactId` references;
- exact decimal `Add`, `Subtract`, and `Multiply`;
- equality and exact numeric comparisons;
- boolean `And`, `Or`, and `Not`.

Numeric arithmetic and comparison accept the same exact decimal/integral
values as decision tables. Floating-point input and text-to-number coercion
are rejected as structured `Consequence` failures. Division, rounding policy,
date/time operations, functions, and arbitrary extension code are deferred.

A `RuleProgram` binds calculation, decision-table, constraint, derivation, and
production instructions to
Rules already declared in one `RuleSet`. Each instruction references exactly
one Rule with the matching family; an instruction cannot silently select a
different Rule or family. Calculation output names, constraint codes, and
derived Fact identifiers are non-empty and unique. Rule declaration order and
program declaration order are not semantic: instructions use canonical Rule
priority and Rule identifier order.

Calculation evaluation returns `RuleCalculationResult` values and a
Record-shaped `RuleEvaluationResult.values` projection. Calculations do not
modify `WorkingMemory`. Calculation explanations contain only structural Rule
and source Fact identifiers plus an optional declared summary.

## Constraints

A constraint predicate expresses a condition that must evaluate to `true`.
Its false result has no side effect and has one of two explicit modes:

- `Report` (the default) appends a normalized
  `RuleConstraintViolation` to the successful evaluation result; or
- `Reject` returns a structured `Consequence` policy-violation failure.

Constraint output does not mutate `WorkingMemory`, fire actions, or create a
component-local error structure. A missing Fact or a non-boolean predicate is
an invalid evaluation input and returns a structured `Consequence` failure.

## Forward Derivation

A derivation has a boolean `when` expression and an output Fact definition.
When its predicate is true, the built-in evaluator creates an immutable Fact
whose provenance is `Derived`, identifies the producing Rule, and contains the
structural Fact references used by the derivation.

Derivation uses canonical Rule order and repeats pure passes to a fixed point.
A missing Fact in a derivation predicate means that the rule is not yet
eligible, not an error. The pass count is bounded by the number of declared
derivations plus one because each derivation owns one unique output Fact id and
may add that Fact at most once. Existing equivalent Facts are idempotent;
attempting to overwrite a non-equivalent Fact is a structured policy failure.

Derived Facts are appended only to the evaluation's returned immutable
`WorkingMemory`. They do not persist to an EntityStore and do not fire actions.
Derivation explanations expose Rule and Fact identifiers and optional declared
summaries, never raw Fact values by default.

## Production Action Plans And Admission

A production instruction has a boolean predicate and one or more immutable
`RuleActionPlan` values. The plan vocabulary is intentionally restricted:

- `Operation`: an operation selector plus a Record-shaped parameter value;
- `Event`: a name, kind, Record-shaped payload, safe string attributes, and
  persistence request;
- `Job`: an operation selector plus Record-shaped parameters for asynchronous
  Job submission; and
- `Recommendation`: non-mutating advice with structural details.

Plan identifiers are non-empty and unique across a RuleProgram. A plan's Rule
identifier MUST equal the enclosing production Rule identifier. Operation and
Job selectors, Event names/kinds, and recommendation summaries are non-empty.
Program declaration order is not semantic: production Rules use canonical Rule
order and a matched Rule's plans use ascending plan identifier order.

Production evaluation is pure. A true predicate appends plans to
`RuleEvaluationResult.actionPlans` and emits a `planned` explanation. A missing
Fact means that the production is not eligible; it is not an error. Evaluation
does not resolve an operation, create an ActionCall, publish an Event, submit a
Job, mutate a store, or start a thread.

Firing is explicit through `RuleActionAdmission`. It resolves an `Operation`
or `Job` selector through the subsystem `OperationResolver`, creates the target
component's normal operation request, and delegates to the existing
`ComponentLogic` execution path. Therefore ActionCall authorization,
UnitOfWork, observability, and normal command execution policy remain active.
Events are sent only through `EventBus.publishAuthorized`; event publish and
dispatch capability checks remain active. Job plans create `ActionTask` values
and submit them through the target component's `JobEngine`; they return the
tracked Job identifier. Recommendations create no runtime work.

Firing admits plans sequentially and stops at the first structured
`Consequence.Failure`. Phase 32 does not define cross-plan atomicity, saga
compensation, or a process-engine transaction. Such orchestration remains
outside this Rule Engine baseline.

## Rule And Inference SPI

`RuleEngine` and `InferenceEngine` are provider-neutral CNCF SPI contracts.
Both take immutable `RuleSet`, `RuleProgram`, and `WorkingMemory` request
values and return `Consequence`; they do not receive persistence handles,
ActionCall handles, or provider-specific execution values.

- `RuleEngine.evaluate` runs the complete built-in evaluation contract:
  constraints, calculations, decision tables, derivations, and production-plan
  selection.
- `InferenceEngine.infer` runs derivation-only inference. It does not evaluate
  calculations or constraint predicates, so a rejecting constraint cannot
  change inference-only semantics.

The built-in engine implementations are baseline providers, not a hard-coded
runtime singleton. Components request a `RuleEngineSocket` or
`InferenceEngineSocket`; the existing `SpiResolver` selects and installs a
provider. Socket-set variants support normal CNCF component selection. An
alternate provider must preserve the immutable request/result models and any
returned `Consequence.Failure`; it must not replace failures with
provider-specific error values.

### Decision-Table Program Instruction

A `RuleProgram` may contain one `RuleDecisionTable` instruction for a declared
`decision-table` Rule. It carries a `DecisionTable` plus bindings from every
declared input column to one non-empty `FactId`. Bindings MUST be complete and
unique. The instruction is invalid when a Rule has another family, a binding is
missing or duplicated, or a bound Fact identifier is empty.

Evaluation builds inputs only from the supplied immutable WorkingMemory. A
missing bound Fact produces a successful no-match; it MUST NOT trigger a store,
provider, or ambient configuration lookup. The result records Rule/Table
identity, optional selected-row identity, output Record, and source Fact
identifiers as `RuleDecisionTableResult`. Decision outputs MUST NOT be merged
implicitly into calculation `values`. Read-only projections expose only the
identity and source-Fact structure, never decision output values.

## Restricted Descriptor And Read-Only Projection

A subsystem descriptor may declare RuleSets only at its root with the canonical
`ruleSets` key. `rule_sets` and `rule-sets` are accepted input aliases. The
component-instance `rules` key is unrelated component configuration and MUST
NOT be decoded as a RuleSet.

Each descriptor RuleSet contains a non-empty `id`, non-empty `version`, and a
non-empty `rules` sequence. Each Rule contains a non-empty `id`, a recognized
family token, optional integral `priority`, optional `conditions` and `outputs`
Records, and optional explanation metadata. Duplicate RuleSet id/version
identities are invalid and return a structured `Consequence` failure.
Present `conditions`, `outputs`, `metadata`, and explanation-attributes values
MUST be Records; a scalar value is a structured descriptor failure and is never
silently discarded.

The descriptor does not define an executable rule language in this phase. It
MUST NOT decode host-language code, reflection handles, provider handles,
`Action`, `ActionCall`, storage handles, expressions, decision-table rows, or
production action plans. Typed `RuleProgram` construction remains the only
baseline path for those executable instructions.

`DescribeProjection` and `SchemaProjection` expose the declared RuleSets only
on subsystem targets through `ruleSets`. Component targets do not duplicate
subsystem-owned RuleSets. `RuleProjection.projectEvaluation` exposes agenda,
explanations, structural calculation/decision-table/constraint/derivation
metadata, and planned action kinds. It MUST NOT expose raw WorkingMemory Fact
values, calculation or decision-table output values, action parameters, Event
payloads, provider state, or secrets.

## Diagnostics And Firing Trace

The common SPI resolver wraps installed `RuleEngine` and `InferenceEngine`
services at the consumer socket. Evaluation and inference therefore emit one
caller-context SPI CallTree span labelled `spi:rule-engine.evaluate` or
`spi:inference-engine.infer`, plus the existing `spi.invocation` runtime metric.
The trace records only contract/operation/provider/socket metadata, RuleSet
identity/version, structural counts, outcome, duration, and a structured
diagnostic projection when evaluation fails. It MUST NOT record WorkingMemory
Fact values, provider request/response payloads, or secrets.

Explicit plan admission emits one `rule:fire` CallTree span and a
`rule.execution` runtime metric. The trace and metric identify RuleSet,
planned-action count, outcome, duration, resulting Job identifiers, and a
`ConclusionDiagnostics`-derived diagnostic key on failure. Nested ActionCall,
Event, Job, and Task traces remain their canonical execution evidence. Their
request and response summaries MUST remain structural by default; a fired Rule
plan MUST NOT cause action parameters or Event payload values to appear in the
Rule trace. `RuleProjection.projectFiring` exposes structural outcome metadata
only.

## Acceptance Evidence

`src/test/scala/org/goldenport/cncf/rule/RuleModelSpec.scala` proves:

- RuleSet declaration order cannot change canonical rule order;
- WorkingMemory and Agenda ordering are deterministic;
- duplicate identifiers fail structurally; and
- explanation provenance normalization uses stable identifiers.

`src/test/scala/org/goldenport/cncf/rule/DecisionTableSpec.scala` proves:

- price-list, tax-rate, and tiered-discount selections;
- canonical selection under reordered table rows; and
- deterministic no-match and invalid-definition behavior.

`src/test/scala/org/goldenport/cncf/rule/BuiltinRuleEvaluatorSpec.scala`
proves:

- exact-decimal consumption-tax calculation without WorkingMemory mutation;
- report and reject constraint semantics;
- deterministic forward derivation to a fixed point across canonical Rule
  ordering; and
- structured failures for invalid program family references and floating-point
  arithmetic; and
- matching production plans remain immutable evaluation output until explicit
  firing.

`src/test/scala/org/goldenport/cncf/rule/RuleActionAdmissionSpec.scala`
proves operation plans execute through ActionCall, Event plans pass the
authorized EventBus boundary, Job plans return tracked Job identifiers,
recommendations remain non-mutating, and Rule firing records payload-safe
CallTree/metric diagnostics.

`RuleSetDescriptorSpec`, `RuleSetSubsystemDescriptorSpec`, and
`RuleSetProjectionIntegrationSpec` prove restricted RuleSet descriptor loading,
component-rule separation, duplicate identity rejection, payload-safe agenda
and explanation projection, and subsystem Describe/Schema projection exposure.

`src/test/scala/org/goldenport/cncf/spi/rule/engine/RuleEngineSpec.scala`
proves:

- built-in rule evaluation through the provider-neutral contract;
- product-price and consumption-tax decision tables execute through the
  installed provider-neutral RuleEngine socket;
- RuleEngine and InferenceEngine installation through independent SPI sockets;
- alternate provider substitution without request/result model changes; and
- preservation of structured constraint rejection plus derivation-only
  inference semantics; and
- consumer-side, payload-safe SPI CallTree and metric observation.
