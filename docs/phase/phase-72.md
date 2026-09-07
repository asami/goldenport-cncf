# Phase 72 - Model-Driven Lifecycle Semantics

status=planned

Plan date: 2026-09-07

## Goal

Define and implement the CNCF runtime contract for admitted model lifecycle semantics needed by semantic component tooling, especially strict composition and aggregation behavior and consistency with StateMachine execution.

This Phase is motivated by Textus CBD Support Phase 9 and Cozy Phase 54. CNCF consumes normalized semantic metadata; it does not own CML syntax or Dashboard presentation.

## Planning Rule

Each subphase is intended to fit within approximately six hours of focused work after required upstream metadata is available. Split before implementation when a slice exceeds that bound.

## Phase 72.1: Existing Runtime Contract Inventory

Compare the requirement with current Entity, Aggregate, relation, persistence, transaction, Workflow, and StateMachine runtime contracts.

Classify each required lifecycle rule as already supported, representable but unenforced, or missing. No implementation begins until this inventory establishes the smallest runtime extension.

## Phase 72.2: Admitted Lifecycle Policy Contract

Define a versioned, CML-independent runtime representation for admitted relation lifecycle semantics.

The contract should distinguish composition, aggregation, and association and preserve only semantics explicitly supplied by authoritative model metadata.

Candidate fields include ownership, independent existence, create/delete policy, reassignment/reparenting, lifecycle propagation, aggregate boundary, and cardinality/reference constraints.

## Phase 72.3: Composition Enforcement

Implement the minimum missing runtime semantics for composition.

Candidate behaviors include owner-mediated part creation/mutation, exclusive ownership, restricted reparenting, owner-dependent termination, and aggregate/persistence boundary validation where the existing CNCF model supports those concepts.

Do not implement semantics already owned by an existing runtime subsystem twice.

## Phase 72.4: Aggregation and Association Enforcement

Implement the minimum missing semantics for aggregation and association.

Aggregation must preserve independently existing member lifecycle and must not silently inherit composition-style cascade semantics. Association must remain non-owning and focus on declared reference/cardinality integrity.

## Phase 72.5: Structure and StateMachine Consistency

Define deterministic validation between structural lifecycle policy and StateMachine behavior.

Examples include detecting an owner termination path that leaves a composition part in an impossible live state, or incorrectly cascading termination to an independently aggregated member.

The runtime should report attributable violations/observations rather than rewriting the model.

## Phase 72.6: Runtime Evidence Projection

Expose bounded, attributable lifecycle evidence suitable for consumers such as Textus CBD Support Review/Dashboard without exposing private runtime implementation details.

Evidence should distinguish declared policy, observed/enforced behavior, violation, and unavailable evidence.

## Phase 72.7: Integration and Closure

Validate representative composition, aggregation, association, Workflow/StateMachine consistency, persistence/aggregate behavior, and downstream CBD Support evidence consumption.

## Boundaries

- Cozy owns CML syntax, semantic transformation, and publication metadata.
- CNCF consumes normalized admitted semantics and owns runtime enforcement/evidence only.
- Textus CBD Support owns Dashboard/Review projection and does not become a lifecycle runtime.
- CNCF must not infer lifecycle semantics from type names, source layout, or diagram notation.
- Existing Entity/Aggregate/StateMachine contracts are preferred over new parallel abstractions.

## Dependencies

- Cozy Phase 54 supplies the semantic metadata contract needed for full downstream integration.
- Textus CBD Support Phase 9.11 consumes runtime evidence when available but must remain absence-safe before Phase 72 closes.

## Completion Conditions

Phase 72 closes when composition, aggregation, and association have explicit admitted runtime semantics, the minimum necessary enforcement is implemented without duplicating existing CNCF facilities, Structure/StateMachine consistency can be validated deterministically, and bounded lifecycle evidence can be consumed downstream.
