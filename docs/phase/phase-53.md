# Phase 53 - Component Documentation and AI Knowledge Integration

status=planned
planned_at=2026-07-25
depends_on=[Phase 52](phase-52.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 53 Checklist](phase-53-checklist.md)

## Purpose

Make every CNCF Component a one-stop, self-describing execution and knowledge
package for humans and AI.

Phase 53 organizes hand-written manuals, generated Help, Scaladoc, source,
schemas, examples, provenance, and contextual documentation under one
machine-readable Component knowledge manifest. It supports physical splitting
into a Documentation Component without exposing that split to users or AI.

Component-specific information continues to follow that Component-owned
contract. Separately, shared CNCF/CML/Cozy/SmartDox framework and toolchain
documentation is published primarily through SimpleModeling.org and is
physically distributed only when required as a versioned Documentation
Component generated from the same publication knowledge.

The phase primarily integrates Textus CBD Support for exact Component
discovery, detail, usage, MCP assistance, and CAR Review. It also implements
the complementary Textus BoK RAG/MCP route for terminology and semantic
discovery. The exact Component Help manifest remains authoritative.

## Dependency

Phase 53 begins after Phase 52 closes.

Phase 53 does not reopen Phase 50 Entity revision/OCC behavior. It may document
those contracts through the new Component knowledge mechanism after their
canonical design/specification is stable.

## Selected Direction

- Physical Components carry the Component-specific information required to
  understand and use them.
- Embedding Component-specific information in the execution Component is the
  default.
- Large Component-specific documentation, Scaladoc, source, media, and
  generated artifacts may be moved to one versioned Documentation Component.
- Physical splitting remains one logical Component information space.
- User Guide and Reference Manual are the two standard hand-written manual
  axes.
- SmartDox is the canonical hand-written document model; admitted Markdown is
  parsed through SmartDox.
- Scaladoc is included in the Component distribution.
- Source is embedded, moved to a Documentation Component, or explicitly
  omitted according to disclosure policy.
- Help is the unified human entry point and advertises a JSON Component
  knowledge manifest for direct AI retrieval.
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

## Scope

- Define a versioned Component knowledge manifest and resource model.
- Define canonical CAR source/archive paths without duplicating Component
  identity metadata.
- Define resource authority, stability, language, media type, digest,
  provenance, license, and source-disclosure metadata.
- Define mandatory User Guide and Reference Manual entry points plus optional
  manual roles.
- Define SmartDox and admitted Markdown authoring/lint behavior.
- Decide and implement required HTML/PDF projection profiles.
- Generate and package Scaladoc plus a structured symbol/search index.
- Define source inclusion, exclusion, license, and commercial omission policy.
- Define Component-specific Documentation Component identity, compatibility,
  resolution, precedence, integrity, diagnostics, and lifecycle.
- Compose embedded and Component-specific Documentation Component resources
  into one resolved Component knowledge model.
- Integrate the model with Help, `/man`, OpenAPI/schema, CLI inspection, and a
  direct JSON manifest/resource route.
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
- Validate the existing representative embedded Component, Component-specific
  Documentation Component split, and source-omitted commercial-style profiles.
- Validate online-only framework documentation, installed framework
  Documentation Components, and a closed-network Documentation Hub SAR
  without changing Component-specific information ownership.
- Promote verified behavior to current CNCF, Textus CBD Support, and Textus BoK
  design/specification documents before closure.

## Boundaries

- Phase 53 does not make CAR embedding the primary public distribution of
  CNCF/CML/Cozy documentation.
- Phase 53 does not treat a framework Documentation Component as an
  independent source of truth; its publication generation and hashes must
  match the canonical source projection.
- Phase 53 does not make HTML scraping the preferred RAG ingestion path when
  structured SmartDox/publication projections are available.
- Phase 53 does not publish every `ai-directive` rule or raw `SKILL.md`;
  publication requires explicit visibility and disclosure admission.
- Public Directive guidance does not override the mounted authoritative
  directive.
- Skill metadata, Help, or MCP/RAG discovery does not install, activate,
  execute, configure, or grant authority to a Skill or its dependencies.
- Actual Skill packaging, installation, and activation remain under the
  `SkillBundleManifest`, Cozy, Launcher, and Codex boundaries.
- Phase 53 does not distribute Component documentation primarily through Maven
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
- Proprietary source and secrets must not enter public CARs, diagnostics,
  indexes, RAG context, or MCP responses.
- Runtime Help must not require a compiler, Scaladoc generator, SmartDox
  renderer, PDF toolchain, or embedding provider.
- Online documentation unavailability must not prevent execution or make Help
  report online material as locally present.
- Human-facing `latest` aliases must not become MCP/RAG evidence identities.
- Fine-grained Documentation Component fragmentation is not the default.
- Phase 53 does not redesign the generic CAR executable dependency mechanism
  beyond the documentation relationship required by this phase.
