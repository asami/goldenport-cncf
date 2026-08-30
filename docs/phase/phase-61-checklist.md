# Phase 61 Checklist - Information CML Runtime Canonicalization

status=closed
phase=[Phase 61 - Information CML Runtime Canonicalization](phase-61.md)
successor=[Phase 61.1 Checklist](phase-61.1-checklist.md)

This checklist is the authoritative Phase 61 state ledger after Phase 61
starts. Only one stage may be `IN_PROGRESS` at a time. On 2026-08-30, approved
decision `D-P61-SPLIT-001` retained IC-01 and IC-02 here and moved each
remaining unchecked IC stage to one sequential child checklist.

## IC-01: Inventory and Failing-First Acceptance

Stage Status:
- Current status: DONE
- Owner: CNCF Information, Entity, projection, provider, and downstream
  maintainers
- Entry rule: Phase 60.8 is closed.
- Completion rule: The exact current split, target contract, migration surface,
  and failing-first acceptance identities are recorded before implementation.

- [x] Inventory every hand-written Information model and helper.
- [x] Inventory every CML-generated Entity, input, view, value, powertype, and
  state-machine output.
- [x] Record which generated types are used by runtime and which are only
  compilation/specification evidence.
- [x] Inventory InformationSpace, Behavior DSL, Component ownership, provider
  requests, projections, serialization, and persistence references.
- [x] Inventory Textus Knowledge Editor and Textus SIE public and persisted
  dependencies.
- [x] Record current source, binary, JSON/YAML/XML/Form, schema, operation, and
  persisted-state compatibility surfaces.
- [x] Fix `src/main/cozy/information.cml` as the canonical source and generated
  `entity.Information` as the target runtime identity.
- [x] Fix the temporary alias/adapter and final removal policy.
- [x] Fix InformationSpace as the public curation/capability boundary and the
  Entity repository as its persistence/OCC boundary.
- [x] Fix the exact expected use of CML state-machine output.
- [x] Register failing-first Executable Specification identities for every
  Phase 61 acceptance group.
- [x] Add a runtime reference test that fails while InformationSpace still
  uses the hand-written Information class.

Evidence:
- `docs/notes/phase-61-ic01-information-cml-runtime-inventory-and-failing-first-contract.md`
  records the local and read-only Textus downstream inventory, generated
  lifecycle-scaffold limitation, compatibility and boundary contract, and
  stable IC-01 through IC-08 executable acceptance registry.
- `org.goldenport.cncf.information.InformationCanonicalRuntimeReferenceSpec`
  is the pending runtime class-identity suite. Its assertion is intentionally
  pending until generated-runtime adoption (IC-03); it exposes the current
  handwritten `Information` runtime returned by InformationSpace.

## IC-02: Canonical CML and Generator Contract

Stage Status:
- Current status: DONE
- Owner: CNCF CML, simple-modeler, and Cozy maintainers
- Entry rule: IC-01 is DONE.
- Completion rule: One CML source generates the complete usable
  revision-aware Entity/value/lifecycle family required by runtime migration.

- [x] Reconcile every hand-written runtime field with `information.cml`.
- [x] Reconcile generated common `SimpleEntity` attributes and Information
  fields without duplicate timestamps, lifecycle, publication, or security
  semantics.
- [x] Fix generated package and canonical type naming.
- [x] Verify required/optional/default behavior for Entity and nested values.
- [x] Verify one canonical generated class for every CML-defined Value and
  Powertype.
- [x] Verify generated `Information` outputs contain one managed revision.
- [x] Verify generated Create, Update, and Query inputs omit managed revision.
- [x] Verify Update uses explicit `Update` semantics for optional and
  collection-valued fields.
- [x] Inspect the generated `informationLifecycle` output for executable
  transition metadata/planning.
- [x] Complete simple-modeler/Cozy state-machine generation if the current
  output does not expose the CML transition contract.
- [x] Add cold-generation deterministic-output specifications.
- [x] Add generated schema/codec/record/persistence round-trip specifications.
- [x] Add invalid lifecycle transition specifications against generated
  transition evidence.

Evidence:
- Step commits: CNCF `86361c11db588bd4d63836af331372937f955868`,
  simple-modeler `a4dce2d2f00354b95806ddf55e93b25c0528cfe2`, and Cozy
  `2636d834ffccac03a0888fe0be96cb764d88c61e`.
- Focused acceptance: `InformationCmlCanonicalContractSpec` passed 10/10;
  Cozy `ModelerValueGenerationSpec` passed through its accepted Step gate.
- Mandatory Phase 61 full review found `CPB-61-001`: Phase 62's CS-01 entry
  rule named Phase 61 rather than the required Phase 61.6 series closure.
  The one-line Phase 62 correction passed focused re-review with no remaining
  Current Phase Blocker.
- The release commit binds the required full suites for CNCF, simple-modeler,
  Cozy, and Cozy Launcher to the final closure tree under
  `phase61-clb-ic02-20260831`.

## Split Transfer Record

IC-03 through IC-08 were moved before implementation and each has exactly one
active checklist owner:

- IC-03 -> [Phase 61.1 Checklist](phase-61.1-checklist.md)
- IC-04 -> [Phase 61.2 Checklist](phase-61.2-checklist.md)
- IC-05 -> [Phase 61.3 Checklist](phase-61.3-checklist.md)
- IC-06 -> [Phase 61.4 Checklist](phase-61.4-checklist.md)
- IC-07 -> [Phase 61.5 Checklist](phase-61.5-checklist.md)
- IC-08 -> [Phase 61.6 Checklist](phase-61.6-checklist.md)
