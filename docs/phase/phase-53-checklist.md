# Phase 53 Checklist - CML ComponentStyle, ExecutionContext, and Capability Resolution

status=in_progress
phase=[Phase 53 - CML ComponentStyle, ExecutionContext, and Capability Resolution](phase-53.md)
planning_source=[Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation](../journal/2026/07/2026-07-30-phase-53-component-style-execution-context-configuration-consolidation.md)

This checklist is the authoritative Phase 53 state ledger after Phase 53
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 52 closes.

## CS-01: Authority Inventory and Failing-First Contract

Stage Status:
- Current status: DONE
- Owner: Phase 53 cross-repository maintainers
- Entry rule: Phase 52 is closed.
- Completion rule: Every current authority, projection, selection path,
  style parameter/policy consumer, and ArtScene behavior is mapped and
  represented by
  failing-first executable evidence.

- [x] Inventory Cozy/SimpleModeling CML semantic ownership before admitting
  `simplemodeling`; the typed `COMPONENT` authority is Kaleidox.  Cozy is the
  current CS-01A executable-evidence consumer; neither `simplemodeling` nor
  `simplemodeling-lib` adds a CS-01A ownership change.
- [x] Inventory existing CNCF metadata registries/factories.
- [x] Record the future Metadata Factory extension boundary without selecting
  a provider API, discovery mechanism, or wire representation.
- [x] Inventory existing component style/default/factory override patterns.
- [x] Inventory component descriptor schemas and packaged generation.
- [x] Inventory sbt-cozy development descriptor and classpath production.
- [x] Inventory the existing `cozyPrepareRuntime` ownership of
  `target/cncf.d/runtime-classpath.txt`,
  `target/cncf.d/car-runtime-manifest.json`, and extensible development
  evidence.
- [x] Inventory CNCF assembly/configuration precedence and Subsystem startup.
- [x] Inventory affected `.cncf` configuration and operational-state paths,
  ownership, lifecycle, permissions, backup expectations, and deletion safety.
- [x] Inventory `simplemodeling-lib` String-keyed `Configuration`,
  `ResolvedConfiguration`, `ConfigurationTrace`, source metadata, and direct
  consumers before selecting the minimal Phase 53 provenance changes.
- [x] Inventory `OperationMode`, `WebApplicationMode`, launcher inputs, and
  runtime inspection.
- [x] Inventory ArtScene's local powertype, private configuration key,
  unconditional datastore default, validation, and mode-dependent behavior.
- [x] Freeze built-in ComponentStyle/capability semantics and catalog
  ownership while deferring identity encoding, provider/schema API, and wire
  layout to CS-02.
- [x] Freeze the selected built-in style/capability semantics while leaving
  its identity/provider wire form and metadata schema open.
- [x] Freeze the selected ComponentCapability bundle semantics, deterministic
  expansion, and required SubsystemCapability contract; CS-02 owns their typed
  catalog and descriptor implementation.
- [x] Freeze the mode-free ComponentFactory evidence/parameter boundary.
- [x] Freeze WebApplicationMode and ExecutionContext ownership boundaries;
  fixed/authenticated provider and datastore realization remains CS-04/CS-05.
- [x] Record FixedUserProfile source admission, canonical parameters, profile
  overlay, multi-user exclusion, and explicit override as CS-05 acceptance
  requirements without inventing an unavailable resolver API.
- [x] Register canonical `textus.web.application-mode` recognition through the
  current resolver; conditional standalone default eligibility and provenance
  remain CS-05 acceptance work.
- [x] Freeze `ResolvedConfiguration`/`ConfigurationTrace` as the provenance
  authority and register ordinary file-source provenance evidence.
- [x] Record generic admitted-source ordering, typed-contract admission, and
  canonical `textus.*` semantics as CS-05 implementation requirements.
- [x] Record typed generic keys, generic qualifiers/candidates, namespace
  catalogs, alias normalization, and new binding/environment codecs as Phase
  55 candidates rather than Phase 53 work.
