# Phase 55 Checklist - Typed Configuration Binding and Provenance Resolution

status=planned
phase=[Phase 55 - Typed Configuration Binding and Provenance Resolution](phase-55.md)
provisional_specification=[Configuration Binding Provisional Specification](../notes/phase-55-configuration-binding-provisional-specification.md)

This checklist is the authoritative Phase 55 state ledger after Phase 55
starts. Only one stage may be `IN_PROGRESS` at a time. No implementation stage
starts before Phase 54 closes and GCF-01 freezes the admitted contract and
repository set.

## GCF-01: Inventory and Binding-Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: generic configuration and CNCF runtime maintainers
- Entry rule: Phase 54 is closed.
- Completion rule: Existing behavior, selected names, invariants,
  compatibility, redaction, repository ownership, and failing-first boundary
  are fixed without creating normative design/spec.

- [ ] Inventory all `Configuration`, `ResolvedConfiguration`,
  `ConfigurationTrace`, resolver, merge, and source contracts.
- [ ] Inventory direct String-key constructors, map access, lookup, trace,
  alias, environment, argument, and serialized-diagnostic consumers.
- [ ] Inventory physical-source discovery and confirm where a source can
  currently load more than once.
- [ ] Freeze the typed `ConfigurationParameter[A]` contract.
- [ ] Freeze the final name and role of `ConfigurationBinding[A]`.
- [ ] Freeze unresolved-candidate and resolved-collection names and invariants.
- [ ] Freeze Global, ComponentClass, SubsystemInstance, and fully qualified
  ComponentInstance as the initial semantic targets.
- [ ] Freeze `SubsystemInstanceId(subsystem, instance)` and retain
  `ComponentInstanceId(component, instance)` as separate validated identities.
- [ ] Freeze ComponentInstance target identity as the containing
  SubsystemInstanceId plus ComponentInstanceId.
- [ ] Freeze explicit and implicit Subsystems under the same stable
  SubsystemInstance target contract.
- [ ] Record that unqualified syntax receives its target from document
  location and is not a semantic target.
- [ ] Freeze the equivalent consolidated `~/.textus/config.yaml` and split
  `~/.textus/components/*` / `~/.textus/subsystems/*` projections.
- [ ] Freeze explicit `instances/<instance>` addressing and the initial rule
  that the canonical default spelling remains `instances/default`.
- [ ] Freeze duplicate/conflict behavior when both physical forms define one
  canonical parameter for the same target in one layer.
- [ ] Freeze direct overridden-binding chain semantics and ordering.
- [ ] Freeze source precedence versus same-source target specificity.
- [ ] Freeze namespace, alias, compatibility, and migration policy.
- [ ] Freeze confidential-value storage and redacted-projection rules.
- [ ] Inventory the owner-deferred Phase 53 profile binding, detailed
  provenance, explicit-override, and ambient-environment requirements before
  freezing their public contracts.
- [ ] Inventory Phase 53 fixed-user identity/change, migration/isolation,
  formatting, and secret-safe diagnostic requirements without reopening profile
  admission semantics.
- [ ] Freeze the admitted repository set and ownership split.
- [ ] Update failing-first acceptance from the completed inventory.

Evidence:
- Pending.

## GCF-02: Failing-First Typed Binding Contract

Stage Status:
- Current status: PLANNED
- Owner: `simplemodeling-lib` configuration maintainers
- Entry rule: GCF-01 is DONE.
- Completion rule: Executable specifications fail for every selected binding,
  collection, typing, provenance, override, conflict, and redaction invariant.

- [ ] Specify parameter/value type coupling.
- [ ] Specify validated parameter, target, provenance, and binding creation.
- [ ] Specify same-parameter/type override-chain invariants.
- [ ] Specify candidate multiplicity and resolved uniqueness.
- [ ] Specify typed lookup without winning-target knowledge.
- [ ] Specify duplicate/conflict rejection.
- [ ] Specify confidential current and historical value redaction.
- [ ] Specify stable fixed-user identity change diagnosis and the required
  explicit-migration-or-isolation outcome.
- [ ] Specify that trace is derived and cannot diverge from effective values.
- [ ] Specify one-load-per-source resolution snapshots.
- [ ] Specify that String lookup is not a second internal authority.

