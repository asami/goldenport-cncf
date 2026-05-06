# CNCF Component Developer Document Index

## Purpose

This note is the entry point for CNCF component developers.

CNCF documentation is intentionally split by concern: component model, CML,
runtime execution, UnitOfWork, Web, authorization, descriptors, and admin
surfaces. That is useful for design ownership, but hard to navigate when
building a component. This index gives a reading path and points to the current
documents by development task.

For the broad design archive, see `docs/design/design-consolidated.md`. This
index is narrower: it is for developers who are modeling, implementing,
packaging, or operating CNCF components.

## First Reading Path

Read these first when starting component development:

1. `docs/spec/glossary.md`
   - Basic CNCF vocabulary: Component, Componentlet, OperationCall, UnitOfWork,
     and ExecutionContext.
2. `docs/design/component-model.md`
   - Stable component boundary and responsibility split.
3. `docs/design/component-and-application-responsibilities.md`
   - What belongs in the framework, the component, and application code.
4. `docs/notes/cml-application-modeling-guideline.md`
   - How to write application CML without duplicating SimpleEntity concerns.
5. `docs/notes/application-logic-guideline.md`
   - How handwritten/generated ActionCall logic should use CNCF internal DSL
     and value types.
6. `docs/notes/unitofwork-guideline.md`
   - UnitOfWork usage and persistence/authorization boundary guidance.

After these, choose the task-specific section below.

## Modeling A Component

Use these when deciding the application model:

- `docs/notes/cml-application-modeling-guideline.md`
  - CML modeling rules, SimpleEntity field reuse, derived fields, and
    relationship choices.
- `docs/notes/entity-kind-and-working-set-policy.md`
  - `entityKind` taxonomy and default runtime/working-set expectations.
- `docs/spec/component-descriptor-entity-classification-examples.md`
  - Descriptor examples for `master`, `document`, `workflow`, `task`, `actor`,
    and `asset`.
- `docs/notes/entity-runtime-architecture.md`
  - Entity runtime, working set, view cache, and runtime policy notes.
- `docs/design/simpleentity-storage-shape-policy.md`
  - SimpleEntity storage shape policy.
- `docs/notes/partitioned-entity-realm.md`
  - Entity realm and partitioning background.
- `docs/notes/id-major-minor-operation-note.md`
  - `EntityId.major/minor` operational namespace policy.

Use `SimpleEntity` for ordinary application entities first. Add CML fields only
when they are domain-specific and not already covered by standard entity
attributes.

## Implementing Application Logic

Use these when writing or reviewing generated/handwritten logic:

- `docs/notes/application-logic-guideline.md`
  - Internal DSL first, no raw DataStore access in business logic, standard
    value types instead of raw primitives.
- `docs/notes/internal-dsl-guideline.md`
  - Internal DSL design and use.
- `docs/notes/unitofwork-guideline.md`
  - UoW boundary, persistence behavior, and consistency expectations.
- `docs/design/entity-authorization-model.md`
  - Entity and UoW access authorization model.
- `docs/notes/entity-authorization-implementation-note.md`
  - Current implementation guidance for entity authorization.
- `docs/notes/aggregate-method-implementation-strategy.md`
  - Aggregate method / ActionCall alignment.

The default rule is: application logic expresses intent; CNCF DSL/UoW helpers
own lifecycle, authorization, logical delete filtering, tenant scope, and store
mechanics.

## Component Runtime And Wiring

Use these when working on component instantiation, ports, packaging, or runtime
assembly:

- `docs/design/cncf-component.md`
  - CNCF component design.
- `docs/design/component-factory.md`
  - Component factory/provider/group contract.
- `docs/design/component-port-wiring.md`
  - Component port wiring and service injection path.
- `docs/design/component-internal-execution-model.md`
  - Internal execution model.
- `docs/design/domain-component.md`
  - DomainComponent / Cozy integration contract.
- `docs/design/assembly-descriptor.md`
  - Runtime assembly descriptor and reproducible wiring.
- `docs/notes/subsystem-descriptor-note.md`
  - Subsystem descriptor guidance.
- `docs/notes/cml-crud-domain-subsystem-bootstrap.md`
  - Minimal CML to CRUD domain subsystem pipeline.
- `docs/notes/component-car-repository-resolved-provider-note.md`
  - Component CAR/repository/provider resolution notes.
- `docs/notes/omponent-discovery-from-classdir.md`
  - Development-time component discovery from class directories.

Use assembly descriptors for resolved operational wiring. Keep subsystem
descriptors as authored intent.

## Web, Admin, And Static Form Apps

Use these when exposing a component through Web/Form/admin surfaces:

The primary CNCF Web application strategy is a split between the application
tier and the Web tier. CNCF components expose application behavior through REST
operations, and a Web-tier application may use any suitable Web technology to
build the user interface on top of those REST APIs.

Static Form Web UI is the CNCF-provided lightweight Web UI path. Use it for:

- the CNCF management console foundation;
- development-time checking and debugging;
- development-time prototypes;
- simple internal-use Web UIs.

Static Form pages can also be composed from Component-provided Web fragments
inside a Subsystem/deemed-subsystem shell. That makes Static Form useful for
internal application screens during development, even though full production
Web applications should normally be designed as Web-tier applications over REST.

- `docs/design/web-layer.md`
  - CNCF Web layer, REST/Form API boundaries, Static Form App, local assets,
    routing, SPA boundary, and UI baseline.
- `docs/notes/cncf-web-static-form-app-contract.md`
  - Static Form App contract, result conventions, and lightweight UI role.
- `docs/notes/cncf-web-descriptor-minimum-schema.md`
  - Minimum Web descriptor schema.