- Component-specific manual content remains owned by each Component.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| DOC-01 | Inventory and executable acceptance | Existing Help/Manual/CAR/CBD Support/BoK contracts, contradictions, routes, publication surfaces, and exact acceptance identities are fixed before implementation. | planned |
| DOC-02 | Knowledge manifest and resource model | Versioned manifest, paths, resource identity, authority, provenance, disclosure, integrity, and codecs are implemented and validated. | planned |
| DOC-03 | Authoring and packaging toolchain | User Guide/Reference, SmartDox/Markdown lint, HTML/PDF decisions, Scaladoc, source filtering, and CAR projection work through Cozy/SmartDox ownership. | planned |
| DOC-04 | Documentation Component composition | Component-specific documentation relationships and framework publication snapshots have explicit identity, resolution, integrity, diagnostics, and offline composition. | planned |
| DOC-05 | Unified Help and direct AI access | Component Help/manifest behavior remains exact while framework links distinguish installed and versioned online documentation. | planned |
| DOC-06 | Textus CBD Support primary integration | CBD admits exact manifests/resources into Component detail, usage, MCP, and CAR Review without losing source authority or selection evidence. | planned |
| DOC-07 | Textus BoK complementary integration | BoK performs Component semantic retrieval and admits structured framework, public Directive guidance, and Skill metadata with attributable RAG/MCP evidence and exact CBD handoff. | planned |
| DOC-08 | Representative Component and framework documentation acceptance | Existing Component-specific profiles plus online/installed/offline framework, public Directive guide, and Skill Catalog profiles prove consistent ownership and access. | planned |
| DOC-09 | Security, regression, and downstream validation | Path, integrity, authorization, disclosure, compatibility, regression, and full cross-repository suites pass. | planned |
| DOC-10 | Canonical documentation and closure | CNCF/CBD Support/Textus BoK design/spec/notes/strategy/phase records match verified behavior; working notes are marked overridden and historical. | planned |

## Acceptance

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
- Source availability is explicit; included source obeys filtering/license
  policy and omitted source is never claimed or indexed.
- A Component-specific Documentation Component resolves as one logical
  information space with its target Component.
- A framework Documentation Component carries an exact product/version
  publication snapshot and proves its canonical publication generation and
  resource digests.
- Missing, incompatible, duplicate, unsafe, corrupt, or unauthorized
  Documentation Components fail with deterministic diagnostics.
- Help provides unified Component-oriented human navigation and a stable
  machine-readable manifest discovery path.
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

## Verification

Phase 53 closure requires:

- failing-first Executable Specifications for every DOC-01 contract group;
- manifest codec/schema/property/path/integrity specifications;
- source-tree versus packaged-CAR equivalence specifications;
- Cozy lint and strict release-readiness specifications;
- SmartDox and admitted Markdown parsing/projection specifications;
- SimpleModeling.org HTML/RDF/JSON-LD/catalog and document/section identity
  projection specifications;
- public Directive projection/visibility/redaction and mounted-authority
  specifications;
- public Skill metadata/catalog and SkillBundle authority-separation
  specifications;
- Scaladoc packaging and public-surface specifications;
- Component-local and Component-specific Documentation Component resolution
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

## Final Documentation Gate

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

Phase 53 cannot close with behavior represented only in notes, journal, phase
documents, source code, or tests.

## Repository Responsibility

| Repository | Phase 53 responsibility |
| --- | --- |
| `/Users/asami/src/dev2025/cloud-native-component-framework` | Manifest/resolver model, Documentation Component composition, Help/direct AI access, authorization, integrity, and canonical CNCF contract |
| `/Users/asami/src/dev2025/cozy` and `sbt-cozy` | Authoring lint, Scaladoc/source/manual projection, CAR packaging, and source/archive equivalence |
| `/Users/asami/src/dev2025/smartdox` | SmartDox/Markdown and required HTML/PDF projection capabilities |
| `/Users/asami/src/dev2025/simplemodeling-org` | Canonical versioned Web publication, stable document/section URLs, RDF/JSON-LD/catalog projection, and online human/AI access |
| `ai/directive` (`ai-directive` repository) | Authoritative Directive ownership, stable public rule identities, visibility, and public-guide projection inputs |
| CAR Skill owners and Textus/CNCF Launchers | Public Skill metadata inputs plus unchanged explicit SkillBundle installation/activation ownership |
| `/Users/asami/src/dev2026/textus-cbd-support` | Primary exact Component detail/usage/MCP/CAR Review integration and canonical CBD contract |
| `/Users/asami/src/dev2026/textus-bok` | Complementary semantic manifest/resource admission, RAG/MCP retrieval, CBD handoff, and canonical BoK contract |
| selected sample/Component repositories | Embedded/split/source/commercial-style end-to-end acceptance |

## Planning References

- `docs/notes/component-documentation-knowledge-package-implementation.md`
- `docs/journal/2026/07/2026-07-25-component-documentation-and-ai-knowledge-package-consideration.md`
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

## Current Resume Point

Phase 53 is planned and must not start before Phase 52 closes.

After Phase 51 closes, begin DOC-01 with a cross-repository inventory. Preserve
the Component-specific documentation contract while fixing the separate
SimpleModeling.org framework publication identity, Help framework-link
resolution, framework Documentation Component snapshot, CBD-primary, and
Textus BoK complementary boundaries. Then record failing-first acceptance
identities before implementing manifests or changing Help routes.
