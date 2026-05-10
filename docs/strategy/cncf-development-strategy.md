status = draft
scope = internal development strategy

# CNCF Development Strategy

## 1. Purpose of This Document
- Provide a shared, top-level strategy for growing CNCF in stages.
- Prevent scope drift by making phase boundaries explicit.
- Serve as the meta-context for future notes and design documents.
- This document covers strategy only; execution, verification, and results live in notes.

## 2. Development Philosophy
- Bootstrap first.
- Model before execution.
- Projection over adapter.
- Subsystem as a reusable execution unit.
- One execution model, multiple frontends (HTTP / CLI / Client).

## 3. Phase Overview

### Phase 1: HelloWorld Bootstrap
- Goal: no-setting startup guarantee.
- Artifact (notes): `docs/notes/helloworld-bootstrap.md`.
- Excluded: CRUD, CML, persistence, authentication.

### Phase 1.5: Subsystem Execution Model Fix (Internal)
- Goal: fix execution responsibilities.
- Artifact (notes): `docs/notes/helloworld-step2-subsystem-execution.md`.
- Internal design note, not a demo or article.

### Phase 2: HelloWorld Demo Strategy
- Goal: user-visible demo experience.
- Artifact (notes): `docs/notes/helloworld-demo-strategy.md`.
- Covers: server, OpenAPI, client, command, component demo.
- This phase exists to support a concrete demo article.

### Phase 2.5: Error Semantics Consolidation

**Goal**
Finalize and freeze the error / failure semantics of CNCF
before entering CML and CRUD-oriented development.

**Scope**
- Consolidate core-level semantics:
  - Observation
  - Conclusion
  - Cause / CauseKind
  - Detail error code strategy
- Define CNCF-level projections:
  - CLI exit code mapping
  - HTTP status mapping
  - Client-visible error representation

**Non-goals**
- No CML modeling
- No CRUD generation
- No workflow or job orchestration

**Rationale**
Error semantics must be completed before domain expansion.
CML and CRUD layers will rely on this frozen contract.

**Artifacts**
- CNCF note: `docs/notes/legacy/2026/05/error-semantics.md`
- Core note: `core-error-semantics.md`
- Related design notes:
  - `docs/notes/scope-context-design.md`
  - `docs/notes/legacy/2026/05/conclusion-observation-design.md`
  - `docs/notes/observability-engine-build-emit-design.md`

### Phase 2.6: Demo Completion on Frozen Platform

**Goal**
Complete remaining Phase 2.0 demo stages on top of frozen platform contracts.

**Scope**
- No platform contract changes:
  - Execution model
  - Error semantics
  - Observability semantics
  - ScopeContext model
- Complete demo stages:
  - OpenAPI projection
  - Client demo
  - Custom component demo
  - Demo consolidation
- Stage 4 (Client demo) is DONE; client demo verified via real/fake HttpDriver with curl-equivalent output (`ok`).

**Exit Criteria**
- Historical note: `docs/notes/history/2026/04/phase-2.6-demo-done-checklist.md`

**Relationship**
Phase 2.0 may be incomplete; Phase 2.6 completes it without re-opening
platform contracts. Phase 3 starts only after Phase 2.6 exit criteria are met.

**References**
- `docs/notes/helloworld-demo-strategy.md`
- `docs/notes/helloworld-bootstrap.md`
- `docs/notes/phase-2.5-observability-overview.md`
- `docs/notes/interrupt-ticket.md`

### Phase 2.8: Deferred Development Resolution

**Purpose**
Resolve architectural technical debt discovered during Phase 2.6 without adding new features.

**Scope**
- Path alias resolution logic
- Canonical vs alias routing normalization
- Normalize `CncfMain` command invocation:
  - Introduce `OperationDefinition` for CLI command definitions.
  - Definition-driven parameter validation and execution dispatch.
  - Consolidate command-line arguments and configuration inputs.

**Non-goals**
- No semantic changes
- No new features

**Artifact (notes)**
- `docs/notes/phase-2.8-infrastructure-hygiene.md`

### Phase 2.9: Error Model Realignment

**Purpose**
Re-align error taxonomy and definitions before Phase 3 (CML).

**Scope**
- Use Phase 2.5 semantics as foundation
- Incorporate practical issues discovered in Phase 2.6
- Define CNCF-level exit code policy:
  - OS / shell exit codes are constrained to 8-bit (0–255) and exposed as `Int`.
  - `Conclusion.Status.detailCode` is a numeric `Long` semantic detail code.
  - Exit codes MUST NOT be derived directly from `detailCode`.
- Introduce a CNCF framework policy to compute exit codes from `Conclusion`:
  - Mapping extracts only operationally relevant information
    (e.g. success vs failure, retryable vs non-retryable, usage vs defect).
  - Mapping normalizes results to valid 8-bit exit codes.
- Centralize process termination:
  - `CncfMain` is the sole component allowed to invoke `sys.exit`.
  - All other layers propagate failures via `Consequence` / `Conclusion` only.

**Non-goals**
- No CML modeling
- No CRUD generation

**Artifact (notes)**
- `docs/notes/legacy/2026/05/phase-2.9-error-realignment.md`

**Relationship**
Phase 3.0 starts only after Phase 2.9 is complete.

### Phase 3: Model-Driven Execution and Orchestration Hub

**Goal**
Establish CNCF as a unified execution and orchestration hub capable of
hosting generated domain components and coordinating heterogeneous
execution forms, with exploratory support for AI agent integration.

Phase 3 is not demo-driven; it focuses on foundational runtime and
modeling capabilities that later phases will build upon.

**Sub-phases**

- **Phase 3.1: Execution Hub Foundation**
  - Introduce Fat JAR Components (JVM-internal, classloader-isolated execution).
  - Introduce Docker Components (external tool and environment execution).
  - Introduce Microservice / SOA Components (remote execution via network APIs).
  - Unify all execution forms under the Component / Service / Operation model.

- **Phase 3.2: CML → Entity Component Generation**
  - Generate executable Entity Components from CML models.
  - Package generated components as self-contained execution units.
  - Co-locate domain logic, documentation sources, and runtime metadata.

- **Phase 3.3: AI Agent Hub (PoC)**
  - Provide experimental projections of CNCF Operations to OpenAPI and MCP.
  - Enable AI agents to invoke CNCF-managed operations indirectly.
  - This sub-phase is exploratory and Proof-of-Concept only.

**Artifacts (notes)**
- `docs/work/phase-3.md`

**Phase Ordering**
Phase 3 begins only after Phase 2.9 completes; the documented flow remains Phase 1 → 1.5 → 2 → … → 2.9, then Phase 3 and beyond.  
AI agent work in Phase 3 remains exploratory/PoC in scope; it must not be treated as production readiness until future phases explicitly reclassify it.

### Phase 4: State Machine Foundation
- Goal: introduce a first-class state machine model usable by domain subsystems and components.
- Scope:
- Define state / transition / guard / effect representation.
- Enable introspection outputs (e.g., transition table / diagram source).
- Provide runtime hooks for validating transitions during execution.
- Non-goals:
- No workflow engine.
- No persistence/event sourcing requirements.
- Artifact (notes): `docs/notes/state-machine-foundation.md`.

### Phase 5: Event Foundation
- Goal: introduce a first-class event model for domain and runtime observability.
- Scope:
- Define event envelope (id, time, correlation, payload).
- Define event emission points from execution / domain actions.
- Define minimal event handling contract (publish/subscribe boundary).
- Non-goals:
- No event sourcing mandate.
- No distributed saga/orchestration engine.
- Artifact (notes): `docs/notes/event-foundation.md`.

### Phase 6: Job and CQRS Foundation
- Goal: establish the job execution baseline and separate command/query handling
  as the runtime foundation for later aggregate/view and Web work.