- [x] Freeze the rule that no operating mode enters Component APIs, generated
  DSLs, ActionCall, or domain policy.
- [x] Register current observable failing-first CML, provenance, mode-boundary,
  Web-key, and descriptor-identity evidence; discovery/generation/launch and
  ArtScene acceptance matrices remain their owning later stages.
- [x] Record Metadata Factory style contribution as future work, not a Phase 53
  implementation or acceptance dependency.
- [x] Confirm no normative design/specification is edited in CS-01.

Evidence:
- CS-01A records the Kaleidox `ComponentDefinition` ownership boundary in
  [CML authority and style-selection failing-first contract](../notes/phase-53-cs01-cml-authority-and-style-selection-failing-first.md).
- CS-01B records the current owner and consumer boundaries in
  [existing authority inventory](../notes/phase-53-cs01b-existing-authority-inventory.md).
- CS-01C records assembly/startup, mode, launcher, operational-state, and
  supported style semantic boundaries in
  [assembly, mode, operational state, and style boundary](../notes/phase-53-cs01c-assembly-mode-operational-state-and-style-freeze.md).
- CS-01D records OperationMode classification and ComponentStyle semantic
  contract freeze in
  [OperationMode and ComponentStyle contract freeze](../notes/phase-53-cs01d-operation-mode-and-component-style-contract-freeze.md).
- CS-01E records current observable provenance and stable identity boundaries,
  and explicitly defers unavailable profile runtime observation to CS-05, in
  [observable boundary and inventory completion](../notes/phase-53-cs01e-observable-boundary-and-completion.md).
- Cozy `KaleidoxCmlParsingSpec` adds component-only
  `full-fledged-with-standalone` and `domain-only` selection fixtures. CS-02B
  admits the Kaleidox production owner and proves that each authored value is
  retained through the typed `ComponentDefinition` semantic.

## CS-02: CNCF Built-in Style Catalog, CML Selection, and Descriptor Projection

Stage Status:
- Current status: DONE
- Current step: CS-02E admission accepted; CS-03 owns development/package projection parity
- Owner: CNCF built-in metadata, Cozy, and confirmed CML model maintainers
- Entry rule: CS-01 is DONE.
- Completion rule: Explicit COMPONENT CML selects one CNCF-provided built-in
  ComponentStyle and produces one validated, deterministic style snapshot in a
  versioned component descriptor.

- [x] Define the versioned ComponentStyle metadata contract.
- [x] Implement CNCF built-in ComponentStyle registration.
- [x] Include provider/schema identity and a future Metadata Factory extension
  boundary without implementing external style contribution.
- [x] Reject duplicate built-in style identities and provider/schema
  disagreement.
- [x] Make the same CNCF built-in style metadata consumable by Cozy generation
  and CNCF runtime.
- [x] Implement the explicit-component CML style-selection grammar.
- [x] Reject unknown or unavailable style identifiers.
- [x] Implement typed, versioned ComponentCapability and
  SubsystemCapability identifiers.
- [x] Implement deterministic bundle expansion, including `domain.full@1`.
- [x] Reject duplicate, unknown, cyclic, or version-incompatible capability
  definitions.
- [x] Implement provided-capability and required-capability descriptor
  projection.
- [x] Keep operating mode, fixed user, locale, and datastore policy out of the
  Component descriptor.
- [x] Keep `project.yaml` free of duplicate capability declarations.
- [x] Generate a deterministic schema-versioned style/provider/parameter
  metadata snapshot.
- [x] Prove explicit COMPONENT declarations generate independently of service,
  entity, or other model declarations.

Evidence:
- [CS-02 schema decision](../notes/phase-53-cs02-schema-decision.md) records
  the owner approval to formalize the minimum versioned contract.
- [CS-02A versioned contract](../notes/phase-53-cs02a-versioned-component-style-contract.md)
  records the catalog, descriptor-v2, ordering, and compatibility boundary.
