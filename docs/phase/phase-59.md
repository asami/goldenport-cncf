# Phase 59 - Component Documentation Contract Inventory

status=closed
planned_at=2026-07-25
started_at=2026-08-23
closed_at=2026-08-23
split_approved_at=2026-08-23
split_decision=D-59-SPLIT
depends_on=[Phase 58.9](phase-58.9.md)
successor=[Phase 59.1](phase-59.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 59 Checklist](phase-59-checklist.md)
implementation_note=[Component Documentation Knowledge Package Implementation Proposal](../notes/component-documentation-knowledge-package-implementation.md)
composition_journal=[Component SubComponent and Development Composition Decision](../journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md)
phase_split_journal=[Resource SubComponent Phase Split and Planning (historical Phase 56)](../journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md)
foundation=[Phase 58 series, closing in Phase 58.9](phase-58.9.md)
canonical_architecture_design=[Component and Subcomponent Architecture](../design/component-subcomponent-architecture.md)
canonical_architecture_specification=[Component and Subcomponent Architecture Specification](../spec/component-subcomponent-architecture.md)
canonical_resource_design=[Component Resource Subcomponent](../design/component-resource-subcomponent.md)
canonical_resource_specification=[Component Resource Subcomponent Specification](../spec/component-resource-subcomponent.md)
admin_consumer=[Phase 60 Component Admin and Documentation Visibility](phase-60.md)
accepted_handoff_note=[Phase 59 DOC-01A source-to-package inventory and failing-first acceptance registry](../notes/phase-59-doc01a-source-to-package-inventory-and-failing-first-acceptance-registry.md)
accepted_handoff_commit=bb4245a92a6e3acc0a6573965a5843b4939c1e84
accepted_closure_commit=f7eb86c14354b259c646ccacc33f9b6901e391ba
closure_binding_scope=phase59-clb-ad9f697eccc55db83a779c6555efab8a9e89b68e6445400cb497fc31615fb965

## Current Status

Phase 59 is closed. The DOC-01A handoff Step was accepted in commit
`bb4245a92a6e3acc0a6573965a5843b4939c1e84`, and its closure-ledger Step was
accepted in commit `f7eb86c14354b259c646ccacc33f9b6901e391ba`.
The mandatory phase-full review passed with no Current Phase Blocker. The
distinct release commit is identified by closure binding
`phase59-clb-ad9f697eccc55db83a779c6555efab8a9e89b68e6445400cb497fc31615fb965`.
Phase 59.2 started DOC-02A on 2026-08-24. The dated nested split record below
owns its remaining work without reopening this closed Phase.

## Goal

Freeze the first half of DOC-01: the CNCF, Cozy/sbt-cozy, and SmartDox
documentation/resource contract inventory and its exact failing-first
acceptance identities. This Phase produces the DOC-01A handoff only; it does
not implement manifests, packaging, Help, CBD Support, BoK, or Phase 60.

## Approved Split

On 2026-08-23 the user approved Decision Request D-59-SPLIT through
$cncf-split-phase Phase 59 and the subsequent confirmation of the corrected
eleven-unit sequence. The former ten-stage Phase had a conservative
40--54-hour estimate and mixed two open-ended discovery kernels with protected
cross-repository implementation, acceptance, and documentation closure work.

| Phase | Owned closure | Parent profile | Estimate |
| --- | --- | --- | --- |
| 59 | DOC-01A source-to-package contract inventory | Sol / high | 4--6h |
| [59.1](phase-59.1.md) | DOC-01B public publication, Directive, Skill, CBD, and BoK boundary inventory | Sol / high | 4--6h |
| [59.2](phase-59.2.md) | DOC-02A base manifest codec and safe Phase 58 binding | Terra / xhigh | 1--2h |
| [59.2.1](phase-59.2.1.md) | DOC-02B framework publication context and projection evidence | Terra / xhigh | 3--5h |
| [59.2.2](phase-59.2.2.md) | DOC-02C portable model and diagram resource contract | Terra / high | 3--4h |
| [59.2.3](phase-59.2.3.md) | DOC-02D public Directive/Skill metadata and read-only consumer contract | Terra / xhigh | 4--5h |
| [59.3](phase-59.3.md) | Closed on 2026-08-25: DOC-03 authoring, source packaging, and publication toolchain | Terra / xhigh | 5--6h |
| [59.4](phase-59.4.md) | Closed on 2026-08-26: DOC-04 knowledge and development-context composition | Terra / high | 4--6h |
| [59.5](phase-59.5.md) | Closed on 2026-08-26: DOC-05 unified Help and direct-AI consumer interface | Terra / xhigh | 5--6h |
| [59.6](phase-59.6.md) | Closed on 2026-08-26: DOC-06 Textus CBD Support exact-detail, usage, MCP, and CAR Review integration. | Terra / high | 4--6h |
| [59.7](phase-59.7.md) | Closed on 2026-08-27: DOC-07 Textus BoK semantic RAG/MCP and CBD handoff. | Terra / xhigh | 5--6h |
| [59.8](phase-59.8.md) | DOC-08 representative Component and framework profiles | Terra / high | 4--6h |
| [59.9](phase-59.9.md) | DOC-09 security, regression, and downstream validation | Terra / high | 4--6h |
| [59.10](phase-59.10.md) | DOC-10 canonical documentation and Phase closure | Terra / high | 4--6h |