- Scope:
- Define job lifecycle, persistence/read model, and async execution contract.
- Introduce the first practical CQRS split between command processing and query
  visibility.
- Align runtime, projections, and observability with job-aware execution.
- Non-goals:
- No distributed workflow/saga engine.
- No multi-node scheduling/orchestration guarantees in this phase.
- Artifact (work): `docs/phase/phase-6.md`.

### Phase 7: Aggregate and View Completion
- Goal: finalize aggregate/view runtime model and complete CQRS separation after Job CQRS baseline.
- Scope:
- Finalize Aggregate model boundaries and execution contract.
- Finalize View model (read-side projection/query contract).
- Define aggregate-to-view synchronization boundary and consistency rules.
- Align runtime projection surfaces (`meta.*`, query/read API) with finalized model.
- Non-goals:
- No distributed workflow engine.
- No multi-node read-model replication in this phase.
- No security model redesign (handled by dedicated security phase).
- Artifact (work): `docs/phase/phase-7.md`.

### Phase 8: CML Operation Grammar Introduction
- Goal: introduce first-class `operation` grammar in CML and integrate parse->AST->generation->runtime path.
- Scope:
- Define operation syntax/semantics (`command`/`query`, parameter/response contract).
- Extend model/generator pipeline to propagate operation metadata.
- Align runtime and projection/meta visibility with generated operation metadata.
- Non-goals:
- No aggregate/view contract redesign.
- No workflow expansion.
- Artifact (work): `docs/phase/phase-8.md`.

### Phase 9: Component/Subsystem Grammar and CAR/SAR Packaging
- Goal: freeze Component/Subsystem grammar and establish CAR/SAR packaging/runtime intake.
- Scope:
- Define/freeze Component, Componentlet, ExtensionPoint, and Subsystem grammar.
- Define CAR/SAR packaging contract and precedence rules.
- Implement packaging flow in `sbt-cozy`.
- Align CNCF runtime/projection loading with packaged artifacts.
- Non-goals:
- No new identity/account domain implementation.
- No broad runtime redesign beyond grammar/packaging alignment.
- Artifact (work): `docs/phase/phase-9.md`.

### Phase 10: Textus Identity and User Account Practicalization
- Goal: develop practical domain boundaries on top of the completed runtime/model foundations.
- Scope:
- Implement `textus-user-account` component.
- Implement `textus-identity` subsystem.
- Track `cncf-samples` as the practical verification vehicle for Phase 10.
- Align runtime/projection/meta surfaces for practical command/query use.
- Add executable specifications for account/identity integration behavior.
- Non-goals:
- No broad security redesign.
- No federation/multi-tenant expansion in this phase.
- Artifact (work): `docs/phase/phase-10.md`.

### Phase 11: Component Wiring and Subsystem Construction
- Goal: formalize Component port/binding semantics and subsystem construction through bound Components.
- Scope:
- Stabilize the Component port/binding baseline.
- Define subsystem construction via bound Components.
- Add executable specifications for Component wiring and subsystem assembly.
- Fix sample-facing wiring guidance.
- Non-goals:
- No replacement of `OperationCall` / `Engine` as the canonical execution boundary.
- No generalized plugin marketplace/runtime in this phase.
- No external service-bus integration in this phase.
- Artifact (work): `docs/phase/phase-11.md`.

### Phase 12: Web Layer
- Goal: establish CNCF Web as an operation-centric integration surface on top
  of the existing runtime, with Static Form App as the first application shape.
- Scope:
- Define REST/Form API exposure and the Static Form App mechanism.
- Define Web Descriptor responsibilities for exposure, security, and asset
  composition.
- Build Dashboard, Management Console, and Manual as shared Web applications on
  the same mechanism.
- Validate the result with `textus-sample-app`, Bootstrap 5 baseline polish,
  Textus widgets, packaging, and executable smoke coverage.
- Non-goals:
- No full SPA framework commitment in this phase.
- No broad UI generation beyond the convention-first Static Form baseline.
- Artifact (work): `docs/phase/phase-12.md`.

### Phase 13: Event Mechanism Extension
- Goal: establish subsystem-internal event collaboration as a standard CNCF
  runtime capability on top of the existing event/job foundation.
- Scope:
- Make subsystem-level shared event wiring explicit.
- Make component subscription bootstrap part of normal startup.
- Stabilize event-to-action dispatch semantics.
- Introduce boundary-aware reception policy selection for:
  - same-subsystem reception
  - external-subsystem reception
  - event name / kind / selectors based policy matching
- Support the Phase 13 baseline execution policies:
  - same-subsystem default sync reception
  - async new-job same-saga reception
  - async new-job new-saga reception
- Add internal await support, observability coverage, and executable
  specifications for the collaboration path.
- Non-goals:
- No external service-bus transport finalization in this phase.
- No ABAC-aware reception policy selection finalization in this phase.
- No async same-job same-transaction guarantee in this phase.
- Artifact (work): `docs/phase/phase-13.md`.

### Phase 14: Execution Layer Expansion
- Goal: complete the lightweight workflow execution baseline on top of the
  event/job foundation.
- Scope:
- Establish lightweight `WorkflowEngine` execution.
- Add workflow inspection and projection surfaces.
- Introduce submission-only `JCL`.
- Integrate JCL-to-workflow entrypoints.
- Harden retry/dead-letter behavior and explicit saga-id propagation.
- Non-goals:
- No full workflow product surface.
- No external orchestration engine.
- Artifact (work): `docs/phase/phase-14.md`.

### Phase 15: Job Scheduling and Timer Boundary
- Goal: finalize shared job scheduling and timer execution as a CNCF runtime
  boundary.
- Scope:
- Shared bounded `JobEngine` scheduler for async execution.
- Explicit queue priority and normalized workflow priority semantics.
- Canonical built-in timer/scheduling boundary.
- Bounded one-shot delayed root job start.
- `Consequence[JobId]` job submission contract for ordinary submit-time
  failures.
- Non-goals:
- No distributed scheduler finalization.
- No cron-like product UI.
- Artifact (work): `docs/phase/phase-15.md`.

### Phase 16: Authentication Baseline With Cwitter
- Goal: close the authentication/user-account baseline using Cwitter as the
  concrete authenticated application driver.
- Scope:
- Complete the CNCF auth/session contract on the existing
  `AuthenticationProvider` boundary.
- Make browser Web session login/logout/current-session a real runtime path.
- Adapt `textus-user-account` as the first auth provider.
- Keep provider-owned account pages for signup/signin/password-reset/optional
  2FA and keep Cwitter app code focused on timeline/post/mention/DM.
- Add CNCF message-delivery provider SPI with stub-backed password reset and
  optional 2FA.
- Harden runtime surfaces discovered by Cwitter manual use:
  structured errors, admin usability, production admin authorization,
  working-set policy, debug trace-job metadata, shared Web theme,
  provider-page light customization, locale-aware display formatting, and
  lifecycle audit defaults.
- Support both Cwitter component CAR deemed-subsystem startup and SAR deployment,
  with provider component CARs resolved from the repository or local
  `repository.d`.
- Non-goals:
- No OAuth/OIDC federation.
- No SSO.
- No mandatory/global MFA policy.
- No real SMTP/SMS provider.
- No separate Cwitter profile model.
- Artifact (work): `docs/phase/phase-16.md`.

### Phase 17: SimpleEntity Storage Shape
- Goal: make the entity storage boundary explicit and define deterministic
  SimpleEntity DB storage-shape rules.
- Status: closed.
- Scope:
- Treat `EntityPersistent` as the canonical entity storage boundary.
- Separate storage records from request, presentation, descriptor, diagnostic,
  and admin records.
