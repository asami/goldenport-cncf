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
- CNCF note: `error-semantics.md`
- Core note: `core-error-semantics.md`
- Related design notes:
  - `docs/notes/scope-context-design.md`
  - `docs/notes/conclusion-observation-design.md`
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
  - `Conclusion.detailCode` is planned as `Long` and represents semantic detail.
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
- `docs/notes/phase-2.9-error-realignment.md`

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
- Status: open.
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
- Non-goals:
- No new Blob payload storage backend in CNCF core.
- No S3/S3-compatible BlobStore provider implementation in this phase.
- No thumbnail generation, image transformation, virus scanning, or resumable
  upload implementation.
- No payload embedding in ordinary Entity records.
- Artifact (work): `docs/phase/phase-19.md`.

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
- Current phase selection: Phase 19 — BlogComponent Entity Image Usage.
- Latest active phase dashboard: `docs/phase/phase-19.md`
- Latest active phase checklist: `docs/phase/phase-19-checklist.md`
- Latest closed phase dashboard: `docs/phase/phase-18.md`
- Latest closed phase checklist: `docs/phase/phase-18-checklist.md`
- Candidate next phase areas: AwsComponent/S3 BlobStore provider; Error Model / Consequence-Conclusion Realignment; Search/index planning; DB migration tooling.
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
- Phase 19: open (`docs/phase/phase-19.md`)

## 8. Development Item Status

This section lists active and future development areas. Once a work area is
completed, it moves to the completed development item history below.

### 8.1 Web Next Stage Candidates
- Static Form Web App next step:
  - Island Architecture introduction on top of the convention-first static baseline
  - richer Textus widget families where concrete application pressure exists
  - stronger application-level job UX and dialog-style reusable surfaces
- Search:
  - full-text search planning layer
  - embedding / semantic search backend strategy
  - CML / View / Query alignment for search-facing metadata
- Web/UI generation:
  - wireframe/UI generation strategy above Static Form primitives
  - clarify responsibility split between generated UI and hand-written static pages
- Web admin integration:
  - allow component CARs to provide component-owned Web admin pages
  - let the CNCF system admin console discover and integrate those component
    admin pages into its navigation and management surface
  - keep CNCF responsible for admin authorization, navigation composition, and
    system-level framing
  - keep component CARs responsible for component-specific admin content and
    local admin routes
- SPA hosting and API gateway remain separate modes, not implicit extensions of
  the current Static Form baseline.
- Source references:
  - `docs/phase/phase-12.md`
  - `docs/phase/phase-12-checklist.md`
  - `docs/notes/phase-12-web-closure.md`

### 8.2 Event Mechanism Extension
Closed in Phase 13. This remains a reference area for Phase 14+ extensions and regressions.

- Phase 13 baseline:
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
- Future extensions beyond the Phase 13 baseline:
  - async same-job same-transaction reception
  - sync-with-async-fallback reception policy
  - ABAC-aware reception policy selection
  - richer event classification beyond name/kind/selectors
  - source component/componentlet specific policy overrides
  - finalized saga-id propagation contract
- Source references:
  - `docs/phase/phase-13.md`
  - `docs/phase/phase-13-checklist.md`
  - `docs/journal/2026/04/event-mechanism-extension-work-items.md`
  - `docs/notes/event-reception-policy-selection.md`
  - `docs/journal/2026/04/phase-13-closure-result-2026-04-22.md`
  - `docs/journal/2026/04/textus-sample-app-event-phase-13-closure-handoff-2026-04-22.md`

### 8.3 Security
- Phase 16 closed the first auth/session and user-account baseline.
- Future security work is extension-oriented:
  - provider replacement and multiple-provider precedence beyond the first
    provider baseline
  - real email/SMS message-delivery providers after the stub-backed path
  - external identity/federation
  - audit logging expansion beyond current lifecycle and operation records
  - first-class arbitrary ACL lists
  - general subject grant administration UI
  - full role-definition lifecycle and role-to-capability registry
  - organization-grade policy management beyond the Blob-required surfaces
- Source references:
  - `docs/phase/phase-16.md`
  - `docs/phase/phase-16-checklist.md`

### 8.4 Metrics and Observability
- Metrics collection
- Observability integration
  - OpenTelemetry support

### 8.5 Tagging and Knowledge Structure
- Hierarchical tagging model
- Classification and navigation support

### 8.6 RDF Integration
- RDF-based data representation
- External knowledge graph integration

### 8.7 Builtin Blob Management Component
Completed in Phase 18.

- Closed dashboard: `docs/phase/phase-18.md`
- Closed checklist: `docs/phase/phase-18-checklist.md`
- Source note: `docs/journal/2026/04/blob-management-component-specification-note.md`
- Completed scope is recorded in Completed Development Item History section 9.7.

### 8.8 AwsComponent
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

### 8.9 BlogComponent Entity Image Usage
Active in Phase 19.

- Active dashboard: `docs/phase/phase-19.md`
- Active checklist: `docs/phase/phase-19-checklist.md`
- Development driver: `textus-blog` `BlogComponent`
- Purpose:
  - make the ordinary Entity-to-image binding usage concrete after Phase 18;
  - model Blog authoring as a user-account owned flow with draft/published and
    active/inactive state;
  - keep public Blog references limited to published active posts while
    protected author/admin operations own draft, publish, deactivate, and image
    synchronization flows;
  - validate primary, inline, thumbnail, and ordered supporting image roles as
    CNCF BlobAttachment Association roles rather than direct entity fields;
  - handle article body `img` tags as image occurrences that can be reconciled
    with managed Blob metadata and inline Association rows;
  - support Blog file-tree import by using reusable CNCF HTML tree values rather
    than Blog-specific parsing code;
  - use managed Blob ZIP archives as the production Blog file-tree import
    input while keeping local `treeRootPath` as a development driver path;
  - keep `registerPost` as the lower-level normalized HTML/Blob-reference
    boundary, with local path payload registration owned by `importPostTree`;
  - use Association-only image links for BlogPost, including primary image
    selection by role priority;
  - expose associated Blob images through reusable `BlobProjection` output with
    `images` plus derived `representativeImage`;
  - expose and fix CNCF runtime/Web/projection/generator gaps using a real
    component driver.
