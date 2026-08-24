# Phase 59 Checklist - Component Documentation Contract Inventory

status=closed
closed_at=2026-08-23
split_approved_at=2026-08-23
split_decision=D-59-SPLIT
phase=[Phase 59 - Component Documentation Contract Inventory](phase-59.md)
predecessor=[Phase 58.9](phase-58.9.md)
successor=[Phase 59.1](phase-59.1.md)
implementation_note=[Component Documentation Knowledge Package Implementation Proposal](../notes/component-documentation-knowledge-package-implementation.md)
composition_journal=[Component SubComponent and Development Composition Decision](../journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md)
phase_split_journal=[Resource SubComponent Phase Split and Planning (historical Phase 56)](../journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md)
canonical_architecture_design=[Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md)
canonical_architecture_specification=[Component and Subcomponent Architecture Specification](../spec/component-subcomponent-architecture.md)
canonical_resource_design=[Component Resource Subcomponent](../design/component-resource-subcomponent.md)
canonical_resource_specification=[Component Resource Subcomponent Specification](../spec/component-resource-subcomponent.md)

This checklist owns DOC-01A only. DOC-01B through DOC-10 moved exactly once to
Phase 59.1 through Phase 59.10 under approved split D-59-SPLIT. DOC-02 is
further partitioned under dated decision D-P59.2-NESTED-SPLIT-NUMBERING-001:
DOC-02A in Phase 59.2, DOC-02B in Phase 59.2.1, DOC-02C in Phase 59.2.2, and
DOC-02D in Phase 59.2.3. The former
Phase-wide ledger is retained below as pre-split historical context; its open
boxes are not current Phase 59 work and must not be used to start a later
child.

References to “Phase 58” below mean the full Phase 58 series; its final entry
gate is Phase 58.9.

## DOC-01A: Source-to-Package Inventory and Executable Acceptance

Stage Status:
- Current status: DONE
- Owner: CNCF, Cozy/sbt-cozy, and SmartDox maintainers
- Update rule: Preserve DONE after the reviewed DOC-01A Step and accepted
  handoff; any later correction proceeds through reviewed maintenance.
- Entry rule: Phase 58.9 is closed.
- Completion rule: Existing CNCF, Cozy/sbt-cozy, and SmartDox source-to-package
  contracts, conflicts, ownership boundaries, and exact failing-first
  acceptance identities are recorded without making DOC-01B public-publication
  or AI-service decisions.

- [x] Inventory CNCF Help, Manual, /man, OpenAPI, MCP, Web, CAR resource,
  production-visibility, and authorization contracts.
- [x] Inventory Cozy CAR documentation lint, source/archive projection,
  Scaladoc, and publication behavior.
- [x] Inventory SmartDox/Markdown parsing and HTML/PDF projection behavior.
- [x] Fix exact Component Help/resource authority separately from framework
  installed-snapshot, online-publication, and RAG-snapshot authority.
- [x] Admit the Phase 58 logical release, Resource SubComponent, resolver,
  integrity, and provenance contract as an input that DOC-01A does not
  reimplement.
- [x] Fix source filtering, license, restricted-access, and operation-mode
  input profiles without exposing OperationMode to Component domain code.
- [x] Register failing-first Executable Specification identities for every
  DOC-01A source-to-package acceptance group.

Evidence:
- [Accepted DOC-01A handoff note](../notes/phase-59-doc01a-source-to-package-inventory-and-failing-first-acceptance-registry.md), accepted in full commit `bb4245a92a6e3acc0a6573965a5843b4939c1e84` (note SHA-256 `f26e9379bbf5b3975fcbc6962afa94204b6dadb8b646e283e6a95f87c648da09`).
- Sealed Luna-high step-lightweight PASS with zero blockers; eight stable acceptance groups recorded; current evidence and linked paths resolved; diff checks passed; Class D documentation/status-only work with no SBT.
- Mandatory phase-full review PASS (`gpt-5.6-sol` / high), with no actionable
  finding and no Current Phase Blocker, over
  `36e22bf6877d0fc01a5b5fbae56b91bf208a4510..f7eb86c14354b259c646ccacc33f9b6901e391ba`
  (binary-diff SHA-256
  `c4203a167d3dd70d766779f8f614e45c7fc50eae09cb4ed679d187aba9b4f141`).
