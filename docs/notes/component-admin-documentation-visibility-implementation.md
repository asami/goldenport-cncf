# Component Admin and Documentation Visibility Implementation Proposal

status = proposed, non-normative
date = 2026-07-31
phase = Phase 60

This note is the provisional implementation plan for Phase 60. It must not be
treated as the final specification. Verified behavior is promoted to
`docs/design/component-admin.md` and `docs/spec/component-admin.md` after
implementation.

## Responsibility

Component Admin is the operator-facing projection of one loaded Component and
its Subsystem context. It combines authoritative information already owned by
the runtime:

```text
Phase 55 ConfigurationBindingCollection
        +
Phase 58 ResolvedComponentResources
        +
Phase 59 ComponentKnowledgeManifest / ComponentModelManifest
        +
runtime lifecycle, health, dependency, datastore, and authorization state
        =
ComponentAdminView
```

Admin does not discover truth by walking files. Every field retains its
authority and provenance.

## Identity Axes

The provisional view distinguishes:

- Component class;
- logical Component release;
- loaded Component instance;
- Subsystem class and instance;
- implicit Component Subsystem;
- primary execution artifact;
- Documentation SubComponent; and
- SourceCode SubComponent.

Selecting one axis must not silently select another instance or version.

## Information Groups

The initial Admin view groups:

1. identity and version;
2. effective configuration and binding provenance;
3. resource composition and availability;
4. Service, Operation, SPI, capability, and dependency contracts;
5. Entity, Powertype, StateMachine, Value, Datatype, schema, relationships,
   class diagrams, and state diagrams;
6. lifecycle, health, runtime, and ClassLoader state;
7. datastore, schema, collection, Entity ID, and collection ID state;
8. documentation, Help, Scaladoc, source availability, examples, and
   troubleshooting navigation; and
9. explicitly admitted management actions.

## Help and Admin Boundary

Help is optimized for learning and use by humans and AI. Admin is optimized
for inspecting and operating a concrete runtime context.

Both consume the Phase 58 resource resolver and Phase 59 knowledge/model
manifest. Help and Admin may link to each other, but neither owns a second
artifact scanner, repository client, or documentation generator.

## Management Boundary

Visibility is read-only by default. A management control appears only when:

- a concrete management Operation is registered;
- the current caller is authorized;
- the target instance and lifecycle preconditions are exact;
- inputs are validated;
- audit evidence is recorded; and
- retry/idempotency semantics are explicit where needed.

A visible Service, Operation, resource, or model element does not itself grant
invocation or management authority.

## Operation Mode

Standalone and multi-user differences are resolved through Subsystem
configuration and `ExecutionContext`. Component implementation and the Admin
view model must not branch on mode. Admin may display the effective context
and its provenance without turning it into Component-domain policy.

## Closure

At Phase 60 closure this note becomes historical. The accepted Admin identity,
view model, authorization, management, and projection contracts must exist in
design/specification and executable evidence.