- Scope boundary:
  - Blob payload storage, Blob metadata, authorization, and content routes
    remain owned by the Phase 18 Blob foundation;
  - S3/S3-compatible provider work remains under AwsComponent;
  - `BlogComponent` owns blog semantics, while CNCF owns reusable image binding
    runtime/Web/projection behavior.

### 8.10 Error Model / Consequence-Conclusion Realignment
Future platform development item.

- `8.10-A Consequence/Conclusion-based failure diagnostics` is now the active
  cleanup slice for the diagnostics work first exposed by Phase 18 Blob
  metrics. The implementation direction is:
  - reusable framework and builtin components emit ordinary
    `Consequence.Failure(Conclusion)` values;
  - `Cause.Kind` carries coarse mechanism classification such as capability,
    permission, guard, relation, format, policy, limit, and inconsistency;
  - `Descriptor.Facet` carries machine-readable detail such as parameter,
    field path, policy, algorithm, capability, permission, guard, relation, and
    reason;
  - metrics, dashboards, Web/admin diagnostics, and observability records
    project diagnostics from `Conclusion` structure;
  - component-local failure labels are not a public or compatibility surface.
- Revisit the core `Consequence` / `Conclusion` / `Observation` model before
  adding more component-local error classification surfaces.
- Clarify `Status` structure and semantics:
  - `Status.detailCodes` are application-owned semantic error codes.
    Reusable framework or builtin components must not write component-specific
    detail codes into this field.
  - `Status.detailCode` / `Status.detailCodes` and `strategies` do not yet have
    the intended shape and need a core-level redesign.
  - External projections such as CLI, HTTP, Web, metrics, and dashboards must
    derive their behavior from the corrected structured `Conclusion` model, not
    from component-local message parsing or private diagnostic taxonomies.
- Clarify the boundary between logic-bearing failure semantics and descriptive
  observability:
  - `Conclusion` remains the execution/control-flow failure value.
  - `Observation` remains descriptive and projection-oriented.
  - Operational diagnostics may project coarse failure groups from
    `Conclusion.status` and `Observation.taxonomy`, but must not create a
    parallel application-specific error structure inside reusable components.
- Promote or rewrite the existing draft notes into a normative design/spec once
  the core model is corrected:
  - `docs/notes/error-semantics.md`
  - `docs/notes/conclusion-observation-design.md`
  - `docs/notes/observation-descriptor-error-notification-guideline.md`
- Blob follows the `Consequence` / `Conclusion` error model. BL-10E added the
  immediate shared validation support needed by Blob: `Cause.Kind` provides
  coarse mechanism classification and `Descriptor.Facet` provides
  machine-readable detail. Reusable validation distinctions such as payload
  byte-size, MIME-kind, digest, expected-size, content-type, and external URL policy are
    projected from structured `Conclusion` data instead of component-local
    `Status.detailCodes` or message parsing.
- Remaining work in this item is broader platform cleanup: the intended
  `Status.detailCode` / `detailCodes` / `strategies` shape, possible
  `Cause.Kind` refinements beyond ordinary validation, and cross-component
  Observation semantics that are not required to complete Blob.

## 9. Completed Development Item History

This section is different from the Phase Overview above.

- Phase Overview explains the intended role of each numbered phase in the
  long-term development sequence.
- Completed Development Item History records major work areas that have already
  been closed and points to the concrete phase/checklist/closure documents.
- A history item usually corresponds to one phase-sized development area, but
  this section is written from the viewpoint of completed work areas rather
  than from the viewpoint of chronological planning.

### 9.1 Web Layer
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

### 9.2 Component Wiring and Subsystem Construction
Completed in Phase 11.

- Closed dashboard: `docs/phase/phase-11.md`
- Closed checklist: `docs/phase/phase-11-checklist.md`
- Completed scope:
  - Component port/binding baseline
  - subsystem construction via bound Components
  - executable wiring and assembly specifications
  - sample-facing wiring rules

### 9.3 Execution Layer Expansion
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

### 9.4 Job Scheduling and Timer Boundary
Completed in Phase 15.

- Closed dashboard: `docs/phase/phase-15.md`
- Closed checklist: `docs/phase/phase-15-checklist.md`
- Completed scope:
  - shared bounded `JobEngine` scheduler for async execution
  - explicit queue priority and normalized workflow priority semantics
  - canonical built-in timer/scheduling boundary
  - bounded one-shot delayed root job start
  - `Consequence[JobId]` job submission contract for ordinary submit-time failures

### 9.5 Authentication Baseline and Cwitter Runtime Hardening
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

### 9.6 SimpleEntity Storage Shape
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

### 9.7 Builtin Blob Management Component
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
    organization-grade policy management remain under 8.3 Security.
  - `Status.detailCode` / `Status.detailCodes` / `strategies` redesign and
    broader Observation semantics remain under 8.9.
  - Retention policy, signed URLs, configurable MIME allowlists, thumbnail
    generation, virus scanning, and resumable upload remain future Blob/AWS
    hardening work.
