# Phase 52 Checklist - Component Documentation and AI Knowledge Integration

status=planned
phase=[Phase 52 - Component Documentation and AI Knowledge Integration](phase-52.md)

This checklist is the authoritative Phase 52 state ledger after Phase 52
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 51 closes.

## DOC-01: Inventory and Executable Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF, Cozy/SmartDox, SimpleModeling.org, ai-directive, Skill/Launcher,
  Textus CBD Support, Textus BoK, and representative Component maintainers
- Entry rule: Phase 51 is closed.
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
- [ ] Fix Documentation Component relationship semantics.
- [ ] Fix source disclosure and commercial omission profiles.
- [ ] Register failing-first Executable Specification identities for every
  Phase 52 acceptance group.

Evidence:
- Pending.

## DOC-02: Knowledge Manifest and Resource Model

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component/CAR contract maintainers
- Entry rule: DOC-01 is DONE.
- Completion rule: One versioned, deterministic, safe manifest/resource model
  is implemented and validated without duplicating Component identity.

- [ ] Define the manifest schema/version and canonical source/archive path.
- [ ] Define resource identity, kind, role, language, media type, size, and
  digest.
- [ ] Define contextual framework canonical publication URL, publication
  generation, document and section identity, and local/online availability
  metadata without replacing Component resource identity.
- [ ] Define authority, stability, source, license, disclosure, and provenance
  metadata.
- [ ] Define generated-from and stale-projection detection.
- [ ] Define Component-specific Documentation Component references.
- [ ] Define framework Documentation Component references without making them
  execution dependencies or independent authoring sources.
- [ ] Define public Directive projection identity, originating
  directive/profile/rule version, authority, visibility, source digest, and
  redaction metadata.
- [ ] Define public Skill Catalog metadata for Skill/bundle identity, owner,
  purpose, trigger, requirements, permissions, side effects, MCP requirements,
  installation reference, visibility, and digest.
- [ ] Implement JSON codec and deterministic validation.
- [ ] Reject unsafe paths, duplicate identities, invalid media/role
  combinations, and digest mismatch.
- [ ] Preserve forward-compatible unknown fields according to an explicit rule.
- [ ] Add property-based manifest and hostile-path specifications.

Evidence:
- Pending.

## DOC-03: Authoring and Packaging Toolchain

Stage Status:
- Current status: PLANNED
- Owner: Cozy/sbt-cozy and SmartDox maintainers
- Entry rule: DOC-02 is DONE.
- Completion rule: Required manuals, projections, Scaladoc, and source are
  validated and projected into a CAR through their owning toolchains.

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
- [ ] Generate and publish CNCF/Cozy/SmartDox Scaladoc with an optional
  framework Documentation Component projection.
- [ ] Generate the selected structured Scaladoc symbol/search index.
- [ ] Implement public/internal Scaladoc exposure policy.
- [ ] Implement source include/exclude/license/disclosure policy.
- [ ] Exclude secrets, local configuration, caches, and unrelated files.
- [ ] Generate resource digests and provenance.
- [ ] Prove SimpleModeling.org and framework Documentation Component
  projections retain the same publication generation, document/section
  identities, and hashes.
- [ ] Prove public Directive and Skill projections retain origin
  version/identity/digest and cannot be mistaken for active contracts.
- [ ] Prove development-source and packaged-CAR manifest/resource equivalence.
- [ ] Extend normal and strict Cozy CAR documentation lint.

Evidence:
- Pending.

## DOC-04: Documentation Component Composition

Stage Status:
- Current status: PLANNED
- Owner: CNCF Component loading, repository, and assembly maintainers
- Entry rule: DOC-03 is DONE.
- Completion rule: Component-specific Documentation Components resolve as one
  Component knowledge space, while optional framework Documentation
  Components resolve separately as exact SimpleModeling.org publication
  snapshots.

- [ ] Define Component-specific Documentation Component kind and target
  relationship.
- [ ] Define target Component identity and compatibility/version matching.
- [ ] Define required/optional Component documentation resolution and
  startup/access behavior.
- [ ] Resolve development directory, expanded CAR, and repository forms.
- [ ] Validate Component documentation digest/signature and disclosure
  compatibility.
- [ ] Define embedded versus Component-specific Documentation Component
  precedence.
- [ ] Reject duplicate/conflicting resource identities deterministically.
- [ ] Return structured missing/incompatible/corrupt diagnostics.
- [ ] Preserve minimal embedded Component overview and dependency diagnostics.
- [ ] Implement `ResolvedComponentKnowledge` or the accepted equivalent.
- [ ] Verify load/unload and multi-instance behavior.
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

## DOC-05: Unified Help and Direct AI Access

Stage Status:
- Current status: PLANNED
- Owner: CNCF Help, HTTP, CLI, Web, and security maintainers
- Entry rule: DOC-04 is DONE.
- Completion rule: Humans and AI reach the same resolved Component knowledge,
  while framework/toolchain links intentionally distinguish installed
  snapshots from canonical online publication.

- [ ] Define the canonical Help manifest discovery route.
- [ ] Advertise the manifest from human Help with a stable relation/media type.
- [ ] Integrate User Guide, Reference, configuration, Operations, schemas,
  OpenAPI, Scaladoc, source, troubleshooting, and provenance navigation.
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
- [ ] Prevent physical Documentation Component boundaries from leaking into
  ordinary navigation.
- [ ] Verify exact Component/version selection under multiple loaded versions
  or instances.
- [ ] Add hostile content, content type, caching, and disclosure
  specifications.
- [ ] Verify framework documentation online failure does not prevent execution
  or misreport remote content as local.

Evidence:
- Pending.

## DOC-06: Textus CBD Support Primary Integration

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
- [ ] Resolve embedded and Documentation Component resources safely.
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

## DOC-07: Textus BoK Complementary RAG/MCP Integration

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
- [ ] Resolve Component-local and Component-specific Documentation Component
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

## DOC-08: Representative Component and Framework Documentation Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF samples and selected Component maintainers
- Entry rule: DOC-07 is DONE.
- Completion rule: Existing Component-specific profiles and separate
  framework online/installed/offline profiles preserve their ownership while
  providing attributable knowledge.

- [ ] Provide one small Component with embedded manuals, Scaladoc, source, and
  manifest.
- [ ] Provide one large Component-specific profile using a Documentation
  Component.
- [ ] Provide one commercial-style Component profile with source omitted and
  public Scaladoc retained.
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
  contract.
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

## DOC-09: Security, Regression, and Downstream Validation

Stage Status:
- Current status: PLANNED
- Owner: all Phase 52 repository maintainers
- Entry rule: DOC-08 is DONE.
- Completion rule: Security, compatibility, full regression, and downstream
  checks pass across every changed repository.

- [ ] Verify traversal, symlink, oversized-resource, malformed-content, and
  digest attacks fail safely.
- [ ] Verify secrets and unauthorized source never enter Help, indexes,
  diagnostics, RAG context, or MCP responses.
- [ ] Verify manifest/Documentation Component relationships grant no Operation
  or MCP execution authority.
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

## DOC-10: Canonical Documentation and Closure

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
- [ ] Update CNCF strategy completed history and remove active Phase 52 item.
- [ ] Close Phase 52 dashboard/checklist with exact validation evidence.

Evidence:
- Pending.
