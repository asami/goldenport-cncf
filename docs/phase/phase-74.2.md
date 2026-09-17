# Phase 74.2 - EntityId CNCF and Generated Consumer Adoption

status=closed
split_full_test_policy=final-only
validation_ownership=aggregate-deferred
aggregate_validation_owner=PHASE-74.3
aggregate_validation_sequence=["PHASE-74","PHASE-74.1","PHASE-74.2","PHASE-74.3"]
planned_at=2026-09-16
closed_at=2026-09-17
split_from=[Phase 74](phase-74.md)
depends_on=[Phase 74.1](phase-74.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md#961-entityid-inheritance-contract-restoration)
checklist=[Phase 74.2 Checklist](phase-74.2-checklist.md)
successor=[Phase 74.3](phase-74.3.md)

Status: CLOSED

Phase 74.2 closed with CNCF consumer Step
`e72b58cdddee85d26e44e6181500d3633129ee3b` and the accepted local repair
for `CPB-74.2-001`. The focused consumer receipts, Phase full review, and
focused re-review establish this handoff. The repository full suite was not
run here: it remains the single aggregate-validation responsibility of Phase
74.3 under the declared final-only sequence.

## Purpose

Adopt the released EntityId contract in CNCF and the exact affected generated
consumer edges. This is EIR-03 of the former Phase 74; the work uses Phase
74.1's typed producer handoff and does not infer new EntityId policy locally.

## Ownership and Scope

- CNCF owns IdGenerationContext integration, JobDefinition entity/store
  adoption, and migration of its classified direct EntityId constructions.
- `simple-modeler` owns only affected generated entity-specific IDs; Cozy
  consumes the accepted generator/model handoff. Freeze exact paths before work.
- Connect IdGenerationContext to subtype construction with normal context
  namespace/time/entropy, never a caller-supplied key-derived token.
- Introduce JobDefinitionId extending EntityId and use it on JobDefinitionEntity
  while preserving generic EntityStore interoperability.
- On creation persist `{ id, key, ... }`; later key lookup obtains the saved ID.
  Neither a business-key-derived ID nor a correspondence table, hash, or
  companion integrity value is introduced. A key index is only a later optional
  store optimization.
- Decode the saved JobDefinitionId with its expected exact collection, preserve
  it on update, and verify `a-b` and `a_b` remain distinct keys.
- Migrate each audited CNCF construction to ordinary issue, restoration, or an
  explicit special bridge. A complete canonical ID validates its collection;
  reconstruction must not silently replace it.
- Classify existing Job-related bridges by contract rather than mechanically
  removing explicit timestamp/entropy values; adapt affected generated IDs.

## Phase Plan Gate: PROCEED

- target and ceiling: expected 6 hours (range 5–7); hard ceiling 8 hours.
- estimate calibration: no comparable completed slice exists; this is the
  original EIR-03 six-hour consumer work group.
- planning demand: bounded-settled implementation; recommended profile:
  `gpt-5.6-terra / high`; profile cost role: lower-cost execution.
- expensive reasoning kernel: owned only by Phase 74. The Phase 74.1 typed API
  is accepted input; newly discovered policy ambiguity is a stop-and-return,
  not a consumer-side design decision.
- incoming semantic handoff:
  - from: `PHASE-74.1`; kind: `migration`; owner: Phase 74.1.
  - input: accepted typed model API, published local producer identity, and
    focused producer acceptance receipts.
  - action: migrate frozen CNCF/generator consumer edges to the released API.
  - output: integrated consumer source, focused consumer receipts, and exact
    release source/artifact identities for Phase 74.3; invalidation requires a
    predecessor contract or artifact change.
- merge/rebalance evidence: none; this is one original six-hour work group.
  Splitting avoids spending the expensive design profile on routine migration.
- runtime suitability: re-evaluate at execution; this record neither starts
  implementation nor permits the final full suite.

## Completion and Handoff Conditions

- JobDefinitionEntity stores JobDefinitionId independently of its key, and
  restart lookup returns that stored identity.
- Generic EntityId boundaries remain supported; generic new durable issuance is
  absent from adopted consumers.
- A complete release handoff names changed producer/consumer identities and
  focused receipts for Phase 74.3's final integration validation.

## Accepted Handoff to Phase 74.3

- Producer input: `simplemodeling-model` commit
  `4d440f99cfc3ccb349a5af1cf4cc127242aa7fef`, locally published as
  `org.simplemodeling:simplemodeling-model:0.2.2-SNAPSHOT` by Phase 74.1.
- Consumer source: CNCF Step
  `e72b58cdddee85d26e44e6181500d3633129ee3b`, adopting concrete
  `JobDefinitionId`, context-owned issuance, saved `{ id, key, ... }`
  persistence, and saved-ID lookup.
- Focused consumer evidence: compile receipt
  `P742-EIR03-01-REVIEW-FIX-001-VAL-COMPILE-001`; representative receipt
  `P742-EIR03-01-STEP-COMMIT-VAL-REPRESENTATIVE-002`; accumulator receipt
  `P742-EIR03-01-STEP-COMMIT-VAL-ACCUMULATOR-002`; and repair receipt
  `P742-PHASE-FIX-001-VAL-JOBCONTROL-001` for `JclJobControlComponentSpec`.
- Review evidence: `PHASE-74.2-full-review-001` found only
  `CPB-74.2-001`; the one-file local naming repair was accepted by
  `PHASE-74.2-focused-rereview-001` with no remaining blocker.
- Compatibility limit: generic `EntityId` storage/recovery remains supported,
  but this Phase neither redesigns generic identity policy nor introduces
  generic durable issuance, key-derived IDs, hash/content revision controls,
  or a separate key-to-ID table. No `simple-modeler` source or artifact is
  changed by this CNCF consumer Step.
- Aggregate disposition: `repository_full_suite=deferred-not-run`; Phase 74.3
  alone owns the declared aggregate full suite and cross-producer closure.

## Non-Goals

- Redesigning generic EntityId policy or the model contract; it belongs to
  Phase 74 and Phase 74.1.
- The aggregate full suite, cross-repository closure review, and specification
  promotion; they belong to Phase 74.3. This Phase's own aggregate-deferred
  release closure records the handoff without claiming that validation.

## References

- [Phase 74.1](phase-74.1.md)
- [Phase 74.2 Checklist](phase-74.2-checklist.md)
- [Phase 69.3](phase-69.3.md)
