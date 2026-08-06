# Phase 56 Checklist - Namespace-qualified Component Identity and Derived Coordinates

status=in-progress
started_at=2026-08-06
phase=[Phase 56 - Namespace-qualified Component Identity and Derived Coordinates](phase-56.md)
planning_journal=[Phase 56 Identity Planning and Phase Renumbering](../journal/2026/08/2026-08-06-phase-56-component-identity-planning-and-renumbering.md)
entry_handoff=[Current Work Closeout before Phase 56](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)

This checklist is the authoritative Phase 56 state ledger after Phase 56
starts. Only one stage may be `IN_PROGRESS` at a time. No implementation stage
starts until the current work described by the entry handoff reaches its
bounded stopping point.

Entry evidence: the [closed entry handoff](../journal/2026/08/2026-08-06-current-work-closeout-before-phase-56.md)
records the accepted pre-documentation baseline accumulator diff SHA-256
`da02ef464cb73e745f02a3439975318793375b7dbcf7945ab75e9ad40173e6eb` at HEAD
`e9b00c9ba0f209e2969947c4e454ce769366d625`; this is not the eventual Step
commit hash.

## CID-01: Inventory and Executable Contract Freeze

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF, Cozy/sbt-cozy, CAR, repository, launcher, CBD, and BoK maintainers
- Entry rule: Phase 55 and its admitted closeout work are closed.
- Completion rule: Existing authorities, projections, ambiguities, and exact
  failing-first acceptance identities are recorded before implementation.

- [ ] Inventory `ComponentId`, `ComponentInstanceId`, `Component.Core.name`,
  descriptor models/codecs, project schemas, generators, and runtime loaders.
- [ ] Inventory SBT organization/name/version, Maven group/artifact/version,
  CAR filenames, repository/index/cache keys, and dependency declarations.
- [ ] Inventory JVM packages, generated class names, CML Component names,
  display names, titles, paths, Help/Admin identities, and diagnostics.
- [ ] Inventory every accepted bare, kebab-case, `textus-`-prefixed, and
  qualified spelling and identify whether it is canonical or compatibility.
- [ ] Inventory every admitted CAR repository and freeze its effective
  version, SNAPSHOT/release status, identity shape, derived coordinates, and
  migration owner.
- [ ] Freeze the mandatory Phase 56 migration cohort to CARs whose effective
  version is SNAPSHOT and record every non-SNAPSHOT legacy CAR in a separate
  next-version deferral ledger.
- [ ] Freeze `namespace`, local `id`, qualified ID, release version, and
  presentation metadata boundaries.
- [ ] Freeze word splitting, acronym/digit, package, artifact, filename, and
  path projection algorithms and collision behavior.
- [ ] Register failing-first cross-repository acceptance for the canonical
  User Account example and same-local-ID/different-namespace isolation.

## CID-02: Typed Identity and Derivation Core

Stage Status:
- Current status: PLANNED
- Entry rule: CID-01 is complete.
- Completion rule: One validated typed identity produces every naming
  projection through one tested implementation.

- [ ] Implement or admit `ComponentNamespace` and `ComponentLocalId`.
- [ ] Make `ComponentId` namespace-qualified and make instance identity carry
  that exact Component ID.
- [ ] Implement one deterministic projection API for qualified name, artifact,
  Maven group/artifact, JVM package, generated class, and path segments.
- [ ] Test validation, normalization rejection, acronym/digit boundaries,
  namespace-leaf collisions, and round trips.
- [ ] Prevent display metadata and version from entering identity equality.

## CID-03: Cozy Project Schema and Generation

Stage Status:
- Current status: PLANNED
- Entry rule: CID-02 is complete.
- Completion rule: A CAR project authors identity once and generation produces
  consistent source, build, and descriptor outputs.

- [ ] Add canonical `project.namespace` and `project.id` schema fields.
- [ ] Remove or deprecate independently authored component name, class name,
  Scala package, artifact name, and organization fields.
- [ ] Derive SBT/Maven metadata, JVM package, generated API class, descriptor,
  and CAR filename from the canonical identity plus version.
- [ ] Reject explicitly supplied derived values that disagree during the
  compatibility window.
- [ ] Add scaffold, regeneration, and upgrade tests in Cozy and sbt-cozy.
- [ ] Expose enough canonical/legacy and effective-version evidence for CAR
  lint to classify migration status without guessing identity.

## CID-04: CAR, Maven, and Repository Coordinates

Stage Status:
- Current status: PLANNED
- Entry rule: CID-03 is complete.
- Completion rule: Publication and resolution retain namespace-qualified
  identity and verify every materialized projection.

- [ ] Replace canonical descriptor `name`/`component` inputs with `namespace`
  and `id`; retain version as release metadata.
