# Component Documentation and AI Knowledge Package Consideration

Date: 2026-07-25

Status: recorded

## Context

The current CNCF component documentation surface distinguishes generated Help
from component-packaged manuals. CAR projects package
`src/main/car/manual/` as the `manual/` subtree, and CNCF exposes those
resources below `/man/{component}`.

This discussion reconsidered that arrangement from the stronger Component
principle that a physical Component should carry the information required to
understand and use it. A user, developer, operator, or AI should not have to
reconstruct the Component contract by locating unrelated Maven classifiers,
source repositories, framework versions, and documentation sites.

The target is a one-stop, self-describing Component knowledge package.

## Primary Principle

A physical Component contains the information required to:

- understand its purpose and responsibility;
- install, configure, and execute it;
- invoke its Services and Operations;
- understand its data, Event, Job, Rule, and API contracts;
- diagnose failures;
- inspect its public Scala API;
- investigate usage through source when disclosure permits; and
- identify the exact CNCF, CML, Cozy, and SmartDox context used to build and
  operate it.

One-stop does not require every resource to be stored in one archive. It means
that all required information is held by physical Components and is reachable
through the target Component's declared Component graph.

External documentation sites and Maven documentation classifiers may be
additional publication surfaces, but they are not the primary Component
documentation contract.

## Component Knowledge Package

The working physical model is:

```text
component.car
├── component/
├── runtime/
├── model/
├── source/
├── documentation/
│   ├── manual/
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

This is a conceptual layout, not yet a normative CAR path specification.

`knowledge/manifest.json` is the machine-readable directory of the Component's
knowledge resources. It identifies embedded resources and any Documentation
Components that extend the package. Resources carry stable identities, media
types, authority classification, version/provenance information, and content
hashes.

The manifest should distinguish at least:

- public executable contracts;
- generated descriptions;
- authoritative hand-written manuals;
- examples;
- implementation source; and
- contextual framework or toolchain information.

Public contracts take precedence over examples and implementation details when
an AI determines supported behavior.

## Hand-Written Manual Baseline

Hand-written documentation has two standard axes:

### User Guide

The User Guide is task-oriented. It explains how to install, configure, invoke,
integrate, and operate the Component through representative usage scenarios.

### Reference Manual

The Reference Manual is systematic. It explains the Component's responsibility,
Services, Operations, configuration, data model, Events, Jobs, Rules,
constraints, errors, extension points, compatibility, and security behavior.

Both may be a single SmartDox document for a small Component or a document tree
for a larger one.

Additional manuals may be introduced when there is a distinct audience or
lifecycle, for example:

- Tutorial;
- Installation Guide;
- Operations or Administration Guide;
- Security Guide;
- Migration Guide;
- Integration Guide;
- Troubleshooting Guide;
- Architecture Guide; or
- Development Guide.

New topics should normally begin as sections of the User Guide or Reference
Manual. They become separate manuals only when that improves ownership,
navigation, lifecycle, or distribution.

## Authoring and Rendering

SmartDox is the canonical hand-written documentation model. `.dox` is the
preferred authoring form. `.md` and `.markdown` are allowed when SmartDox can
parse and normalize them without losing the required document semantics.

HTML, PDF, generated Help, OpenAPI, schemas, and Scaladoc are projections or
generated resources rather than the editable manual source of truth.

The discussion favors physically packaging the useful generated projections so
that the Component remains usable without an external build environment.
The exact required HTML/PDF projections and their build paths remain to be
specified.

## Scaladoc Decision

Scaladoc is part of the Component distribution.

- A Component with a public Scala API generates Scaladoc during build or
  publication.
- Generated Scaladoc is physically embedded in the execution Component or its
  declared Documentation Component.
- A Maven `-javadoc.jar` classifier is not the primary distribution mechanism.
- Scaladoc remains available even when commercial source-disclosure policy
  omits implementation source.
- Public and internal API exposure must follow an explicit publication policy.
- A release that requires Scaladoc but cannot generate or package it is not
  documentation-complete.

For AI consumption, a structured symbol/search index alongside the rendered
Scaladoc is desirable. It should expose symbols, signatures, ownership,
inheritance, descriptions, and links without requiring HTML screen scraping.

## Source Inclusion Policy

Implementation source is a valuable Component knowledge resource. When source
is available, an AI can investigate initialization, configuration use,
extension points, failure conditions, examples, and the relationship between
CML and generated implementation with greater accuracy.

Source inclusion is policy-controlled:

- `embedded`: source is included in the execution Component;
- `documentation-component`: source is carried by a declared Documentation
  Component; or
- `omitted`: source is intentionally not distributed, for example for a
  commercial product.

The manifest must state source availability, license, included source kinds,
and disclosure policy. An AI must not claim to have inspected source when the
manifest declares it omitted.

Source is evidence about implementation, but it does not override public
contracts. Proprietary source must not be admitted into Textus BoK indexing or
RAG without the corresponding authorization.

## Documentation Component

Embedding is the default. When documentation, Scaladoc, source, media, PDF, or
search assets make the execution Component too large, those resources may move
to a Documentation Component.

```text
customer-component.car
  └── documentation dependency
        └── customer-component-docs.car