- Final release validation passed for the complete documentation-only Phase
  range. The frozen Phase program-change repository set is empty, so no SBT
  suite was applicable.
- Closure binding:
  `phase59-clb-ad9f697eccc55db83a779c6555efab8a9e89b68e6445400cb497fc31615fb965`.
- `HYG-P59-001` is persisted as nonblocking Hygiene; there are no accepted
  Development Candidates and no unpersisted ledger item.

Overall checklist status is `closed`. Phase 59.2 started DOC-02A; its remaining
DOC-02 ownership is the dated 59.2 → 59.2.1 → 59.2.2 → 59.2.3 sequence. No
historical unchecked box below activates successor work.

## Pre-Split Historical Ledger

The remaining sections are the original unsplit record. Their current
ownership is: DOC-01B in Phase 59.1; DOC-02A in Phase 59.2; DOC-02B in Phase
59.2.1; DOC-02C in Phase 59.2.2; DOC-02D in Phase 59.2.3; DOC-03 in Phase
59.3; DOC-04 in Phase 59.4; DOC-05 in Phase 59.5; DOC-06 in Phase 59.6;
DOC-07 in Phase 59.7; DOC-08 in Phase 59.8; DOC-09 in Phase 59.9; and DOC-10
in Phase 59.10. They are historical only and are not duplicate current work.

## Pre-Split DOC-01: Inventory and Executable Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy/SmartDox, SimpleModeling.org, ai-directive, Skill/Launcher,
  Textus CBD Support, Textus BoK, and representative Component maintainers
- Entry rule: Phase 58.9 closes the Phase 58 series.
- Completion rule: Existing contracts, conflicts, ownership boundaries, and
  exact failing-first acceptance identities are recorded before implementation.

- [ ] Inventory CNCF Help, Manual, `/man`, OpenAPI, MCP, Web, CAR resource,
  production-visibility, and authorization contracts.
- [ ] Inventory Cozy CAR documentation lint, source/archive projection,
  Scaladoc, and publication behavior.
- [ ] Inventory SmartDox/Markdown parsing and HTML/PDF projection behavior.
- [ ] Inventory SimpleModeling.org versioned HTML, RDF/JSON-LD, ontology,
  schema, glossary, catalog, and canonical URL publication behavior.
- [ ] Inventory authoritative `ai-directive` core/profile/sample authority,
  versioning, visibility, and project-local extension boundaries.
- [ ] Inventory `SkillBundleManifest`, CAR Skill ownership, Cozy projection,
  Launcher installation, MCP requirements, and non-activation boundaries.
- [ ] Inventory Textus CBD Support exact retrieval, usage, MCP, CAR Review,
  catalog, local artifact, and BoK evidence contracts.
- [ ] Inventory Textus BoK existence-only, SIE federation, CBD handoff, RAG,
  and MCP contracts.
- [ ] Fix CBD Support as the primary exact Component-use integration and
  Textus BoK as the complementary semantic route.
- [ ] Fix SimpleModeling.org as the basic public information surface for
  shared CNCF/CML/Cozy/SmartDox knowledge and framework Documentation
  Components as optional versioned publication snapshots.
- [ ] Preserve Component-specific documentation ownership while fixing
  separate framework product, document, section, resource, canonical URL, and
  content-hash identities.
- [ ] Fix exact Component Help/resource authority separately from framework
  installed-snapshot, online-publication, and RAG-snapshot authority.
- [ ] Fix public AI guidance as a projection that cannot override the mounted
  directive.
- [ ] Fix public Skill metadata as discovery information that cannot install,
  activate, execute, configure, or grant authority.
- [ ] Fix mandatory resource/manual/projection profiles.
- [ ] Admit the Phase 58 logical release, Resource SubComponent, resolver,
  integrity, and provenance contract as an input that Phase 59 does not
  reimplement.
- [ ] Fix source filtering, license, and restricted-access profiles without a
  source-omitted logical release.
- [ ] Fix Develop-mode Documentation consumption and AI development-context
  requirements only through the admitted explicit Phase 58 policy, without a
  separate fetch or resolver.
- [ ] Fix the `Develop`, `Test`, `Demo`, and `Production` runtime resource
  policy matrix without exposing `OperationMode` to Component domain code.
- [ ] Fix Component model coverage for Entity, Powertype, StateMachine, Value,
  Datatype, relationships, class diagrams, and state diagrams.
