# Phase 53 Checklist - CML ComponentStyle, ExecutionContext, and Capability Resolution

status=planned
phase=[Phase 53 - CML ComponentStyle, ExecutionContext, and Capability Resolution](phase-53.md)
planning_source=[Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation](../journal/2026/07/2026-07-30-phase-53-component-style-execution-context-configuration-consolidation.md)

This checklist is the authoritative Phase 53 state ledger after Phase 53
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 52 closes.

## CS-01: Authority Inventory and Failing-First Contract

Stage Status:
- Current status: PLANNED
- Owner: Phase 53 cross-repository maintainers
- Entry rule: Phase 52 is closed.
- Completion rule: Every current authority, projection, selection path,
  style parameter/policy consumer, and ArtScene behavior is mapped and
  represented by
  failing-first executable evidence.

- [ ] Inventory Cozy/SimpleModeling CML semantic ownership before admitting
  `simplemodeling`.
- [ ] Inventory existing CNCF metadata registries/factories and freeze an
  extension-ready metadata boundary for a future Metadata Factory.
- [ ] Inventory existing component style/default/factory override patterns.
- [ ] Inventory component descriptor schemas and packaged generation.
- [ ] Inventory sbt-cozy development descriptor and classpath production.
- [ ] Inventory the existing `cozyPrepareRuntime` ownership of
  `target/cncf.d/runtime-classpath.txt`,
  `target/cncf.d/car-runtime-manifest.json`, and extensible development
  evidence.
- [ ] Inventory CNCF assembly/configuration precedence and Subsystem startup.
- [ ] Inventory affected `.cncf` configuration and operational-state paths,
  ownership, lifecycle, permissions, backup expectations, and deletion safety.
- [ ] Inventory `simplemodeling-lib` String-keyed `Configuration`,
  `ResolvedConfiguration`, `ConfigurationTrace`, source metadata, and direct
  consumers before selecting the minimal Phase 53 provenance changes.
- [ ] Inventory `OperationMode`, `WebApplicationMode`, launcher inputs, and
  runtime inspection.
- [ ] Inventory ArtScene's local powertype, private configuration key,
  unconditional datastore default, validation, and mode-dependent behavior.
- [ ] Freeze ComponentStyle identity, provider identity, metadata schema,
  duplicate/conflict handling, and built-in catalog ownership.
- [ ] Freeze the provided ComponentCapability bundles, deterministic expansion,
  and required SubsystemCapability contract.
- [ ] Freeze the mode-free ComponentFactory evidence/parameter boundary.
- [ ] Freeze WebApplicationMode, fixed/authenticated user-context provider,
  Subsystem datastore, and ExecutionContext ownership boundaries.
- [ ] Freeze `~/.textus/user-profile.yaml` as the normal-operation baseline
  and `~/.cncf/user-profile.yaml` as the always-admitted higher-precedence
  layer for fixed-user resolution.
- [ ] Freeze `apiVersion: textus/v1`, `kind: FixedUserProfile`,
  common/`subsystems` field overlay, and stable Subsystem identifier lookup.
- [ ] Freeze HOME-only profile-file admission, common-field
  environment/argument input, and controlled
  `ConfigurationOrigin.ExplicitOverride`.
- [ ] Freeze complete FixedUserProfile exclusion from multi-user resolution.
- [ ] Freeze the canonical `textus.web.application-mode` parameter,
  subsystem-specific lookup, resolution before FixedUserProfile, and the
  conditional traceable direct-Component standalone default.
- [ ] Freeze existing `ResolvedConfiguration`/`ConfigurationTrace` as the
  provenance authority and limit generic changes to required origin/source
  metadata.
- [ ] Freeze generic admitted-source order as `.textus` baseline followed by
  `.cncf` override at each HOME/PROJECT/CWD scope, then environment,
  arguments, and controlled explicit override.
- [ ] Freeze typed-contract source admission before generic precedence.
- [ ] Freeze one canonical `textus.*` spelling for each public Phase 53
  semantic even when `.cncf` supplies its value, without a duplicate
  `cncf.*` semantic.
- [ ] Record typed generic keys, generic qualifiers/candidates, namespace
  catalogs, alias normalization, and new binding/environment codecs as Phase
  55 candidates rather than Phase 53 work.
- [ ] Freeze the rule that no operating mode enters Component APIs, generated
  DSLs, ActionCall, or domain policy.
- [ ] Register failing-first built-in style discovery, generation, composition,
  precedence, launch, diagnostic, and ArtScene matrix specifications.
- [ ] Record Metadata Factory style contribution as a future development item,
  not a Phase 53 implementation or acceptance dependency.
- [ ] Confirm no normative design/specification is edited in CS-01.

Evidence:
- Pending.

## CS-02: CNCF Built-in Style Catalog, CML Selection, and Descriptor Projection

Stage Status:
- Current status: PLANNED
- Owner: CNCF built-in metadata, Cozy, and confirmed CML model maintainers
- Entry rule: CS-01 is DONE.
- Completion rule: Explicit COMPONENT CML selects one CNCF-provided built-in
  ComponentStyle and produces one validated, deterministic style snapshot in a
  versioned component descriptor.

- [ ] Define the versioned ComponentStyle metadata contract.
- [ ] Implement CNCF built-in ComponentStyle registration.
- [ ] Include provider/schema identity and a future Metadata Factory extension
  boundary without implementing external style contribution.
- [ ] Reject duplicate built-in style identities and provider/schema
  disagreement.
- [ ] Make the same CNCF built-in style metadata consumable by Cozy generation
  and CNCF runtime.
- [ ] Implement the explicit-component CML style-selection grammar.
- [ ] Reject unknown or unavailable style identifiers.
- [ ] Implement typed, versioned ComponentCapability and
  SubsystemCapability identifiers.
- [ ] Implement deterministic bundle expansion, including `domain.full@1`.
- [ ] Reject duplicate, unknown, cyclic, or version-incompatible capability
  definitions.
- [ ] Implement provided-capability and required-capability descriptor
  projection.
- [ ] Keep operating mode, fixed user, locale, and datastore policy out of the
  Component descriptor.
- [ ] Keep `project.yaml` free of duplicate capability declarations.
- [ ] Generate a deterministic schema-versioned style/provider/parameter
  metadata snapshot.
- [ ] Prove explicit COMPONENT declarations generate independently of service,
  entity, or other model declarations.

Evidence:
- Pending.

## CS-03: Development and Packaged Projection Parity

Stage Status:
- Current status: PLANNED
- Owner: Cozy and sbt-cozy maintainers
- Entry rule: CS-02 is DONE.
- Completion rule: Source-directory and CAR launches receive semantically
  identical capability evidence from one CML/catalog generation.

- [ ] Extend the existing `cozyPrepareRuntime` route with the development
  ComponentStyle and implicit Subsystem descriptor projection.
- [ ] Preserve its ownership of `target/cncf.d/runtime-classpath.txt`,
  `target/cncf.d/car-runtime-manifest.json`, and any additional coherent
  runtime evidence.
- [ ] Define deterministic freshness/generation identity across those files.
- [ ] Keep packaged `component-descriptor.json` semantically equivalent.
- [ ] Reject missing, mixed-generation, and stale development evidence.
- [ ] Prove a development launch does not require `buildCar`.
- [ ] Prove no fallback to an older locally published CAR.

Evidence:
- Pending.

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

Phase 53 is PLANNED. CS-01 has not started.