- Introduce/standardize explicit storage-oriented APIs such as `toStoreRecord`
  and `fromStoreRecord` after the Record purpose taxonomy is fixed.
- Define SimpleEntity storage rules for management fields, permission,
  independent value objects, and repeated value objects.
- Move SimpleEntity authorization away from ad hoc `Record` path semantics
  toward typed security/permission access.
- Non-goals:
- No broad persistence engine replacement.
- No DB migration framework in this phase.
- No Blob payload storage.
- No permission model redesign beyond the access boundary required to stop
  using generic record-path lookup.
- Result:
- Record purpose taxonomy separates storage, presentation, logic, mutation,
  query, request, descriptor, and diagnostic records.
- `EntityPersistent.toStoreRecord/fromStoreRecord` is the storage boundary.
- Entity presentation has an explicit View Record path.
- SimpleEntity authorization uses typed security/permission access rather than
  stale generic record security paths.
- SimpleEntity storage-shape policy is defined, implemented, and covered by
  executable specs, including generated-code and unsupported scalar fallback
  coverage.
- Effective storage-shape metadata is visible in projection, Web manual, and
  component admin pages.
- Artifact (work): `docs/phase/phase-17.md`.

### Phase 19: BlogComponent Entity Image Usage
- Goal: use `textus-blog` `BlogComponent` as the concrete development driver
  for ordinary Entity-to-image binding usage.
- Status: closed.
- Scope:
- Establish the recommended way for application components to bind images to
  Entity instances using the Phase 18 Blob and Association foundation.
- Treat Blog creation as a user-account owned authoring workflow with draft,
  published, and inactive lifecycle state.
- Keep Blog read/search public for published active posts while protected
  author/admin operations handle draft, publish, deactivate, and image sync
  flows.
- Validate primary/cover image, inline image, thumbnail, and ordered supporting
  image use cases against `BlogComponent`.
- Validate article body `img` tag handling, including source URL discovery,
  managed image resolution, alt/title metadata, ordering, and inline binding
  synchronization.
- Introduce reusable CNCF HTML tree values for full-HTML parsing, article
  fragment extraction, head metadata fallback, `img` discovery, and `src`
  rewriting before considering a CML HTML datatype.
- Define how create/update/read/search operations, Aggregate/View projections,
  Web forms, admin pages, and manual/help metadata expose associated images.
- Implement CNCF runtime/Web/projection gaps discovered by the BlogComponent
  driver.
- Current BI-04 slice completed reusable `BlobProjection` output, Entity admin
  image visibility with existing Blob attach/detach controls, operation
  manual/help image-binding metadata, Entity admin create/update image
  attachments, and the generic `associationBinding` core used by
  image-specific binding. It also now includes `childEntityBinding` for
  SalesOrder/SalesOrderLine-style same-request child Entity creation from an
  operation result `entity_id`, plus generated `relationshipDefinitions` for
  Cozy-authored association/aggregation/composition metadata, including
  embedded value object composition stored in parent Entity fields. The
  `textus-blog` driver validates those generated definitions with
  `BlogPost.images` as BlobAttachment Association metadata and
  `BlogPost.inlineImages` as child-parent-id-field composition metadata. The
  latest BI-04 slice adds non-image Association admin/manual surfaces for
  generic association list, attach, detach, and Entity detail rendering, plus
  operation HTML/Form API binding adapters for image upload/existing Blob
  attachments and generic Association target id inputs outside Entity admin.
  It also standardizes `filebundle` as a core DataType so command execution can
  keep directory, single-file, and existing-ZIP inputs as path-preserving
  FileBundle values while client HTTP transport converts them to validated ZIP
  payloads at the network boundary. Packaged source activation was hardened so
  `--component-dev-dir` and `--subsystem-dev-dir` are selector-less development
  startup routes, while expanded `car.d` and `sar.d` remain explicit
  loader/debug inputs via `--component-car-dir` and `--subsystem-sar-dir`.
  CAR Web metadata is now sourced from `src/main/car/web`, Web application
  resources remain under `src/main/web`, and future private Web-app metadata is
  reserved for `WEB-INF` rather than `META-INF`.
  AF-01 adds reusable CNCF AtomFeed
  model/projection/rendering support and applies it to BlogComponent as a
  public `atomFeed` query for published active posts.
  BW-01 adds the component-owned `textus-blog` Web app using Static Form
  file-layout routing: `src/main/web/index.html` mounts at `/web/blog`, public
  reading is on `/web/blog/publicblogs`, authenticated authors manage posts on
  `/web/blog/userblogs`, and create/update use `/web/blog/new` and
  `/web/blog/update` with page-local result templates. Authors can upload Blog
  file bundles and insert existing Blob images through an editor picker.
  The latest inline-image core slice adds Textus URN parsing/resolution,
  Blob URN resolution, and reusable Blob inline-image normalization/attachment
  UoW operations. HTML fragment image references are normalized to Textus URNs
  for persisted content, relative filebundle images can be registered as
  managed Blobs, external URLs can be preserved or captured as metadata-only
  Blob rows, and public rendering expands URNs back to CNCF Blob content URLs.
  Phase 19 now generalizes that image-only slice into SimpleEntity content
  reference occurrences. The implemented HTML surface covers `img/src`,
  `video/src`, media `source/src`, `a/href`, and `a[download]/href`
  attachment references. `iframe/src` is deferred until an embed/sandbox
  policy exists.
  Entity-to-media/blob relationships are separate from occurrence data, which
  becomes the content-derived reference index. `textus-blog` now stores
  generated occurrences in `BlogPost.contentAttributes.references`, retires the
  Blog-specific inline image Entity from the runtime path, rejects
  `contentReferences` as external operation input, and synchronizes inline
  Associations from server-derived references. MB-01 adds
  CNCF builtin `Image`, `Video`, `Audio`, and `Attachment` Entities layered
  over BlobStore Blob metadata through `blobId`. The Textus URN vocabulary
  splits document references into `image`, `video`, `audio`, `attachment`, and
  generic `blob` kinds, for example `urn:textus:image:{value}` and
  `urn:textus:attachment:{value}`. New inline image content uses image URNs and
  `MediaAttachment`; Blob remains the generic fallback Entity for storage-level
  access, compatibility, and payloads outside the media kinds. `attachment` is
  reserved for CNCF-opaque formats such as Excel or Word attached as
  supporting Entity material. CT-01 makes content format/mimetype handling an
  explicit SimpleEntity content concern: `ContentAttributes.content` now uses
  `ContentBody`, `mimeType`, `charset`, and `ContentMarkup`; HTML fragments
  render through an article wrapper, GFM-compatible Markdown renders to HTML
  including table support, and SmartDox now has a Scala 3 parser/AST/rendering
  core in `simplemodeling-lib` for CNCF content rendering and reference
  extraction. The SmartDox slice preserves XML/JSON structured tokens and
  rewrites only parser source-spanned image target text to canonical Textus
  image URNs, while link references remain indexed without rewriting. Source
  spans mark rewrite candidates and now cover headings, lists, definition
  lists, table cells, captions, quote prose, and mapped inline markup in
  addition to ordinary paragraphs and block image lines. Markdown
  inline image/link references now feed the same content reference index:
  `![alt](src)` image destinations are normalized to image URNs, while
  `[label](href)` destinations are indexed without rewriting. Reference-style,
  collapsed, and shortcut Markdown images now normalize definitions and
  destinations to image URNs; reference-style links and autolinks are indexed
  without rewriting. The SmartDox Textus profile records the safe
  CNCF/Textus authoring subset, disabled executable/site features, XML/JSON
  structured token behavior, and source-span-aware image rewrite policy. The content storage
  policy keeps small text inline
  and overflows larger bodies through charset-aware byte sizing, leaving
  binary/opaque referenced payloads under Blob/media boundaries. SD-01B fixes
  the i18n/content boundary: existing `I18n*` values remain the standard for
  short localized metadata and bounded help/descriptive prose, while
  Blog/article/document bodies use `ContentBody`; rich multilingual document
  bodies are reserved for the future SmartDox Textus profile. The
  `textus-blog` driver now uses project-local
  version files rather than any `cncf-samples` version root when running its
  Web app against local CNCF snapshots. Job-adjacent verification now uses
  shared `JobEngineTestFixture` polling helpers and managed subsystem/job
  lifecycle fixtures, while real scheduler observation-window assertions are
  isolated in the timing-tagged `JobRetryTimingSpec` instead of ordinary
  `sbt test`. Entity kind and Working Set runtime policy is documented outside
  the authorization model: canonical `entityKind` values are `master`,
  `document`, `workflow`, `task`, `actor`, `asset`, and `system`; `BlogPost` is a CMS
  public-content document with a store-backed canonical record, Working Set
  disabled by default, and view/index/cache projections as the
  read-optimization path. The first
  runtime application of that policy is now in place: CNCF `ViewSpace` has
  application-facing read-side registration helpers, `ViewCollection` has
  explicit shared/principal/disabled query cache scopes, and `textus-blog`
  reads list/search/feed/slug/author dashboard surfaces through derived Blog
  view cache rows while detail content still loads the canonical store-backed
  `BlogPost`. EK-02 normalized `entityKind` default runtime policy through a
  single CNCF policy source and documented `operationKind` as a shrinking
  legacy `resource` / `task` bridge for compatibility and ABAC context. Phase
  19 also decides the `EntityId` runtime namespace policy: `major` / `minor`
  are operational partition keys, not layer identifiers; the default namespace
  is `single/global`; runtime configuration keys are
  `textus.id.namespace.major` and `textus.id.namespace.minor`; `sys/sys` is
  migration debt.