- [ ] Fix Help and AI as Phase 59 consumers and Phase 60 Admin as a later
  consumer of the same resource and knowledge/model contracts.
- [ ] Prohibit independent CAR, repository, cache, development-directory, or
  SubComponent scanning by Help, AI, CBD Support, BoK, or Admin.
- [ ] Register failing-first Executable Specification identities for every
  Phase 59 acceptance group.

Evidence:
- Pending.

## Pre-Split DOC-02: Knowledge and Model Resource Contracts

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component/CAR contract maintainers
- Entry rule: DOC-01 is DONE.
- Completion rule: Versioned knowledge and Component-model manifests bind
  content to Phase 58 resource identity and provenance without duplicating
  Component identity or physical resolution.

- [ ] Consume the Phase 58 composition manifest and
  `ResolvedComponentResources` or accepted equivalent.
- [ ] Define the knowledge and model manifest schema/version and canonical
  resource paths.
- [ ] Define resource identity, kind, role, language, media type, size, and
  digest.
- [ ] Define contextual framework canonical publication URL, publication
  generation, document and section identity, and local/online availability
  metadata without replacing Component resource identity.
- [ ] Define authority, stability, source, license, disclosure, and provenance
  metadata.
- [ ] Define generated-from and stale-projection detection.
- [ ] Bind each Documentation and SourceCode knowledge entry to a Phase 58
  logical resource identity while preserving physical provenance.
- [ ] Define portable Component model resources for Entity, Powertype,
  StateMachine, Value, Datatype, and relationships.
- [ ] Define deterministic class-diagram and StateMachine-diagram resource
  identities and provenance.
- [ ] Define framework Documentation Component references without making them
  execution dependencies or independent authoring sources.
- [ ] Define public Directive projection identity, originating
  directive/profile/rule version, authority, visibility, source digest, and
  redaction metadata.
- [ ] Define public Skill Catalog metadata for Skill/bundle identity, owner,
  purpose, trigger, requirements, permissions, side effects, MCP requirements,
  installation reference, visibility, and digest.
- [ ] Implement JSON codec and deterministic validation.
- [ ] Define the stable read-only consumer contract later used by Phase 60
  Admin.
- [ ] Reject unsafe paths, duplicate identities, invalid media/role
  combinations, and digest mismatch.
- [ ] Preserve forward-compatible unknown fields according to an explicit rule.
- [ ] Add property-based manifest and hostile-path specifications.

Evidence:
- Pending.

## Pre-Split DOC-03: Authoring and Content Packaging Toolchain

Stage Status:
- Current status: PLANNED
- Owner: Cozy/sbt-cozy and SmartDox maintainers
- Entry rule: DOC-02 is DONE.
- Completion rule: Required manuals, model projections, diagrams, Scaladoc,
  and filtered release source are validated and handed to the Phase 58
  packaging contract through their owning toolchains.

- [ ] Define User Guide and Reference Manual entry-point conventions.
- [ ] Define optional manual role extension.
- [ ] Validate SmartDox and admitted Markdown parsing.
- [ ] Generate stable framework document/section metadata shared by Web, Help
  references, framework Documentation Components, MCP, and RAG.
- [ ] Generate SimpleModeling.org framework HTML and structured
  RDF/JSON-LD/catalog projections from the same publication generation.
- [ ] Generate a general-public AI Development Guide only from explicitly
  admitted public Directive projections.
- [ ] Generate a public Skill Catalog from admitted Skill metadata without
  copying restricted raw `SKILL.md` content.
- [ ] Decide compatibility treatment of existing Asciidoc/HTML sources.
- [ ] Decide and implement mandatory HTML/PDF profiles.
- [ ] Generate and package Component Scaladoc.
- [ ] Generate portable Component model metadata and deterministic Mermaid
  class/state diagrams from CML/generated metadata rather than reflection.
- [ ] Generate and publish CNCF/Cozy/SmartDox Scaladoc with an optional
  framework Documentation Component projection.
- [ ] Generate the selected structured Scaladoc symbol/search index.
- [ ] Implement public/internal Scaladoc exposure policy.
- [ ] Implement source include/exclude/license/disclosure and restricted-access
  policy without an omitted-source logical release.
