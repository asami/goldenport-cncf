# Component Documentation Knowledge Package Implementation Proposal

status = historical, non-normative, superseded
date = 2026-07-25
phase = Phase 59

This note is the historical working implementation proposal for Phase 59. It
is not the current Component documentation contract and must not be treated
as a current proposal or specification. The canonical design and
specification now supersede this note:

- `docs/design/component-documentation-knowledge-package.md`
- `docs/spec/component-documentation-knowledge-package.md`

During Phase 59, verified decisions and behavior must be promoted to:

- `docs/design/component-documentation-knowledge-package.md`; and
- `docs/spec/component-documentation-knowledge-package.md`.

The consideration history below is retained for traceability. Any difference
between this historical note and either canonical document is resolved in
favor of the current design/specification. This note contains no competing
latest requirements.

Consideration history:

- `docs/journal/2026/07/2026-07-25-component-documentation-and-ai-knowledge-package-consideration.md`
- `docs/journal/2026/07/2026-07-31-phase-56-component-subcomponent-development-composition.md`
- `docs/journal/2026/07/2026-07-31-phase-56-resource-subcomponent-phase-split.md`

## Purpose

Implement a one-stop, self-describing Component documentation and knowledge
surface for humans and AI.

A Component must logically contain the Component-specific information needed
to understand, use, configure, invoke, operate, diagnose, inspect, and develop
it. Physical runtime constraints permit one logical Component release to
consist of a primary execution CAR plus exact Documentation and SourceCode
SubComponents. The Component Repository guarantees that every required
SubComponent remains retrievable for the exact release.

Subcomponent Component identity, composition, publication completeness,
repository/cache access, resolution precedence, integrity, physical
provenance, lifecycle, and operation-mode policy are Phase 58 contracts. Phase
59 consumes the Phase 58 composition proposal/design/specification and must
not build a second CAR or payload resolver. Phase 60 Admin is a later consumer
of the same Phase 58 view and the Phase 59 knowledge/model manifests.

Shared CNCF, CML, Cozy, and SmartDox framework/toolchain documentation follows
a separate distribution rule. SimpleModeling.org is its basic public
information surface. When physical distribution is required, the same
publication generation is packaged as a framework Documentation Component.

## Implementation Principles

1. Documentation and release source are part of the logical Component
   contract.
2. The primary execution CAR and its declared Documentation and SourceCode
   SubComponents form one exact-version Component release.
3. Documentation and SourceCode Subcomponents are repository-managed Component
   CARs. Their payloads are not implicit parent runtime dependencies,
   Componentlets, or separately activated Subsystem members.
4. A root composition manifest plus subordinate knowledge/model manifests
   deterministically index every physical resource without duplicating
   identity authority.
5. Human Help and AI retrieval project the Phase 58 resolved Component
   resources. Phase 60 Admin consumes the same read-only resource and
   knowledge/model contract.
6. `OperationMode.Develop` automatically resolves and mounts Documentation and
   the development target's admitted source without exposing mode to Component
   domain code.
7. Publication completeness and runtime activation are distinct: every
   required SubComponent must exist before release visibility, while
   production may activate the primary CAR without fetching knowledge
   artifacts.
8. SmartDox is the canonical hand-written document model.
9. User Guide and Reference Manual are the standard hand-written manual axes.
10. Component Scaladoc is included in the logical Component distribution.
11. Release source is embedded or packaged as a SourceCode SubComponent;
    disclosure and authorization govern access rather than logical omission.
12. Portable model metadata and diagrams cover Entity, Powertype,
    StateMachine, Value, Datatype, and relationships.
13. The SourceCode SubComponent retains the release inputs and generated
    development evidence needed for later reproduction, investigation, and
    debugging without copying transient build directories.
14. Textus CBD Support is the primary mediated AI integration for exact
   Component discovery, detail, usage, and review.
15. Textus BoK provides the complementary terminology and semantic RAG/MCP
    route over admitted Component knowledge.
