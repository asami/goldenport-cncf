# SWF-04: Composite State-Machine Action Composition Contract

## Purpose and boundary

SWF-04 defines the canonical, causal composition of already-declared producer
actions around composite state-machine occurrences. It is a semantic contract;
it neither defines an action runtime nor changes constituent, composite, or
transactional execution ownership.

Cozy/CML owns producer-side construction of the closed typed input. Phase 64.2
owns planning, test/simulation-interpreter, and production-alignment work,
including execution-failure ownership. Phase 77 owns API/SPI, `ComponentFactory`,
`Provider`, `Action`, durable continuation, and protocol concerns. Phase 85 owns
2PC, compensation, and recovery.

## Closed typed producer action input

The producer supplies a closed typed action descriptor set, pinned to the
`CompositeStateMachineDefinitionIdentity` and its exact definition version. Each
descriptor contains:

- a stable logical action identity;
- its declaration phase and declaration order within its owner sequence;
- its owning transition identity and owning machine role/level;
- deterministic source, model, and source-location provenance; and
- a logical binding to the existing `ExecProgram[UnitOfWorkOp, A]`.

The descriptor set is the complete action input for this contract. CNCF must not
reparse CML, infer names or source information, invoke callbacks, acquire provider
handles, or introduce a second action algebra.

## Causal eligibility

Phase 63.1 completes a constituent sequence before Phase 63.2 records its
post-commit `CommittedTransition`. Only after that committed transition may SWF-02
derive a changed composite transition; only such a derived transition makes its
composite action sequence eligible. If no composite transition is derived, no
composite action sequence exists for that cause.

A lower-level committed action sequence cannot be replayed, folded back into the
composite sequence, or represented as atomic with later composite actions. It is
already committed history, not a member of a subsequently caused composite
occurrence.

## Deterministic composition

Composition preserves declaration order within every sequence. For one causal
chain, the lower committed sequence precedes the composite sequence it caused;
nested composite chains are ordered inner-to-outer. Every composed entry retains:

- definition identity and exact definition version;
- logical action identity;
- owner occurrence and machine level;
- causal predecessor;
- supplied correlation when present; and
- source, model, and source-location provenance.

Priority ordering, deduplication, flattening, inference, and unordered
collections are prohibited. Composition has no implicit ordering or inferred
causation rule.

Normal causal-chain example: constituent machine `M1` completes its declared
sequence in declaration order; Phase 63.2 records `CommittedTransition(t1)`;
SWF-02 derives composite transition `c1`; then `c1`'s declared composite sequence
follows the already committed `M1` sequence. If `c1` causes an outer transition
`c2`, `c1`'s sequence precedes `c2`'s sequence, yielding inner-to-outer order.

No-derived-transition example: `M1` completes and records
`CommittedTransition(t1)`, but SWF-02 yields no changed composite transition. The
`M1` sequence remains committed history and no composite action descriptor is
eligible or synthesized.

## Admission rejection

Admission structurally rejects incomplete, foreign, unpinned, opaque, unordered,
or unprovenanced descriptors. Such rejection has no default, partial execution,
retry, compensation, or recovery behavior. It is an admission-contract outcome,
not `UnmappedConfiguration` or `AmbiguousConfiguration`.

This contract does not define CML grammar, classes, methods, runtime execution,
provider behavior, API/SPI shape, persistence, protocols, retries, compensation,
or recovery.
