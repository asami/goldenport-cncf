# Component Documentation Knowledge Package Implementation Proposal

status = proposed, non-normative
date = 2026-07-25
phase = Phase 56

This note is the working implementation proposal for Phase 56. It is not the
final Component documentation contract.

During Phase 56, verified decisions and behavior must be promoted to:

- `docs/design/component-documentation-knowledge-package.md`; and
- `docs/spec/component-documentation-knowledge-package.md`.

When either canonical document differs from this note, the design and
specification take precedence. At Phase 56 closure this note must be marked
historical and must point to the final design/specification instead of
remaining a competing latest specification.

Consideration history:

- `docs/journal/2026/07/2026-07-25-component-documentation-and-ai-knowledge-package-consideration.md`

## Purpose

Implement a one-stop, self-describing Component documentation and knowledge
surface for humans and AI.

A Component must physically carry the Component-specific information needed
to understand, use, configure, invoke, operate, diagnose, and inspect it. The
normal case embeds that information in the execution Component. Large material
may move to a versioned Component-specific Documentation Component while
remaining logically part of the target Component's information space.

Shared CNCF, CML, Cozy, and SmartDox framework/toolchain documentation follows
a separate distribution rule. SimpleModeling.org is its basic public
information surface. When physical distribution is required, the same
publication generation is packaged as a framework Documentation Component.

## Implementation Principles

1. Component-specific documentation is part of the Component contract.
2. The execution Component or its declared Component-specific Documentation
   Component physically carries every required Component-specific resource.
3. One Component knowledge manifest is the deterministic machine-readable
   directory.
4. Human Help and AI retrieval project the same resolved Component knowledge.
5. SmartDox is the canonical hand-written document model.
6. User Guide and Reference Manual are the standard hand-written manual axes.
7. Component Scaladoc is included in the Component distribution.
8. Source inclusion is explicit and policy-controlled.
9. Textus CBD Support is the primary mediated AI integration for exact
   Component discovery, detail, usage, and review.
10. Textus BoK provides the complementary terminology and semantic RAG/MCP
    route over admitted Component knowledge.
11. The exact installed Component manifest remains authoritative over a stale
    BoK snapshot.
12. SimpleModeling.org is the basic public information surface for shared
    CNCF/CML/Cozy/SmartDox documentation.
13. Framework Documentation Components are optional versioned snapshots of the
    same publication generation, not Component-specific documentation owners.
14. Framework MCP/RAG prefers structured SmartDox/publication projections over
    HTML scraping and preserves immutable product/version evidence.
15. Public AI Directive guidance is a projection and never overrides the
    mounted authoritative directive.
16. Public Skill metadata supports discovery only; installable Skills remain
    CAR-owned `SkillBundleManifest` resources and require explicit
    installation/activation.

## Proposed Physical Shape

The following layout is provisional:

```text
component.car
├── component/
├── runtime/
├── model/
├── source/
├── documentation/
│   ├── manual/
│   │   ├── user-guide/
│   │   └── reference/
│   ├── scaladoc/
│   ├── help/
│   ├── api/
│   ├── context/
│   ├── assets/
│   └── index/
├── knowledge/
│   └── manifest.json
└── provenance/
```

Phase 56 must confirm paths against existing CAR source/archive rules before
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
the Component-specific Documentation Component relationship.

## Component Knowledge Manifest

Introduce one versioned manifest schema, provisionally:

```text
cncf.component-knowledge.v1
```

The manifest should contain:

- schema version;
- Component identity, kind, and version;
- knowledge revision and manifest digest;
- contextual framework publication subject, version, generation, and
  canonical base URL when referenced;
- stable contextual framework document and section identities when referenced;
- resource identity, kind, role, media type, language, path, digest, and size;
- authority and stability classification;
- source availability and disclosure policy;
- generated-with and compatible-with provenance;
- Documentation Component references;
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
- Scaladoc;
- Scaladoc symbol/search index;
- document search index; and
- provenance metadata.

The Component distribution must remain useful without a compiler, Scaladoc
generator, SmartDox renderer, or PDF toolchain installed at runtime.

Phase 56 must decide which HTML and PDF projections are mandatory. Generated
output must record its source resource and source digest so stale projections
can be detected.

## Scaladoc

A Component with a public Scala API packages generated Scaladoc in the
execution Component or its Documentation Component.

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
- `documentation-component`; and
- `omitted`.

The manifest records license, disclosure policy, source kinds, and path or
Documentation Component reference. Source packaging must exclude credentials,
local configuration, build caches, generated secrets, unrelated test data, and
host-specific files.

Source improves investigation but remains implementation evidence. It does not
override public contracts. Commercial Components may declare source omitted
while still supplying complete manuals, public contracts, examples, and
Scaladoc.

## Documentation Component

Define a Component-specific Documentation Component as a normal versioned
physical Component with a declared documentation-for relationship to a target
Component.

The contract must fix:

- Documentation Component identity;
- target Component identity and compatible version range;
- required versus optional relationship semantics;
- resolution from development directories, expanded CARs, and repositories;
- digest and signature validation;
- precedence between embedded and external Component documentation resources;
- duplicate resource identity handling;
- missing/incompatible dependency diagnostics;
- lifecycle and unload behavior; and
- authorization and production visibility.