16. The exact installed Component manifest remains authoritative over a stale
    BoK snapshot.
17. SimpleModeling.org is the basic public information surface for shared
    CNCF/CML/Cozy/SmartDox documentation.
18. Framework Documentation Components are optional versioned snapshots of the
    same publication generation, not Component-specific documentation owners.
19. Framework MCP/RAG prefers structured SmartDox/publication projections over
    HTML scraping and preserves immutable product/version evidence.
20. Public AI Directive guidance is a projection and never overrides the
    mounted authoritative directive.
21. Public Skill metadata supports discovery only; installable Skills remain
    CAR-owned `SkillBundleManifest` resources and require explicit
    installation/activation.

## Proposed Physical Shape

The following layout is provisional:

```text
logical Component release
├── primary component.car
│   ├── component/
│   ├── runtime/
│   ├── runtime-required model/config/schema metadata
│   ├── composition-manifest.json
│   ├── minimal help and SubComponent diagnostics
│   └── provenance/
├── documentation subcomponent.car
│   ├── manual/
│   ├── model/
│   ├── diagrams/
│   ├── scaladoc/
│   ├── help/
│   ├── api/
│   ├── assets/
│   └── knowledge/manifest.json
└── source-code subcomponent.car
    ├── source/
    ├── generated-source/
    ├── cml/
    ├── build/
    ├── tests/
    └── provenance/
```

Phase 59 must confirm paths against existing CAR source/archive rules before
making them normative. It must not introduce a second identity source that
duplicates `project.yaml`, the CAR descriptor, or the existing runtime
descriptor.

The separate shared framework publication and optional distribution
projections are provisionally:

```text
SmartDox publication source
├── SimpleModeling.org
│   ├── versioned HTML
│   ├── RDF/JSON-LD
│   ├── ontology/schema/glossary
│   └── catalog and document/section metadata
└── documentation.car
    ├── source/
    ├── rendered/html/
    ├── rendered/pdf/        # optional
    ├── scaladoc/            # when applicable
    ├── index/
    ├── ai-guide/            # public Directive projection
    ├── skills/              # public Skill metadata/catalog
    └── publication-manifest.json
```

This framework Documentation Component is a versioned snapshot of the same
publication generation. It does not create another authoring source or replace
the Component-specific Documentation/SourceCode SubComponent relationship.

## Component Knowledge Manifest

Introduce one versioned manifest schema, provisionally:

```text
cncf.component-knowledge.v1
```

The manifest should contain:

- schema version;
- Component identity, kind, and version;
- primary artifact coordinate and digest;
- SubComponent role, exact coordinate, parent relationship, logical version,
  digest/signature, requiredness, access policy, and repository identity;
- knowledge revision and manifest digest;
- contextual framework publication subject, version, generation, and
  canonical base URL when referenced;
- stable contextual framework document and section identities when referenced;
- resource identity, kind, role, media type, language, path, digest, and size;
- authority and stability classification;
- source availability and disclosure policy;
- generated-with and compatible-with provenance;
- Documentation and SourceCode SubComponent references;
- explicit local, installed, cached, online, and unavailable resolution state;
- Help entry points;
- optional search and symbol-index resources; and
- integrity/signature metadata where the CAR contract supplies it.

Candidate resource authority values are:

- `contract`;
- `authoritative`;
- `generated`;
- `example`;
- `implementation`; and
- `context`.

The exact enum vocabulary remains subject to executable use cases. The
selection rule must ensure that public contracts outrank examples and source
implementation when an AI constructs supported usage.

The manifest is a directory and evidence map. It does not grant execution
authority, make an Operation public, or make an Operation MCP-ready.

## Manual Contract

Every ordinary user-facing Component provides:

- a User Guide entry point; and
- a Reference Manual entry point.

Small Components may use one file per manual. Larger Components may use a
document tree. Optional roles such as Tutorial, Operations Guide, Security
Guide, Migration Guide, Integration Guide, Troubleshooting Guide, Architecture
Guide, and Development Guide are admitted only when they have a distinct
audience or lifecycle.

