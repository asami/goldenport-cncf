# Component Admin Design Boundary

Status: current Phase 60.8 ADM-09A design boundary

## Purpose and authority

Component Admin is the operator-facing projection of one loaded Component and
its loaded Subsystem context. It is a read-oriented view assembled from facts
that have already been admitted by their owning runtime, configuration,
resource, knowledge, documentation, authorization, and Web-descriptor
boundaries. Each displayed fact retains its existing authoritative owner and
safe provenance; Admin is not a new authority for any fact.

The paired [Component Admin specification](../spec/component-admin.md) is the
normative behavioral contract. The earlier [implementation note](../notes/component-admin-documentation-visibility-implementation.md)
is historical, non-normative consideration material and is superseded by this
design and specification.

## Non-duplication boundary

Admin introduces no independent file or archive scan, Component or resource
resolution, resource read, documentation generation, model generation, Help
ownership, repository/cache client, alternate dispatcher, or management
authority. It consumes caller-supplied, already-resolved values and projects
them without changing identity, state, provenance, authorization, or
lifecycle. A visible resource, contract, Operation, or page is descriptive
evidence; it is never an invocation grant.

The Phase 58 resource boundary owns Component/SubComponent composition and
resource state. The Phase 59 knowledge and Help boundaries own manifest
identity, resource evidence, and human navigation. The runtime owns lifecycle,
health, dependencies, datastore, Entity ownership, and execution context. The
Component-owned Operation policy owns management registration and
authorization. The Web descriptor owns declared page metadata and route
admission. Admin only joins these facts at one exact loaded identity.

## Identity axes and safe absence

The view preserves, without implicit fallback or cross-axis selection:

- Component class (`ComponentId`);
- selected logical Component release and its release candidates;
- selected loaded Component instance (`ComponentInstanceId`) and its instance
  candidates;
- Subsystem class and selected Subsystem instance;
- the implicit Component Subsystem;
- resource availability/failure state; and
- field-level safe provenance, including source kind and optional logical
  resource identity.

Release, instance, Subsystem, artifact, route, display name, and compatibility
aliases are different axes. Selecting one never selects a first, nearest,
latest, aliased, or otherwise inferred value on another axis. Unavailable,
forbidden, stale, incompatible, and corrupt observations remain typed values;
an absent or unsafe fact is represented by the owning boundary's safe absence
or a failed admission, never by a guessed replacement.

## View-model schema

The stable schema identifier is `cncf.component-admin-view.v1`. The
`ComponentAdminViewModel` contains `componentClass`,
`selectedLogicalRelease` with `logicalReleaseCandidates`,
`selectedLoadedInstance` with `loadedInstanceCandidates`, `subsystemClass`,
`subsystemInstance`, `implicitComponentSubsystem`, and `resourceState`.
Every field is a value plus `ComponentAdminSafeProvenance`; logical provenance
is limited to safe source kind and a validated
`ComponentResourceLogicalIdentity`. The strict codec rejects unknown or
duplicate fields, malformed identities, unsafe text/routes, and mismatched
selection membership, and emits deterministic JSON. The codec is a
serialization boundary, not a resolver.

## Layered projections

The layers are separate and retain their owner-specific evidence:

### Configuration and composition

`ComponentAdminConfigurationCompositionProjection` projects the typed
`ConfigurationBindingCollection` trace, including effective and overridden
bindings, scope, redacted values, and source provenance. It retains the
caller-supplied Phase 58 `ResolvedComponentResources` composition (primary,
Documentation, and SourceCode) and performs no scan, resolution, loading, or
authority grant.

### Contract and model

`ComponentAdminContractModelProjection` projects the Phase 59
`ComponentKnowledgeManifestConsumerContract` after exact Component and
logical-release matching. Service, Operation, SPI, capability, dependency,
schema, model, relationship, and diagram metadata remain authoritative
descriptions. Visibility does not reconstruct a model, invoke an Operation,
or grant management authority.

### Runtime and datastore

