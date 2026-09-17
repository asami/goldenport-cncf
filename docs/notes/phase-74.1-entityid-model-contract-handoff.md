# Phase 74.1 EntityId Model Contract Handoff

Status: accepted producer handoff for Phase 74.2

Phase 74.1 materialized the EntityId producer contract from Phase 74 without
reopening identity policy. This note records the exact consumer boundary; it
does not start Phase 74.2.

## Accepted producer identity

- Source repository: `simplemodeling-model`.
- Accepted Step commit: `4d440f99cfc3ccb349a5af1cf4cc127242aa7fef`.
- Locally published producer artifact:
  `org.simplemodeling:simplemodeling-model:0.2.2-SNAPSHOT`.
- Publication evidence: P007 (`publishLocal`).

## API and compatibility boundary

- `EntityId` is the public abstract base under `UniversalId`.
- A domain/entity-specific subtype owns ordinary durable issuance through its
  concrete `issue` API.
- Type-erased and common-framework boundaries may use generic `EntityId`
  recovery through `parse`, `restore`, and `bridgeFromParts`, plus generic
  `Codec` and `ValueReader` support.
- Generic recovery preserves the supplied canonical identity but neither
  infers a concrete subtype nor creates new entropy.
- Direct generic durable issuance, business-key-derived IDs, and hash-derived
  IDs are not producer APIs.

## Producer acceptance

- P005: `SimpleEntityRevisionSpec` accepted generic entity-model consumption
  with restored/bridged identity.
- P006: `EntityIdSpec` accepted concrete typed boundaries, generic recovery,
  canonical Record/JSON transport, equality/hash, and immutability.
- The Phase full review and the management closure-projection focused review
  both returned PASS with no Current Boundary Blocker, Hygiene, or Development
  Candidate finding.

## Consumer handoff

Phase 74.2 may introduce and consume concrete IDs such as `JobDefinitionId`,
migrate direct construction, and adopt the saved-entity identity lookup model.
It must consume the source/artifact identity and API boundary above rather than
inventing a new generic issuer or deciding new issuance/recovery policy. Any
such policy change returns to Phase 74.

The aggregate producer/consumer full suite is intentionally not part of this
handoff. Phase 74.3 remains its sole owner.
