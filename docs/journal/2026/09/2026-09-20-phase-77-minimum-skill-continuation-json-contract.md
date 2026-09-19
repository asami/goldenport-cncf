# Phase 77 Minimum Skill Continuation JSON Contract

Date: 2026-09-20
Status: normative planning clarification

Phase 77 includes the minimum schema-versioned, fail-closed JSON wire contract
needed for `sm-workflow` to hand a suspended Required SPI operation to Skill
and accept its typed result in a later process or turn. JSON is a wire encoding;
it is not the canonical Workflow domain model.

The Phase 77 contract contains:

- `ContinuationRequest` and `ContinuationResult`;
- Workflow and Continuation identity;
- expected revision and `ContextSnapshot`;
- typed input/result and Completion/Evidence requirements;
- schema identity/version with fail-closed codecs; and
- a minimum `WorkflowHandle` that references terminal or suspension state.

Resume rejects unknown or incompatible schema/version, invalid typed payload,
incorrect identity, stale revision/snapshot, missing required evidence, and
duplicate or incompatible results through the durable Continuation contract.

The following remain later-phase extensions: broad generic Start APIs, rich
Presentation, broad reasoning-level vocabularies, parent/child Workflow
composition, orchestration, and REST/MCP/UI protocol surfaces.

This clarification supersedes the protocol scope of
[Generic Workflow JSON Protocol and Application Layer Boundary](2026-09-20-generic-workflow-json-protocol-boundary.md).
