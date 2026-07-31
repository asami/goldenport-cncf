# Phase 53 CS-02E - Component-only Generation and Descriptor Projection

date=2026-07-31
status=pending-review
phase=53
scope=CS-02E

## Decision

An authored CML `COMPONENT` and its unversioned `STYLE` selection are the only
selection authority. The digest-pinned framework catalog resolves that selection
to one canonical snapshot. Cozy writes the resolved snapshot into generated
model metadata and, when packaging a CAR, projects it unchanged into numeric
descriptor `schemaVersion: 2`.

`project.yaml` remains build and packaging metadata. It may not declare
ComponentStyle or ComponentCapability authority, and a CML-derived snapshot
cannot be replaced by a source-managed `component-descriptor.json` or
`componentDescriptorJson` extension.

## Rules

- CS02E-R1: an explicit `COMPONENT` generates a component even when it has no
  entity, service, value, or datatype declarations.
- CS02E-R2: catalog-derived descriptor projection includes the exact v2
  `componentStyle` snapshot: api/provider/id/major, closed empty parameter
  schema, empty parameters, declared bundles and capabilities, deterministic
  effective capabilities, and required Subsystem capabilities.
- CS02E-R3: no project or source descriptor authority may compete with the
  CML/catalog projection.

## Ownership

Kaleidox owns the typed authored ComponentStyle selection. The framework owns
the catalog and descriptor-v2 meaning. Cozy is the consumer and projection
boundary: it validates the selected runtime catalog, carries the resolved
snapshot in generated metadata, and packages that snapshot without recreating
style or capability semantics. `project.yaml` owns neither selection nor
capability declarations.

## Evidence

- Cozy `ComponentStyleCatalogSpec` uses the component-only `artscene` fixture
  and proves generation creates a `ComponentFactory` without entity output;
  its generated metadata contains the framework catalog snapshot.
- Cozy `CozyArchivePackagerSpec` proves the packaged descriptor has numeric
  schema v2 and the exact catalog-derived `full-fledged-with-standalone@1`
  bundles, effective capabilities, and Subsystem requirements.
- Cozy rejects a project metadata key that attempts to become ComponentStyle or
  ComponentCapability authority and rejects an overriding source or extension
  descriptor whenever CML metadata provides the snapshot.
- The initial focused Cozy validation passed 74/74 tests across
  `ComponentStyleCatalogSpec`, `CozyArchivePackagerSpec`, and
  `ModelerScaffoldSpec`; the broader regression selection passed 122/122.
  The CS-02E review-fix adds exact snapshot, emitted JSON/YAML metadata, and
  competing-authority coverage; its focused compile/test run passed 75/75.
- The earlier serialized Cozy full suite passed 771/771 tests (8 cancelled,
  no failures). Final phase-release validation remains a later closure gate.
- After Cozy `publishLocal`, framework runtime-descriptor generation,
  `Test/compile`, and the three catalog/descriptor-v2 focused suites passed
  36/36 tests. This confirms the emitted v2 shape remains inside the existing
  framework admission boundary without a framework redesign.

## Boundary

CS-02 implementation awaits focused independent re-review. It does not provide
development-directory versus packaged runtime parity, invoke
`cozyPrepareRuntime`, or change launcher behavior; those are CS-03 work.
