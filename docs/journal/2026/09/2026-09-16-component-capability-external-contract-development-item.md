# Component Capability External Contract Development Item

Date: 2026-09-16
Status: admitted for planning
Development item: DEV-011
Target phase: Phase 78

## Record

SimpleModeling adopted Capability Model as a non-instantiated model IR that
connects Use Case, Scenario, Component, Workflow, Operation, Specification,
and Evidence. CNCF therefore needs an externally consumable Component
specification surface for provided and required Capabilities and their explicit
realization mappings.

This record admits DEV-011 and creates Phase 78 as its execution ledger. It is
chronological and non-normative; the proposal remains exploratory until Phase
78 promotes accepted behavior into design/specification and executable
specifications.

## Cross-repository allocation

1. CNCF Phase 78 owns the public Component Capability contract, ABI admission,
   realization references, compatibility, and ComponentFactory/package
   exposure.
2. Cozy Phase 64 owns CML-facing Capability IR authoring, validation, and
   generation of the CNCF-admitted projection.
3. Textus CBD Support Phase 11 owns catalog ingestion, search, view, and
   traceability over the admitted public projection.

The intended acceptance order is CNCF contract, Cozy producer, then cbd-support
consumer. Contract co-design may proceed in parallel, but no downstream Phase
may define a competing Capability identity or infer semantics from names.

## Preserved boundary

Capability remains a model element. Runtime execution continues through
Operation, Workflow, and StateMachine contracts. This record does not change
CML syntax, publish artifacts, or begin implementation.

## References

- [Proposal](../../../notes/component-capability-external-contract-proposal.md)
- [Phase 78](../../../phase/phase-78.md)
- `asami/simplemodelingorg/docs/journal/2026/09/2026-09-16-capability-model-provisional-cml-syntax.md`