Pre-split gate evidence: SPLIT_REQUIRED. The critical path is DOC-01A,
DOC-01B, DOC-02, DOC-03, DOC-04, DOC-05, DOC-06, DOC-07, DOC-08, DOC-09, and
DOC-10 in that order. The expensive kernels are limited to the two DOC-01
inventory children. Every later child consumes its predecessor's frozen
handoff rather than rediscovering Component identity, Phase 58 resource
resolution, documentation ownership, authority, or Phase 60's later-consumer
boundary.

### Dated Nested Split Correction — 2026-08-24

The user approved `D-P59.2-NESTED-SPLIT-NUMBERING-001` after DOC-02A exposed a
protected manifest-schema review boundary. Phase 59.2 retains its completed
DOC-02A history; Phases 59.2.1, 59.2.2, and 59.2.3 own the remaining framework
context, model/diagram, and public-metadata/consumer scopes exactly once. The
sequence is 59.2 → 59.2.1 → 59.2.2 → 59.2.3 → 59.3. The added release and
handoff overhead is accepted so each schema change receives one Phase full
review rather than an impermissible Step full review.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: open-ended-discovery
- recommended_parent_profile: gpt-5.6-sol / high
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: reconcile CNCF, Cozy/sbt-cozy, and SmartDox documentation/resource ownership and acceptance evidence into the DOC-01A handoff
- frozen_profile_transition_handoff: none
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4--6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 59

## Current Scope

- Inventory CNCF Help, Manual, /man, OpenAPI, MCP, Web, CAR resource,
  production-visibility, and authorization contracts.
- Inventory Cozy CAR documentation lint, source/archive projection, Scaladoc,
  and publication behavior.
- Inventory SmartDox/Markdown parsing and HTML/PDF projection behavior.
- Inventory and admit the closed Phase 58 logical-release, resolved-resource,
  integrity, provenance, operation-mode, and no-second-resolver contracts as
  read-only input; Phase 59 must not change or reimplement Phase 58 behavior or
  create a second resolver.
- Record DOC-01A ownership conflicts and exact failing-first acceptance
  identities for the source-to-package boundary.

## Closure

The accepted DOC-01A inventory and acceptance registry is the sole handoff to
Phase 59.1. It does not make publication, Directive, Skill, CBD Support, or
BoK decisions owned by Phase 59.1 or later children.

Closure evidence:

- Phase-base commit:
  `36e22bf6877d0fc01a5b5fbae56b91bf208a4510` (Phase 58.9 release).
- Complete reviewed Phase range:
  `36e22bf6877d0fc01a5b5fbae56b91bf208a4510..f7eb86c14354b259c646ccacc33f9b6901e391ba`;
  binary-diff SHA-256
  `c4203a167d3dd70d766779f8f614e45c7fc50eae09cb4ed679d187aba9b4f141`.
- Mandatory phase-full review: PASS (`gpt-5.6-sol` / high), with no actionable
  finding and no Current Phase Blocker.
- Final release validation: complete Phase-range diff checks, UTF-8-aware
  local-link checks, closure/status truth checks, acceptance-identity checks,
  and closure-ledger checks passed. No SBT suite was required because the
  frozen Phase program-change repository set is empty.
- Accepted nonblocking Hygiene: `HYG-P59-001`, persisted in the canonical
  Phase 59 Hygiene journal. Accepted Development Candidates: none.
