# Phase 47 - Component Initialization Parameter Resolution

status=active
started_at=2026-07-22
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 47 Checklist](phase-47-checklist.md)

## Purpose

Provide a general CNCF mechanism that interprets configuration and other
property values in the correct component-instance context before component
initialization, then delivers typed initialization parameters without exposing
raw configuration maps to component code.

## Selected Direction

- Resolve initialization parameters in CNCF from the already resolved
  configuration and assembly context.
- Bind resolution to both `ComponentId` and `ComponentInstanceId`.
- Deliver typed parameters through the component bootstrap boundary.
- Keep source precedence, safe provenance, and confidential-value handling
  under CNCF ownership.
- Keep component-specific declaration, projection, and domain validation under
  component ownership.
- Preserve `ComponentConfigurationAccess` as the separate operation-time
  declared configuration boundary.
- Use Textus AI or CBD Support as the first real downstream consumer.

## Scope

- Normative design and static specification for initialization-time parameter
  resolution.
- Typed keys, values, resolution outcomes, parameter collections, and safe
  provenance.
- Deterministic input-layer precedence for packaged defaults, assembly
  defaults, subsystem/SAR instance settings, runtime configuration, and
  explicit test overlays.
- Component-instance isolation.
- Integration with `ComponentFactory.bootstrap` and special-component
  initialization.
- Secret-reference and confidential-parameter boundaries.
- Payload-safe diagnostics and optional bootstrap observability.
- Executable specifications and one downstream migration.

## Boundaries

- `ResolvedConfiguration` remains the raw resolved key/value store and does
  not gain component-domain semantics.
- CNCF owns source precedence, component-context selection, typed decoding,
  required/optional semantics, safe provenance, and bootstrap delivery.
- Components own their parameter declarations, typed domain projection, and
  combination validation.
- Components do not receive the raw configuration map through this mechanism.
- Request parameters, action properties, ambient environment access, and
  arbitrary runtime lookups cannot override initialization parameters.
- Runtime `ComponentConfigurationAccess` is preserved and is not replaced by
  this phase.
- Secret values are not exposed through parameter APIs, default diagnostics,
  or observability.

## Stages

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CIP-01 | Normative contract | Design and specification define initialization parameter resolution separately from runtime component configuration. | done |
| CIP-02 | Typed parameter model | CNCF provides the resolver, typed key/value, result, collection, and safe provenance vocabulary. | done |
| CIP-03 | Resolution layers | All admitted initialization sources have one deterministic precedence contract. | done |
| CIP-04 | Instance context | Resolution is isolated by component and component-instance identity. | done |
| CIP-05 | Bootstrap integration | Component factories and special components receive typed initialization parameters without raw maps. | done |
| CIP-06 | Confidential values | Secret references and confidential parameters remain opaque and payload-safe. | open |
| CIP-07 | Diagnostics and observability | Bootstrap resolution exposes bounded identity and provenance facts only. | open |
| CIP-08 | Executable evidence | Deterministic specifications cover success, failure, precedence, isolation, overlays, and confidentiality. | open |
| CIP-09 | Downstream acceptance | Textus AI or CBD Support consumes the mechanism and the phase closes with validated evidence. | open |

## Acceptance

- A component declares and receives typed initialization parameters before
  operation execution.
- `ComponentInstanceId` selects the applicable context and isolates component
  instances.
- The documented layer order is deterministic and executable-specification
  backed.
- Missing required and malformed values fail during initialization with
  structured `Consequence`/`Conclusion` results.
- Component code cannot observe a raw configuration map through the new API.
- Provenance is available without exposing physical paths, credentials,
  confidential values, or unrelated configuration.
- Existing operation-time `ComponentConfigurationAccess` behavior remains
  compatible.
- Explicit test overlays remain deterministic and cannot become an implicit
  production source.
- Textus AI or CBD Support uses the mechanism without owning CNCF source
  precedence or leaking application-specific keys into a reusable component.

## Non-goals

- Organization-wide configuration management.
- Dynamic mutation of initialization parameters after bootstrap.
- Per-request initialization-parameter overrides.
- A secret-value accessor for component code.
- Migration of every existing component.
- UI tooling for editing component parameter policies.

## Source

- `docs/journal/2026/07/2026-07-22-component-initialization-parameter-resolution-consideration.md`
- `docs/spec/config-resolution.md`
- `docs/spec/component-runtime-boundary-capabilities.md`
- `docs/design/configuration-model.md`
- `docs/design/component-runtime-boundary-capabilities.md`
- `docs/design/typed-component-api-and-multi-instance-spi.md`

## Current Resume Point

CIP-01 is complete. The configuration and component runtime boundary
design/specification documents define initialization ownership, the
pre-construction/bootstrap lifecycle, the `ResolvedConfiguration` raw-store
boundary, operation-time `ComponentConfigurationAccess` separation, structured
failure, and raw-map/ambient-lookup prohibitions.

CIP-01 and CIP-02 are complete. The typed parameter model provides declared
keys and decoders, required-or-optional semantics, bounded provenance,
structured failures, a CNCF-protected resolver contract, and an immutable
snapshot without raw-map or arbitrary-name access.

CIP-01 through CIP-04 are complete. The fixed CNCF-private source model gives
packaged defaults, assembly defaults, subsystem-instance settings, resolved
runtime configuration, and explicit test overlays one deterministic precedence
order without admitting ambient or request sources. A CNCF-private context
selects exactly one packaged descriptor owner and one admitted assembly
instance metadata record for a coherent `ComponentId` and
`ComponentInstanceId`, including componentlet ownership, and the resolver
derives its subsystem-instance layer only from that selected context.

CIP-05 is complete. Factory declarations are
resolved after final participant identity through the five fixed CNCF-owned
layers; `ComponentInit`, ordinary initialization, componentlets, and special
collaborator initialization receive the same immutable typed snapshot.
Consequence-aware factory/bootstrap/subsystem paths prevent failed
initialization from entering component space. Descriptor-bound repository
construction now receives per-binding instance metadata after the effective
descriptor is attached, actual repository factory failures retain their
`Conclusion`, and special-component exceptions remain in the same failure
channel. Packaged CAR descriptors remain the packaged-default source through
assembly matching and participant materialization. An exact repository-bound
participant is retained after its single initialization; only unmatched
prototypes are rematerialized for another declared instance. Requested CAR/SAR
archive failures preserve the originating factory `Conclusion` across archive
extraction, and assembly API metadata and classloader validation failures also
remain in their original structured `Conclusion` channel. No-declaration
factories retain the empty-snapshot path.
Confidential values and diagnostics remain CIP-06 and CIP-07.