- Result: Phase 19 is closed. Remaining work is tracked as future platform,
  distributed-system, scalability, media-model, transaction/event, fallback,
  recovery, workflow-residency, optimistic-locking, and runtime-namespace
  hardening items below.
- Non-goals:
- No new Blob payload storage backend in CNCF core.
- No S3/S3-compatible BlobStore provider implementation in this phase.
- No thumbnail generation, image transformation, virus scanning, or resumable
  upload implementation.
- No payload embedding in ordinary Entity records.
- No finalized CML primitive datatype lock-in for HTML, Markdown, or SmartDox
  until the SimpleEntity content operation model is proven.
- Artifact (work): `docs/phase/phase-19.md`.

### Phase 20: Hierarchical Tagging and Knowledge Structure
- Goal: add CNCF builtin hierarchical Tags and validate them through CNCF
  admin/runtime and `textus-blog`.
- Status: closed; core Tag runtime, generic CNCF admin/manual surfacing,
  `textus-blog` driver, and closure verification are complete.
- Scope:
- Add `Tag` as a `SimpleEntity`-backed builtin master Entity.
- Treat the Tag hierarchy as a strict tree in this phase.
- Keep canonical Tag records store-backed while making the runtime Tag tree a
  resident Working Set candidate.
- Store Entity-to-Tag links through Association records, not embedded fields
  on ordinary domain Entities.
- Support parent Tag search with descendant expansion by default and explicit
  direct-only matching when needed.
- Support both lightweight CMS-style tagging and powertype-like external
  classification.
- Use CNCF admin/runtime as the generic driver for Tag tree management,
  arbitrary Entity tag attach/detach, and tag-expanded Entity search.
- Use `textus-blog` as the application driver for BlogPost tags, public tag
  navigation, and parent-tag descendant search.
- Completed so far:
- Runtime Tag model, TagSpace-scoped resident tree cache, effective
  Subsystem/Component/User/explicit TagSpace merge, TagAttachment Association,
  generic tag operations, Bootstrap 5 Tag admin/manual/projection surfaces, and
  `textus-blog` shared `blog` TagSpace integration.
- Remaining:
- None for Phase 20. RDF/Knowledge Graph integration and DAG/polyhierarchy Tag
  graphs remain future work outside Phase 20.
- Non-goals:
- No RDF store, RDF import/export, or external knowledge graph integration in
  this phase.
- No DAG/polyhierarchy Tag graph.
- No compile-time CML type generation from powertype-like Tags.
- No full-text or embedding search backend implementation.
- Artifact (work): `docs/phase/phase-20.md`.

### Phase 21: Web Next Stage / Static Form UI
- Goal: advance the CNCF Web layer after the Phase 12 Static Form baseline by
  standardizing Bootstrap 5 UI primitives, expanding Textus widgets, and adding
  reusable dialog-style action surfaces.
- Status: closed.
- Scope:
- Use CNCF admin/runtime pages, Static Form App pages, and selected
  `textus-blog` pages as concrete drivers.
- Keep the Web layer convention-first, server-rendered, and usable without
  JavaScript.
- Normalize page shell, breadcrumbs, action bars, cards, responsive tables,
  forms, alerts, empty states, job panels, and development debug panels around
  Bootstrap 5.
- Expand Textus widgets as server-rendered Bootstrap components over the
  existing property/result/action metadata model.
- Add reusable dialog-style action surfaces that reuse existing action metadata
  and provide no-JS fallbacks.
- Completed:
- Phase 21 work documents are opened and the Static Form UI scope is selected.
- Bootstrap primitive normalization, Textus widget expansion, dialog-style
  action surfaces, targeted CNCF admin/runtime driver pages, selected
  `textus-blog` driver pages, application-level Job UX baseline, the Web-facing
  full-text search planning/UI baseline, and the Static Form UI generation
  composition contract are implemented as partial Phase 21 slices. WN-14 also
  defines SPA hosting and API gateway as explicit deployment boundaries without
  adding a SPA runtime, and WN-15 completes the developer-facing Web
  documentation path.
- Remaining:
- None. Post-Phase 21 work should select the next development item explicitly.
- Non-goals:
- No SPA framework adoption as the default CNCF Web mode.
- No API gateway runtime or implicit separate SPA hosting mode.
- No visual design system separate from Bootstrap 5.
- No broad UI generation, file generation, or standalone wireframe DSL outside
  the Static Form layout composition contract.
- No Component Web page composition without explicit descriptor opt-in.
- Artifact (work): `docs/phase/phase-21.md`.

## 4. Relationship Between Phases
- Later phases depend on earlier phases.
- Phase 1.5 constrains Phase 2 and Phase 3.
- Execution model must not be changed by demo needs.
- Phase 4 depends on Phase 3 outputs (domain model / CRUD scaffolding as baseline consumers).
- Phase 5 depends on Phase 4 (events may reference state transitions and state machine lifecycle).

## 5. How to Read the Documents
- CNCF developers: use this strategy to sequence work.
- Demo/article authors: respect phase boundaries.
- AI assistants (Codex / ChatGPT): treat this as the top-level planning context.
- Notes contain execution details and results for each phase.

## Process Status Pointers
- Current phase selection: next phase to be selected.
- Current active phase dashboard: none.
- Current active phase checklist: none.
- Latest closed phase dashboard: `docs/phase/phase-23.md`
- Latest closed phase checklist: `docs/phase/phase-23-checklist.md`
- Candidate next phase areas: AwsComponent/S3 BlobStore
  provider; Search/index planning; DB migration tooling.
- Status interpretation rules: `docs/rules/stage-status-and-checklist-convention.md`

## 6. Explicit Non-Goals
- No skipping phases.
- No CRUD before demo foundation.
- No REST adapter explosion.
- No demo-driven architecture distortion.

## 7. Current Phase Status Snapshot