- Closure binding:
  `phase59-clb-ad9f697eccc55db83a779c6555efab8a9e89b68e6445400cb497fc31615fb965`.

## Non-Goals

- Manifest/schema implementation, authoring/package implementation, Help
  routes, direct AI retrieval, CBD Support, BoK, representative profiles,
  security/regression closure, canonical design/specification promotion, and
  any Phase 60 Admin behavior.

## Pre-Split Context (Historical)

The former Phase-wide material below is retained as dated pre-split context.
It is not the current scope of Phase 59. Its unfinished work is owned exactly
once by the child map in Approved Split and the child checklists.

### Purpose

Make every CNCF Component a one-stop, self-describing execution and knowledge
package for humans and AI.

Phase 59 organizes hand-written manuals, generated Help, Scaladoc, source,
schemas, examples, provenance, and contextual documentation under one
machine-readable Component composition and knowledge model. One logical,
versioned Component release may use a small primary execution CAR plus exact
Documentation and SourceCode SubComponents. The Component Repository
guarantees that every required SubComponent is retrievable even when it is not
installed with the primary CAR.

Component-specific information continues to follow that Component-owned
contract. Separately, shared CNCF/CML/Cozy/SmartDox framework and toolchain
documentation is published primarily through SimpleModeling.org and is
physically distributed only when required as a versioned Documentation
Component generated from the same publication knowledge.

The phase primarily integrates Textus CBD Support for exact Component
discovery, detail, usage, MCP assistance, and CAR Review. It also implements
the complementary Textus BoK RAG/MCP route for terminology and semantic
discovery. The exact Component Help manifest remains authoritative.

### Dependency

Phase 59 begins after Phase 58.9 closes the Phase 58 series.

The Phase 58 series is authoritative for Subcomponent Component identity, composition,
publication completeness, repository/cache access, resolution precedence,
integrity, operation-mode policy, lifecycle, and physical provenance. Phase 59
consumes that foundation and must not implement a second resolver.

Unless an entry gate names Phase 58.9 explicitly, references to “Phase 58” in
this document denote the full Phase 58 series rather than RSC-01 alone.

Phase 59 does not reopen Phase 50 Entity revision/OCC behavior. It may document
those contracts through the new Component knowledge mechanism after their
canonical design/specification is stable.

### Selected Direction

- Physical Components carry the Component-specific information required to
  understand and use them.
- A Component knowledge package is projected over the logical release and
  resolved resource space supplied by Phase 58.
- Small runtime-required metadata and the composition manifest remain in the
  primary CAR; large documentation and source may be packaged as
  Documentation and SourceCode SubComponents.
- Documentation and SourceCode Subcomponents are independently describable
  Component CARs. Their payload resources are not implicit parent runtime
  dependencies, Componentlets, or separately installed Subsystem capabilities.
- Publication completeness, primary-only activation, and exact resource states
  follow the Phase 58 contract.
- `OperationMode.Develop` consumes required Documentation only through the
  admitted explicit Phase 58 policy.
- The development target's source tree is used only when admitted; otherwise
  the exact SourceCode artifact is consumed under access policy. Dependency
  source follows explicit authorization and disclosure policy.
- Component code does not receive or branch on `OperationMode`; development
  composition remains a launcher/runtime resource-resolution concern.
- `OperationMode.Test` uses deterministic explicitly selected local fixtures
  or offline bundles without implicit remote retrieval.
- `OperationMode.Demo` uses installed or cached content, or Documentation
  remote access only under explicit Phase 58 policy; it does not automatically
  resolve SourceCode.
- `OperationMode.Production` retains independent primary activation, selects
  only embedded-primary resources, performs no remote resource selection, and
  never automatically resolves, mounts, or fetches SourceCode.
- User Guide and Reference Manual are the two standard hand-written manual
  axes.
- SmartDox is the canonical hand-written document model; admitted Markdown is
  parsed through SmartDox.
- Scaladoc is included in the Component distribution.
- Source is embedded or carried by an exact SourceCode SubComponent. It is not
  silently omitted from the logical release; restricted source is represented
  by an explicit access policy and never exposed without authorization.
- Portable Component model metadata covers Entity, Powertype, StateMachine,
  Value, Datatype, and relationships. Documentation projects deterministic
  class and state diagrams from that metadata rather than runtime reflection.
- Help is the unified human entry point and advertises a JSON Component
  knowledge manifest for direct AI retrieval.