The execution Component retains a minimal embedded overview, getting-started
information, manifest, and dependency diagnostic information. Physical
splitting must not create a separate user-facing Component documentation
namespace.

The default large Component-package topology is one execution Component and
one Component-specific Documentation Component. Additional language/media
Components require explicit need.

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

Phase 56 must confirm the canonical route and its relationship with existing
`/help`, `/man`, OpenAPI, MCP, authorization, and production-mode policies.

Human Help should advertise the manifest with a machine-readable link relation.
AI consumers should switch to the manifest and structured resources after
discovery instead of scraping HTML.

The CNCF runtime needs a provider-neutral resolver model, provisionally:

```text
embedded Component resources
  + resolved Component-specific Documentation Component resources
  -> ResolvedComponentKnowledge
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

Phase 56 includes real integration with
`/Users/asami/src/dev2026/textus-cbd-support`.

CBD Support is the primary AI-facing Component use path after direct Help. Its
existing ownership already includes versions, runtime compatibility,
dependencies, Operations, artifacts, manuals, examples, reuse guidance, CAR
Review, and evidence-bearing read-only MCP operations.

Phase 56 extends that contract so CBD Support consumes exact Component
knowledge manifests and resources while preserving canonical
SimpleModeling.org publication references for broader documentation. It does
not rely only on catalog links or model-metadata sidecars.

The integration requires:

- catalog, local development directory, warehouse CAR, and managed-cache
  observations to retain the exact Component knowledge manifest location and
  digest when available;
- safe resolution of Component-local resources, Documentation Components, and
  immutable canonical publication references;
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

Phase 56 also includes real integration with
`/Users/asami/src/dev2026/textus-bok`.

The current Textus BoK contract treats CAR/SAR references as existence-only and
hands detailed usage questions to CBD Support. Phase 56 extends the BoK route
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
- safe resolution of declared Component-local and Component-specific
  Documentation Component resources;
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
must be updated in the Textus BoK repository as part of Phase 56. A CNCF-only
mock is not sufficient acceptance.

## Repository Responsibilities

| Repository | Responsibility |
| --- | --- |
| `cloud-native-component-framework` | Manifest model, Component-local/Documentation Component/online resolution, Help/AI direct surface, authorization, integrity, and executable framework contract |
| `cozy` / `sbt-cozy` | Authoring validation, publication knowledge, SmartDox/manual projection, Scaladoc generation, source filtering, CAR packaging, and source/archive equivalence |
| `smartdox` | SmartDox/Markdown parsing and HTML/PDF/document projection capabilities used by the build toolchain |
| `simplemodeling-org` | Canonical versioned HTML publication, stable document/section URLs, RDF/JSON-LD/catalog projection, and online human/AI access |
| `ai-directive` | Authoritative Directive ownership, public-rule identity/visibility, and public-guide projection inputs |
| `textus-cbd-support` | Primary exact Component discovery/detail/usage/review integration, manifest/resource resolution, evidence-bearing MCP, and documentation quality assessment |
| `textus-bok` | Complementary terminology/semantic Component knowledge admission, indexing, RAG/MCP retrieval, CBD handoff, and stale/disclosure enforcement |
| representative Component/sample | End-to-end online-only, local manual, Documentation Component, offline Hub, source-policy, Help discovery, CBD usage/review, and BoK retrieval evidence |

If a missing capability belongs to one of these repositories, Phase 56 changes
the owning repository rather than duplicating the capability in CNCF.

## Implementation Sequence

1. Inventory current Help/Manual/CAR/SimpleModeling.org/ai-directive/Skill
   bundle/CBD Support/Textus BoK contracts and freeze failing-first acceptance
   identities.
2. Define publication, product, Component, document, section, resource,
   canonical URL, authority/disclosure, and canonical path identities.
3. Implement manifest codec, validation, and embedded-resource resolution in
   CNCF.
4. Implement Cozy/SmartDox authoring, lint, SimpleModeling.org HTML and
   structured publication projections, public AI guidance, Skill Catalog,
   Scaladoc, source, and packaging support.
5. Implement optional Documentation Component and canonical-online resolution
   with composed
   `ResolvedComponentKnowledge`.
6. Implement unified Help and direct manifest/resource access.
7. Implement Textus CBD Support manifest/resource admission, exact detail,
   usage, MCP, and CAR Review integration.
8. Implement Textus BoK structured-publication admission, indexing, RAG/MCP
   retrieval, evidence, CBD handoff, and stale/disclosure behavior.
9. Prove representative online-only, installed-snapshot, offline-Hub,
   Component-local, and source-omitted profiles through direct Help, CBD
   Support, and Textus BoK.
10. Complete security, compatibility, regression, and downstream validation.
11. Promote verified behavior to canonical design/specification and mark this
    note historical.

## Documentation Lifecycle and Closure

During implementation:

- this note holds the current working implementation proposal;
- the journal holds chronological reasoning and decision history;
- phase/checklist documents hold plan, status, and acceptance evidence; and
- executable specifications determine verified behavior.

At Phase 56 closure:

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

Phase 56 cannot close while the latest contract exists only in this note,
journal, phase document, or implementation.
