# Phase 56 CID-06B Component Descriptor Compatibility Plan

## Status

- Phase: 56
- Step: CID-06 Compatibility Adapters
- Slice: CID-06B Legacy Descriptor Projection
- Slice status: ACCEPTED / REVIEWED
- Phase full validation: pending until Phase 56 release

## Goal

Legacy Component descriptor fields remain decode-only presentations. They may
enter canonical in-memory assembly state only when an owning boundary already
supplies the exact expected `ComponentId`. The decoder never invents a
namespace, schema-3 admission remains strict, and no compatibility projection
becomes a new serialization authority.

## Contract

- `CID06-R9`: Legacy `name`, nested `component.name`, and `componentName`
  values are evaluated independently against one expected canonical identity.
  Exact qualified, exact local-ID, exact normalized artifact, and exact
  namespace-leaf-prefixed artifact projections are admitted. Unsupported or
  disagreeing fields reject with field, expected identity, actual value, and
  adapter reason.
- `CID06-R10`: Successful legacy projection preserves the source schema
  version, release, style, extensions, configuration, and other descriptor
  content. Only `name`, `componentName`, and typed `componentId` become
  canonical in memory. The result retains compatibility notices; warning and
  Admin/observability publication remain CID-06C.
- Authored schema-3 descriptors are revalidated by
  `requireCanonicalIdentityC`, must equal the expected identity exactly, and
  are returned unchanged without a compatibility notice.
- Ordinary `RecordDecoder[ComponentDescriptor]` remains unbound and never
  assigns a `ComponentId` to a legacy descriptor.
- `SubsystemAssemblyAdmission` applies projection to explicit static
  overrides and configured repository static descriptors before descriptor
  closure. Repository lookup tries only the deterministic aliases derived from
  the already-known expected identity and never constructs a repository.

## Implementation Boundary

- Extend `ComponentIdentityCompatibilityAdapter` with `DescriptorField`,
  artifact alias classification, typed `DescriptorProjection`, exact
  descriptor lookup aliases, and expected-identity-bound projection.
- Route `SubsystemAssemblyAdmission` static descriptor discovery and closure
  through the projection. Preserve the configured repository precedence and
  reject multiple matching explicit overrides.
- Add `Phase56ComponentDescriptorCompatibilitySpec` covering direct field
  agreement/disagreement, strict schema-3 preservation/mismatch, explicit
  override projection, configured static repository projection, and absence of
  decoder inference.

## Non-goals

- No free-form normalization or namespace inference.
- No warning/Admin emission; CID-06C owns observable compatibility notices.
- No runtime selector, Help/Meta, or Web-path changes; CID-06C owns them.
- No legacy released-CAR ClassLoader/factory compatibility; CID-06D owns it.
- No serialization of legacy alias state.
- No repository construction, CAR rewrite, publish, commit, or Phase full
  validation in this Slice.

## Focused Validation Boundary

- Every accepted descriptor field resolves to the same expected canonical
  `ComponentId`.
- A disagreement or foreign schema-3 identity fails deterministically.
- Assembly state contains the typed canonical identity for both explicit
  overrides and configured repository static descriptors.
- Legacy decode without expected authority remains untyped.
- Focused evidence invocation `10489-20260808T013738Z` completed four suites
  with 36 tests succeeded; zero failed, canceled, ignored, pending, or
  aborted; main and test compile succeeded; `sbt_exit=0`, `wrapper_exit=0`,
  and `lock=released`. `VF-CID06B-003` then repaired one discarded-Assertion
  warning. Final warning-free focused evidence invocation
  `13371-20260808T014534Z` used the exact logical argv:

  ```text
  ["--batch", "testOnly org.goldenport.cncf.component.Phase56ComponentIdentityCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentDescriptorCompatibilitySpec org.goldenport.cncf.component.Phase56ComponentIdentityContractSpec org.goldenport.cncf.subsystem.SubsystemAssemblyAdmissionSpec"]
  ```

  Four suites completed with zero aborted; 36 tests succeeded; zero failed,
  canceled, ignored, or pending; compile was warning-free; `sbt_exit=0`,
  `wrapper_exit=0`, and `lock=released`. Independent focused re-review
  returned PASS with no findings and `FULL_REVIEW_REQUIRED=no` on status SHA
  `cd38b345a039fdf0d850b5b9b3db4167e4989eaf098219f2a540bdac1b5ddb16` and
  tracked diff SHA
  `5af442141553a258093364af59afe476c9f7c313c5872438096db0b9561a344f`; all ten
  target hashes were exact. CID-06B through CID-06E are accepted/reviewed;
  the CID-06 Step commit and Phase full validation remain pending.

## REVIEW #1 / REVIEW_FIX #1 Record

`REVIEW #1` admitted `R1-F1` through `R1-F6`: static dev-repository candidate
discovery; adapter visibility; accepted-spelling and metadata-preservation
coverage; semantic specification grouping; adapter-plan lifecycle reporting;
and phase/checklist lifecycle reporting. `REVIEW_FIX #1` applies those repairs.
`VF-CID06B-001` and `VF-CID06B-002` correct the two
`SubsystemAssemblyAdmission` brace placements. Status:
`ACCEPTED / REVIEWED`. The discarded-Assertion warning was repaired by
`VF-CID06B-003`. Authoritative warning-free invocation
`13371-20260808T014534Z` completed as recorded above. Independent focused
re-review returned PASS with no findings and `FULL_REVIEW_REQUIRED=no` on
status SHA `cd38b345a039fdf0d850b5b9b3db4167e4989eaf098219f2a540bdac1b5ddb16`
and tracked diff SHA
`5af442141553a258093364af59afe476c9f7c313c5872438096db0b9561a344f`; all ten
target hashes were exact. `R1-F1` through `R1-F6` and `VF-CID06B-001` through
`VF-CID06B-003` are closed. CID-06C through CID-06E are accepted/reviewed;
the CID-06 Step commit and Phase full validation remain pending.