- [ ] Derive and verify artifact name, CAR filename, Maven coordinate, and
  repository path/index metadata.
- [ ] Include namespace in repository, cache, dependency, and integrity keys
  even when filenames collide.
- [ ] Update dependency declaration codecs and error diagnostics.
- [ ] Test publish, retrieve, cache, offline, and transitive dependency paths.

## CID-05: CNCF Runtime Identity Migration

Stage Status:
- Current status: PLANNED
- Entry rule: CID-04 is complete.
- Completion rule: Runtime loading and all internal consumers use the exact
  qualified `ComponentId`.

- [ ] Align CML declaration, generated Component core, descriptor admission,
  and runtime `ComponentId` without heuristic normalization.
- [ ] Migrate instance IDs, dependency lookup, class loading, configuration
  targets, routing, Help/Admin metadata, logs, and diagnostics.
- [ ] Remove internal artifact-name-as-Component-ID use.
- [ ] Test two namespaces with one local ID through loading and routing.
- [ ] Preserve presentation-only display names and titles.

## CID-06: Compatibility Adapters

Stage Status:
- Current status: PLANNED
- Entry rule: CID-05 is complete.
- Completion rule: Old inputs remain bounded decode aliases and all internal
  state is canonical.

- [ ] Decode legacy descriptor `name`/`component` shapes through an explicit
  compatibility adapter.
- [ ] Admit known bare, artifact, prefixed, and legacy Web-path spellings only
  where a unique canonical identity is available.
- [ ] Reject ambiguity and disagreement with structured diagnostics.
- [ ] Emit only the new descriptor/project identity shape.
- [ ] Record warning, observability, and removal policy for every alias.
- [ ] Preserve non-SNAPSHOT legacy CAR releases without rewriting or
  republishing them solely for identity migration.

## CID-07: CAR Lint and Development CAR Migration

Stage Status:
- Current status: PLANNED
- Entry rule: CID-06 is complete.
- Completion rule: CAR lint enforces the version-sensitive migration policy,
  every CAR in the frozen SNAPSHOT cohort uses canonical authoring, and every
  non-SNAPSHOT legacy CAR has a next-version migration entry.

- [ ] Extend CAR lint to pass canonical identity with consistent projections.
- [ ] Make legacy identity on a SNAPSHOT CAR a migration-required lint error.
- [ ] Detect legacy identity on a non-SNAPSHOT CAR as a
  deferred-to-next-version warning without invalidating the existing release.
- [ ] Prove advancing a deferred CAR beyond its recorded release version,
  whether to a SNAPSHOT or directly to another release, promotes that finding
  to a migration-required lint error.
- [ ] Make canonical/derived disagreement a lint error regardless of version.
- [ ] Include effective version, identity shape, expected projections,
  migration status, owner, and actionable path in lint diagnostics.
- [ ] Migrate Textus User Account to
  `org.simplemodeling.textus + UserAccount`.
- [ ] Verify `org.simplemodeling.textus.UserAccount`,
  `textus-user-account`, `UserAccountComponent`, and
  `org.simplemodeling.textus.useraccount` are projections, not copied inputs.
- [ ] Migrate representative first-party CARs and one same-local-ID fixture.
- [ ] Migrate every admitted CAR whose frozen effective version is SNAPSHOT;
  do not limit Phase 56 adoption to representative fixtures.
- [ ] Leave non-SNAPSHOT CAR releases unchanged and record their exact next
  development version migration owner and entry condition.

## CID-08: Ecosystem Regression and Normative Closure

Stage Status:
- Current status: PLANNED
- Entry rule: CID-07 is complete.
- Completion rule: Cross-repository evidence, CAR lint results, and normative
  documentation show exactly one identity authority and a complete migration
  ledger.

- [ ] Update launchers, samples, CBD Support, BoK, repository metadata, and
  downstream dependency consumers.
- [ ] Verify existing admitted Web routes through compatibility aliases.
- [ ] Run focused and full validation for every modified repository under the
  required serialized SBT execution policy.
- [ ] Run CAR generation, packaging, publication-local, repository-resolution,
  launcher, and representative runtime acceptance.
- [ ] Perform independent review of collisions, compatibility, routing,
  package generation, and coordinate integrity.
- [ ] Promote verified behavior to CNCF/Cozy design and specification.
- [ ] Publish migration guidance for CAR authors and downstream consumers.
- [ ] Record exact commits, commands, results, remaining aliases, and removal
  owners before closing Phase 56.
- [ ] Record a lint-clean result for every frozen SNAPSHOT CAR and a complete
  next-version deferral report for every non-SNAPSHOT legacy CAR.
- [ ] Update Phase 57 entry contracts to consume the qualified Component
  identity without reopening Phase 56 naming decisions.
