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
- [x] Record StandaloneUserProfile source admission and multi-user exclusion as
  CS-05 acceptance requirements without inventing an unavailable resolver API;
  CS-05B later defers effective binding, canonical parameter spelling, and
  explicit override to Phase 55 ConfigurationBinding work.
- [x] Register canonical `textus.web.application-mode` recognition through the
  current resolver; conditional standalone default eligibility and provenance
  remain CS-05 acceptance work.
- [x] Freeze `ResolvedConfiguration`/`ConfigurationTrace` as the provenance
  authority and register ordinary file-source provenance evidence.
- [x] Record typed-contract admission as CS-05 work; generic source ordering
  and canonical `textus.*` semantics are Phase 55 ConfigurationBinding
  contract candidates.
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
- Current status: DONE
- Owner: CNCF assembly, Subsystem, configuration, and HTTP maintainers
- Entry rule: CS-03 is DONE.
- Completion rule: One Subsystem satisfies every Component requirement and
  constructs the same mode-free ExecutionContext contract for fixed and
  authenticated current users.

- [x] Resolve the root Component and complete dependency Component closure.
- [x] Implement forward and reverse provider/requirement capability matching.
- [x] Reject unknown, missing, ambiguous, and version-incompatible capability
  matches.
- [x] Validate before class loading, datastore creation, binding, or job
  admission.
- [x] Preserve `WebApplication` and `WebApplicationMode` as the Web context
  model and keep it inside the Web boundary.
- [x] Implement mode-free implicit Subsystem ExecutionProfiles for fixed,
  authenticated, and controlled test identity evidence.
- [x] Make WebApplication profiles project onto Subsystem ExecutionProfiles
  without making WebApplicationMode the universal provider selector.
- [x] Implement ingress-independent fixed-user context-provider resolution.
- [x] Implement ingress-independent authenticated-user context construction.
- [x] Produce canonical SecurityContext, formatting, authorization, datastore,
  and UnitOfWork bindings in both paths.
- [x] Prove the Component-facing ExecutionContext contract cannot reveal which
  construction path was used.
- [x] Add structured capability and context-construction diagnostics.

Evidence:
- [CS-04 assembly admission boundary](../journal/2026/07/2026-07-31-phase-53-cs04-assembly-admission-boundary.md)
  records the descriptor-only closure and provider-authority sequence.

## CS-05: Fixed User, Subsystem Datastore, Launcher, and Diagnostics

Stage Status:
- Current status: DONE
- Owner: simplemodeling-lib, CNCF configuration/datastore, and launcher
  maintainers
- Entry rule: CS-04 is DONE.
- Completion rule: strict StandaloneUserProfile admission, stable Subsystem
  identity, Web sequencing, Component-boundary exclusion, and deterministic
  launcher transport have executable evidence. Unimplemented binding,
  migration, datastore-policy, diagnostics, Factory-policy, and CS-06
  acceptance work is relocated below with an explicit owner.

- [x] Implement the typed `apiVersion: textus/v1`,
  `kind: StandaloneUserProfile` contract in `goldenport-cncf`.
- [x] Implement the `~/.textus/user-profile.yaml` standalone baseline.
- [x] Implement `~/.cncf/user-profile.yaml` as the second always-admitted
  standalone layer using the same schema; effective precedence remains pending.
- [x] Prove StandaloneUserProfile admission is not gated by `OperationMode`.
- [x] Record the owner-approved CS-05B boundary: effective profile binding and
  precedence, detailed field provenance, `ConfigurationOrigin.ExplicitOverride`,
  and ambient environment conversion are Phase 55 ConfigurationBinding work;
  Phase 53 retains only admission and existing trace evidence.
- [x] Deferred to Phase 55: apply low-to-high field precedence across Textus common, Textus
  subsystem, CNCF common, CNCF subsystem, common-field environment,
  common-field arguments, and controlled runtime/test override. Deferred to
  Phase 55 ConfigurationBinding work.
- [x] Reject PROJECT/CWD StandaloneUserProfile documents.
- [x] Establish the stable Subsystem identifier from the explicit or implicit
  Subsystem descriptor before subsystem-specific profile or Web-operation
  lookup.