- [CS-02B typed CML style selection](../journal/2026/07/2026-07-31-phase-53-cs02b-typed-cml-style-selection.md)
  records the Kaleidox-owned typed selection boundary and Cozy consumer
  evidence.
- [CS-02C catalog handoff and selection admission](../journal/2026/07/2026-07-31-phase-53-cs02c-catalog-handoff-and-selection-admission.md)
  records the digest-protected runtime-descriptor carrier and Cozy's explicit
  selection admission/rejection boundary.
- [CS-02D capability graph validation](../journal/2026/07/2026-07-31-phase-53-cs02d-capability-graph-validation.md)
  records the recursive bundle contract and the matching framework/Cozy
  rejection evidence.
- [CS-02E component-only generation and descriptor projection](../journal/2026/07/2026-07-31-phase-53-cs02e-component-only-descriptor-projection.md)
  records the one-way CML/catalog-to-CAR snapshot projection and the
  `project.yaml` authority guard.

## CS-03: Development and Packaged Projection Parity

Stage Status:
- Current status: DONE
- Owner: Cozy and sbt-cozy maintainers
- Entry rule: CS-02 is DONE.
- Completion rule: Source-directory and CAR launches receive semantically
  identical capability evidence from one CML/catalog generation.

- [x] Extend the existing `cozyPrepareRuntime` route with the development
  ComponentStyle and implicit Subsystem descriptor projection.
- [x] Preserve its ownership of `target/cncf.d/runtime-classpath.txt`,
  `target/cncf.d/car-runtime-manifest.json`, and any additional coherent
  runtime evidence.
- [x] Define deterministic freshness/generation identity across those files.
- [x] Keep packaged `component-descriptor.json` semantically equivalent.
- [x] Reject missing, mixed-generation, and stale development evidence.
- [x] Prove a development launch does not require `buildCar`.
- [x] Prove no fallback to an older locally published CAR.

Evidence:
- [CS-03 development projection parity](../journal/2026/07/2026-07-31-phase-53-cs03-development-projection-parity.md)
  records the v2 writer, v1 reader migration, strict v2 descriptor admission,
  style-less legacy fallback boundary, and the validated fail-closed source
  authority checks.

## CS-04: Subsystem Capability Matching and ExecutionContext Authority

Stage Status:
- Current status: PLANNED
- Owner: CNCF assembly, Subsystem, configuration, and HTTP maintainers
- Entry rule: CS-03 is DONE.
- Completion rule: One Subsystem satisfies every Component requirement and
  constructs the same mode-free ExecutionContext contract for fixed and
  authenticated current users.

- [ ] Resolve the root Component and complete dependency Component closure.
- [ ] Implement forward and reverse provider/requirement capability matching.
- [ ] Reject unknown, missing, ambiguous, and version-incompatible capability
  matches.
- [ ] Validate before class loading, datastore creation, binding, or job
  admission.
- [ ] Preserve `WebApplication` and `WebApplicationMode` as the Web context
  model and keep it inside the Web boundary.
- [ ] Implement mode-free implicit Subsystem ExecutionProfiles for fixed,
  authenticated, and controlled test identity evidence.
- [ ] Make WebApplication profiles project onto Subsystem ExecutionProfiles
  without making WebApplicationMode the universal provider selector.
- [ ] Implement ingress-independent fixed-user context-provider resolution.
- [ ] Implement ingress-independent authenticated-user context construction.
- [ ] Produce canonical SecurityContext, formatting, authorization, datastore,
  and UnitOfWork bindings in both paths.
- [ ] Prove the Component-facing ExecutionContext contract cannot reveal which
  construction path was used.
- [ ] Add structured capability and context-construction diagnostics.

Evidence:
- Pending.

## CS-05: Fixed User, Subsystem Datastore, Launcher, and Diagnostics