```

A Documentation Component is a versioned physical Component associated with a
specific target Component and compatible version. It may provide:

- manuals;
- generated HTML or PDF;
- Scaladoc;
- source;
- examples;
- media;
- search indexes; and
- contextual framework/toolchain material.

The execution Component retains enough embedded information to identify itself,
start using it, diagnose a missing documentation dependency, and resolve the
Documentation Component.

Physical separation must remain transparent to consumers. CNCF composes the
embedded resources and Documentation Component resources into one resolved
Component knowledge space. Fine-grained splitting should be avoided; the
normal large-package shape is one execution Component plus one Documentation
Component.

## Human Access Path

Help is the unified human-facing entry point. It should navigate to the User
Guide, Reference Manual, configuration, Operations, schemas/OpenAPI, Scaladoc,
source when available, troubleshooting, provenance, and downloadable
projections.

The presentation should not expose physical Documentation Component boundaries
unless they are relevant to diagnostics. Users should experience one
Component-oriented information space.

## AI Access Paths

AI access has three coordinated routes: direct Component Help, primary Textus
CBD Support mediation, and complementary Textus BoK semantic retrieval.

### Direct Help and Manifest Route

When the target Component is known, AI starts at Help, discovers the JSON
manifest, and follows the manifest to exact Component resources:

```text
/help/{component}
  -> /help/{component}/manifest.json
  -> embedded and Documentation Component knowledge resources
```

Help therefore serves humans while advertising a stable machine-readable
entry point. After discovery, AI should consume the JSON manifest and
structured resources rather than continue scraping rendered HTML.

This route provides the authoritative information corresponding to the actual
Component version. It is the preferred route for exact configuration,
Operation, schema, and invocation decisions.

### Textus CBD Support Primary Route

Textus CBD Support is the primary mediated route for Component development and
use. It resolves exact Component identity and version, then uses the Component
knowledge manifest and resources for:

- detailed Component lookup;
- versions, dependencies, and runtime compatibility;
- configuration, Operations, schemas, manuals, examples, and Scaladoc;
- source-aware usage investigation when disclosure permits;
- evidence-bearing usage guidance through MCP; and
- CAR Review documentation and knowledge-quality assessment.

CBD Support preserves catalog/source identity and explicit absence. It does not
scrape HTML, silently choose a latest version, or fabricate missing Component
facts. It remains usable when Textus BoK is absent.

### Textus BoK Complementary RAG/MCP Route

Textus BoK provides ecosystem-wide discovery and reasoning through RAG and
MCP. It supports:

- finding a Component from a requirement;
- comparing Components or versions;
- cross-Component questions;
- combined CNCF, CML, Cozy, and Component knowledge;
- semantic search over manuals, contracts, examples, and admitted source; and
- investigation when the target Component is not running locally.

BoK holds a searchable semantic index or snapshot, not the authoritative live
Component package or the primary detailed Component-use service. Each indexed
resource or chunk must retain Component identity, Component version, knowledge
resource identity, source location, content hash, and index time.

The normal discovery-to-execution flow is:

```text
Textus BoK RAG/MCP
  -> identify the Component and version
  -> hand exact identity and evidence to Textus CBD Support
  -> resolve detailed usage and the Component Help manifest
  -> retrieve exact authoritative resources
  -> construct the usage or invocation