- Phase 4: closed (`docs/phase/phase-4.md`)
- Phase 5: closed (`docs/phase/phase-5.md`)
- Phase 6: closed (`docs/phase/phase-6.md`)
- Phase 7: closed (`docs/phase/phase-7.md`)
- Phase 8: closed (`docs/phase/phase-8.md`)
- Phase 9: closed (`docs/phase/phase-9.md`)
- Phase 10: closed (`docs/phase/phase-10.md`)
- Phase 11: closed (`docs/phase/phase-11.md`)
- Phase 12: closed (`docs/phase/phase-12.md`)
- Phase 13: closed (`docs/phase/phase-13.md`)
- Phase 14: closed (`docs/phase/phase-14.md`)
- Phase 15: closed (`docs/phase/phase-15.md`)
- Phase 16: closed (`docs/phase/phase-16.md`)
- Phase 17: closed (`docs/phase/phase-17.md`)
- Phase 18: closed (`docs/phase/phase-18.md`)
- Phase 19: closed (`docs/phase/phase-19.md`)
- Phase 20: closed (`docs/phase/phase-20.md`)
- Phase 21: closed (`docs/phase/phase-21.md`)
- Phase 22: closed (`docs/phase/phase-22.md`)
- Phase 23: closed (`docs/phase/phase-23.md`)

## 8. Completed Development Item History

This section is different from the Phase Overview above.

- Phase Overview explains the intended role of each numbered phase in the
  long-term development sequence.
- Completed Development Item History records major work areas that have already
  been closed and points to the concrete phase/checklist/closure documents.
- A history item usually corresponds to one phase-sized development area, but
  this section is written from the viewpoint of completed work areas rather
  than from the viewpoint of chronological planning.

### 8.1 Web Layer
Completed in Phase 12.

- Closed dashboard: `docs/phase/phase-12.md`
- Closed checklist: `docs/phase/phase-12-checklist.md`
- Closure note: `docs/notes/phase-12-web-closure.md`
- Completed scope:
  - operation-centric Web integration surface
  - REST/Form API exposure and Static Form App mechanism
  - Web Descriptor model and runtime completion
  - dashboard / management console / manual baseline
  - Bootstrap 5 baseline and first-pass Textus widget set
  - Web app packaging, local assets, and descriptor-scoped asset composition
  - shortid, job UX baseline, result/detail navigation, and action metadata normalization
  - executable specifications and sample-app smoke validation through `textus-sample-app`

### 8.2 Component Wiring and Subsystem Construction
Completed in Phase 11.

- Closed dashboard: `docs/phase/phase-11.md`
- Closed checklist: `docs/phase/phase-11-checklist.md`
- Completed scope:
  - Component port/binding baseline
  - subsystem construction via bound Components
  - executable wiring and assembly specifications
  - sample-facing wiring rules

### 8.3 Execution Layer Expansion
Completed in Phase 14.

- Closed dashboard: `docs/phase/phase-14.md`
- Closed checklist: `docs/phase/phase-14-checklist.md`
- Completed scope:
  - lightweight `WorkflowEngine` baseline
  - workflow inspection/projection surfaces
  - submission-only `JCL`
  - JCL-to-workflow entrypoint integration
  - retry/dead-letter hardening
  - explicit saga-id propagation
  - explicit-rule-only ABAC matching in event reception

### 8.4 Job Scheduling and Timer Boundary
Completed in Phase 15.

- Closed dashboard: `docs/phase/phase-15.md`
- Closed checklist: `docs/phase/phase-15-checklist.md`
- Completed scope:
  - shared bounded `JobEngine` scheduler for async execution
  - explicit queue priority and normalized workflow priority semantics
  - canonical built-in timer/scheduling boundary
  - bounded one-shot delayed root job start
  - `Consequence[JobId]` job submission contract for ordinary submit-time failures

### 8.5 Authentication Baseline and Cwitter Runtime Hardening
Completed in Phase 16.

- Closed dashboard: `docs/phase/phase-16.md`
- Closed checklist: `docs/phase/phase-16-checklist.md`
- Completed scope:
  - CNCF auth/session contract centered on `AuthenticationProvider`
  - browser Web session login/logout/current-session runtime path
  - `textus-user-account` as the first auth provider
  - provider-owned signup/signin/password-reset/optional 2FA pages
  - CNCF message-delivery provider SPI and built-in stub provider
  - Cwitter auth-aware baseline with post, mention, and direct-message flows
  - Cwitter minimal-code sample direction using provider pages directly
  - structured Web/API error display based on `Conclusion` detail codes
  - management console/manual/admin usability improvements
  - production admin authorization using privilege ceiling plus role policy
  - working-set policy and async startup fallback to direct store search
  - debug trace-job metadata and job-specific calltree retention policy
  - shared Web theme and provider common page light customization
  - locale/time-zone aware display formatting
  - lifecycle audit defaults for SimpleEntity create/update
  - component CAR deemed-subsystem startup with repository-resolved provider
    component CAR dependencies
  - SAR override model that preserves component CAR defaults and overrides by
    field

### 8.6 SimpleEntity Storage Shape
Completed in Phase 17.

- Closed dashboard: `docs/phase/phase-17.md`
- Closed checklist: `docs/phase/phase-17-checklist.md`
- Source note: `docs/journal/2026/04/simpleentity-db-storage-shape-note.md`
- Completed scope:
  - Record purpose taxonomy and boundary rules
  - explicit `EntityPersistent.toStoreRecord/fromStoreRecord` storage boundary
  - View Record boundary for entity presentation
  - migration of DB Record boundary call sites to store APIs
  - typed SimpleEntity security/permission access for authorization
  - SimpleEntity storage-shape policy for management fields, security identity,
    compact permission, scalar fields, value objects, and promoted children
  - executable storage-shape coverage, including generated-code and unsupported
    scalar fallback specs
  - storage-shape metadata projection for component describe/schema
  - storage-shape visibility in Web manual and component admin entity pages

### 8.7 Builtin Blob Management Component
Completed in Phase 18.

- Closed dashboard: `docs/phase/phase-18.md`
- Closed checklist: `docs/phase/phase-18-checklist.md`
- Source note: `docs/journal/2026/04/blob-management-component-specification-note.md`
- Final implementation snapshot:
  - CNCF: `22f0f32 Add structured Blob validation diagnostics`
  - goldenport core/simplemodeling-lib: `3725ce6 Add structured validation diagnostics`
- Completed scope:
  - builtin Blob metadata model as a SimpleEntity-backed management entity
  - managed and external-url Blob source modes
  - BlobStore SPI with in-memory/local backends, configurable backend selection,
    provider/plugin extension points, and restart-safe local sidecar metadata
  - authorized CNCF content route `/web/blob/content/{id}` with cache/content
    headers and conditional GET
  - post-close Blob core hardening guardrails for BlobStore/content URL
    boundaries, id-based content routes, and missing-payload route failures
  - user-facing Blob register/read/metadata/attach/detach/list operations
  - admin-facing Blob list/get/delete/association/store-status operations
  - Web/admin Blob metadata, association, store, delete, attach, and detach pages
  - generic Association runtime foundation used by Blob attachment
  - application create/update Blob attachment workflow for uploads and existing
    Blob ids
  - Aggregate/View Blob metadata projection with flat additive `blobs` rows,
    display/download URLs, ordering, and no inline payload bytes
  - Blob-required authorization support: role-to-capability expansion,
    resource policies for collections/associations/stores, operation integration,
    and read-only authorization visibility
  - upload hardening: external URL safety, content type syntax, byte-size/digest
    validation, max upload size, and MIME-kind policy
  - Blob operational metrics and structured observability using common
    `Consequence` / `Conclusion` validation facets instead of component-local
    error codes or message parsing
