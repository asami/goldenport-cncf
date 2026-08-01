# Phase 56 Resource SubComponent Phase Split and Planning

Date: 2026-07-31

Status: planning decision record

## Context

The prior Phase 56 plan combined:

- Component documentation and source content;
- Documentation and SourceCode SubComponent mechanics;
- repository publication and resolution;
- development composition;
- Help and AI access;
- Textus CBD Support and Textus BoK integration; and
- later Component Admin consumption.

The Resource SubComponent mechanism is independently large. It introduces
logical/physical identity, multi-artifact release completeness, repository
transactions, cache and resolution policy, integrity, access control,
provenance, lifecycle, operation-mode policy, and downstream consumer APIs.

Implementing those mechanics inside the Documentation/AI phase would produce a
cross-repository stage too large to review, validate, and diagnose coherently.

## Decision

Make Resource SubComponent foundation the earlier independent Phase 56.

Move Component Documentation and AI Knowledge Integration to Phase 57 so it
consumes the completed Resource SubComponent resolver.

Make Component Admin and Documentation Visibility an independent Phase 58
after Phase 57. Admin consumes both the Phase 56 composition/provenance model
and Phase 57 knowledge/model manifests.

Shift the existing later plans:

```text
Phase 56  Component Resource SubComponent Foundation
Phase 57  Component Documentation and AI Knowledge Integration
Phase 58  Component Admin and Documentation Visibility
Phase 59  Information CML Runtime Canonicalization
Phase 60  Web Session CSRF Unification
```

## Resource SubComponent Meaning

The correct term is SubComponent, not Subsystem.

Documentation and SourceCode SubComponents are non-executable resource
artifacts. They do not participate as Components, Componentlets, Subsystems,
services, operations, or capabilities.

The initial roles are Documentation and SourceCode. Phase 56 builds a common
resource mechanism with a closed initial role vocabulary rather than two
unrelated special-case loaders.

## Dependency Direction

The dependency direction is:

```text
Phase 56 Resource SubComponent resolver/provenance
    -> Phase 57 Help/AI knowledge access and content packaging
    -> Phase 58 Component Admin presentation and management
```

Help and Admin use the same resolved resource inventory, content access, state,
integrity, and provenance APIs. Neither may scan physical archive/repository
layouts independently.

## Documentation and Source Responsibilities

Phase 56 owns:

- physical artifact composition;
- identity and parent/release binding;
- publication completeness;
- repository and cache mechanics;
- resolution precedence;
- access and integrity;
- operation-mode resource policy;
- lifecycle, concurrency, diagnostics, and provenance; and
- downstream read-only consumer APIs.

Phase 57 owns:

- User Guide and Reference Manual content;
- SmartDox, HTML/PDF, Scaladoc, model, diagram, Help, and AI resources;
- authored and generated source selection;
- managed-source normalization and debugging completeness;
- Documentation/SourceCode content manifests and packaging inputs;
- `ComponentDevelopmentContext`;
- Help, CBD Support, BoK, and AI integration; and
- use of Phase 56 resources through the common resolver.

Phase 58 owns:

- Component Admin information architecture;
- composition and SubComponent state display;
- effective configuration and provenance display;
- runtime instance, Service, Operation, SPI, Capability, Entity, Powertype,
  StateMachine, Value, Datatype, datastore, collection, health, and lifecycle
  visibility;
- authorized management actions; and
- navigation to Phase 57 Documentation resources.

## Runtime Independence

Repository completeness does not mean every resource is installed at runtime.
Production may activate the primary CAR without fetching Documentation or
SourceCode. Help and Admin report actual resource availability, and authorized
content access may resolve permitted resources on demand.

Production never automatically fetches source.

## Source Evidence

SourceCode SubComponent content must eventually contain the admitted evidence
needed for reproduction, investigation, and debugging. Build collection paths
such as `target/scala-*/src_managed/**` are inputs, not artifact paths.
Normalized generated-source content belongs under stable logical paths;
transient build state does not.

Phase 56 provides the artifact mechanics. Phase 57 implements the content and
generation policy.

## Consequence

Phase 56 can close on a generic, executable Resource SubComponent foundation
using deterministic fixtures. Phase 57 then validates real Documentation and
SourceCode production without simultaneously inventing repository semantics.
Phase 58 can build Admin behavior on stable resource and knowledge contracts.