- [ ] Exclude secrets, local configuration, caches, and unrelated files.
- [ ] Package exact authored/generated release source, CML, build definitions,
  tests, dependency evidence, and generation provenance in the primary or
  SourceCode SubComponent.
- [ ] Collect admitted `Compile / managedSources` and diagnostically required
  `Test / managedSources` output into normalized `generated-source/main` and
  `generated-source/test` resources.
- [ ] Preserve generator inputs, identities, versions, options, dependency
  evidence, digests, and provenance needed to reproduce, investigate, and
  debug the exact release.
- [ ] Exclude raw `target` layout, class files, incremental compiler caches,
  temporary files, logs, downloaded caches, and host-specific state.
- [ ] Add release-readiness checks for missing or stale debugging evidence and
  generated-source/input digest mismatch.
- [ ] Generate Documentation and SourceCode content inventories, resource
  digests, and provenance for Phase 58 packaging.
- [ ] Prove SimpleModeling.org and framework Documentation Component
  projections retain the same publication generation, document/section
  identities, and hashes.
- [ ] Prove public Directive and Skill projections retain origin
  version/identity/digest and cannot be mistaken for active contracts.
- [ ] Prove development-source and packaged-CAR manifest/resource equivalence.
- [ ] Extend normal and strict Cozy CAR documentation lint.

Evidence:
- Pending.

## Pre-Split DOC-04: Knowledge and Development Context Composition

Stage Status:
- Current status: PLANNED
- Owner: CNCF Help, development-context, knowledge, and assembly consumers
- Entry rule: DOC-03 is DONE.
- Completion rule: Phase 58 resolved resources compose into one attributed
  knowledge and AI development context while framework Documentation
  Components remain separate publication snapshots.

- [ ] Consume local, remote, restricted, unavailable, corrupt, incompatible,
  and stale states exactly as returned by Phase 58.
- [ ] Preserve logical identity, physical provenance, access, disclosure,
  integrity, and resolution trace in `ResolvedComponentKnowledge` or the
  accepted equivalent.
- [ ] Build `ComponentDevelopmentContext` from admitted manuals, models, APIs,
  configuration, examples, source, generated source, Scaladoc, tests, and
  provenance.
- [ ] Never walk development directories, expanded artifacts, caches, or
  repositories outside the Phase 58 resolver.
- [ ] Preserve Phase 58 operation-mode policy without exposing
  `OperationMode` to Component domain APIs.
- [ ] Map incomplete Phase 58 development resources to structured,
  attributable development-context failure.
- [ ] Define framework Documentation Component subject/product/version and
  publication-generation identity separately from target-Component
  relationships.
- [ ] Allow framework Documentation Components to carry the public AI
  Development Guide and Skill Catalog without carrying authoritative project
  directives or installable Skill authority.
- [ ] Validate framework canonical URL, publication generation, resource
  hashes, and optional installation semantics.
- [ ] Prove absence of framework Documentation Components never prevents
  Component startup or access to Component-specific Help/manuals.
- [ ] Define a closed-network Documentation Hub SAR composition profile.

Evidence:
- Pending.

## Pre-Split DOC-05: Unified Help and Direct AI Access

Stage Status:
- Current status: PLANNED
- Owner: CNCF Help, HTTP, CLI, Web, and security maintainers
- Entry rule: DOC-04 is DONE.
- Completion rule: Humans and AI reach the same resolved Component resources;
  develop mode provides a complete, attributed development context, and the
  stable manifest/resource consumer contract is ready for Phase 60 Admin.

- [ ] Define the canonical Help manifest discovery route.
- [ ] Advertise the manifest from human Help with a stable relation/media type.
- [ ] Integrate User Guide, Reference, configuration, Operations, schemas,
  OpenAPI, Scaladoc, source, troubleshooting, and provenance navigation.
- [ ] Integrate Entity, Powertype, StateMachine, Value, Datatype, class
  diagram, and state diagram navigation.
- [ ] Build a manifest-based `ComponentDevelopmentContext` with manuals,
  models, APIs, configuration, examples, source, generated source, Scaladoc,
  tests, and provenance.
- [ ] Consume Develop-mode Documentation only through the admitted explicit
  Phase 58 policy; compose the resulting resources into the development
  context without a separate fetch or resolver.
- [ ] Project mounted/local/remote/restricted/unavailable/incompatible/stale/
  corrupt resource state from Phase 58 for Help and the later Admin surface.