`ComponentAdminRuntimeDatastoreProjection` projects supplied operational,
lifecycle, health, dependency, ClassLoader, datastore, schema, collection,
Entity-ID, and `ExecutionContext` evidence for the selected loaded instance.
An admitted Entity-ID input resolves only through its declared `EntitySpace`
owner and must retain exact `EntityCollectionId` equality; scalar locators,
foreign IDs, missing owners, ambiguous owners, and cross-instance facts are
absent through deterministic failure. Standalone and MultiUser are retained as
context evidence, not interpreted as Component policy.

### Documentation navigation

`ComponentAdminDocumentationNavigationProjection` binds explicit selections
(User Guide, Reference Manual, Scaladoc, model diagrams, examples, source
availability, and troubleshooting) to the exact Phase 59 manifest entries and
the existing human Help navigation. It preserves availability, integrity,
authorization, safe provenance, logical identity, and canonical encoded Help
routes. It does not scan, read, generate, cache, resolve, or own a second
documentation inventory. Help remains the owner of human and Direct-AI
navigation; Admin exposes selected links only.

### Authorized management

`ComponentAdminAuthorizedManagement` accepts only the finite, exact catalog of
known Component-owned management selections. Its six admitted selectors are
`entity/create`, `entity/update`, `data/create`, `data/update`,
`association/admin_attach_association`, and
`association/admin_detach_association`; ordinary queries remain distinct.
Admission requires the current target Component, loaded instance, release and
Subsystem binding, lifecycle evidence, stored Operation authorization,
validated input, principal/subject attribution, and an idempotency key where
replay is relevant. Outcomes are typed (`Admitted`, `Forbidden`, `Conflict`,
`Unavailable`, `StaleInstance`, `RetryRequired`, or `IdempotentReplay`) and
carry audit evidence. Catalog visibility does not invoke an action; this
value-only boundary never executes management or discovers Operations.

### Surface security

`ComponentAdminSurfaceSecurity` projects one validated view and the accepted
navigation/catalog through Web, HTTP, CLI, and machine-readable descriptors.
All four retain the exact same identity view. Documentation is metadata-only:
it discloses canonical Help paths, safe resource state, digest, and safe
provenance while withholding content, physical paths, repositories, and
credentials. Management display eligibility additionally requires exact
target identity, Active lifecycle, visibility, availability, and current
stored-operation authorization; display does not authorize or invoke.

## Help/Admin and Web page boundary

Help is optimized for human learning and Direct-AI navigation. Admin is
optimized for inspecting and operating one concrete runtime context. They may
link to one another and consume the same Phase 58/59 evidence, but neither
duplicates the other's ownership, resolver, scanner, or generator.

Component Admin descriptor pages are projected only when a
`WebDescriptor.AdminPage` is an exact, declared, canonical component page with
a safe lower-case page segment and the component-qualified
`/web/{component}/admin/{page}` href. A declared canonical page may remain
descriptor metadata even when its template is missing. The downstream HTTP
dispatcher independently requires a present template before rendering; a
declared page without one therefore produces deterministic downstream absence
(HTTP 404). The descriptor's canonical page set and existing dispatcher remain
the authority. Case and underscore aliases, path/query/fragment/traversal or
encoded-separator variants, foreign-component pages, reserved dispatcher
names (`descriptor`, `entities`, `data`, `aggregates`, `views`), and undeclared
pages are absent from descriptor projection. Admin does not fall back to
another page or invoke an alternate route.

## Evidence and scope

This design promotes the closed boundaries recorded by [Phase 60.1](../phase/phase-60.1.md)
through [Phase 60.7](../phase/phase-60.7.md), which consume the closed
[Phase 58.9 resource boundary](../phase/phase-58.9.md) and [Phase 59.10
knowledge closure](../phase/phase-59.10.md). The exact executable mapping and
acceptance obligations are in the [Component Admin specification](../spec/component-admin.md).

No code, schema, resolver, Help contract, route wiring, CLI parser,
authorization policy, runtime lifecycle, publication, or deployment behavior
is changed by this documentation promotion.