`.dox` is preferred. `.md` and `.markdown` are admitted only through the
SmartDox Markdown parser/profile selected by the final contract. Existing
Asciidoc and HTML inputs require an explicit compatibility decision; they must
not silently become equal canonical authoring formats.

Cozy lint should validate:

- required entry points;
- SmartDox parseability;
- safe relative links and assets;
- unique resource/manual identities;
- manifest/resource consistency;
- non-empty minimum content;
- source/archive projection equivalence; and
- strict release-readiness requirements.

AI semantic review remains separate from deterministic lint.

## Generated Projections

Generated resources include:

- human Help;
- HTML manual projection;
- PDF projection when required by the final profile;
- OpenAPI and schema resources;
- generated Component/Service/Operation descriptions;
- portable Entity/Powertype/StateMachine/Value/Datatype and relationship
  metadata;
- deterministic Mermaid class diagrams and StateMachine diagrams;
- Scaladoc;
- Scaladoc symbol/search index;
- document search index; and
- provenance metadata.

The Component distribution must remain useful without a compiler, Scaladoc
generator, SmartDox renderer, or PDF toolchain installed at runtime.

Phase 59 must decide which HTML and PDF projections are mandatory. Generated
output must record its source resource and source digest so stale projections
can be detected.

## Scaladoc

A Component with a public Scala API packages generated Scaladoc in the primary
CAR or its Documentation SubComponent.

The implementation should:

- generate Scaladoc during the build/publication workflow;
- filter public versus internal API by an explicit publication rule;
- package rendered output as a manifest resource;
- generate a structured symbol/search index where feasible;
- retain source links only when the source disclosure policy permits them; and
- fail strict release readiness when required Scaladoc is absent or stale.

A Maven `-javadoc.jar` is not the primary Component documentation contract.

## Source

Candidate source availability values are:

- `embedded`;
- `source-code-subcomponent`;
- `development-directory`; and
- `restricted`.

The manifest records license, disclosure policy, source kinds, and embedded,
development-directory, or SourceCode SubComponent identity. Source packaging
must exclude credentials, local configuration, build caches, generated
secrets, unrelated host files, and other non-release state.

Source improves investigation but remains implementation evidence. It does not
override public contracts. Restricted source remains a required logical
SubComponent with an explicit access policy; it is never claimed as inspected,
indexed, or available to an unauthorized caller.

The SourceCode SubComponent must preserve the release evidence needed to
reproduce, investigate, and debug the exact Component version. This includes:

- hand-written main and admitted test source;
- CML and other authoritative generator inputs;
- the public/admitted output of SBT `Compile / managedSources` and, when
  required for diagnosis, `Test / managedSources`;
- build definitions and exact dependency/generator identity;
- compiler and generation options that affect emitted source or behavior;
- source, generator-input, and generated-output digests; and
- generation and packaging provenance.

Managed source is collected from build locations such as
`target/scala-*/src_managed/**`, but is normalized into
`generated-source/main` or `generated-source/test`. The physical `target`
layout is not part of the artifact contract. Class files, incremental compiler
caches, temporary files, logs, downloaded caches, host paths, and other
transient build state are excluded.

The contract aims for debugging completeness, not a byte-for-byte archive of
the build workspace. Phase 59 must define executable release-readiness checks
for evidence that is required to explain or regenerate the released behavior.

## Component SubComponents

Phase 58 defines Documentation and SourceCode Subcomponents as versioned
Component CARs with a declared parent relationship to one exact logical
Component release. Their payloads use Component Repository identity,
integrity, cache, and retrieval services; a parent does not implicitly
activate the child merely to access its payload.

The inherited Phase 58 contract fixes:

- SubComponent kind and identity;
- exact parent Component identity and logical version;
- required relationship and publication completeness;
- resolution from development directories, expanded CARs, and repositories;
- digest and signature validation;
- precedence between embedded, development, and SubComponent resources;
- duplicate resource identity handling;
- local, remote, restricted, unavailable, missing, and incompatible
  diagnostics;