- [ ] Provide structured manifest/resource HTTP retrieval.
- [ ] Provide CLI manifest/resource inspection.
- [ ] Reconcile `/help`, `/man`, OpenAPI, Web, and compatibility routes.
- [ ] Make `/help/system` describe the running runtime and `/man/system`
  resolve matching CNCF documentation locally or online.
- [ ] Put CML/Cozy documentation under explicit developer/toolchain
  navigation rather than ordinary operator Help.
- [ ] Show active directive version/profile/digest and its public-guide
  reference without exposing restricted rule bodies.
- [ ] Show Component-associated Skill metadata, availability, compatibility,
  and installation state without installing or activating the Skill.
- [ ] Show framework documentation installed, cached, online, unavailable, and
  version-mismatch states without changing Component manual resolution.
- [ ] Keep human `latest` links separate from immutable evidence URLs.
- [ ] Apply authorization and production visibility deliberately.
- [ ] Prevent physical Documentation/SourceCode SubComponent boundaries from
  leaking into ordinary navigation.
- [ ] Publish the exact read-only resource and knowledge/model API consumed by
  Phase 60; do not implement Admin runtime views or management actions here.
- [ ] Verify exact Component/version selection under multiple loaded versions
  or instances.
- [ ] Add hostile content, content type, caching, and disclosure
  specifications.
- [ ] Verify framework documentation online failure does not prevent execution
  or misreport remote content as local.

Evidence:
- Pending.

## Pre-Split DOC-06: Textus CBD Support Primary Integration

Stage Status:
- Current status: PLANNED
- Owner: Textus CBD Support and CNCF Component knowledge maintainers
- Entry rule: DOC-05 is DONE.
- Completion rule: CBD Support uses exact Component knowledge manifests and
  resources as the primary evidence for detail, usage, MCP, and CAR Review.

- [ ] Admit manifest identity/location/digest from catalog, development
  directory, warehouse CAR, cache, and exact Component observations.
- [ ] Preserve catalog/source identity and require exact Component/version
  selection before detailed retrieval.
- [ ] Resolve embedded and Documentation/SourceCode SubComponent resources
  safely.
- [ ] Project configuration, Operations, schemas, manuals, examples, Scaladoc,
  source availability, and provenance.
- [ ] Return explicit absence when a source does not publish Component
  knowledge.
- [ ] Make `getUsage` cite exact contract/manual/example/source evidence and
  distinguish inference.
- [ ] Provide bounded read-only manifest/resource retrieval through CBD MCP.
- [ ] Add CAR Review checks for manual completeness, manifest integrity,
  Scaladoc, source policy, Help discovery, and BoK publication readiness.
- [ ] Enforce origin, digest, size, license, authorization, and disclosure.
- [ ] Keep CBD Support independently useful without Textus BoK.
- [ ] Preserve BoK semantic evidence as a separate attributable input.
- [ ] Update CBD design/spec/strategy/manual contracts.

Evidence:
- Pending.

## Pre-Split DOC-07: Textus BoK Complementary RAG/MCP Integration

Stage Status:
- Current status: PLANNED
- Owner: Textus BoK RAG/MCP maintainers
- Entry rule: DOC-06 is DONE.
- Completion rule: Component semantic retrieval and shared framework knowledge
  retrieval remain distinct, bounded, attributable, and capable of exact
  handoff to CBD Support/direct Help.

- [ ] Define the Component knowledge manifest admission resource kind.
- [ ] Define admission resource kinds for SimpleModeling.org publication
  metadata and equivalent framework Documentation Component snapshots without
  changing Component-specific admission.
- [ ] Define separate public AI-guidance and Skill-metadata admission resource
  kinds.
- [ ] Admit SmartDox-derived document/section metadata, RDF/JSON-LD, glossary,
  ontology, schema, and catalog projections without requiring HTML scraping.
- [ ] Resolve Component-local and Documentation/SourceCode SubComponent
  resources safely.
- [ ] Define deterministic document, section, chunk, and evidence identities.
- [ ] Preserve Component version, manifest/resource digests, authority,
  license, source path, and indexed-at time.
- [ ] Preserve framework product/version, canonical URL, publication
  generation, document/section digest, and indexed-at time separately.