```

When a Component is already known, AI may use CBD Support or the direct
Help/manifest route without BoK.

## Version and Integrity

The direct Component manifest and Textus BoK index use the same Component and
resource identities. Manifest hashes allow an AI or runtime to detect that a
BoK snapshot differs from the installed Component.

When contextual CNCF, CML, Cozy, or SmartDox information is physically
available beside a Component, it is an immutable, version-identified
projection of its owning project's documentation. The follow-up decision below
places that projection in a framework Documentation Component rather than the
target execution Component. It is not edited as a second source of truth.

## Initial Decisions Recorded

The discussion established these directions:

1. A Component is a self-describing execution and knowledge package.
2. Component information is physically carried by the execution Component or
   declared Documentation Components.
3. Hand-written manuals use User Guide and Reference Manual as their two
   standard axes.
4. SmartDox is the canonical hand-written documentation model; supported
   Markdown is an admitted authoring form.
5. Scaladoc is included in the Component distribution and is not delegated to
   a Maven documentation classifier.
6. Source may be included to improve human and AI investigation, moved to a
   Documentation Component when large, or explicitly omitted by commercial
   policy.
7. Help is the unified human entry point and leads AI to a JSON knowledge
   manifest.
8. Textus CBD Support is the primary mediated AI route for exact Component
   detail, usage, MCP guidance, and CAR Review.
9. Textus BoK provides the complementary terminology/semantic RAG/MCP route
   with exact CBD Support handoff.
10. Physical documentation splitting does not change the logical one-stop
   Component information space.

These Component-specific decisions remain in force. The later clarification
below changes only the treatment of shared framework and toolchain information:
the execution Component is not the normal physical carrier of complete
CNCF/CML/Cozy/SmartDox documentation.

## Follow-up: Framework Documentation Distribution

The follow-up discussion separated public information provision from optional
physical distribution.

This clarification concerns framework and toolchain information such as CNCF,
CML, Cozy, and SmartDox. It does not replace the existing rule for
Component-specific manuals, contracts, Scaladoc, source policy, or knowledge
manifests.

SimpleModeling.org is the basic public information surface for CNCF, CML,
Cozy, and SmartDox documentation. The same SmartDox-owned publication
generation should project:

- versioned human-facing HTML;
- stable canonical document and section URLs;
- RDF/JSON-LD, ontology, schema, glossary, and catalog metadata;
- deterministic document/section identities and content hashes; and
- inputs suitable for lexical, structural, and optional embedding retrieval.

The Web publication is not merely a rendered copy. It is the canonical
published knowledge surface for both human navigation and machine evidence.
HTML remains the human projection; structured publication metadata is the
preferred MCP/RAG input. RAG must not depend on screen-scraping rendered HTML
when the structured source and projections are available.

### Help Boundary

CNCF Help remains runtime-owned because it describes the actual running
assembly:

- loaded Components and instances;
- Services and Operations;
- configuration and effective parameters;
- OpenAPI and schemas;
- MCP publication state; and
- the running CNCF version.

Help does not become a second framework documentation repository. Component
Help and Component manuals continue to resolve as before. For framework and
toolchain context, Help advertises:

1. the running or build-time product and version;
2. an installed framework Documentation Component snapshot, when present; and
3. the versioned canonical SimpleModeling.org location.

The UI and structured Help manifest should distinguish local and online
availability instead of silently redirecting or presenting `latest` as exact
evidence. `/help/system` describes the running runtime, while `/man/system`
resolves the matching CNCF documentation snapshot or canonical online
publication. CML and Cozy material belongs under developer/toolchain
navigation rather than ordinary operator Help.

### Documentation Component Boundary

Physical distribution of CNCF, CML, Cozy, SmartDox, or other framework and
toolchain documentation uses a Documentation Component. It is an optional,
versioned snapshot of the same publication generation used by
SimpleModeling.org, not an independent documentation source and not an
ordinary execution dependency. This does not change the existing
Component-specific Documentation Component relationship.

A Documentation Component may carry:

- SmartDox or admitted Markdown source;
- pre-rendered HTML;
- optional PDF;
- Scaladoc and a structured symbol index;
- document/section/search indexes;
- RDF/JSON-LD and catalog projections; and
- canonical URL, publication generation, provenance, and content hashes.

The normal online case uses SimpleModeling.org without installing these
Components. Documentation Components serve version-pinned, cached, local, or
closed-network operation. An offline Documentation Hub SAR may compose CNCF,
CML, Cozy, Textus BoK, and an approved retrieval provider without changing the
public source of truth.

### MCP and RAG Boundary

MCP exposes bounded framework-knowledge Operations rather than turning every
page or framework Documentation Component Operation into an AI tool. Textus
BoK owns the curated terminology and cross-document discovery surface, while
Textus CBD Support remains the separate detailed Component-use service.

The preferred knowledge flow is:

```text
SmartDox publication source
  -> Cozy publication knowledge
  -> SimpleModeling.org HTML + RDF/JSON-LD + catalog
  -> Textus BoK admission and RAG projections
  -> bounded evidence-bearing MCP reads
  -> exact CBD Support or Help handoff