- Phase 60 Admin consumes the same Phase 58 resource view and Phase 59
  knowledge/model manifests; Phase 59 does not implement the Admin surface.
- Textus CBD Support supplies the primary mediated AI route for exact
  Component discovery, detail, usage guidance, and review.
- Textus BoK supplies the complementary terminology and cross-Component
  semantic RAG/MCP route with exact CBD Support handoff.
- Shared CNCF/CML/Cozy/SmartDox documentation is published primarily through
  versioned SimpleModeling.org pages and structured publication metadata.
- Physical distribution of that shared framework/toolchain information uses
  Documentation Components that remain separate from Component-specific
  documentation ownership.
- Framework Help links and MCP/RAG evidence resolve immutable product/version
  document identities rather than human-facing `latest` aliases.
- Public AI Development Guidance is an explicitly public projection of the
  authoritative `ai-directive`; the mounted directive remains authoritative
  for actual project behavior.
- Public Skill Catalog metadata is information for discovery and comparison;
  actual Skill bundles remain CAR-owned and use `SkillBundleManifest`.
- Help or knowledge retrieval does not install, activate, execute, or grant
  authority to a Skill.
- Exact installed Component resources outrank a stale BoK snapshot for
  execution decisions.
- Notes are working proposals, journal is history, design/spec are the
  canonical closed-phase contract, and executable specifications are behavior
  evidence.

### Scope

- Define a versioned Component knowledge manifest and resource model.
- Consume the Phase 58 composition manifest and resolved resource API without
  duplicating Component identity or physical resource discovery.
- Define canonical CAR source/archive paths without duplicating Component
  identity metadata.
- Define resource authority, stability, language, media type, digest,
  provenance, license, and source-disclosure metadata.
- Define mandatory User Guide and Reference Manual entry points plus optional
  manual roles.
- Define SmartDox and admitted Markdown authoring/lint behavior.
- Decide and implement required HTML/PDF projection profiles.
- Generate and package Scaladoc plus a structured symbol/search index.
- Define source inclusion, exclusion, license, restricted-access, and
  release-source reproducibility policy without a source-omitted profile.
- Capture admitted SBT managed source under normalized
  `generated-source/main` and `generated-source/test` resources together with
  generator inputs, options, identities, digests, and provenance required for
  later reproduction, investigation, and debugging.
- Exclude the raw `target` layout, class files, incremental caches, temporary
  files, logs, and host-specific state from SourceCode SubComponents.
- Bind Component knowledge and model resources to Phase 58 logical resource
  identities and physical provenance.
- Consume Phase 58 development, repository, offline, access, integrity, and
  operation-mode resolution outcomes without reconstructing them.
- Define a `ComponentDevelopmentContext` or accepted equivalent that projects
  admitted manuals, model metadata and diagrams, APIs, configuration schema,
  examples, source, generated source, Scaladoc, tests, and provenance to
  development AI without exposing credentials or host-local state.
- Define the common Component model projection and deterministic Mermaid
  `classDiagram` plus StateMachine `stateDiagram-v2` resources.
- Integrate the model with Help, `/man`, OpenAPI/schema, CLI inspection, and a
  direct JSON manifest/resource route.
- Supply Phase 60 with the same knowledge/model manifest and exact resource
  navigation contract used by Help and AI.
- Enforce authorization, production visibility, path safety, integrity,
  confidentiality, and disclosure policy.
- Extend Cozy/sbt-cozy and SmartDox-owned build paths rather than duplicating
  authoring/rendering behavior in CNCF runtime.
- Extend Cozy and SmartDox publication so one generation supplies
  versioned SimpleModeling.org HTML, RDF/JSON-LD/catalog, stable
  document/section metadata, and optional framework Documentation Components.
- Define framework Documentation Component subject/version,
  publication-generation relationship, integrity, repository resolution, and
  closed-network Hub SAR composition.
- Define public AI Directive rule projection with directive/rule identity,
  version, source digest, authority, visibility, redaction, and canonical URL.
- Define public Skill Catalog metadata with Skill/bundle identity, owner,
  purpose, triggers, requirements, permissions, side effects, MCP
  requirements, installation reference, visibility, and digest.
- Generate a SimpleModeling.org AI Development Guide and Skill Catalog plus
  equivalent framework Documentation Component snapshots.
