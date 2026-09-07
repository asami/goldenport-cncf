# Phase 72 Checklist - Model-Driven Lifecycle Semantics

status=planned
phase=[Phase 72 - Model-Driven Lifecycle Semantics](phase-72.md)

Planning rule: each subphase should remain within approximately six hours of focused implementation once prerequisites are available.

## Phase 72.1: Runtime inventory

- [ ] Inventory current Entity/Aggregate/relation/persistence/transaction/Workflow/StateMachine semantics relevant to composition and aggregation.
- [ ] Classify requirements as already supported, representable but unenforced, or missing.
- [ ] Freeze the minimum extension boundary before implementation.

## Phase 72.2: Admitted lifecycle policy

- [ ] Define a versioned CML-independent relation lifecycle policy contract.
- [ ] Distinguish composition, aggregation, and association explicitly.
- [ ] Preserve only authoritative metadata; reject inference by names or diagram shape.
- [ ] Define absence/unsupported behavior.

## Phase 72.3: Composition enforcement

- [ ] Implement only missing composition semantics identified by Phase 72.1.
- [ ] Cover exclusive ownership and owner-mediated lifecycle where required.
- [ ] Cover reparenting and termination propagation where declared.
- [ ] Verify aggregate/persistence boundary interaction without duplicate runtime mechanisms.

## Phase 72.4: Aggregation and association

- [ ] Preserve independently existing aggregation-member lifecycle.
- [ ] Prevent accidental composition-style cascade behavior for aggregation.
- [ ] Enforce declared association/cardinality/reference integrity without ownership propagation.

## Phase 72.5: Structure/StateMachine consistency

- [ ] Define deterministic structural-lifecycle versus StateMachine validation.
- [ ] Detect representative composition lifecycle contradictions.
- [ ] Detect representative aggregation lifecycle contradictions.
- [ ] Report violations without mutating or repairing the model silently.

## Phase 72.6: Runtime evidence

- [ ] Define bounded evidence identities and attribution.
- [ ] Distinguish declared policy, enforced/observed behavior, violation, and unavailable evidence.
- [ ] Make evidence consumable by CBD Support without exposing private runtime internals.

## Phase 72.7: Integration and closure

- [ ] Validate representative composition, aggregation, and association cases.
- [ ] Validate Workflow/StateMachine consistency cases.
- [ ] Validate aggregate/persistence interaction.
- [ ] Validate downstream Textus CBD Support Phase 9.11 consumption.
- [ ] Update notes/journal and close only with reproducible evidence.