```

An installed Documentation Component is an alternate admitted source for the
same versioned publication generation. MCP/RAG results must retain product or
Component identity, version, document and section identity, canonical URL,
source generation, content hash, and retrieval evidence. `latest` aliases may
support human navigation but must not be returned as the evidence identity.

Mutation, source replacement, indexing administration, and runtime execution
remain absent from the public documentation MCP catalog. Public Web or Help
visibility does not imply MCP readiness.

### Revised Decisions

The follow-up fixed these directions for Phase 52 planning:

1. SimpleModeling.org is the basic public information surface.
2. SmartDox source and Cozy publication knowledge generate the human and
   machine-facing projections together.
3. CNCF Help describes the exact running system and resolves local or online
   documentation without becoming a second authoring source.
4. CNCF/CML/Cozy documentation is distributed only when needed, as versioned
   Documentation Components.
5. A Documentation Component is a verifiable snapshot of the canonical
   publication generation.
6. Textus BoK MCP/RAG consumes structured publication knowledge or the
   equivalent Documentation Component snapshot, not HTML scraping.
7. Component-specific information and CBD Support ownership are unchanged;
   framework knowledge remains separately attributable.
8. Online-only, installed-snapshot, and closed-network Documentation Hub
   profiles must resolve the same document and section identities.

## Follow-up: Public AI Directive Guidance and Skill Information

The shared framework knowledge scope also includes a general-public projection
of `ai-directive` and public Skill information.

The authoritative `ai-directive` remains a versioned contract mounted into a
project and obeyed by AI runtimes. It is not replaced by its public
documentation. The public projection explains the purpose, rationale,
applicability, and examples of selected public rules while preserving the
originating directive version, rule identity, source digest, authority, and
visibility.

The publication model distinguishes:

- authoritative AI Directive contracts;
- general-public AI development guidance derived from those contracts;
- non-authoritative examples and templates;
- public Skill metadata and usage guidance; and
- installable Skill bundles.

Directive and Skill resources require explicit visibility such as `public`,
`ecosystem`, `project`, `internal`, or `restricted`. Project-local paths,
credentials, approval configuration, private provider data, and restricted
operational rules are not admitted into the public projection.

SimpleModeling.org provides the general-public AI Development Guide and Skill
Catalog. A framework Documentation Component may carry the same versioned
guide and catalog for installed or closed-network use.

The actual execution contracts remain separate:

- the mounted `ai-directive` version/digest remains authoritative for project
  AI behavior;
- a Skill Catalog entry does not install, activate, or execute a Skill;
- an installable Skill remains owned by its CAR and distributed through the
  existing `SkillBundleManifest` contract;
- a Skill manifest may describe MCP requirements but does not grant MCP
  authority or mutate Codex configuration; and
- installation remains an explicit Launcher or Skill-installer action.

Help may expose the active directive version/profile/digest and public guide
reference, and may list Component-associated Skill metadata and installation
state. Help must not expose restricted rule bodies, raw prompts, credentials,
or approval configuration.

Textus BoK may provide bounded read-only discovery such as AI guidance search
and Skill metadata search. Standard RAG admission includes only public
projections. Results preserve directive or Skill identity, version, authority,
visibility, owner, canonical URL, and source digest. Knowledge retrieval does
not install a Skill, activate a dependency, or override the locally mounted
directive.

Phase 52 owns these information-provision and knowledge-publication surfaces.
The Skill installation and activation mechanism remains with the separate
`SkillBundleManifest`, Cozy projection, and Launcher implementation boundary.

## Open Specification Work

The following items remain to be formalized:

- canonical CAR paths and the `component-knowledge` manifest schema;
- the stable Help manifest route and media types;
- required versus optional HTML and PDF projections;
- Documentation Component identity, compatibility, resolution, and failure
  behavior;
- source inclusion filters, licensing, and access-control rules;
- public Directive projection, rule identity, authority, visibility, and
  redaction policy;
- public Skill metadata/catalog schema and the boundary to installable
  `SkillBundleManifest` content;
- Scaladoc public-surface policy and structured symbol index;
- localization and large-media packaging;
- integrity, signature, and trust metadata;
- production-mode exposure and authorization of Help/knowledge resources;
- Textus CBD Support manifest/resource admission, exact selection, usage/MCP,
  and CAR Review integration;
- Textus BoK admission, refresh, stale-snapshot detection, and source-disclosure
  enforcement plus exact CBD handoff; and
- lint and release-readiness requirements for all mandatory resources.

## Phase 52 Selection

The discussion was initially selected as Phase 51 and was renumbered on
2026-07-26 as Phase 52, `Component Documentation and AI Knowledge
Integration`.

Phase 52 starts after Phase 51 closes and includes:

- the CNCF Component knowledge manifest and resolved knowledge surface;
- SimpleModeling.org-first versioned publication and stable document/section
  identities for CNCF/CML/Cozy/SmartDox framework knowledge;
- a general-public AI Development Guide projected from explicitly public
  `ai-directive` rules;
- a public Skill Catalog projected from Skill bundle metadata without
  installing or activating Skills;
- User Guide and Reference Manual authoring/package rules;
- Scaladoc and policy-controlled source packaging;
- optional framework/toolchain Documentation Component snapshots without
  changing Component-specific documentation ownership;
- unified human Help with explicit local/online documentation resolution;
- Cozy/SmartDox build and lint integration;
- Textus CBD Support exact Component detail, usage, MCP, and CAR Review as the
  primary mediated AI integration;
- Textus BoK admission from structured publication knowledge, semantic RAG,
  read-only MCP retrieval, and exact CBD handoff as the complementary route;
  and
- representative online-only, installed framework Documentation Component,
  and offline Documentation Hub acceptance in addition to the existing
  Component-specific profiles.

The follow-up discussion clarified that CBD Support is the primary integration,
not Textus BoK. CBD Support already owns versions, dependencies, Operations,
artifacts, manuals, examples, detailed usage, reuse guidance, and CAR Review.
Phase 52 makes the Component knowledge manifest and packaged resources primary
evidence for those CBD surfaces.

Textus BoK remains the complementary terminology and semantic RAG/MCP route. It
may index admitted Component knowledge and return attributable resource
evidence, but it hands exact Component identity/version/digest to CBD Support
for detailed usage and review. BoK must not invent unsupported capability or
compatibility claims from unstructured similarity. CBD Support remains usable
when BoK is absent.

The documentation lifecycle for Phase 52 is:

```text
journal
  -> chronological reasoning and selection history