- Extend Help with framework/toolchain documentation references:
  `/help/system` describes the running CNCF runtime, `/man/system` resolves the
  matching CNCF framework documentation locally or online, and CML/Cozy links
  remain developer/toolchain navigation.
- Extend Help with active directive version/profile/digest and public-guide
  reference plus Component-associated Skill metadata and installation state.
- Integrate Textus BoK admission of structured framework publication
  knowledge, evidence-preserving indexing, RAG retrieval, read-only MCP
  operations, stale detection, and disclosure enforcement.
- Integrate Textus CBD Support manifest/resource admission, exact detail and
  usage retrieval, evidence-bearing MCP, and CAR Review documentation quality
  checks.
- Reconcile the Textus BoK existence-only/CBD-handoff boundary with BoK-owned
  semantic retrieval of authoritative packaged Component knowledge while CBD
  Support remains the primary detailed Component-use service.
- Validate embedded-small and primary-plus-required-SubComponent profiles,
  Develop-policy composition admitted explicitly by Phase 58,
  restricted-source authorization, and offline complete-release bundles.
- Validate online-only framework documentation, installed framework
  Documentation Components, and a closed-network Documentation Hub SAR
  without changing Component-specific information ownership.
- Promote verified behavior to current CNCF, Textus CBD Support, and Textus BoK
  design/specification documents before closure.

### Boundaries

- Phase 59 does not make CAR embedding the primary public distribution of
  CNCF/CML/Cozy documentation.
- Phase 59 does not treat a framework Documentation Component as an
  independent source of truth; its publication generation and hashes must
  match the canonical source projection.
- Phase 59 does not make HTML scraping the preferred RAG ingestion path when
  structured SmartDox/publication projections are available.
- Phase 59 does not publish every `ai-directive` rule or raw `SKILL.md`;
  publication requires explicit visibility and disclosure admission.
- Public Directive guidance does not override the mounted authoritative
  directive.
- Skill metadata, Help, or MCP/RAG discovery does not install, activate,
  execute, configure, or grant authority to a Skill or its dependencies.
- Actual Skill packaging, installation, and activation remain under the
  `SkillBundleManifest`, Cozy, Launcher, and Codex boundaries.
- Phase 59 does not distribute Component documentation primarily through Maven
  documentation classifiers.
- A manifest or Skill does not grant Operation authority or MCP readiness.
- Textus BoK retrieval does not make mutation or execution Operations MCP
  visible.
- Textus BoK does not invent Component compatibility, suitability, or support
  claims.
- CBD Support owns exact Component detail, versions, dependencies, usage,
  comparison, assessment, review, and primary AI assistance.
- BoK owns terminology, semantic discovery, evidence-bearing RAG/MCP retrieval,
  and exact identity handoff; it does not replace CBD Support.
- Source does not override public contracts and is not indexed when disclosure
  or authorization forbids it.
- Restricted source may reside in an access-controlled SourceCode SubComponent
  but secrets must not enter any Component artifact, diagnostics, indexes, RAG
  context, or MCP responses.
- Runtime Help must not require a compiler, Scaladoc generator, SmartDox
  renderer, PDF toolchain, or embedding provider.
- Online documentation unavailability must not prevent execution or make Help
  report online material as locally present.
- Human-facing `latest` aliases must not become MCP/RAG evidence identities.
- Fine-grained SubComponent fragmentation beyond Documentation and SourceCode
  is not part of the initial contract.
- Phase 59 does not make a Subcomponent CAR an implicit parent runtime
  dependency or activation request.
- Phase 59 does not implement Component Admin runtime views or management
  actions; those belong to Phase 60.
- Help, CBD Support, BoK, and later Admin must not independently scan
  SubComponent archives, repositories, caches, or development directories.
- Component-specific manual content remains owned by each Component.

### Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| DOC-01 | Inventory and executable acceptance | Existing Help/Manual/CAR/CBD Support/BoK contracts, contradictions, routes, publication surfaces, and exact acceptance identities are fixed before implementation. | planned |
| DOC-02 | Knowledge and model resource contracts | Versioned knowledge and Component-model manifests bind content, identity, provenance, disclosure, integrity, and codecs to the Phase 58 resource contract. | planned |
| DOC-03 | Authoring and content packaging toolchain | User Guide/Reference, model diagrams, Scaladoc, filtered release source, and Documentation/SourceCode content are generated through Cozy/SmartDox ownership for Phase 58 packaging. | closed |
| DOC-04 | Knowledge and development context composition | Knowledge, model, source, and framework publication resources compose over Phase 58 resolution into one attributed development context. | closed |
| DOC-05 | Unified Help and direct AI access | Help and AI consume one resolved Component resource space and publish the stable consumer contract later used by Phase 60 Admin. | closed |
| DOC-06 | Textus CBD Support primary integration | CBD admits exact manifests/resources into Component detail, usage, MCP, and CAR Review without losing source authority or selection evidence. | planned |
| DOC-07 | Textus BoK complementary integration | BoK performs Component semantic retrieval and admits structured framework, public Directive guidance, and Skill metadata with attributable RAG/MCP evidence and exact CBD handoff. | closed |
| DOC-08 | Representative Component and framework documentation acceptance | Existing Component-specific profiles plus online/installed/offline framework, public Directive guide, and Skill Catalog profiles prove consistent ownership and access. | planned |
| DOC-09 | Security, regression, and downstream validation | Path, integrity, authorization, disclosure, compatibility, regression, and full cross-repository suites pass. | planned |
| DOC-10 | Canonical documentation and closure | CNCF/CBD Support/Textus BoK design/spec/notes/strategy/phase records match verified behavior; working notes are marked overridden and historical. | planned |

### Acceptance

- One manifest deterministically enumerates every admitted Component knowledge
  resource.
- For shared CNCF/CML/Cozy/SmartDox information, one publication generation
  deterministically relates SmartDox source, SimpleModeling.org HTML,
  structured metadata, and optional framework Documentation Component
  resources.
- SimpleModeling.org exposes immutable versioned framework document/section
  identities and canonical URLs; human `latest` aliases are not evidence
  identities.
- Public AI guidance preserves directive version, rule identity, authority,
  visibility, canonical URL, and source digest without becoming the active
  directive.
- Public Skill metadata preserves Skill/bundle identity, owner, requirements,
  side effects, permissions, installation reference, visibility, and digest
  without exposing restricted Skill content.
- Manifest and resource paths are safe, versioned, integrity-checked, and
  identical between the admitted source tree and packaged CAR.
- Every representative user-facing Component provides a User Guide and
  Reference Manual accepted by deterministic lint.
- Admitted Markdown is parsed through the selected SmartDox profile.
- A public Scala API has packaged Scaladoc without relying on a Maven
  documentation classifier or runtime generator.
- Source availability is explicit; embedded or SourceCode SubComponent source
  obeys filtering, license, and authorization policy.
- SourceCode SubComponents contain the admitted hand-written and managed
  source plus generation/build provenance needed to investigate and debug the
  exact release without packaging transient `target` state.
- Phase 59 knowledge consumption uses the exact logical release, resolution,
  integrity, and availability results supplied by Phase 58.
- Help reports local, remote, restricted, unavailable, incompatible, stale,
  and corrupt states from Phase 58 without claiming physical content it did
  not resolve.
- Develop mode consumes required Documentation and the development target's
  admitted source only through the explicit Phase 58 policy, without a
  separate fetch or resolver, before claiming development readiness.
- Test mode uses only explicitly selected deterministic local fixtures,
  expanded artifacts, or offline bundles and performs no implicit remote
  retrieval.
- Demo mode performs no automatic source composition; remote Documentation
  access requires explicit policy.
- Production retains independent primary activation, selects only
  embedded-primary resources, performs no remote resource selection, and never
  automatically resolves, mounts, or fetches SourceCode.
- Missing, stale, corrupt, or incompatible required development resources
  produce structured `development-resource-incomplete` failure.
- The resolved development context supplies exact Component documentation,
  model, API, configuration, examples, source, tests, and provenance to AI.
- Entity, Powertype, StateMachine, Value, and Datatype metadata produce
  deterministic machine-readable projections, class diagrams, and state
  diagrams.
- A framework Documentation Component carries an exact product/version
  publication snapshot and proves its canonical publication generation and
  resource digests.
- Missing, incompatible, duplicate, unsafe, corrupt, or unauthorized
  SubComponents fail knowledge/development composition with deterministic
  diagnostics without being misreported as local.
- Help provides unified Component-oriented human navigation and a stable
  machine-readable manifest discovery path.
- Phase 60 can consume the same manifest/resource identities and navigation
  contract without scanning or regenerating knowledge artifacts.
- Framework/toolchain links explicitly distinguish installed and versioned
  online availability without changing Component manual resolution.