- Deferred scope:
  - S3/S3-compatible BlobStore implementation is tracked under AwsComponent,
    not CNCF core.
  - Broader ACL administration, subject grant UI, role lifecycle UI, and
    organization-grade policy management remain under 9.3 Security.
  - Broader Observation semantics and structured failure normalization remain
    under 9.7.
  - Retention policy, signed URLs, configurable MIME allowlists, thumbnail
    generation, virus scanning, and resumable upload remain future Blob/AWS
    hardening work.

### 8.8 BlogComponent Entity Image Usage
Completed in Phase 19.

- Closed dashboard: `docs/phase/phase-19.md`
- Closed checklist: `docs/phase/phase-19-checklist.md`
- Final implementation snapshot:
  - CNCF: `0a57c64 Extend content references to HTML media`
- Completed scope:
  - application-driven Entity image binding contract using `textus-blog`
    `BlogComponent`
  - Blog authoring lifecycle driver covering draft, published, inactive,
    public-read, author, and admin flows
  - reusable CNCF HTML tree parsing and fragment rewriting support
  - reusable Atom feed model/projection/rendering applied to Blog published
    active posts
  - component-owned Blog Web app driver over Static Form file-layout routing
  - Textus URN and URN repository baseline for document-facing references
  - SimpleEntity `ContentAttributes` / `ContentReferenceOccurrence` model for
    content-derived reference metadata
  - `BlogInlineImage` retirement in favor of
    `BlogPost.contentAttributes.references`
  - builtin Blob-backed media Entities for Image, Video, Audio, Attachment, and
    generic Blob fallback
  - media/document Textus URNs for image, video, audio, attachment, and blob
  - `MediaAttachment` Association as Entity-to-media relationship, with
    `BlobAttachment` retained for lower-level compatibility
  - HTML reference normalization for `img/src`, `video/src`, media
    `source/src`, plain `a/href` indexing, and `a[download]/href` attachment
    normalization; `iframe/src` is deferred to a future embed/sandbox policy
  - GFM-compatible Markdown rendering/reference normalization, including
    inline, reference-style, collapsed, shortcut, and autolink support where
    appropriate
  - SmartDox parser/AST/rendering/reference extraction baseline in
    `simplemodeling-lib`, plus Textus profile documentation and
    source-span-aware image rewrite for safe parser-spanned targets
  - `ContentBody`, `ContentMarkup`, MIME type, charset, and content
    inline/overflow storage policy baseline
  - Entity kind taxonomy and default runtime policy documentation with
    `master`, `document`, `workflow`, `task`, `actor`, and `asset`
  - BlogPost runtime classification as a CMS public-content document whose
    canonical record stays store-backed with Working Set disabled by default
  - first CNCF View cache projection usage for Blog public list/search/feed,
    slug lookup, and author dashboards, including principal-scoped query cache
  - `entityKind` default policy normalization and `operationKind` shrinkage to
    compatibility / ABAC context labeling
  - `EntityId.major/minor` operational namespace policy and initial
    `single/global` default implementation
  - job/subsystem lifecycle fixture hardening and separation of real scheduler
    timing checks from ordinary `sbt test`
- Deferred scope:
  - S3/S3-compatible BlobStore provider remains under AwsComponent.
  - `iframe/src` normalization requires a separate embed/sandbox policy.
  - MediaAttributes cleanup remains a future platform item.
  - Transaction outcome event lanes, ServiceCall fallback, compensation
    recovery events, workflow active-state residency, Entity/Aggregate version
    conflict policy, distributed component runtime, inter-component Saga, and
    persistent materialized view storage remain future development items.
  - Runtime namespace SAR/CAR descriptor defaults and remaining accidental
    `sys/sys` cleanup remain under Runtime Namespace Descriptor Defaults.

### 8.9 Event Mechanism Extension
Completed in Phase 13.

- Closed dashboard: `docs/phase/phase-13.md`
- Closed checklist: `docs/phase/phase-13-checklist.md`
- Completed scope:
  - subsystem-level shared event wiring
  - component subscription bootstrap
  - event-to-action dispatch and continuation/job semantics
  - boundary-aware reception policy selection by subsystem origin, event name,
    event kind, and selectors
  - same-subsystem default sync reception
  - async new-job same-saga reception
  - async new-job new-saga reception
  - internal await support
  - executable specifications and observability coverage
- Deferred scope remains under 9.2 Event Mechanism Follow-ups.

### 8.10 Tagging and Knowledge Structure
Completed in Phase 20.

- Closed dashboard: `docs/phase/phase-20.md`
- Closed checklist: `docs/phase/phase-20-checklist.md`
- Completed scope:
  - CNCF builtin hierarchical `Tag` model
  - `SimpleEntity`-backed master Tag records with TagSpace-scoped resident tree
    lookup
  - dot notation such as `a.b.c` as canonical Tag path/name grammar
  - effective runtime merge of Subsystem, Component, and User TagSpaces in
    `ExecutionContext`
  - operational master TagSpaces, shared application TagSpaces such as `blog`,
    and user-editable TagSpaces for personal tagging
  - Entity-to-Tag links through Associations
  - parent Tag search with descendant expansion
  - CNCF runtime/admin/manual/projection Tag management surfaces
  - `textus-blog` BlogPost tag navigation/search driver
  - resident TagTree cache use for effective TagSpaces
  - tag-expanded Entity search through EntityStore filtering
- Deferred scope remains under 9.5 Knowledge Structure Follow-ups.

### 8.11 Web Next Stage / Static Form UI
Completed in Phase 21.

- Closed dashboard: `docs/phase/phase-21.md`
- Closed checklist: `docs/phase/phase-21-checklist.md`
- Completed scope:
  - Bootstrap 5 primitive normalization for CNCF admin/runtime, system console,
    job/admin result, and shared result sections
  - Textus widget card/list/feedback surfaces, including card-list layout,
    empty-state actions, source-driven nav lists, and conservative Bootstrap
    feedback/status variants
  - `textus:confirm-action` dialog-style action surfaces with Bootstrap modal
    markup and no-JS fallback
  - CNCF admin/runtime driver page polish for Blob admin, Association admin,
    result action rows, destructive detach confirmation, performance
    diagnostics, form operation pages, default form results, and Web Descriptor
    detail pages
  - selected `textus-blog` driver page polish for user post list/results,
    editor/fallback edit Bootstrap cards, selected tag filter parity, and
    modal-style image/import surfaces
  - application-level Job UX baseline for Form-launched asynchronous Commands
    through `/web/{app}/jobs` and `/web/{app}/jobs/{jobId}`
  - development-mode collapsed execution debug panel and CallTree display for
    Form/HTML operation result pages
  - full-text Web search planning/UI baseline through `WebSearchQueryPlanner`,
    admin entity search cards, View search metadata, and deterministic
    unsupported feedback for semantic/hybrid modes without a backend
  - Static Form UI generation contract and `WEB-INF` layout/partial contract
  - Subsystem Web app composition from Component Web, including the
    `textus-blog` deemed-subsystem driver and descriptor-driven shell owner
    selection for multi-Component deemed-Subsystems
  - Progressive Enhancement / Island boundary contract
  - SPA hosting / API gateway boundary design, including CNCF-hosted minimal SPA
    boundary note
  - application developer documentation path and Web document index updates
- Deferred scope remains under 9.1 Web Next Stage Follow-ups.

### 8.12 Job Management
Completed in Phase 22.