- lifecycle and unload behavior; and
- authorization and production visibility.

The primary Component retains runtime-required metadata, a minimal overview,
the composition manifest, and SubComponent diagnostics. Physical splitting
must not create separate user-facing Component documentation or source
namespaces.

The initial large Component topology is one primary execution CAR, one
Documentation SubComponent, and one SourceCode SubComponent. Fine-grained
language/media fragmentation is deferred.

Phase 59 consumes the resulting exact resource inventory, state, content
handle, and provenance. It does not implement repository publication,
retrieval, cache, archive walking, or resolution precedence.

### Development Composition

`OperationMode.Develop` selects a Phase 58 runtime-owned development resource
profile. It does not become a Component-domain mode.

The inherited resolution order is:

```text
explicit development directory
  -> development-local documentation/source
  -> expanded SubComponents
  -> local Component Repository
  -> remote Component Repository
```

The Phase 58 resolver mounts the Documentation SubComponent automatically. The
development target's checked-out source tree satisfies the SourceCode role;
otherwise the exact SourceCode SubComponent is resolved subject to access
policy. Missing, stale, corrupt, or incompatible required development
resources produce structured `development-resource-incomplete` failure.

The resulting `ComponentDevelopmentContext` contains admitted manuals, model
metadata and diagrams, Service/Operation/SPI contracts, configuration schema,
examples, source, generated source, Scaladoc, tests, build/generation
provenance, and exact dependency documentation. It excludes credentials,
developer-local configuration, caches, and untracked host state.

### Operation Mode Resource Policy

Operation mode selects a Phase 58 runtime-owned resource-composition policy.
It never becomes an input to Component domain behavior.

| Operation mode | Documentation resources | Source resources | Implicit remote access |
| --- | --- | --- | --- |
| `Develop` | Automatically resolve, verify, and mount exact Documentation resources. | Mount the development target tree; otherwise resolve its exact SourceCode SubComponent. Dependency source requires explicit disclosure authorization. | Allowed by configured development repository policy. |
| `Test` | Use only explicitly selected, deterministic local fixtures, expanded artifacts, or offline bundles. | Use only explicitly selected deterministic test source resources. | Disabled by default so test results do not depend on network state. |
| `Demo` | Resolve installed or cached Documentation on demand; remote retrieval requires an explicit demo policy. | Do not automatically resolve or mount source. | Disabled by default. |
| `Production` | Keep primary-only activation possible; resolve authorized Documentation on demand without changing execution readiness. | Do not automatically resolve, mount, or fetch source. | Documentation only when explicitly configured and authorized; never source. |

An explicit resource request may report a required SubComponent as remote,
restricted, or unavailable without changing Component execution semantics.
The launcher/runtime owns these choices and projects the resulting resources
through the same `ResolvedComponentKnowledge` contract in every mode.

### Framework Documentation Component

Shared CNCF/CML/Cozy/SmartDox information has a distinct relationship:

- the documentation subject is a framework or toolchain product and version,
  not a target execution Component;
- SimpleModeling.org is the primary public surface;
- the Documentation Component is an optional snapshot of the same publication
  generation;
- canonical URL, publication generation, document/section identities, hashes,
  and signature bind the snapshot to the public projection;
- absence of the framework Documentation Component never prevents Component
  startup or Component-specific Help/manual access; and
- an offline Documentation Hub SAR may compose framework Documentation
  Components, Textus BoK, and an approved retrieval provider.

### AI Directive and Skill Projections

The authoritative `ai-directive` is a versioned AI behavior contract, not a
documentation source that can be copied wholesale into public knowledge.
Public projection requires explicit rule visibility and records:

- directive, profile, and rule identity;
- originating version and source digest;
- authority and visibility;
- general-public title, purpose, rationale, applicability, and examples;
- redaction/disclosure decision; and
- immutable canonical publication URL.

