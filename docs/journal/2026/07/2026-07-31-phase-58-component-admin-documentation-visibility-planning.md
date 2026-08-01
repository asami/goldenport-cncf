# Phase 58 Component Admin and Documentation Visibility Planning

Date: 2026-07-31

Status: planning decision record

## Context

The original documentation plan included Admin consumption as a downstream
detail. Discussion established that Admin is substantial work in its own
right. It must combine Component configuration, resource composition, model
metadata, runtime state, datastore state, documentation navigation, security,
and authorized management.

Keeping that work inside Documentation/AI would mix two different acceptance
goals:

- whether Component knowledge is complete and retrievable; and
- whether a concrete loaded Component is inspectable and manageable.

## Decision

Create an independent Phase 58:

```text
Phase 56  Component Resource SubComponent Foundation
Phase 57  Component Documentation and AI Knowledge Integration
Phase 58  Component Admin and Documentation Visibility
Phase 59  Information CML Runtime Canonicalization
Phase 60  Web Session CSRF Unification
```

Phase 58 consumes Phase 55 configuration provenance, Phase 56 resource
resolution/provenance, and Phase 57 knowledge/model manifests.

## Shared Consumer Contract

Help and Admin use the same Resource SubComponent resolver. Admin must not
scan CARs, repositories, caches, development directories, documentation
archives, or source trees independently.

Help remains the human/AI knowledge entry point. Admin is the operator/runtime
surface. Admin links to exact Documentation resources while adding instance,
configuration, health, lifecycle, datastore, and authorized management
context.

## Visibility Requirement

A Component is not adequately represented when Admin and Documentation cannot
make its constituent elements visible. The Admin plan therefore covers:

- primary, Documentation, and SourceCode artifact composition;
- typed configuration values and provenance;
- Service, Operation, SPI, capability, and dependency contracts;
- Entity, Powertype, StateMachine, Value, Datatype, and diagrams;
- lifecycle, health, ClassLoader, datastore, schema, and collection state;
- exact Help/manual/Scaladoc/source navigation; and
- separately authorized management actions.

## Consequence

The former Phase 57 and Phase 58 plans move to Phase 59 and Phase 60.
Documentation/AI Phase 57 supplies knowledge; Admin Phase 58 presents it in
the context of a loaded Component. Final design/specification for each phase
is written only after its implementation is verified.
