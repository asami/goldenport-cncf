# Phase 32 - Rule Engine and Inference Runtime Checklist

This checklist tracks Phase 32 implementation and evidence. The summary
dashboard is `phase-32.md`.

## RE-01: Freeze the Rule and Inference Contract

Status: DONE

- [x] Select strategy item 9.27 as the Phase 32 owner.
- [x] Open the Phase 32 dashboard and checklist.
- [x] Define the engine-neutral Rule/Inference model and SPI boundaries.
- [x] Define deterministic evaluation, separated firing, and ActionCall/Event/
  Job admission rules.
- [x] Define concrete-engine, process-engine, UI, and sandboxing deferrals.

Acceptance evidence:

- `docs/design/rule-engine-inference-runtime.md` defines the normative model
  before implementation begins.
- No human-confirmation gate is required for the Phase 32 baseline.

## RE-02: Foundational Models

Status: DONE

- [x] Add RuleSet, Rule, Fact, WorkingMemory, Agenda, and result models.
- [x] Define stable identity, version, priority, and explanation semantics.
- [x] Add Given/When/Then and property-based executable specifications.

Acceptance evidence:

- `src/main/scala/org/goldenport/cncf/rule/RuleModel.scala` provides the
  immutable engine-neutral foundation and deterministic collection factories.
- `docs/spec/rule-engine-inference-runtime.md` defines the normative foundation
  contract.
- `RuleModelSpec` verifies declaration-order independence with ScalaCheck and
  rejects duplicate, empty, or inconsistent foundation identities.

## RE-03: Decision Tables

Status: DONE

- [x] Implement deterministic row matching and tie-breaking.
- [x] Add price-list, tax-rate, and tiered-discount executable drivers.
- [x] Verify stable result and explanation ordering.

Acceptance evidence:

- `src/main/scala/org/goldenport/cncf/rule/DecisionTable.scala` provides the
  pure built-in evaluator with canonical candidate selection.
- `DecisionTableSpec` verifies product pricing, consumption-tax lookup,
  half-open discount tiers, precedence, reordered rows, no-match behavior, and
  invalid numeric/table inputs.
- Exact numeric acceptance covers Scala and JVM decimal/integral values while
  floating-point input remains a structured failure.

## RE-04: Calculation, Constraint, and Derivation

Status: DONE

- [x] Add restricted calculation expressions.
- [x] Add side-effect-free constraint outputs and structured failures.
- [x] Add deterministic forward derivation over WorkingMemory.

Acceptance evidence:

- `BuiltinRuleEvaluator.scala` provides an engine-neutral restricted AST for
  exact decimal arithmetic, boolean predicates, and immutable forward
  derivation. It has no host-language code, store, provider, or action path.
- `BuiltinRuleEvaluatorSpec` verifies consumption-tax calculation, report and
  reject constraints, bounded fixed-point derivation, and order-invariant
  evaluation with ScalaCheck.
- The static specification fixes missing-Fact, no-overwrite, exact numeric,
  constraint, and derivation provenance semantics.

## RE-05: SPI Resolution

Status: DONE

- [x] Define and install RuleEngine and InferenceEngine sockets.
- [x] Preserve provider-neutral models and Consequence failure behavior.
- [x] Verify built-in and provider resolution paths.

Acceptance evidence:

- `spi/rule/engine/RuleEngine.scala` provides canonical RuleEngine and
  InferenceEngine contracts, single/set sockets, and opt-in built-in providers.
- `RuleEngineSpec` verifies built-in evaluation, independent socket
  installation, alternate provider substitution, structured failure behavior,
  and derivation-only inference semantics.
- Provider selection remains owned by the existing `SpiResolver`; the rule
  contract exposes no provider-specific evaluation or error type.

## RE-06: Production Action Admission

Status: DONE

- [x] Represent actions as plans, not direct mutations.
- [x] Admit operations, Events, Jobs, and recommendations through canonical
  runtime boundaries.