- `/help/system` describes the running runtime while `/man/system` resolves
  the matching CNCF documentation locally or online.
- AI can retrieve exact configuration, Operation, schema, manual, example,
  Scaladoc, and admitted-source information from the direct manifest route.
- Textus CBD Support resolves the same exact resources for selected
  Component/version detail, usage guidance, read-only MCP, and CAR Review.
- CBD Support remains operational without BoK and keeps BoK evidence
  separately attributable when present.
- Textus BoK ingests exact Component manifest resources under the existing
  Component-specific contract and separately admits structured
  SimpleModeling.org framework publication knowledge or an equivalent
  framework Documentation Component snapshot.
- Framework RAG retrieval does not require HTML screen scraping and returns
  product version, document/section identity, canonical URL, source generation,
  and content hash.
- AI-guidance and Skill-catalog MCP/RAG reads cannot install or activate a
  Skill, mutate Codex configuration, grant MCP authority, or override the
  mounted directive.
- BoK responses distinguish snapshot version/hash from the installed Component
  and expose stale mismatch rather than silently mixing versions.
- Proprietary or unauthorized source never enters BoK indexes, RAG context, or
  MCP responses.
- BoK retrieval and Help access do not grant runtime Operation authority or
  broaden MCP-ready execution surfaces.
- CBD Support and Textus BoK documentation describe CBD as the primary
  Component-use service and BoK as the complementary semantic retrieval route.
- Runtime presentation requires no build/render/embedding toolchain.
- Online-only, installed framework snapshot, and offline-Hub profiles resolve
  the same versioned framework document and section identities.
- Canonical CNCF, Textus CBD Support, and Textus BoK design/specification
  documents describe the final verified contract.
- The implementation note is marked historical and explicitly overridden by
  final design/specification.
- No current design/spec/note/manual/strategy/phase record contradicts the
  verified behavior.

### Verification

Phase 59 closure requires:

- failing-first Executable Specifications for every DOC-01 contract group;
- manifest codec/schema/property/path/integrity specifications;
- source-tree versus packaged-CAR equivalence specifications;
- managed-source normalization, generation-provenance, debugging-completeness,
  and transient-build-state exclusion specifications;
- Cozy lint and strict release-readiness specifications;
- SmartDox and admitted Markdown parsing/projection specifications;
- SimpleModeling.org HTML/RDF/JSON-LD/catalog and document/section identity
  projection specifications;
- public Directive projection/visibility/redaction and mounted-authority
  specifications;
- public Skill metadata/catalog and SkillBundle authority-separation
  specifications;
- Scaladoc packaging and public-surface specifications;
- Phase 58 resolver-consumer and physical-provenance preservation
  specifications;
- Help/AI behavior for every Phase 58 availability and integrity state;
- Component model metadata plus deterministic class/state diagram
  specifications;
- manifest-based AI development-context, provenance, and redaction
  specifications;
- framework Documentation Component, canonical-online, and offline-Hub
  resolution specifications;
- direct Help/manifest/resource HTTP and CLI specifications;
- authorization, production visibility, disclosure, and hostile-resource
  specifications;
- Textus CBD Support exact retrieval, usage, MCP, and CAR Review
  specifications;
- Textus BoK structured-publication ingestion, indexing, evidence, stale, RAG,
  MCP, and CBD handoff specifications;
- ai-directive public-projection and Skill Catalog/SkillBundle separation
  validation;
- an end-to-end packaged Component to direct Help, CBD Support, and BoK
  RAG/MCP check;
- representative downstream Component/CAR lint;
- full CNCF, Cozy/sbt-cozy, SmartDox, Textus CBD Support, Textus BoK, and
  selected downstream test suites;
- `sbt --batch Test/compile` in every changed Scala repository;
- `git diff --check`;
- read-only review, review-fix, and clean re-review;
- final reconciliation of implementation, Executable Specifications,
  `docs/design`, `docs/spec`, and notes; and
- strategy/phase/checklist closure records with exact evidence.

### Final Documentation Gate

DOC-10 is mandatory and occurs only after implementation and cross-repository
acceptance are stable.

It must:

- create or update
  `docs/design/component-documentation-knowledge-package.md`;
- create or update
  `docs/spec/component-documentation-knowledge-package.md`;