- `docs/design/web-form-api-schema.md`
  - JSON Form API schema and relationship to Static Form rendering.
- `docs/design/web-operation-dispatcher.md`
  - Web operation dispatch path.
- `docs/spec/textus-widget.md`
  - Textus widget contract and Bootstrap output requirements.
- `docs/notes/static-form-web-app-bootstrap-guide.md`
  - Practical Bootstrap 5 screen construction guide.
- `docs/notes/web-bootstrap-ui-polish-design.md`
  - Bootstrap 5 polish direction for built-in pages and Static Form apps.
- `docs/notes/web-textus-widget-design.md`
  - Textus widget design notes.
- `docs/notes/web-application-theme-and-provider-page-customization-note.md`
  - Theme and provider page customization notes.
- `docs/notes/cncf-hosted-spa-boundary-note.md`
  - CNCF-hosted minimal SPA boundary and production SPA-mode gaps.

Static Form Web Apps should use the local Bootstrap/Textus asset baseline and
ordinary Bootstrap layout primitives before adding application CSS.

## Content, Media, Tags, And Associations

Use these when a component stores content, references media, or attaches
external classification:

- `docs/journal/2026/05/ct-01-content-body-storage-note.md`
  - ContentBody, MIME/charset, and storage policy notes.
- `docs/design/entity-image-binding-usage-contract.md`
  - Entity image binding usage contract.
- `docs/notes/entity-kind-and-working-set-policy.md`
  - `asset` and `document` runtime classification.
- `docs/phase/phase-20.md`
  - Phase 20 hierarchical tagging and knowledge structure.
- `docs/phase/phase-20-checklist.md`
  - Tagging implementation status and remaining work.

For new document/content entities, keep body content in `ContentAttributes`.
For media, use BlobStore-backed media entities and associations rather than
embedding payloads in domain records.

## Authorization And Security

Use these when defining access behavior:

- `docs/design/entity-authorization-model.md`
  - Canonical entity/UoW authorization model.
- `docs/notes/entity-authorization-implementation-note.md`
  - Implementation notes and current behavior.
- `docs/design/web-layer.md`
  - Web dispatch and admin authorization context.
- `docs/notes/application-logic-guideline.md`
  - Application logic boundary: do not bypass DSL/UoW authorization paths.

Do not use security documents as the place to define entity taxonomy or runtime
residency policy. Those belong in entity/runtime notes.

## Operations, Jobs, Events, And Long Running Work

Use these for execution behavior beyond simple synchronous calls:

- `docs/design/event-driven-job-management.md`
  - Canonical event-driven job management overview.
- `docs/design/job-state-transition.md`
  - Job state transition model.
- `docs/design/event-shape.md`
  - Canonical event shape.
- `docs/notes/event-driven-job-management-decisions.md`
  - Practical job/event decisions.
- `docs/notes/event-reception-policy-selection.md`
  - Event reception policy guidance.
- `docs/notes/event-reception-latest-processing-spec.md`
  - Latest event processing behavior.

Use job/event surfaces when operation duration, retry, or external signal
handling should be explicit rather than hidden inside request/response code.

## Admin, Manual, And Introspection

Use these when checking what a component exposes:

- `docs/design/cncf-introspection-spec.md`
  - Component-level help/describe/schema projection.
- `docs/notes/doc-help-design.md`
  - Help/manual design notes.
- `docs/spec/output-format.md`
  - Output format expectations.
- `docs/spec/path-resolution.md`
  - CLI/path resolution behavior.
- `docs/design/assembly-descriptor.md`
  - Admin assembly descriptor and warnings.

Generated manual/admin pages are part of the component development feedback
loop. Use them to inspect descriptors, operations, entities, storage shape,
associations, and Web descriptor completion.

## Testing And Validation

Use these when adding tests or validating changes:

- `docs/spec/test-policy.md`
  - Test policy for CNCF.
- `docs/rules/executable-spec-display-and-tagging-rules.md`
  - Executable spec display/tagging rules.
- `docs/rules/stage-status-and-checklist-convention.md`
  - Checklist/status convention.

For normal component development, prefer focused specs first, then full repo
validation before release commits. Specs that create Subsystems or JobEngines
should use the shared fixtures so runtime threads are always shut down.

## Common Development Questions

### I am creating a CRUD-style component. Where do I start?

Start with:

1. `docs/notes/cml-application-modeling-guideline.md`
2. `docs/notes/cml-crud-domain-subsystem-bootstrap.md`
3. `docs/notes/application-logic-guideline.md`
4. `docs/notes/cncf-web-static-form-app-contract.md`

### I am adding custom operation logic. What should I avoid?

Read `docs/notes/application-logic-guideline.md` and
`docs/notes/unitofwork-guideline.md`. Avoid raw `DataStore` access from
application logic. Add or reuse an internal DSL helper when lifecycle,
authorization, tenant scope, or association behavior is involved.

### I am making a Web page for my component. Which UI rules apply?

Read:

1. `docs/design/web-layer.md`
2. `docs/notes/cncf-web-static-form-app-contract.md`
3. `docs/spec/textus-widget.md`
4. `docs/notes/static-form-web-app-bootstrap-guide.md`

Use Bootstrap 5 primitives and local assets.

### I need tags, media, content references, or attachments.

Use CNCF builtin content/media/tagging mechanisms. Do not create component-local
payload storage or local tag fields unless the domain has a clear reason beyond
ordinary content/media/tag behavior.

### I need to know whether an entity should be resident.

Read `docs/notes/entity-kind-and-working-set-policy.md` and
`docs/notes/entity-runtime-architecture.md`. Entity kind decides default policy;
working set residency is runtime policy, not the same thing as business type.