- Closed dashboard: `docs/phase/phase-22.md`
- Closed checklist: `docs/phase/phase-22-checklist.md`
- Completed scope:
  - Command execution policy normalization around explicit interface timing,
    Job run timing, and managed-by-Job policy, with synchronous direct/no-Job
    as the ordinary default.
  - Job SimpleEntity management as a CNCF `entityKind = system` runtime
    management projection synchronized from JobEngine lifecycle snapshots.
  - Canonical single-Job JCL diagnostics for declared Event/Action chains,
    observed profile reconstruction, and declared-vs-observed comparison.
  - JobDefinition SimpleEntity management as a separate `entityKind = system`
    reusable definition record, including `jobDefinitionRef` binding and Job
    launch snapshots.
  - Execution record boundary for lightweight Job Entity records, full
    timeline, Task Execution Tree, task-local calltree, large result body, and
    raw event history.
  - Task transaction and explicit compensation boundary inside managed Jobs,
    including recovery-required diagnostics for incomplete cleanup.
  - User Job notification provider SPI plus Event-based forwarding so JobEngine
    emits lifecycle/recovery events while notification creation remains a
    provider concern.
  - `textus-user-notification` and `textus-blog` driver integration for
    provider-backed notifications, notification badges, composed notification
    pages, and application/system admin separation.
  - Query-only CompositeQuery boundary for page-view context aggregation across
    Web tier, App tier, and Domain tier responsibilities.
- Deferred scope remains under 9.14 Job Management Follow-ups, 9.4 Metrics and
  Observability, 9.15 Saga Management, 9.13 Distributed Component Runtime,
  9.2 Event Mechanism Follow-ups, and 9.1 Web Next Stage Follow-ups.

### 8.13 Error Model / Consequence-Conclusion Realignment
Completed in Phase 23.

- Closed dashboard: `docs/phase/phase-23.md`
- Closed checklist: `docs/phase/phase-23-checklist.md`
- Completed scope:
  - Normative Error Model policy entry point in
    `docs/design/error-model-policy.md`, with legacy Phase 2.x draft notes
    moved under `docs/notes/legacy/2026/05/`.
  - Formal Error Model vocabulary in `org.goldenport.observation` and
    `org.goldenport.conclusion`, while `org.goldenport.Conclusion` remains the
    public aggregate type.
  - Canonical taxonomy, cause, interpretation, disposition, status, and detail
    vocabulary ordering and numbering documented in
    `docs/design/error-taxonomy-catalog.md`.
  - Numeric `Long` `DetailCode` generation from structured `Conclusion` data,
    with `Conclusion.Status.detailCode` as the single authoritative detail-code
    value and `Status.detailCodes` / `Status.strategies` removed.
  - Focused `Consequence` helper expansion and representative CNCF Blob,
    Static Form/Web, Job, and Event failure normalization onto structured
    `Consequence.Failure(Conclusion)` values.
  - Web/API/Admin/Observability projection alignment on materialized
    `Conclusion.Status`, taxonomy, cause, disposition, facets, and
    `Conclusion.previous` source-error chains.
- Deferred scope remains under 9.7 Error Model Follow-ups, 9.4 Metrics and
  Observability, and 9.1 Web Next Stage Follow-ups.

## 9. Development Item Status

This final section lists planned active and future development areas only.
Completed work areas are recorded in section 8. When a development item closes,
remove its completion record from this section and add or update the
corresponding completed-history entry.

No current development item is selected. Phase 23 is closed; choose the next
phase from the candidate development areas below.

### 9.1 Web Next Stage Follow-ups
Future Web/platform development item.

- Production-grade CNCF-hosted SPA mode remains future work. CNCF can host a
  minimal same-origin static SPA for internal/prototype use, but first-class SPA
  mode still needs app-scoped History API fallback, SPA asset lifecycle,
  auth/session/CSRF policy, REST exposure policy, and a dedicated SPA-mode
  developer guide.
- Island Architecture runtime remains deferred. CNCF does not yet provide a
  `data-textus-island` standard attribute, core island loader, registry,
  WebDescriptor island schema, or island asset dependency resolution.
- External API gateway runtime remains deferred. REST remains the operation
  execution surface, but gateway concerns such as public API exposure policy,
  rate limiting, authentication translation, and cross-origin policy need their
  own explicit design.
- Component-owned admin page discovery beyond explicit Web composition remains
  future work.
- Full application notification UX remains future work beyond the Phase 22
  provider/forwarding/badge baseline. This includes richer user inbox
  operations, read/dismiss workflows, multicast/broadcast audience operations,
  and operator-facing notification administration as application UX rather than
  Job core behavior.
- Structured Web/API error presentation polish remains future work after
  application feedback on the Phase 23 projection baseline.

### 9.2 Event Mechanism Follow-ups
Future event/runtime development item.

- Add async same-job same-transaction reception.
- Add sync-with-async-fallback reception policy.
- Add ABAC-aware reception policy selection.
- Add richer event classification beyond name/kind/selectors.
- Add source component/componentlet specific policy overrides.
- Finalize saga-id propagation contract.
- Add transaction outcome event lanes:
  - transaction-success domain events for committed domain changes, projections,
    read-side updates, and downstream actions;
  - transaction-failure / rollback events for operational failure and rollback
    facts that must not be projected as committed domain changes;
  - compensation / recovery-required events for cleanup and human recovery
    signals.
- Align transaction outcome events with EventStore/EventBus lanes, ActionCall /
  UoW transaction boundaries, Job lifecycle records, and existing
  non-transactional / error event concepts.
- Align richer Event reception policy and async/sync same-job continuation
  semantics with future executable JCL `events` / `onEvent` orchestration.

### 9.3 Security
Future security development item.

- Provider replacement and multiple-provider precedence beyond the first
  provider baseline.
- Real email/SMS message-delivery providers after the stub-backed path.
- External identity/federation.
- Audit logging expansion beyond current lifecycle and operation records.
- First-class arbitrary ACL lists.
- General subject grant administration UI.
- Full role-definition lifecycle and role-to-capability registry.
- Organization-grade policy management beyond the Blob-required surfaces.

### 9.4 Metrics and Observability
Future observability development item.

- Metrics collection expansion.
- OpenTelemetry support.
- Dashboard drill-down for structured diagnostics, including
  `Conclusion.previous` source-error chains and grouping by structured
  taxonomy/cause/disposition fields.
- CallTree / execution-history / Job diagnostic result externalization:
  - CallTree, Task calltree, and Job result records must store compact
    summaries/references by default, not full action/UoW/space/I/O
    result/response payloads.
  - Small result values may be displayed inline with JSON/YAML pretty printing;
    large values should be represented by summary metadata such as byte size,
    record count, result type, and truncation/externalization status.
  - Explicit debug configuration may write selected large payloads to external
    diagnostic files or object storage and surface only the file/object reference
    in Web/admin diagnostics.
  - Externalized payload retention, redaction, authorization, and cleanup policy
    must be defined before production use.

### 9.5 Knowledge Structure Follow-ups
Future knowledge-structure development item.

- RDF-based data representation.
- External knowledge graph integration.
- DAG/polyhierarchy Tag graphs beyond the Phase 20 strict tree model.

### 9.6 AwsComponent
Future component development item.

- Provide AWS-facing integrations outside CNCF core so CNCF itself does not
  depend on AWS SDKs or AWS deployment assumptions.
- Initial scope includes S3/S3-compatible BlobStore provider support for the
  BlobStore SPI introduced in Phase 18.
- AwsComponent may provide:
  - BlobStore provider implementation for S3/S3-compatible object storage;
  - deployment-specific public base URL or signed URL integration;
  - AWS credential/configuration handling owned by the component, not by CNCF
    core;
  - future AWS service adapters when they are useful as CNCF components.
- CNCF core remains responsible for the generic BlobStore SPI, Blob metadata,
  Blob content route fallback, authorization, and projection contracts.
- AwsComponent remains optional. Local and in-memory BlobStores continue to
  serve development and executable-spec use cases.

### 9.7 Error Model Follow-ups
Future error-model hardening item.