- [ ] Preserve Directive/rule/profile or Skill/bundle identity, version,
  authority, visibility, owner, canonical URL, and digest separately.
- [ ] Preserve SmartDox structure and schema/API resources without flattening
  away required semantics.
- [ ] Implement lexical and structural retrieval independent of embeddings.
- [ ] Integrate optional embedding/vector retrieval through existing provider
  boundaries.
- [ ] Return exact Component/resource/section evidence with every result.
- [ ] Return exact framework product, version, document/section identity,
  canonical URL, source generation, and content hash with every framework
  result.
- [ ] Distinguish contract, manual, example, source, and context authority.
- [ ] Detect and report stale snapshot versus current manifest digest.
- [ ] Add read-only MCP discovery, search, manifest, resource, and section
  operations under explicit MCP readiness.
- [ ] Keep framework Documentation Component Operations out of MCP by default
  and expose curated BoK retrieval Operations instead.
- [ ] Add bounded read-only AI-guidance and Skill-metadata discovery/retrieval
  Operations under explicit MCP readiness.
- [ ] Prove guidance/Skill retrieval cannot override directives, mutate Codex
  configuration, install/activate Skills, or grant MCP authority.
- [ ] Keep mutation and execution Operations absent from the retrieval MCP
  catalog.
- [ ] Enforce proprietary-source and caller-authorization policy at response
  time.
- [ ] Return exact identity/version/resource/digest evidence for CBD Support
  handoff.
- [ ] Preserve CBD Support detail/usage/comparison/review ownership.
- [ ] Update Textus BoK domain/design/spec/strategy/manual contracts.
- [ ] Add RAG/MCP no-match, ambiguous-version, stale, forbidden, and bounded
  result specifications.

Evidence:
- Pending.

## Pre-Split DOC-08: Representative Component and Framework Documentation Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF samples and selected Component maintainers
- Entry rule: DOC-07 is DONE.
- Completion rule: Embedded and required-SubComponent Component profiles plus
  separate framework online/installed/offline profiles preserve ownership
  while providing attributable human and AI knowledge.

- [ ] Provide one small Component with embedded manuals, model metadata,
  diagrams, Scaladoc, source, and manifests.
- [ ] Provide one large Component using required Documentation and SourceCode
  SubComponents.
- [ ] Provide one restricted-source profile whose SourceCode SubComponent is
  repository-complete but authorization-controlled.
- [ ] Verify `OperationMode.Develop` consumes required Documentation and uses
  the development target's source only under the admitted explicit Phase 58
  policy, without a separate fetch or resolver.
- [ ] Verify `OperationMode.Test` uses deterministic explicitly selected
  local resources and performs no implicit remote retrieval.
- [ ] Verify `OperationMode.Demo` does not automatically mount source and
  requires explicit policy for remote Documentation.
- [ ] Verify `OperationMode.Production` retains independent primary
  activation, selects only embedded-primary resources, performs no remote
  resource selection, and never automatically resolves, mounts, or fetches
  SourceCode.
- [ ] Verify dependency Documentation is available to AI by exact version and
  dependency source obeys authorization policy.
- [ ] Verify production activation does not fetch required knowledge
  SubComponents or fail only because their repository is offline.
- [ ] Verify an offline complete-release bundle resolves all SubComponents.
- [ ] Provide one online-only profile using versioned SimpleModeling.org
  framework publication.
- [ ] Provide one installed CNCF/CML/Cozy framework Documentation Component
  profile.
- [ ] Provide one closed-network Documentation Hub SAR profile.
- [ ] Provide one public AI Development Guide generated from explicitly public
  `ai-directive` rules.
- [ ] Provide one public Skill Catalog linked to an actual CAR-owned
  `SkillBundleManifest`.
- [ ] Verify User Guide and Reference Manual navigation.
- [ ] Verify direct Help-to-manifest AI discovery.
- [ ] Verify exact configuration, Operation, schema, example, and Scaladoc
  retrieval.
- [ ] Verify admitted source improves retrieval without becoming a public
  contract or leaking secrets/local state.
- [ ] Verify Component model metadata and diagrams cover Entity, Powertype,
  StateMachine, Value, and Datatype.