The mounted project directive remains authoritative. Public guidance, Help,
MCP, and RAG cannot override it. Non-authoritative samples remain examples and
must not be projected as rules.

Skill publication similarly separates public metadata from executable
contents. Public metadata records Skill and bundle identity, owner CAR,
purpose, trigger, requirements, permissions, side effects, MCP requirements,
installation reference, visibility, version, and digest. Raw `SKILL.md`
content requires its own explicit public-admission decision.

The actual Skill bundle remains owned by its CAR and follows the
`SkillBundleManifest` packaging/compatibility contract. Publication,
documentation lookup, or MCP discovery does not install, activate, execute,
configure, or grant authority to that Skill.

## Help and Direct AI Route

Help remains the unified Component information surface for humans and
structured consumers.

The provisional AI discovery route is:

```text
/help/{component}
  -> /help/{component}/manifest.json
  -> resolved Component knowledge resources
```

Phase 59 must confirm the canonical route and its relationship with existing
`/help`, `/man`, OpenAPI, MCP, authorization, and production-mode policies.

Human Help should advertise the manifest with a machine-readable link relation.
AI consumers should switch to the manifest and structured resources after
discovery instead of scraping HTML.

The CNCF runtime needs a provider-neutral resolver model, provisionally:

```text
embedded Component resources
  + resolved Documentation SubComponent resources
  + resolved SourceCode SubComponent or development-directory resources
  -> ResolvedComponentKnowledge
  -> ComponentDevelopmentContext in develop mode
  -> Help / HTTP manifest / CLI / MCP or other projections
```

The Component resolver owns path safety, identity, precedence, integrity, and
visibility. Projection code must not independently walk CAR internals.

Framework/toolchain context uses a separate documentation-reference resolver:

```text
running or built-with product/version
  + installed framework Documentation Component
  + cached publication metadata
  + immutable canonical SimpleModeling.org reference
  -> ResolvedFrameworkDocumentationReference
  -> system/developer Help links and framework MCP/RAG evidence
```

`/help/system` describes the running CNCF runtime. `/man/system` resolves the
matching CNCF framework documentation from an installed snapshot or immutable
versioned SimpleModeling.org URL. CML and Cozy documentation belongs under
developer/toolchain navigation rather than ordinary operator Help.

An AI Help projection may show the active directive version/profile/digest and
its public-guide reference. Component Help may show associated Skill metadata,
availability, compatibility, and installation state. It must not expose
restricted rule bodies, raw private Skill instructions, credentials, approval
configuration, or provider secrets, and it must not perform installation.

The framework resolver must not silently present online content as local,
block Component execution when online documentation is unavailable, or use a
human `latest` alias as an evidence identity.

## Textus CBD Support Primary Integration

Phase 59 includes real integration with
`/Users/asami/src/dev2026/textus-cbd-support`.

CBD Support is the primary AI-facing Component use path after direct Help. Its
existing ownership already includes versions, runtime compatibility,
dependencies, Operations, artifacts, manuals, examples, reuse guidance, CAR
Review, and evidence-bearing read-only MCP operations.

Phase 59 extends that contract so CBD Support consumes exact Component
knowledge manifests and resources while preserving canonical
SimpleModeling.org publication references for broader documentation. It does
not rely only on catalog links or model-metadata sidecars.

The integration requires:

- catalog, local development directory, warehouse CAR, and managed-cache
  observations to retain the exact Component knowledge manifest location and
  digest when available;
- safe resolution of Component-local resources, Documentation/SourceCode
  SubComponents, and immutable canonical publication references;
- explicit absence when a catalog or artifact does not publish Component
  knowledge;
- exact Component/version/catalog selection before detailed retrieval;
- authoritative configuration, Operation, schema, manual, example, Scaladoc,
  source-availability, and provenance projection;
- source-disclosure, license, authorization, origin, size, and digest
  enforcement;
- `getUsage` and related detailed guidance to cite exact manifest resources
  and distinguish contract, manual, example, inferred advice, and source
  evidence;