- Broader replacement of remaining message-only / `Conclusion.simple` /
  component-local failure paths with structured `Consequence.Failure(Conclusion)`
  values where they are part of reusable framework behavior.
- Stable CNCF compatibility policy for taxonomy, numeric ordering, and
  `DetailCode` after pre-stable iteration ends.
- Generated error catalog/reference documentation from formal taxonomy, cause,
  interpretation, disposition, status, and detail-code rules.
- Application-level `appCode` / `appStatus` conventions and examples.
- CLI exit-code mapping policy from `Conclusion`; exit codes remain separate
  from numeric `DetailCode`.
- Additional source-error trace UX around `Conclusion.previous`, including
  dashboard drill-down and structured diagnostic grouping.

### 9.8 Media Attributes Model Cleanup
Future platform development item.

- Revisit `MediaAttributes` after the Phase 19 media/entity work has enough
  application feedback.
- Clarify which metadata belongs on reusable media values versus concrete media
  Entities such as Image, Video, Audio, Attachment, and generic Blob.
- Keep media-specific attributes out of unrelated domain objects; domain
  Entities should point to media through associations or content references
  rather than embedding ad hoc media fields.
- Align media metadata with Textus URN/media reference behavior, BlobStore
  payload metadata, representative image projection, and future thumbnail /
  derived rendition work.

### 9.9 ServiceCall Fallback
Future platform development item.

- Add fallback behavior for `ServiceCall` failures.
- The fallback contract should be explicit about which failures are eligible for
  fallback, whether fallback runs synchronously or as a Job/Event continuation,
  and how fallback results are represented in `Consequence` / `Conclusion` /
  observability output.
- Fallback must be declared by policy. It should not become an implicit retry or
  silent alternate path, and diagnostics should show the original failure,
  selected fallback, and fallback result.
- Align this with the broader error model cleanup and event/job continuation
  policy.

### 9.10 Compensation Recovery Events
Future platform development item.

- Introduce a recovery-required event for cases where compensation itself cannot
  fully clean up partial work.
- Target compensation-of-compensation situations where in-progress Entities,
  associations, media links, side-storage records, or other derived state can
  remain after automated compensation fails or becomes ambiguous.
- The event should carry enough structured context for human recovery: source
  operation/job, affected Entity ids, attempted compensation steps, remaining
  partial artifacts, failure cause, and suggested recovery surface.
- This is a human-in-the-loop recovery signal, not a silent best-effort cleanup.
  It should integrate with admin diagnostics, Job/Event history, and future
  recovery dashboards.

### 9.11 Workflow Active-State Working Set Policy
Future platform development item.

- Add an explicit Working Set policy for `entityKind = workflow` Entities whose
  active state should be resident while completed or inactive records remain
  store-backed.
- Define the state field, active values, inactive/completed values, and
  transition/update-time residency re-evaluation.
- `entityKind = workflow` remains only an active-residency candidate until this
  policy is explicitly configured by an application.
- Completed, cancelled, archived, or otherwise inactive workflow records should
  be evicted from the Working Set when their state changes.

### 9.12 Entity and Aggregate Version Conflict Policy
Future platform development item.

- Add a first-class optimistic locking / version conflict policy for Entity and
  Aggregate updates.
- Define a canonical concurrency token such as revision, version, or equivalent
  store-backed metadata, and allow update paths to carry an expected token.
- `entity_save`, `entity_update`, Aggregate update, and state transition paths
  should reject stale updates deterministically when the stored token no longer
  matches the expected token.
- Resident Working Set values must not bypass the store-backed version check.
- Intentional overwrite or repair should require an explicit force/repair API
  rather than ordinary save/update behavior.

### 9.13 Distributed Component Runtime
Future distributed-system development item.

- Defer distributed scheduler, distributed cache coherence, and clustered
  Working Set behavior to the distributed-system phase.
- The intended component clustering model is multiple component instances with a
  master/slave structure: the master handles read/write, slaves handle read
  only, and a slave may be promoted when the master fails.
- This item owns master election/promotion, write ownership, slave cache
  refresh, cross-instance View/Working Set coherence, and distributed Job/Event
  ownership.
- Current CNCF runtime hardening should preserve boundaries so a future
  distributed implementation can replace or extend the in-process `JobEngine`,
  Working Set, and View cache behavior.

### 9.14 Job Management Follow-ups
Future Job Management development item.

Phase 22 closed the baseline Job Management scope. Completed behavior is
recorded in section 8.12 and the closed Phase 22 documents:

- Dashboard: `docs/phase/phase-22.md`
- Checklist: `docs/phase/phase-22-checklist.md`

Future follow-ups:

- Implement executable JCL runtime for procedural `flow` and Event-driven
  `events` / `onEvent` sections. Phase 22 stores and documents those sections
  as future language surfaces only.
- Add JobDefinition accept/apply workflow and operational lifecycle expansion
  beyond create/update/activate/retire/search. This includes operator review of
  reconstructed JCL, promotion policy, and definition rollout/rollback
  handling.
- Persist Job/Task execution records beyond the current lightweight Job Entity
  projection. Full timeline, Task Execution Tree, task-local calltree, raw
  event history, and large result bodies need durable storage, retention, and
  authorization policy before production-scale use.
- Add CompositeQuery v2 features: parallel execution, a cross-subsystem
  protocol surface, richer App-tier page-view query contracts, and more
  explicit diagnostics for Web -> App and App -> Domain query composition.
- Continue refining Job UX around user-visible async work, including confirmed
  completion state, read/unread job indicators, and application-specific
  operator workflows. These should build on the Phase 22 Job/Event/notification
  boundaries rather than adding notification logic back into JobEngine.

### 9.15 Saga Management
Future distributed-collaboration development item.

- Manage Saga as a first-class Saga Entity.
- Treat Saga as the distributed and long-running extension of Job management
  across multiple Subsystems, multiple machines, and longer time horizons.
- Add support for long-running process sharing across components, component
  clusters, and remote runtimes.
- This item owns cross-component Saga coordination, correlation/causation/saga
  identity propagation, remote retry, remote compensation, and failure
  observability across cluster boundaries.
- Saga diagnostics should include distributed observability for remote
  operation boundaries, remote retry/compensation attempts, saga identity
  propagation, and cross-subsystem execution records.
- Saga management should reuse the Job/JCL concepts where practical, but its
  ownership, persistence, compensation, and observability boundaries are
  distributed rather than local to one in-process JobEngine.

### 9.16 Persistent Materialized View Store
Future scalability development item.

- Consider persistent materialized view storage only after CNCF has enough
  adoption pressure to justify read-side scaling beyond runtime memory cache.
- The current v1 model remains store-backed canonical Entities plus
  `ViewCollection` runtime memory cache with invalidate-on-write.
- A future materialized view store would own durable projection rows,
  incremental synchronization, replay/rebuild, stale projection detection, and
  query/index optimization.
- Blog/CMS list, slug index, feed, and author dashboard projections are
  candidate drivers when runtime memory cache becomes insufficient.

### 9.17 Runtime Namespace Descriptor Defaults
Future platform hardening item.

- Continue the runtime namespace policy after the initial `single/global`
  implementation baseline.
- Runtime configuration keys `textus.id.namespace.major` and
  `textus.id.namespace.minor` are the primary override surface, and CNCF default
  remains `single/global`.
- Add SAR descriptor defaults and CAR deemed-subsystem defaults ahead of the
  CNCF default when those descriptor fields are introduced.
- Keep system, subsystem, and component identity outside `EntityId.major` /
  `EntityId.minor`; those layer identifiers belong in descriptors, collection
  namespace, runtime metadata, and observability context.
- Remove or migrate remaining fixture and generated-code assumptions that still
  use `sys/sys` where they are not deliberately testing legacy compatibility or
  a different non-EntityId namespace axis.
