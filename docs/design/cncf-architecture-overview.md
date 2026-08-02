# CNCF Architecture Overview

Status: draft

This document provides an overview of the CNCF runtime command architecture relevant to CLI and introspection.

## Layers

CLI
  -> Command Dispatch
  -> Selector Resolution
  -> Runtime Model
  -> Introspection
  -> Projection

## CLI Layer

Responsibilities:

- argument normalization
- help aliases (`--help`, `-h`)
- command help routing
- selector help rewrite for `cncf command`

## Runtime Model

Subsystem
  Component
    Service
      Operation

## System Topology and Deployment

The conceptual runtime topology is:

```text
System = N SystemNode
SystemNode = N Subsystem
Subsystem = N Component
```

A `SystemNode` is a logical deployment, communication, and shared-runtime-
resource node in one `System`. A `Subsystem` is the stable application-runtime,
execution, and binding boundary hosted by a SystemNode. A `Component` is
installed and executed inside its owning Subsystem.

These conceptual identities are independent of their current process and
container placement. The supported default deployment is currently:

```text
1 SystemNode = 1 Subsystem = 1 JVM = 1 container
1 machine node = N containers/JVMs/SystemNodes
```

The one-to-one default is an operational constraint, not an identity rule.
The architecture continues to permit one SystemNode and its JVM to host
multiple Subsystems without changing Subsystem identity or ownership
semantics. A Docker container is a deployment envelope, and a machine node is
physical or virtual compute capacity; neither becomes a Subsystem identity.

Managed connection pools are shared runtime infrastructure owned by the
SystemNode. A Subsystem owns its logical datastore binding and a lease on the
SystemNode-managed pool; Components and UnitOfWork-scoped operations borrow
connections through that binding. The pool cardinality is:

```text
(system node runtime identity, canonical datastore identity) -> one managed pool
```

Subsystems in the same SystemNode may share a pool only when their fully
resolved canonical datastore identities are equal. Different SystemNodes never
share a pool merely because their datastore definitions are equal. The pool is
not a JVM-global, container-global, or machine-global singleton detached from
SystemNode identity. SystemNode shutdown owns deterministic pool closure;
Subsystem shutdown releases its binding but never directly closes a node-owned
pool. Any zero-binding reclamation policy remains owned by the SystemNode.

The deployment decision history is recorded in
`docs/journal/2026/08/2026-08-02-system-node-subsystem-jvm-deployment.md`.

Component runtime capabilities for reusable component code are defined in
`docs/design/component-runtime-boundary-capabilities.md`. The corresponding
behavioral contract is
`docs/spec/component-runtime-boundary-capabilities.md`.

Long-lived component/runtime-owned service lifecycles are defined in
`docs/design/managed-service-container-runtime.md`. They are distinct from the
operation-scoped Process Execution boundary and from legacy one-shot Docker
adapters.

Provider-neutral remote MCP tool consumption is defined in
`docs/design/mcp-client-boundary.md`. It uses runtime-owned Port/ExtensionPoint
wiring and remains separate from the MCP server projection that publishes CNCF
Operations.

Internal Operation tools and the MCP server are separate boundaries. The
in-process source is defined in
`docs/design/internal-operation-tool-boundary.md`; the external server
lifecycle is defined in `docs/design/mcp-server-boundary.md`. Internal tool
invocation never loops back through `/mcp`, while the server remains an adapter
over normal Subsystem Operation execution.

Provider-neutral automatic operation facts, explicit Corpus/Experiment
admission, and application supplemental capture are defined in
`docs/design/operation-evaluation-capture.md`. The corresponding behavioral
contract is `docs/spec/operation-evaluation-capture.md`. Capture follows normal
operation authorization and ActionCall/UnitOfWork execution; Corpus,
Experiment, and observability do not become alternate operation authorities.

## Subsystem Construction

Subsystem construction is performed from resolved Components.

The construction path is:

1. Resolve the subsystem descriptor from explicit descriptor configuration or
   subsystem name.
2. Convert descriptor component bindings into repository discovery parameters.
3. Discover matching Components from configured repositories.
4. Add built-in Components unless the descriptor excludes them.
5. Collapse duplicate Component names with deterministic assembly selection.
6. Add the resulting Components into the Subsystem-owned ComponentSpace.
7. Rebuild the OperationResolver and assembly/security wiring views from the
   installed Components.

The Subsystem owns Component installation into subsystem scope. Components own
their internal port/binding installation. Once a Component is added to
ComponentSpace, its protocol services become visible through
OperationResolver.build and can be selected by the runtime invocation path.

Duplicate Component names are handled before ComponentSpace installation.
AssemblyReport.selectPreferred determines the selected Component, and the
dropped candidate is recorded as an assembly warning for admin and diagnostic
surfaces.

Binding lifecycle is intentionally separated:

- Component port/binding installation happens inside the owning Component.
- Subsystem construction installs Components into subsystem scope.
- Runtime execution uses the rebuilt OperationResolver and does not reinstall
  bindings during invocation.

## Selector Resolution

Selector resolution is deterministic and follows precedence:

1. subsystem meta
2. component meta
3. service meta
4. operation invocation

## Introspection Layer

Primary introspection namespace:

meta.*

Endpoints currently exposed:

meta.help
meta.describe
meta.components
meta.services
meta.operations
meta.schema
meta.openapi
meta.mcp
meta.statemachine
meta.tree
meta.version

## Projection Layer

Projection converts runtime model into response representations for CLI and API
surfaces, preserving a single source of structural truth.