- [ ] Verify CBD Support exact detail, usage, MCP, and CAR Review.
- [ ] Verify Textus BoK ingestion and evidence-bearing RAG/MCP retrieval.
- [ ] Verify BoK-to-CBD-to-direct-Help identity/version/hash handoff.
- [ ] Verify offline/runtime operation without build/render/embedding tools.
- [ ] Verify online-only, installed-framework-snapshot, and offline-Hub
  profiles resolve the same framework document and section identities without
  changing Component-specific resource resolution.
- [ ] Verify the public guide cannot override the mounted directive and the
  Skill Catalog cannot install or activate its referenced Skill.

Evidence:
- Pending.

## Pre-Split DOC-09: Security, Regression, and Downstream Validation

Stage Status:
- Current status: PLANNED
- Owner: all Phase 59 repository maintainers
- Entry rule: DOC-08 is DONE.
- Completion rule: Security, compatibility, full regression, and downstream
  checks pass across every changed repository.

- [ ] Verify traversal, symlink, oversized-resource, malformed-content, and
  digest attacks fail safely.
- [ ] Verify secrets and unauthorized source never enter Help, indexes,
  diagnostics, RAG context, or MCP responses.
- [ ] Verify manifest/SubComponent relationships grant no Operation, runtime
  Component, Componentlet, or MCP execution authority.
- [ ] Verify Develop-mode consumption under the admitted explicit Phase 58
  policy cannot bypass source disclosure, authorization, path, digest, or
  signature policy.
- [ ] Verify Production selects only embedded-primary resources, performs no
  remote resource selection, and never automatically resolves, mounts, or
  fetches SourceCode SubComponents.
- [ ] Verify production Help/knowledge exposure follows the accepted policy.
- [ ] Verify online-documentation timeout, unavailable, cache, version
  mismatch, and immutable-evidence behavior.
- [ ] Verify restricted directives, project-local rules, raw private Skill
  content, credentials, approvals, and provider configuration never enter
  public Help, publication, indexes, RAG context, or MCP responses.
- [ ] Run CNCF focused and full suites.
- [ ] Run Cozy/sbt-cozy and SmartDox focused/full suites.
- [ ] Run SimpleModeling.org publication and structured metadata validation.
- [ ] Run Textus BoK focused/full suites and CAR lint/build.
- [ ] Run Textus CBD Support focused/full suites, CAR lint/build, and
  representative MCP checks.
- [ ] Run representative Component and subsystem integration suites.
- [ ] Run `sbt --batch Test/compile` in every changed Scala repository.
- [ ] Run `git diff --check` in every changed repository.
- [ ] Complete read-only review, review-fix, and clean re-review.

Evidence:
- Pending.

## Pre-Split DOC-10: Canonical Documentation and Closure

Stage Status:
- Current status: PLANNED
- Owner: CNCF, SimpleModeling.org/Cozy, ai-directive, Skill/Launcher, Textus
  CBD Support, and Textus BoK architecture maintainers
- Entry rule: DOC-09 is DONE and implementation behavior is stable.
- Completion rule: Design, specification, notes, strategy, phase records,
  implementation, and executable evidence agree without a competing latest
  contract.

- [ ] Create/update
  `docs/design/component-documentation-knowledge-package.md`.
- [ ] Create/update
  `docs/spec/component-documentation-knowledge-package.md`.
- [ ] Update affected CNCF Help/Manual/CAR/Web/MCP design/spec documents.
- [ ] Update SimpleModeling.org and Cozy publication design/specification for
  stable versioned HTML, structured metadata, and Documentation Component
  projection.
- [ ] Update `ai-directive` public-projection guidance without weakening its
  authoritative contract or sample non-authority.
- [ ] Update Skill bundle/catalog documentation so publication metadata,
  installation, activation, execution, and MCP authority remain separate.
- [ ] Update Textus CBD Support and Textus BoK
  design/spec/strategy/manual documents.
- [ ] Record exact executable evidence in normative documents.
- [ ] Mark
  `docs/notes/component-documentation-knowledge-package-implementation.md`
  historical and non-normative.
- [ ] State in that note that final design/specification override it.
- [ ] Retain the journal as chronological consideration history.
- [ ] Remove or mark superseded contradictory current documentation.
- [ ] Confirm no latest specification exists only in notes, journal, phase
  documents, implementation, or tests.
- [ ] Update CNCF strategy completed history and remove active Phase 59 item.
- [ ] Close Phase 59 dashboard/checklist with exact validation evidence.

Evidence:
- Pending.
