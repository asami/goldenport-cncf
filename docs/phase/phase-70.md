# Phase 70 - Post-Assembly Component Activation

status=planned
planned_at=2026-08-16
depends_on=[Phase 55](phase-55.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 70 Checklist](phase-70-checklist.md)
consumer=textus-bok Phase 8

## Phase Plan Gate

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- estimated_at_recommended_effort: 4–6h
- recommended_minimum_effort: high
- runtime_suitability: re-evaluate in the Phase execution task
- source: Textus BoK configured KnowledgeSource bootstrap requirement

## Purpose

Provide one explicit component activation lifecycle after complete Subsystem
assembly and runtime-service binding but before managed server readiness.
Components with startup work that depends on other installed components must
not perform that work during `Component.initialize`, infer readiness from
construction order, or require an external HTTP call after the server becomes
visible.

Textus BoK Phase 8 is the first consumer. It requires SIE and the complete
component graph before it can publish configured KnowledgeSource generations,
and control-center must observe startup failure rather than a ready server with
an empty or degraded catalog.

## Dependency and scheduling

Phase 70 is a separately selectable supplier branch after the closed Phase 55
configuration-binding contract. It does not renumber or block the independent
Phase 63--69 plans. Only one CNCF Phase may be active at a time.

Phase 70 consumes the existing component bootstrap, context injection,
runtime-service binding, typed configuration, execution profile, shutdown, and
structured diagnostic contracts. It does not reopen their accepted semantics.

## Selected direction

- Add one typed opt-in activation capability for a Component. Activation is
  distinct from object construction and `Component.initialize`.
- Assemble, bootstrap, inject, and bind the complete admitted component set
  before invoking any activation callback.
- Invoke activation exactly once in deterministic admitted component order for
  managed server startup and complete every required activation before
  readiness is reported. A bound listener, when required by the host lifecycle,
  must not expose the application as ready or admit ordinary application
  traffic before activation completes.
- Supply an activation context derived from the assembled Subsystem and its
  typed runtime configuration/capabilities. Do not expose CLI parsing,
  control-center internals, credentials, or mutable global configuration.
- Let a component use normal CNCF component/API selection and
  ExecutionContext-aware contracts during activation; do not provide direct
  implementation-object lookup or bypass component boundaries.
- A required activation failure fails startup, prevents readiness, emits one
  structured redacted diagnostic, and enters ordinary Subsystem cleanup.
- Bound activation by explicit timeout/cancellation policy and preserve
  deterministic sequential outcome reporting. No detached background task may
  later convert a failed startup into ready state.
- Direct `command` and `client` modes do not implicitly run server activation.
  Test and emulator use require explicit runtime-test/assembly admission and
  deterministic cleanup.
- Preserve existing components unchanged: absence of the opt-in capability has
  no behavior, readiness, or compatibility effect.

## Scope

1. Freeze activation lifecycle states, callback/API shape, execution modes,
   ordering, timeout, failure, readiness, observability, and cleanup contracts.
2. Insert activation after `_prepare_components_c` has bootstrapped/injected
   components and bound runtime services, at the final managed assembly point
   before server readiness.
3. Add structured activation result/diagnostic evidence with safe component,
   phase, status, duration, and conclusion facets.
4. Prove normal success, multiple-component order, dependency access,
   once-only behavior, no-opt-in compatibility, failure, timeout, cleanup,
   server readiness, direct-command exclusion, and controlled-test behavior.
5. Validate Textus BoK Phase 8 as the representative downstream consumer.

## Non-goals

- A configuration language for invoking arbitrary Operations at startup.
- A general Workflow, Job, scheduler, service-container, health-check, or
  deployment orchestration framework.
- File watching, hot reload, restart control, rolling deployment, distributed
  activation, leader election, or cluster readiness.
- Allowing components to depend on initialization order or access another
  component's implementation class directly.
- Moving application-specific KnowledgeSource, SIE, Glossary, or profile logic
  into CNCF.
- Running managed server activation implicitly for every CLI command.

## Work stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CA70-01 | Contract and failing-first evidence | Exact lifecycle point, API, ordering, mode, readiness, timeout, failure, cleanup, and diagnostics are frozen. | planned |
| CA70-02 | Runtime implementation | Opt-in components activate once after assembly/runtime binding and before readiness through normal component boundaries. | planned |
| CA70-03 | Managed runtime and consumer acceptance | Server success/failure/cleanup plus Textus BoK startup bootstrap acceptance pass without command-mode regression. | planned |
| CA70-04 | Validation and closure | Focused/full tests, documentation, review, version evidence, and release commit converge. | planned |

## Acceptance

- A representative component can resolve and call another admitted component
  through its public CNCF boundary during activation.
- Activation cannot run before the complete component set is assembled and
  runtime services are bound.
- Required activation completes exactly once before the managed server becomes
  ready; failed or timed-out activation prevents readiness and triggers cleanup.
- Two activating components run in deterministic order with bounded diagnostic
  evidence and no detached task or duplicate callback.
- Existing non-activating components and direct command/client execution retain
  their current behavior.
- Controlled tests can explicitly admit activation without weakening runtime
  test descriptors, fixed-user/authenticated-user wiring, or production
  security.
- Textus BoK can publish an explicitly configured development KnowledgeSource
  through SIE before control-center observes readiness, and invalid metadata
  produces a structured startup failure.
- Focused/full CNCF and representative downstream validation, documentation,
  independent review, repair/re-review when needed, version evidence, and the
  Phase release commit complete before closure.

## Planning references

- [Phase 70 Checklist](phase-70-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [Phase 53 ComponentStyle and Capability Resolution](phase-53.md)
- [Phase 55 Typed Configuration Binding](phase-55.md)
- `src/main/scala/org/goldenport/cncf/component/Component.scala`
- `src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala`
- `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
- `textus-bok:docs/phase/phase-8.md`
