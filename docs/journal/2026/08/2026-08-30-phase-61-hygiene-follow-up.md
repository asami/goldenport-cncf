# Phase 61 Hygiene Follow-up

status=open
date=2026-08-30
phase=[Phase 61](../../../phase/phase-61.md)

This non-normative journal records maintenance follow-up without changing
Phase 61 acceptance or beginning a successor Phase.

## HYG-61-IC02-001 — Information CML contract scenario grouping

- Status: OPEN
- Discovered: 2026-08-30, Phase 61 mandatory full review.
- Repository and affected path: `cloud-native-component-framework`;
  `src/test/scala/org/goldenport/cncf/information/InformationCmlCanonicalContractSpec.scala`.
- Evidence: The required/default contract scenario covers the generated root
  and nested import-context behavior in one long Executable Specification
  example, without `which` grouping to make the distinct asserted behaviors
  independently scannable.
- Classification: Hygiene — specification presentation and maintainability;
  no generated CML, runtime, or acceptance behavior is in question.
- Outside reason: Splitting/rewording that scenario would be a non-behavioral
  specification refactor beyond the frozen IC-02 contract delivery.
- Owner and later boundary: A separately authorized Executable Specification
  hygiene task for the Information CML contract.
- Resume condition: Preserve the current assertions, introduce behavior-level
  grouping only, and run the focused contract suite.
- Prohibited workaround: Do not weaken, remove, or reinterpret the required
  and default assertions; do not use this follow-up to begin Phase 61.1.
- Source identity: `HYG-61-IC02-001`; Phase 61 mandatory full review of
  `da8e51bb4b5cf48bf35ed7d05d3741646f177eab..86361c11db588bd4d63836af331372937f955868`.

## HYG-61-IC02-002 — Required/default scenario separation

- Status: OPEN
- Discovered: 2026-08-30, Phase 61 mandatory full review.
- Repository and affected path: `cloud-native-component-framework`;
  `src/test/scala/org/goldenport/cncf/information/InformationCmlCanonicalContractSpec.scala`.
- Evidence: One scenario currently combines root required-field rejection with
  default construction behavior for `InformationImportContext`, making two
  independently meaningful generated-model semantics share one narrative.
- Classification: Hygiene — Executable Specification decomposition; no
  required/default model rule, CML schema, or generator output is disputed.
- Outside reason: Separating the examples would broaden the accepted IC-02
  specification structure after the semantic contract is already reviewed.
- Owner and later boundary: A separately authorized Information CML
  specification hygiene task.
- Resume condition: Split the required root and default nested-value behavior
  into focused scenarios, preserve all current assertions, and run the focused
  contract suite.
- Prohibited workaround: Do not change CML cardinality/default semantics or
  collapse coverage merely to shorten the specification; do not reopen Phase
  61.
- Source identity: `HYG-61-IC02-002`; Phase 61 mandatory full review of
  `da8e51bb4b5cf48bf35ed7d05d3741646f177eab..86361c11db588bd4d63836af331372937f955868`.