- [x] Preserve development-directory and packaged-CAR stable Subsystem
  identity parity.
- [x] CS-05D: rename the thirteen internal `Component.Factory` DSL operations
  to camelCase, make fixed lookup operations final, retain the reviewed
  authorization and construction extension points, and migrate every framework
  call site.
- [x] CS-05D: prove the final fixed surface, legacy-name removal, and retained
  authorization/construction extension points with an executable specification.
- [x] CS-05D: record external-CAR source/binary migration impact; Phase-level
  full validation and release coordination remain pending.
- [x] CS-05G: remove the dormant public `Component.Config` mode/configuration
  authority and prove Factory-facing inputs retain no raw resolved
  configuration, CLI run mode, Web mode, operation mode, or Subsystem profile.
  Focused validation and review-fix re-review are clean.
- [x] CS-05E: admit only `textus.web.application-mode` as the canonical Web
  operation parameter; former CNCF and execution-scoped spellings cannot select
  or override it. Focused validation and review-fix re-review are clean.
- [x] Deferred to Phase 55: add `ConfigurationOrigin.ExplicitOverride` after arguments without
  introducing a parallel provenance model. Deferred to Phase 55
  ConfigurationBinding work.
- [x] Deferred to Phase 55: retain source path/input identity, `.textus`/`.cncf` layer,
  common/subsystem target, stable Subsystem identity, logical field key,
  overridden history, and effective source for every resolved value. Detailed
  provenance and effective binding are deferred to Phase 55
  ConfigurationBinding work.
- [x] Deferred to Phase 55 GCF-01/GCF-02/GCF-07/GCF-10: require a stable
  fixed UserId and diagnose identity changes as data migration.
- [x] Deferred to Phase 55 GCF-02/GCF-07/GCF-10: require isolated data when
  any override changes the fixed UserId unless an explicit migration is
  performed; silent data reuse is prohibited.
- [x] Prove the StandaloneUserProfile resolver seam returns no HOME layers for
  authenticated or controlled-test evidence.
- [x] CS-05K: prove authenticated ingress has no local fixed-user fallback at
  the runtime boundary. Controlled execution remains covered by CS-05I's
  profile-admission exclusion, not by a Web-request propagation claim.
- [x] Deferred to Phase 55 GCF-07 and CS-06 acceptance: resolve locale/timezone
  from the effective fixed or authenticated user into
  RuntimeContext.FormattingContext.
- [x] Deferred to Phase 54 DSP-01--DSP-04: keep provider, endpoint/path,
  credential reference, local/shared placement, and lifecycle in Subsystem
  datastore configuration.
- [x] CS-05K: provide the same mode-free datastore and EntityStore interfaces
  to Component execution.
- [x] Deferred to strategy candidate 9.53: restrict ComponentFactory to typed,
  side-effect-free Component parameters and capability-implementation evidence.
  CS-05D/G prove typed parameter keys and mode/configuration-carrier exclusion.
- [x] Deferred to strategy candidate 9.53 (with Phase 54/55 owners for
  datastore/fixed-user resolution): prohibit ComponentFactory fixed-user
  lookup, provider/datastore selection, and mode-specific output. CS-05G
  proves the mode/configuration-carrier portion.
- [x] Resolve the canonical `textus.web.application-mode` at the Web boundary
  and contribute its traceable conditional default.
- [x] CS-05I: sequence Web-operation resolution before fixed-user runtime
  StandaloneUserProfile admission; authenticated and controlled execution do
  not invoke HOME-profile admission, and fixed admission requires a
  descriptor-owned stable Subsystem identity.
- [x] Keep `cncf.web.application-mode` from becoming a duplicate semantic.
- [x] Contribute the traceable normal direct-Component `standalone` default
  only when the implicit Subsystem and Component capability conditions hold.
- [x] CS-05J: keep Web operation selection out of StandaloneUserProfile and
  wrapper launcher semantics. CNCF and Textus transport target/runtime
  selection plus runtime arguments without Web/user/datastore interpretation;
  the document contract contains no Web operation field.