Stage Status:
- Current status: PLANNED
- Owner: simplemodeling-lib, CNCF configuration/datastore, and launcher
  maintainers
- Entry rule: CS-04 is DONE.
- Completion rule: FixedUserProfile and Subsystem datastore configuration
  resolve before Component execution with inspectable provenance, while
  packaged/development launch surfaces behave deterministically.

- [ ] Implement the typed `apiVersion: textus/v1`,
  `kind: FixedUserProfile` contract in `goldenport-cncf`.
- [ ] Implement the `~/.textus/user-profile.yaml` normal-operation baseline.
- [ ] Implement `~/.cncf/user-profile.yaml` as an always-admitted
  higher-precedence overlay using the same schema.
- [ ] Prove FixedUserProfile admission is not gated by `OperationMode`.
- [ ] Apply low-to-high field precedence across Textus common, Textus
  subsystem, CNCF common, CNCF subsystem, common-field environment,
  common-field arguments, and controlled runtime/test override.
- [ ] Reject PROJECT/CWD FixedUserProfile documents.
- [ ] Establish the stable Subsystem identifier from the explicit or implicit
  Subsystem descriptor before subsystem-specific profile or Web-operation
  lookup.
- [ ] Preserve development-directory and packaged-CAR stable Subsystem
  identity parity.
- [ ] Add `ConfigurationOrigin.ExplicitOverride` after arguments without
  introducing a parallel provenance model.
- [ ] Retain source path/input identity, `.textus`/`.cncf` layer,
  common/subsystem target, stable Subsystem identity, logical field key,
  overridden history, and effective source for every resolved value.
- [ ] Require a stable fixed UserId and diagnose identity changes as data
  migration.
- [ ] Require isolated data when any override changes the fixed UserId unless
  an explicit migration is performed.
- [ ] Ignore both HOME FixedUserProfile documents in multi-user operation and
  keep fixed-user fallback out of authenticated ingress.
- [ ] Resolve locale/timezone from the effective fixed or authenticated user
  into RuntimeContext.FormattingContext.
- [ ] Keep provider, endpoint/path, credential reference, local/shared
  placement, and lifecycle in Subsystem datastore configuration.
- [ ] Provide the same mode-free datastore and EntityStore interfaces to
  Component execution.
- [ ] Restrict ComponentFactory to typed, side-effect-free Component parameters
  and capability implementation evidence.
- [ ] Prohibit ComponentFactory mode input, fixed-user lookup, user-context
  provider selection, datastore selection, and mode-specific output.
- [ ] Resolve canonical `textus.web.application-mode` before
  FixedUserProfile.
- [ ] Keep `cncf.web.application-mode` from becoming a duplicate semantic.
- [ ] Contribute the traceable normal direct-Component `standalone` default
  only when the implicit Subsystem and Component capability conditions hold.
- [ ] Keep Web operation selection out of FixedUserProfile and wrapper
  launcher semantics.
- [ ] Expose style/provider, capabilities, requirement matches,
  WebApplicationMode, context provider, effective non-secret fixed-user
  values, field-level source provenance, and datastore binding in operator
  inspection.
- [ ] Keep diagnostics secret-safe.
- [ ] Prove Phase 53 does not rename, migrate, overwrite, or delete unrelated
  CNCF operational state while changing configuration precedence.
- [ ] Prove no ApplicationMode, ComponentMode, SubsystemMode,
  WebApplicationMode, or OperationMode enters Component execution.

Evidence:
- Pending.

## CS-06: ArtScene Adoption and Real Acceptance

Stage Status:
- Current status: PLANNED
- Owner: ArtScene and Phase 53 integration maintainers
- Entry rule: CS-05 is DONE.
- Completion rule: ArtScene selects the CNCF component ComponentStyle and
  operates unchanged with fixed and authenticated current-user
  ExecutionContexts without a local competing authority.

