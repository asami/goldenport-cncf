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


## Domain Evidence and Hybrid Inference Direction

The Rule architecture is also the natural decision boundary for domain
evidence that originates outside the Rule Engine itself. The long-term model
should distinguish the semantic kind of evidence from its provenance.

Evidence is classified into Fact, Measure, Estimate, and Judgment. Fact is
further classified as DefinedFact, StateFact, ObservedFact, DeclaredFact, and
DerivedFact. Measure includes Similarity, Distance, and Score; embedding-based
similarity is represented as an EmbeddingSimilarity Measure. Estimate includes
StatisticalEstimate, Prediction, and Forecast. Judgment includes AIJudgment and
HumanJudgment.

These categories express different certainty semantics.

- A DefinedFact is established by an applicable definition, regulation,
  contract, catalog, configuration, or other authority.
- A StateFact is a domain proposition projected from operational state, such
  as Entity/Aggregate/DataStore-backed facts. A database row is not itself the
  Fact; the Domain Model interprets stored data as a proposition.
- An ObservedFact records an observation.
- A DeclaredFact records a proposition asserted by a person or external party.
- A DerivedFact is deterministically derived from other Facts by an explicit
  calculation, symbolic rule, or knowledge inference. Traditional expert-system
  reasoning therefore feeds its results back into the Fact space.
- A Measure represents a value obtained by an explicit measurement or
  deterministic comparison/calculation over its inputs. Similarity and distance
  are Measures, not probabilities. Embeddings and vectors are representations,
  not Evidence by themselves; an embedding/vector comparison may produce an
  EmbeddingSimilarity Measure with embedding-model/version/vector-space
  provenance. Measures may be consumed directly by Domain Rules or used as
  input Evidence for Estimates and Judgments.
- An Estimate represents quantified uncertainty produced by an analytical,
  statistical, or predictive model. Its semantics should include the model,
  applicable population/context, uncertainty/calibration information when
  available, and evaluation time.
- A Judgment represents an interpretive decision whose correctness is not
  established merely by deterministic derivation or statistical uncertainty.
  Generative-AI and human judgments belong here by default.

Evidence kind and provenance are orthogonal. Provenance may identify Domain
state, DataStore, contract, regulation, external system, sensor, Knowledge,
BI/Analytics, AI, Human, or another source. Every Evidence value should retain
enough provenance, scope, validity, and version information for the consumer to
understand why it is admissible.

This model allows the Rule architecture to combine traditional symbolic
reasoning and generative-AI reasoning without pretending that they have the
same certainty semantics. Symbolic and knowledge inference return Derived
Facts to the Fact space. BI, statistical, and ML processing can provide
Estimates. Generative AI normally provides AI Judgments. Human semantic
decisions provide Human Judgments. Domain Rules consume the admissible
combination and produce Domain Decisions.

Symbolic inference remains deterministic and explainable at the CNCF
compatibility boundary and may repeatedly derive new Facts to a bounded fixed
point. Generative inference is not modeled as a stronger replacement for
symbolic inference. It supplies semantic interpretation or judgment where
deterministic inference is insufficient.

A Rule may consume Facts, Measures, Estimates, and Judgments according to its declared
evidence requirements. It should be possible for high-impact rules to require
specific evidence kinds or Admission before accepting an AI Judgment. An AI
model's self-reported confidence must not be treated as equivalent to a
statistically calibrated probability. AI-Judgment reliability should instead
be evaluated through external evidence such as AI Audit, Experiment, Admission
outcomes, human correction, and downstream outcomes.

The resulting architecture is intentionally hybrid:

- symbolic/expert-system inference provides deterministic derivation;
- embedding/vector processing provides Similarity Measures and retrieved Evidence;
- BI/statistical/ML analysis provides quantified estimates and predictions;
- Knowledge provides source material and may participate in deterministic
  inference;
- generative AI provides semantic inference and judgment;
- Human judgment remains a first-class source where required;
- Domain Rules combine admissible Evidence into a Domain Decision.


### Evidence Provider Boundaries

Domain Rule evaluation operates on CNCF-owned Evidence semantics and does not
read physical stores or provider-specific engines directly.

StateFact acquisition should normally follow the semantic domain path:
Fact Provider SPI -> Entity Space/EntityRealm -> DataStore -> physical store
such as PostgreSQL. The Entity/Domain model, not the SQL schema, determines the
proposition exposed as a StateFact. This preserves DataStore substitution and
prevents Rule definitions from depending on tables, rows, SQL, or repository
handles.

Collection-oriented analytics may use a separate Analytics Provider that reads
PostgreSQL views, aggregates, materialized views, or another analytical store
directly when set processing through Entity Space would be inefficient. Such a
provider returns Measure or Estimate Evidence, never raw storage semantics.

Knowledge and semantic retrieval should be consumed through an SIE-facing
provider/SPI boundary. SIE remains responsible for its RDF DB and Vector DB
implementation. RDF/knowledge inference can be normalized as DerivedFact;
semantic/vector similarity as EmbeddingSimilarity Measure; and retrieval as
references to retrieved Evidence plus associated Measures. Rule Engine core
does not depend on Fuseki, Chroma, SPARQL, embedding indexes, or another
provider implementation.

Likewise, a traditional expert-system provider may supply deterministic
DerivedFacts, while a generative-AI judgment provider such as Codex supplies
AIJudgment. The exact future SPI decomposition must be reconciled with the
existing RuleEngine and InferenceEngine contracts before ABI expansion.

The current Phase 32 Fact, WorkingMemory, RuleEngine, and InferenceEngine
contracts remain the implemented deterministic foundation. This section
records the architectural direction; it does not retroactively change the
closed Phase 32 ABI. A later phase must reconcile this Evidence model with the
existing Rule model, Candidate-Admission Evidence, JudgmentAction, AI Audit,
Knowledge, and analytics integration before adding new public runtime types.

## Non-Goals

- JESS compatibility, RETE optimization, Drools integration, and other
  concrete external engines;
- business-process execution, BPMN, human-task workflow, or general
  orchestration;
- a rule authoring UI;
- untrusted arbitrary-code execution or sandboxing beyond existing CAR
  capability boundaries;
- replacing authorization policy with rules.