- [x] Deferred to Phase 55 GCF-06/GCF-10 for sanitized effective binding/trace
  and Phase 60 ADM-03--ADM-05 for the operator surface: expose style/provider,
  capabilities, context, non-secret fixed-user values, trace evidence, and
  datastore binding in operator inspection.
- [x] Deferred to Phase 55 GCF-01/GCF-02/GCF-06/GCF-10 and Phase 60 rendering:
  keep diagnostics secret-safe.
- [x] CS-05F: prove profile admission does not rename, migrate, overwrite, or
  delete unrelated CNCF operational state. Review-fix validation and focused
  re-review are clean.
- [x] Deferred to CS-06 acceptance: prove no ApplicationMode, ComponentMode,
  SubsystemMode, WebApplicationMode, or OperationMode enters Component
  execution. CS-05G covers Factory/create/init inputs.

Deferred-owner ledger:

- Phase 54 owns datastore provider/target/principal/credential/lifecycle
  metadata and managed realization; Phase 53 makes no datastore-policy change.
- Phase 55 owns effective binding, fixed-user identity/migration/isolation,
  fixed-user formatting, provenance, redacted diagnostics, and explicit
  override behavior; it preserves Phase 53 admission semantics.
- Phase 60 owns the operator-admin presentation after Phase 55 supplies
  sanitized evidence.
- Strategy candidate 9.53 owns the unproven general ComponentFactory purity
  and capability-implementation policy. CS-06 owns final Component-execution
  and ArtScene acceptance.

Evidence:
- [CS-05 standalone-user configuration admission](../journal/2026/07/2026-07-31-phase-53-cs05-standalone-user-configuration-admission.md)
  records the resolver/admission ownership, fail-closed implementation order,
  and CS-05B deferral boundary.
- [CS-05C stable Subsystem identity](../journal/2026/08/2026-08-01-phase-53-cs05c-stable-subsystem-identity.md)
  records strict descriptor-owned direct-bootstrap identity, the separate
  general-discovery fallback, and development/packaged projection parity.
- [CS-05D Component.Factory internal DSL boundary](../journal/2026/08/2026-08-01-phase-53-component-factory-internal-dsl-extension-boundary.md)
  records the finalized internal operation surface and focused evidence; its
  [external-CAR migration note](../journal/2026/08/2026-08-01-component-factory-public-api-finalization-migration.md)
  records the intentional compatibility break.
- CS-05L in the standalone-user admission journal records this closure
  disposition; it moves no runtime behavior.

## CS-06: ArtScene Adoption and Real Acceptance

Stage Status:
- Current status: IMPLEMENTED with explicit Phase 54/55 migration deferrals
- Owner: ArtScene and Phase 53 integration maintainers
- Entry rule: CS-05 is DONE.
  Completion rule: ArtScene selects the CNCF ComponentStyle, exposes its
  generated capability evidence, accepts the canonical Web operation key, and
  preserves existing fixed/authenticated execution and generated CRUD
  authorization. Rebinding the retained legacy application-mode and datastore
  policy is explicitly owned by Phase 54/55, not silently completed here.

- [x] Select `full-fledged-with-standalone` in ArtScene CML.
- [x] Generate `domain.full@1`, `user.multi-user@1`, and
  `user.fixed-context-compatible@1` capability evidence.
- [x] Defer removal/rebinding of the private `ApplicationMode`, legacy mode
  configuration, mode powertype, fixed standalone identity, and dependent
  Component branches to Phase 55 `ConfigurationBinding`; existing behavior is
  preserved and receives no migration-completion credit in Phase 53.
- [x] Defer the retained `local-default`/`external-required` datastore policy
  to Phase 54 datastore binding/lifecycle work.
- [x] Keep the canonical Web operation projection at the WebApplication
  boundary; moving ArtScene's remaining presentation mode contract is Phase 55
  work.
- [x] Resolve formatting, scope, and UnitOfWork access through public
  `ExecutionContext` members; fixed-user identity rebinding remains Phase 55.
- [x] Preserve standalone fixed-user ownership behavior.
- [x] Preserve multi-user authenticated-user and administration behavior,
  including generated CRUD mutation denial for non-administrators.