Evidence:
- Pending.

## GCF-03: Typed Parameter and Binding Core

Stage Status:
- Current status: PLANNED
- Owner: `simplemodeling-lib` configuration maintainers
- Entry rule: GCF-02 is DONE.
- Completion rule: Generic typed parameters, targets, provenance, bindings,
  factories, and typed lookup satisfy the failing-first core contract without
  Textus/CNCF semantics.

- [ ] Implement validated canonical parameter identity.
- [ ] Implement typed parameter definitions and value codecs.
- [ ] Implement validated Component, Subsystem, SubsystemInstance, and
  ComponentInstance identities and semantic targets.
- [ ] Enforce containing-SubsystemInstance qualification for every
  ComponentInstance target.
- [ ] Implement complete, bounded configuration provenance.
- [ ] Implement immutable typed ConfigurationBinding.
- [ ] Implement direct overridden-binding linkage.
- [ ] Enforce type, identity, acyclicity, and construction invariants.
- [ ] Implement typed lookup support for heterogeneous bindings.
- [ ] Keep generic core free of Textus/CNCF namespace meaning.

Evidence:
- Pending.

## GCF-04: Source Decoding and Candidate Construction

Stage Status:
- Current status: PLANNED
- Owner: generic source, codec, and runtime maintainers
- Entry rule: GCF-03 is DONE.
- Completion rule: Each admitted physical source loads once and produces
  immutable validated binding candidates with exact provenance.

- [ ] Create the unresolved candidate collection.
- [ ] Load each source exactly once per resolution snapshot.
- [ ] Assign runtime-owned rank and stable ordinal.
- [ ] Decode canonical parameter, target, and typed value.
- [ ] Decode consolidated and target-tree-split Textus documents into the same
  canonical candidate representation.
- [ ] Decode top-level `components/<component-id>` as ComponentClass.
- [ ] Decode `subsystems/<subsystem-id>/instances/<instance>` as
  SubsystemInstance.
- [ ] Decode nested
  `components/<component-id>/instances/<instance>` as ComponentInstance owned
  by the containing SubsystemInstance.
- [ ] Normalize admitted aliases before binding construction.
- [ ] Retain original input key, typed-document field path, layer, and source.
- [ ] Reject malformed, duplicate, or conflicting same-source bindings.
- [ ] Reject same-parameter/same-target collisions across consolidated and
  split documents in one admitted layer.
- [ ] Preserve contract-derived default evidence without making trace an input.

Evidence:
- Pending.

## GCF-05: Deterministic Resolution and Override History

Stage Status:
- Current status: PLANNED
- Owner: generic resolver maintainers
- Entry rule: GCF-04 is DONE.
- Completion rule: One immutable candidate collection resolves independently
  for selected Subsystem instances into one deterministic winner and exact
  override chain per canonical parameter.

- [ ] Resolve Global-only requests.
- [ ] Apply matching Global, ComponentClass, SubsystemInstance, and
  ComponentInstance specificity within one source.
- [ ] Apply source precedence across sources.
- [ ] Record the previous winner as the direct overridden binding.
- [ ] Exclude invalid and context-ineligible candidates from override history.
- [ ] Preserve rejected-candidate diagnostics separately from winning history.
- [ ] Resolve multiple Subsystem instances and Component instances
  independently from one candidate snapshot.
- [ ] Prove deterministic precedence, conflict, and history behavior.

Evidence:
- Pending.

## GCF-06: Resolved Collection and Trace Projection

Stage Status:
- Current status: PLANNED
- Owner: generic configuration, trace, and diagnostics maintainers
- Entry rule: GCF-05 is DONE.
- Completion rule: The resolved binding collection is the sole effective-value
  authority and all trace/diagnostic output is a sanitized projection.

- [ ] Implement one-winner-per-parameter resolved collection.
- [ ] Implement typed binding and value lookup.
- [ ] Derive trace from effective binding and override chain.
- [ ] Remove separate trace mutation from resolution.
- [ ] Align trace key, effective value, winning provenance, and history by
  construction.
- [ ] Implement bounded sanitized `explain-config` projection.
- [ ] Redact confidential effective and overridden values.