- [ ] Select `full-fledged-with-standalone` in ArtScene CML.
- [ ] Generate `domain.full@1`, `user.multi-user@1`, and
  `user.fixed-context-compatible@1` capability evidence.
- [ ] Remove the private ApplicationMode, private mode configuration key,
  `local-default`/`external-required` Component policy, local mode powertype,
  hardcoded standalone UserId, and mode-dependent Component branches.
- [ ] Keep Web-only presentation differences in the WebApplication adapter.
- [ ] Resolve current user and locale entirely through ExecutionContext.
- [ ] Preserve standalone fixed-user ownership behavior.
- [ ] Preserve multi-user authenticated-user and administration behavior.
- [ ] Prove default standalone and explicit standalone launches.
- [ ] Prove `.cncf/user-profile.yaml` remains admitted in every OperationMode
  for fixed-user resolution.
- [ ] Prove multi-user reads neither FixedUserProfile document and never falls
  back to the fixed user.
- [ ] Prove `textus-art-scene` is the stable Subsystem identifier for
  development-directory and packaged-CAR direct launch.
- [ ] Prove Subsystem-owned local/shared datastore binding for both Web
  operations.
- [ ] Prove `develop` and `production` datastore isolation/ownership for both
  Web operations.
- [ ] Prove development standalone cannot overwrite production standalone
  data implicitly.
- [ ] Run the same representative Component operations with equivalent fixed
  and authenticated ExecutionContexts and compare domain semantics.
- [ ] Prove Component mode input is absent in the complete
  OperationMode/WebApplicationMode matrix.
- [ ] Run equivalent source-directory and packaged-CAR acceptance.

Evidence:
- Pending.

## CS-07: Full Validation and Post-Implementation Promotion

Stage Status:
- Current status: PLANNED
- Owner: all admitted Phase 53 repository maintainers
- Entry rule: CS-06 is DONE.
- Completion rule: All modified repositories and real acceptance are green,
  clean review passes, and normative documentation describes verified
  implementation rather than the initial proposal.

- [ ] Full-test every modified required repository.
- [ ] Run clean read-only review over the complete Phase 53 implementation.
- [ ] If the clean review reports actionable findings, fix every finding.
- [ ] After review-fix only, run focused clean re-review over the changed
  problem areas; skip re-review when the initial clean review has no findings.
- [ ] Re-run affected and final full validation.
- [ ] Write CNCF design and specification from verified behavior.
- [ ] Record the verified extension-ready metadata boundary and hand off the
  future Metadata Factory ComponentStyle development item.
- [ ] Write Cozy/CML style-selection and descriptor specification from
  verified generation.
- [ ] Write launcher development-projection specification.
- [ ] Promote only the minimal verified generic configuration/provenance
  changes and retain the larger generic framework proposal as Phase 55 work.
- [ ] Update ArtScene specification and operations documentation.
- [ ] Mark the notes proposal implemented/superseded with exact normative
  links.
- [ ] Update strategy and Phase 53 evidence.
- [ ] Close Phase 53 only after every completion rule passes.

Evidence:
- Pending.

## Planning Baseline

No runtime, CML generator, launcher, or ArtScene source is modified by this
planning-only insertion. No normative `docs/design` or `docs/spec` document is
created or changed at planning time.

The Phase 53 required repository set is provisionally:

- `cozy`;
- `sbt-cozy`;
- `simplemodeling-lib`;
- `cloud-native-component-framework`;
- `cncf-launcher`;
- `textus-launcher`; and
- `textus-art-scene`.

CS-01 may admit `simplemodeling` or a focused sample repository only with
recorded ownership or acceptance evidence.

## Current State

Phase 53 is IN_PROGRESS. CS-01A through CS-01E have completed the current
authority inventory and registered observable failing-first contracts. Exact
ComponentStyle catalog identity/provider/schema and descriptor wire behavior
remain deliberately owned by CS-02. FixedUserProfile, configuration overlay,
and conditional Web-default runtime acceptance remain deliberately owned by
CS-05.