notes
  -> working implementation proposal during the phase

implementation + Executable Specifications
  -> verified behavior

design + spec
  -> canonical closed-phase architecture and normative contract
```

At Phase 52 closure,
`docs/notes/component-documentation-knowledge-package-implementation.md` must
be marked historical and non-normative. It must state that the final design and
specification override it. The journal remains history. Phase 52 may not close
with its latest specification present only in notes, journal, phase documents,
source code, or tests.

Planning documents:

- `docs/notes/component-documentation-knowledge-package-implementation.md`
- `docs/phase/phase-52.md`
- `docs/phase/phase-52-checklist.md`

## Current References

- `docs/design/static-form-ui-generation-contract.md`
- `docs/design/packaged-source-activation.md`
- `/Users/asami/src/dev2025/cozy/docs/design/car-documentation-lint.md`
- `/Users/asami/src/dev2025/cozy/docs/design/car-project-metadata-ownership.md`
- `/Users/asami/src/dev2025/cozy/docs/strategy/cozy-development-strategy.md`
- `/Users/asami/src/dev2025/smartdox/README.md`
- `/Users/asami/src/dev2025/smartdox/src/main/scala/org/smartdox/parser/Dox2Parser.scala`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-rdf-structure-and-usage.md`
- `/Users/asami/src/dev2025/simplemodeling-org/docs/journal/2026/05/site-structure-expansion-for-ai-era-engineering-knowledge-platform.md`
- `/Users/asami/src/dev2026/textus-bok/src/main/car/manual/user-guide.md`
- `ai/directive/README.md`
- `ai/directive/samples/README.md`
- `docs/journal/2026/07/2026-07-21-codex-skill-bundle-contract.md`