Evidence:
- Pending.

## GCF-07: Textus/CNCF Parameter Catalog Adoption

Stage Status:
- Current status: PLANNED
- Owner: CNCF configuration, StandaloneUserProfile, and runtime maintainers
- Entry rule: GCF-06 is DONE.
- Completion rule: Phase 53 Textus/CNCF layering and typed profile semantics
  use registered generic bindings without moving domain semantics into
  `simplemodeling-lib`.

- [ ] Register closed `textus.*` and `cncf.*` namespaces.
- [ ] Register public/internal parameter definitions and allowed targets.
- [ ] Preserve one canonical parameter per semantic.
- [ ] Map typed-document field paths through owning schema adapters.
- [ ] Migrate `.textus` baseline and `.cncf` override contributions.
- [ ] Migrate StandaloneUserProfile and WebApplication-related parameters.
- [ ] Adopt fixed-user identity and formatting only through resolved typed
  bindings; reject silent data reuse after an identity change.
- [ ] Preserve Phase 53 provenance, default, and ExecutionContext boundaries.
- [ ] Keep Components unaware of source, layer, target selection, and raw
  trace.

Evidence:
- Pending.

## GCF-08: External Codecs and Boundary Adapters

Stage Status:
- Current status: PLANNED
- Owner: generic codec, CNCF runtime, and launcher maintainers
- Entry rule: GCF-07 is DONE.
- Completion rule: Every admitted String boundary round-trips through an
  explicit codec and no boundary adapter becomes an internal authority.

- [ ] Implement canonical binding-string codec.
- [ ] Implement collision-free environment binding codec.
- [ ] Reject malformed and non-canonical encodings.
- [ ] Preserve exact Subsystem identity round-trip.
- [ ] Keep file, environment, argument, launcher, and diagnostic boundaries
  explicit.
- [ ] Prove codec round-trip and non-collision properties.
- [ ] Keep launchers free of parameter semantics.

Evidence:
- Pending.

## GCF-09: Admitted Consumer Migration

Stage Status:
- Current status: PLANNED
- Owner: assigned from the GCF-01 repository inventory
- Entry rule: GCF-08 is DONE.
- Completion rule: Every frozen consumer uses typed binding lookup and no
  temporary internal String authority remains.

- [ ] Migrate direct constructors and map access.
- [ ] Migrate resolver, merge, and trace consumers.
- [ ] Migrate admitted runtime and launcher boundary consumers.
- [ ] Remove or confine temporary String adapters to external codecs.
- [ ] Validate every admitted repository with focused and full tests.
- [ ] Record deferred, unadmitted consumers without speculative mutation.

Evidence:
- Pending.

## GCF-10: Regression, Review, and Normative Closure

Stage Status:
- Current status: PLANNED
- Owner: configuration, CNCF runtime, documentation, and release maintainers
- Entry rule: GCF-09 is DONE.
- Completion rule: Full validation and clean review pass, and verified behavior
  is promoted to normative design/spec.

- [ ] Run typed parameter, binding, candidate, resolution, trace, and codec
  specifications.
- [ ] Run source-precedence and
  Global/ComponentClass/SubsystemInstance/ComponentInstance resolution
  matrices.
- [ ] Run confidential-value redaction specifications.
- [ ] Run fixed-user identity-change, migration/isolation, formatting, and
  secret-safe diagnostic specifications.
- [ ] Run `simplemodeling-lib` focused and full tests.
- [ ] Run CNCF focused and full tests.
- [ ] Run every admitted consumer's applicable full tests.
- [ ] Run naming, executable-specification, and `git diff --check` gates.
- [ ] Perform independent read-only review.
- [ ] Apply every actionable review finding.
- [ ] Perform focused re-review only when fixes were required.
- [ ] Create or update normative generic-configuration design documentation.
- [ ] Create or update normative generic-configuration specification.
- [ ] Update strategy, phase, checklist, developer, and migration guidance.
- [ ] Record exact test, review, migration, and release evidence.
- [ ] Close Phase 55 only after all completion rules pass.

Evidence:
- Pending.

## Current Status

Phase 55 is PLANNED. The ConfigurationBinding-centered direction is accepted
for planning; GCF-01 has not started.