- [x] Prove default standalone and explicit standalone launch resolution at the
  framework Web-operation boundary.
- [x] Prove `.cncf/user-profile.yaml` remains admitted in every OperationMode
  for fixed-user resolution.
- [x] Prove multi-user reads neither StandaloneUserProfile document and never falls
  back to the fixed user.
- [x] Prove `textus-art-scene` is the stable Subsystem identifier for
  development-directory and packaged-CAR direct launch.
- [x] Defer Subsystem-owned local/shared datastore binding, develop/production
  isolation, and no-overwrite acceptance to Phase 54.
- [x] Run representative standalone and authenticated Component operation
  acceptance, including subject-scoped reads and administrator-only generated
  mutations.
- [x] Defer absence of all legacy Component mode inputs across the complete
  matrix to Phase 55's migration acceptance.
- [x] Defer equivalent source-directory and packaged-CAR acceptance to the
  Phase 54/55 migration packages, because it depends on their datastore and
  effective-binding contracts.

Evidence:
- ArtScene CML generation reports `full-fledged-with-standalone@1` with
  `domain.full@1`, `user.fixed-context-compatible@1`, and
  `user.multi-user@1`.
- Focused ArtScene acceptance covers canonical assembly Web keys, static Web
  formatting through `ExecutionContext`, standalone/multi-user behavior, and
  multi-user generated CRUD authorization.

## CS-07: Full Validation and Post-Implementation Promotion

Stage Status:
- Current status: IMPLEMENTED and VERIFIED
- Owner: all admitted Phase 53 repository maintainers
- Entry rule: CS-06 is DONE.
- Completion rule: All modified repositories and real acceptance are green,
  clean review passes, and normative documentation describes verified
  implementation rather than the initial proposal.

- [x] Full-test every modified required repository.
- [x] Run clean read-only review over the complete Phase 53 implementation.
- [x] If the clean review reports actionable findings, fix every finding.
- [x] After review-fix only, run focused clean re-review over the changed
  problem areas; skip re-review when the initial clean review has no findings.
- [x] Re-run affected and final full validation.
- [x] Write CNCF design and specification from verified behavior.
- [x] Record the verified extension-ready metadata boundary and hand off the
  future Metadata Factory ComponentStyle development item.
- [x] Write Cozy/CML style-selection and descriptor specification from
  verified generation.
- [x] Write launcher development-projection specification.
- [x] Promote only the minimal verified generic configuration/provenance
  changes and retain the larger generic framework proposal as Phase 55 work.
- [x] Update ArtScene specification and operations documentation.
- [x] Mark the notes proposal implemented/superseded with exact normative
  links.
- [x] Update strategy and Phase 53 evidence.
- [x] Close Phase 53 only after every completion rule passes.

Evidence:
- Framework final `test`: 2,728 succeeded, 0 failed (386 suites).
- ArtScene final `test`: 390 succeeded, 0 failed (42 suites); its final
  `clean; publishLocal` generated and published the styled CAR.
- Cozy final `test`: 779 succeeded, 0 failed (69 suites); its focused
  `CozyCarRuntimeManifestSpec` and `CozyCarLintSpec` checks also passed.
  sbt-cozy final `test`: 125 succeeded, 0 failed (27 suites); its focused
  `CozyManifestMetadataSpec` check also passed.
- The standalone-user, Web-resolution, execution-profile, and static Web
  focused suites are green. The reviewed configuration-binding, provenance,
  profile-ordering, locale/timezone, datastore, and operator presentation
  work remains explicitly deferred to Phase 54/55/58 as recorded above.

## PM-53-01: Transport-Neutral Subsystem User-Mode Admission

Stage Status:
- Current status: CLOSED
- Owner: CNCF framework runtime and ingress maintainers
- Entry rule: CS-07 is DONE.
- Completion rule: `standalone` / `multi-user` is resolved once per stable
  Subsystem as `SubsystemUserMode`, Command, REST, and Web consume the owning
  Subsystem resolution, and no transport, SystemNode/JVM, or Component becomes
  a competing authority.

