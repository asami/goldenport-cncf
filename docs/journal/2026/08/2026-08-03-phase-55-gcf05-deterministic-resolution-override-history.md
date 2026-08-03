# Phase 55 GCF-05 Deterministic Resolution and Override History

GCF-05 completes deterministic resolution of immutable typed configuration
candidates. `simplemodeling-lib` owns the generic resolver; CNCF owns the
explicit target-context adapter. No source discovery, catalog adoption, trace
projection, or String boundary conversion is introduced by this stage.

## Completed Behavior

- The generic resolver accepts one immutable, validated ordered target context
  from least to most specific and resolves candidates independently per
  context.
- Context validation rejects null, empty, duplicate, and null-containing
  target vectors with structured configuration failures.
- For one canonical parameter and physical
  `(sourceRank, sourceOrdinal)` source, exactly one maximum-specificity target
  wins. A same-source tie fails structurally.
- Source winners fold from lower to higher rank and then ordinal. Each new
  winner references only the preceding source winner through its direct
  `overridden` binding.
- Context-ineligible candidates and same-source losers do not enter the
  override history. A canonical-id group with different parameter witnesses
  fails structurally before target selection can discard a witness.
- CNCF constructs either Global-only or exactly Global, ComponentClass,
  SubsystemInstance, and containing-Subsystem-qualified ComponentInstance.
  Independent resident Subsystems use the same candidate collection without
  sharing a winner.

## Validation and Review Evidence

Initial implementation and cross-repository validation:

- generic compile passed: `89624-20260802T151853Z`;
- initial generic resolver specification: 3 succeeded,
  `90846-20260802T152112Z`;
- generic local `0.4.3-SNAPSHOT` `publishLocal` passed:
  `91369-20260802T152208Z`;
- CNCF classpath recorded local `goldenport-core_3:0.4.3-SNAPSHOT`:
  `91881-20260802T152304Z`;
- CNCF `Test/compile` passed: `93235-20260802T152532Z`;
- four focused CNCF suites passed with 11 succeeded, 0 failed, and 3
  intentional pending: `95352-20260802T153011Z`.

Review-fix progression:

- generic resolver specifications passed 5/5, 7/7, and 10/10 at
  `97857-20260802T153503Z`, `99803-20260802T153849Z`, and
  `2190-20260802T154311Z`;
- corresponding generic `Test/compile` runs passed at
  `98365-20260802T153556Z`, `571-20260802T153949Z`, and
  `2752-20260802T154413Z`.

Final review-fix validation passed with 11/11 generic resolver examples at
`7884-20260802T155302Z`; generic `Test/compile` passed at
`8391-20260802T155356Z`. The independent review and two focused re-review
passes are clean. `git diff --check` passed in both admitted repositories.

The three intentional CNCF pending scenarios are later-stage work and are not
GCF-05 failures. Full suites remain reserved for the Phase 55 release gate.