- [x] Verify authorization, UnitOfWork, Job/Task, and Event semantics remain
  active.

Acceptance evidence:

- `RuleActionPlan.scala` provides immutable operation, Event, Job, and
  recommendation plans. Plans contain selectors and Record-shaped inputs only;
  they do not retain ActionCall, repository, provider, or thread handles.
- `RuleActionAdmission` resolves operation plans through the target
  `ComponentLogic`, publishes events through `EventBus.publishAuthorized`, and
  submits Job plans as `ActionTask` work through the target JobEngine.
- `RuleActionAdmissionSpec` verifies ActionCall execution, Event authorization
  and dispatch, Job tracking, and non-mutating recommendation outcomes.

## RE-07: Descriptor and Projection

Status: DONE

- [x] Add restricted Record-shaped RuleSet descriptor decoding.
- [x] Add read-only RuleSet, agenda, and explanation projections.
- [x] Reject arbitrary executable-code embedding.

Acceptance evidence:

- `GenericSubsystemDescriptor` accepts subsystem-root `ruleSets` plus input
  aliases and leaves component-instance `rules` configuration untouched.
- `RuleProjection` exposes declaration metadata and value-redacted evaluation
  agenda/explanation diagnostics; `DescribeProjection` and `SchemaProjection`
  expose declared RuleSets only on subsystem targets.
- `RuleSetDescriptorSpec`, `RuleSetSubsystemDescriptorSpec`, and
  `RuleSetProjectionIntegrationSpec` prove decoder, projection, duplicate, and
  payload-redaction behavior.

## RE-08: Diagnostics

Status: DONE

- [x] Add CallTree and observability records for evaluation and firing.
- [x] Add sanitized Job/Task diagnostic linkage for fired plans.
- [x] Verify confidential fact values are not emitted by default.

Acceptance evidence:

- Consumer-installed `RuleEngine` and `InferenceEngine` services emit one
  payload-safe SPI span and `spi.invocation` metric per evaluation/inference
  call without changing provider selection.
- `RuleActionAdmission` emits one `rule:fire` span and `rule.execution` metric;
  `RuleProjection.projectFiring` exposes only structural action outcomes and
  submitted Job identifiers.
- `RuleEngineSpec` and `RuleActionAdmissionSpec` prove CallTree, metrics,
  structured failure diagnostics, and the absence of WorkingMemory/action-plan
  values from traces.

## RE-09: Driver and Documentation

Status: DONE

- [x] Complete tax and pricing-table domain drivers.
- [x] Update developer documentation and static specifications.
- [x] Run focused and cross-boundary regression suites.

Acceptance evidence:

- `RuleEngineSpec` evaluates product-price and jurisdictional tax tables through
  a consumer-installed RuleEngine socket; deterministic table output is a
  typed `RuleDecisionTableResult`, not an ad hoc component utility result.
- `cncf-developer-guide.md` and the component developer index document typed
  RuleProgram construction, socket use, descriptor boundaries, and ActionCall
  application rules.
- Focused Rule Engine regression covers 30 executable specifications across
  evaluator, table, descriptor, projection, SPI, and action-admission
  boundaries.

## RE-10: Closure

Status: DONE

- [x] Run final review and resolve actionable findings.
- [x] Validate every included repository and commit the phase work.
- [x] Update strategy/phase status and record deferred follow-ups.

Acceptance evidence:

- Final review confirmed the RuleProgram decision-table boundary, payload-safe
trace behavior, descriptor/program separation, and documentation consistency;
missing-Fact/no-match and incomplete-binding behavior are executable SPI
coverage.
- `sbt --batch test` passed with 1,835 successful tests and no failures;
`sbt --batch Test/compile`, focused Phase 32 specs, naming scans, and
`git diff --check` also passed.
- Phase 32 closes with concrete-engine providers, executable descriptor
languages, business-process orchestration, BPMN/human tasks, rule-authoring UI,
and untrusted-code sandboxing deferred to their explicit future items.