- [x] Replace the Web-owned selector with the Subsystem-scoped canonical key
  `textus.subsystem.user-mode`.
- [x] Establish `SubsystemUserMode` with canonical values `standalone` and
  `multi-user`, resolved from the selected stable Subsystem identity.
- [x] Record `System = N SystemNode`, `SystemNode = N Subsystem`, and
  `Subsystem = N Component`; current one-JVM/one-SystemNode support must not
  make user mode JVM-global or prevent future mixed-mode Subsystems.
- [x] Keep `OperationMode` (`develop` / `production`) as an independent axis.
- [x] Resolve Subsystem user mode before fixed-user or authenticated-user
  context selection.
- [x] Make Command, REST, and Web consume the owning Subsystem's resolved user
  mode after target Component ownership is identified.
- [x] In `standalone`, construct the fixed-user ExecutionContext for every
  transport; in `multi-user`, require the transport-admitted authenticated
  principal and prohibit fixed-user fallback.
- [x] Keep Subsystem user mode out of ComponentFactory, Component create/init,
  generated operation input, ActionCall, and domain results.
- [x] Restrict Web-owned configuration to presentation concerns such as
  locale negotiation, formatting, and display override.
- [x] Prove `textus.web.application-mode`,
  `textus.web.execution.application-mode`, `cncf.web.application-mode`, and
  `cncf.runtime.web.execution.application-mode` cannot select or override the
  Subsystem user mode.
- [x] Preserve the existing conditional direct-Component `standalone` default
  under the Subsystem-scoped key.
- [x] Reject malformed canonical values during bootstrap/admission, including
  list, object, null, number, and boolean values.
- [x] Execute the `OperationMode x SubsystemUserMode x Transport` matrix and
  prove equivalent representative Component semantics for Command, REST, and
  Web.
- [x] Re-run affected full validation and clean review, then close PM-53-01
  without altering the historical CS-01--CS-07 completion record.

Explicit deferral boundary:

- Phase 55 continues to own effective profile precedence, detailed
  provenance, `ConfigurationBinding`, environment codecs, and complete
  fixed-user field binding. PM-53-01 changes Subsystem user-mode authority and
  transport admission only. It excludes `ApplicationMode`, `RuntimeUserMode`,
  `textus.application-mode`, JVM-global mode, launcher/ArtScene changes,
  datastore policy, and CML/generator work.

Evidence:
- Admitted repository: `dev2025/cloud-native-component-framework` only.
  Launcher and ArtScene worktrees are explicitly excluded and preserved.
- Target programs: `SubsystemUserMode`, `Subsystem`,
  `SubsystemExecutionProfile`, `RuntimeStandaloneUserProfileAdmission`,
  `WebExecutionResolution`, `WebExecutionProjection`, `Http4sHttpServer`,
  and their listed Subsystem/CLI/HTTP/Component boundary specifications.
- Corrected historical claim: CS-05E made Web the selector; PM-53-01 moves
  selection to the stable owning Subsystem without changing CS-01--CS-07.
- Final validation: serialized `clean; test` exited 0 on 2026-08-01: 2,735
  tests succeeded, 0 failed, across 388 completed suites. Post-review focused
  `Test/compile; testOnly` evidence also passed for 406 metadata-target tests,
  368 style/naming-target tests, and 39 session/dispatch tests. `git diff
  --check` passed.
- Clean review: Subsystem ownership, transport admission, Component boundary,
  Web non-authority, and cache safety passed. The remaining legacy
  scenario-prose cleanup in HYG-P53-004 is user-authorized deferred hygiene;
  it carries no PM-53-01 behavior or validation deficit.
- Controlled-test execution remains an explicit test-only evidence path. It
  does not make an unauthenticated production multi-user Subsystem valid.

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

Phase 53 base work is COMPLETE: CS-01 through CS-07 are DONE and retain their
historical evidence.

Maintenance status: CLOSED (`PM-53-01`). The maintenance item corrects the
Web-owned application-mode authority into one runtime authority shared by
Command, REST, and Web. Existing Phase 54/55/58 and strategy-candidate
deferrals are unchanged and receive no Phase 53 maintenance credit.