- bounded read-only MCP access to the selected manifest and knowledge
  resources under CBD-owned Component usage semantics;
- CAR Review admission of documentation completeness, manifest integrity,
  Scaladoc, source policy, Help discovery, and BoK publication evidence;
- no HTML scraping, hidden latest-version selection, cross-catalog identity
  merging, or unsupported completion of missing Component facts; and
- end-to-end acceptance across direct Help, published/local Component
  knowledge, CBD exact retrieval, usage guidance, and CAR Review.

CBD Support remains usable without Textus BoK. BoK semantic evidence can enrich
requirement matching, but it must remain separately attributable and cannot
rewrite CBD-owned Component facts.

## Textus BoK Complementary RAG/MCP Integration

Phase 59 also includes real integration with
`/Users/asami/src/dev2026/textus-bok`.

The current Textus BoK contract treats CAR/SAR references as existence-only and
hands detailed usage questions to CBD Support. Phase 59 extends the BoK route
without reversing that primary ownership:

- Textus BoK owns evidence-bearing indexing, search, and retrieval of admitted
  Component knowledge under the existing Component-specific contract;
- shared framework knowledge is an additional, separately identified source
  class admitted from SimpleModeling.org structured publication metadata or
  the equivalent framework Documentation Component snapshot;
- public AI guidance and public Skill metadata are additional source kinds
  whose authority and visibility remain distinct from active directives and
  installed Skill bundles;
- Textus BoK RAG/MCP may answer from SmartDox-derived documents and sections,
  RDF/JSON-LD, glossary, ontology, schema, catalog data, exact packaged
  manuals, contracts, examples, Scaladoc indexes, and source admitted by
  disclosure policy;
- Textus BoK owns terminology, semantic association, and cross-Component
  knowledge discovery;
- Textus BoK returns exact Component identity, version, resource evidence, and
  manifest digest for handoff to CBD Support;
- CBD Support owns exact Component detail, versions, dependencies, usage,
  suitability guidance, comparison policy, review, and AI-assisted use;
- Textus BoK does not invent capability, compatibility, or support claims that
  are absent from authoritative Component resources; and
- the direct Help manifest for the exact Component version remains
  authoritative for execution decisions.

The integration requires:

- continued admission of the Component knowledge manifest as a versioned
  Component-specific BoK source;
- separate admission of SimpleModeling.org framework publication metadata and
  the equivalent framework Documentation Component snapshot;
- safe resolution of declared Component-local and Documentation/SourceCode
  SubComponent resources;
- deterministic chunk/resource identities;
- preservation of Component version, resource ID, section ID, source path,
  authority, digest, license, and indexed-at metadata;
- separate preservation of framework product/version, canonical URL,
  publication generation, document/section ID, digest, and indexed-at metadata;
- separate preservation of directive/profile/rule or Skill/bundle identity,
  version, owner, authority, visibility, canonical URL, and source digest;
- structured ingestion without requiring HTML screen scraping;
- lexical/structural retrieval independent of a particular embedding model;
- optional embedding/vector projections behind existing provider boundaries;
- RAG results with exact evidence citations;
- bounded read-only MCP operations for discovery, search, manifest retrieval,
  and resource/section retrieval;
- curated BoK MCP Operations rather than automatic publication of framework
  Documentation Component Operations;
- bounded read-only AI-guidance and Skill-metadata discovery without
  installation, activation, execution, configuration mutation, or authority
  grant;
- stale-snapshot detection against manifest digest;
- source-disclosure and authorization enforcement before indexing or response;
- no automatic publication of mutation or execution Operations through MCP;
  and
- end-to-end acceptance from a packaged sample Component through BoK
  ingestion/RAG/MCP, CBD Support handoff and usage retrieval, and direct Help
  verification.

Textus BoK design, specification, strategy, manual, and executable evidence
must be updated in the Textus BoK repository as part of Phase 59. A CNCF-only
mock is not sufficient acceptance.