- update affected current Help/Manual/CAR/Web/MCP designs and specifications;
- update SimpleModeling.org and Cozy publication designs for canonical
  versioned HTML, structured metadata, and Documentation Component projection;
- update `ai-directive` public-projection guidance and Skill
  bundle/catalog/installation documentation without weakening their
  authoritative boundaries;
- update Textus CBD Support design/spec/strategy/manuals for the primary
  manifest/detail/usage/review boundary;
- update Textus BoK design/spec/strategy/manuals for the complementary
  ingestion/RAG/MCP/CBD-handoff boundary;
- update
  `docs/notes/component-documentation-knowledge-package-implementation.md`
  to `historical, non-normative`;
- state in that note that canonical design/specification override it;
- retain the journal as chronological history;
- remove or mark superseded every contradictory current documentation rule;
- link normative behavior to exact Executable Specification evidence; and
- confirm that no latest specification remains only in notes.

Phase 59 cannot close with behavior represented only in notes, journal, phase
documents, source code, or tests.

### Repository Responsibility

| Repository | Phase 59 responsibility |
| --- | --- |
| `/Users/asami/src/dev2025/cloud-native-component-framework` | Knowledge/model manifests, Phase 58 resolver consumption, Help/direct AI access, development context, authorization, and canonical CNCF contract |
| `/Users/asami/src/dev2025/cozy` and `sbt-cozy` | Authoring lint, model/diagram generation, Scaladoc/manual projection, filtered release-source capture, and content handoff to Phase 58 packaging |
| `/Users/asami/src/dev2025/smartdox` | SmartDox/Markdown and required HTML/PDF projection capabilities |
| `/Users/asami/src/dev2025/simplemodeling-org` | Canonical versioned Web publication, stable document/section URLs, RDF/JSON-LD/catalog projection, and online human/AI access |
| `ai/directive` (`ai-directive` repository) | Authoritative Directive ownership, stable public rule identities, visibility, and public-guide projection inputs |
| CAR Skill owners and Textus/CNCF Launchers | Public Skill metadata inputs plus unchanged explicit SkillBundle installation/activation ownership |
| `/Users/asami/src/dev2026/textus-cbd-support` | Primary exact Component detail/usage/MCP/CAR Review integration and canonical CBD contract |
| `/Users/asami/src/dev2026/textus-bok` | Complementary semantic manifest/resource admission, RAG/MCP retrieval, CBD handoff, and canonical BoK contract |
| selected sample/Component repositories | Embedded and required-SubComponent, develop-mode, restricted-source, and offline-bundle end-to-end acceptance |

### Planning References

- `docs/notes/component-documentation-knowledge-package-implementation.md`
- `docs/journal/2026/07/2026-07-25-component-documentation-and-ai-knowledge-package-consideration.md`
- `docs/journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md`
- `docs/notes/component-resource-subcomponent-implementation.md`
- `docs/phase/phase-60.md`
- `docs/design/static-form-ui-generation-contract.md`
- `docs/design/packaged-source-activation.md`
- `/Users/asami/src/dev2025/cozy/docs/design/car-documentation-lint.md`
- `/Users/asami/src/dev2025/cozy/docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/dev2025/smartdox/README.md`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-rdf-structure-and-usage.md`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-structure-expansion-for-ai-era-engineering-knowledge-platform.md`
- `ai/directive/README.md`
- `ai/directive/samples/README.md`
- `docs/journal/2026/07/2026-07-21-codex-skill-bundle-contract.md`
- `/Users/asami/src/dev2026/textus-cbd-support/docs/strategy/textus-cbd-support-development-strategy.md`
- `/Users/asami/src/dev2026/textus-cbd-support/docs/spec/mcp-ownership.md`
- `/Users/asami/src/dev2026/textus-bok/docs/strategy/textus-bok-development-strategy.md`
- `/Users/asami/src/dev2026/textus-bok/docs/spec/bok-domain-model.md`

### Current Resume Point (Historical/Superseded)

This former pre-split resume point is historical and superseded by the approved
D-59-SPLIT child sequence. Phase 59 is planned and must not start before Phase
58.9 closes the Phase 58 series.

After Phase 58.9 closes the Phase 58 series, begin DOC-01 with a cross-repository inventory. Freeze
the knowledge/model resource contract, AI development context, Help consumer
API, and framework-publication separation before implementing manifests or
changing Help routes. Treat Phase 58 composition/resolution as an input and
Phase 60 Admin as a later consumer.