## Repository Responsibilities

| Repository | Responsibility |
| --- | --- |
| `cloud-native-component-framework` | Knowledge/model manifests, Phase 58 resolver consumption, development context, Help/AI direct surface, authorization, and executable documentation contract |
| `cozy` / `sbt-cozy` | Authoring validation, model/diagram generation, SmartDox/manual projection, Scaladoc generation, filtered release source, content handoff to Phase 58 packaging, and source/archive equivalence |
| `smartdox` | SmartDox/Markdown parsing and HTML/PDF/document projection capabilities used by the build toolchain |
| `simplemodeling-org` | Canonical versioned HTML publication, stable document/section URLs, RDF/JSON-LD/catalog projection, and online human/AI access |
| `ai-directive` | Authoritative Directive ownership, public-rule identity/visibility, and public-guide projection inputs |
| `textus-cbd-support` | Primary exact Component discovery/detail/usage/review integration, manifest/resource resolution, evidence-bearing MCP, and documentation quality assessment |
| `textus-bok` | Complementary terminology/semantic Component knowledge admission, indexing, RAG/MCP retrieval, CBD handoff, and stale/disclosure enforcement |
| representative Component/sample | End-to-end embedded/required-SubComponent, develop-mode, restricted-source, offline-bundle, Help discovery, CBD usage/review, and BoK retrieval evidence |

If a missing capability belongs to one of these repositories, Phase 59 changes
the owning repository rather than duplicating the capability in CNCF.

## Implementation Sequence

1. Inventory current Help/Manual/CAR/SimpleModeling.org/ai-directive/Skill
   bundle/CBD Support/Textus BoK contracts and freeze failing-first acceptance
   identities.
2. Consume the Phase 58 Component release/resource identities and define
   publication, document, model, section, canonical URL, and
   authority/disclosure identities.
3. Implement knowledge/model manifest codecs and Phase 58 resolver adapters in
   CNCF.
4. Implement Cozy/SmartDox authoring, lint, SimpleModeling.org HTML and
   structured publication projections, public AI guidance, Skill Catalog,
   model diagrams, Scaladoc, filtered release source, and content packaging
   inputs.
5. Compose Phase 58 resolved Documentation/SourceCode resources into
   `ResolvedComponentKnowledge`.
6. Implement `ComponentDevelopmentContext` over the Phase 58 develop-mode
   resource view.
7. Implement unified Help and direct manifest/resource access.
8. Implement Textus CBD Support manifest/resource admission, exact detail,
   usage, MCP, and CAR Review integration.
9. Implement Textus BoK structured-publication admission, indexing, RAG/MCP
   retrieval, evidence, CBD handoff, and stale/disclosure behavior.
10. Prove representative embedded, required-SubComponent, develop-mode,
    restricted-source, production-primary-only, and offline-complete profiles
    through direct Help, CBD Support, and Textus BoK.
11. Complete security, compatibility, regression, and downstream validation.
12. Promote verified behavior to canonical design/specification and mark this
    note historical.

## Documentation Lifecycle and Closure

During implementation:

- this note holds the current working implementation proposal;
- the journal holds chronological reasoning and decision history;
- phase/checklist documents hold plan, status, and acceptance evidence; and
- executable specifications determine verified behavior.

At Phase 59 closure:

- `docs/design/component-documentation-knowledge-package.md` describes the
  verified architecture, ownership, flows, and rationale;
- `docs/spec/component-documentation-knowledge-package.md` defines the
  normative static contract;
- this note is updated to `historical, non-normative` and explicitly states
  that design/spec override it;
- the journal remains historical and is not rewritten as a specification;
- Textus CBD Support and Textus BoK design/spec/strategy documents reflect the
  final primary/supporting integration boundary; and
- no current README, design, spec, note, phase, manual, or executable
  specification contradicts the implemented behavior.

Phase 59 cannot close while the latest contract exists only in this note,
journal, phase document, or implementation.
