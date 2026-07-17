# Component Runtime Boundary Capabilities

Status: normative design

## Scope

Reusable CNCF components consume operational inputs only through capabilities
bound to `ExecutionContext`. This design covers component-observable execution
time, declared configuration, opaque secret references, admitted read-only
resource trees, and Process Execution tree materialization.

The behavioral contract is defined by
`docs/spec/component-runtime-boundary-capabilities.md`.

## Capability Ownership

`ExecutionContext` is the component-facing capability boundary. A component
MUST NOT obtain operational values from ambient JVM, operating-system,
filesystem, network, or process state.

The runtime owns:

- configuration source selection, precedence, parsing, and provenance;
- secret value resolution and authorization;
- physical resource-tree root selection and provider binding;
- resource-tree admission, bounded snapshot construction, and WorkArea
  materialization; and
- Process Execution capability admission, driver selection, WorkArea lifecycle,
  and cleanup.

A component owns only its declared configuration keys, logical resource-tree
requests, process capability request, and domain interpretation of returned
values.

## Existing Capability Foundations

The canonical component time authority is the clock already bound to
`ExecutionContext`. Components use the protected internal DSL time operations;
they do not construct or select host clocks.

`ResourceAccess` remains the canonical boundary for one logical resource
content read. It does not enumerate directories or represent a resource tree.

Process Execution retains its admitted `ProcessExecutionRequest`, bounded
fixed input files, WorkArea-relative paths, declared outputs, runtime-owned
driver, and UnitOfWork resource lifecycle. A fixed input file is supplied
bytes. It is not a resource-tree capability.

## Declared Configuration And Secrets

The component configuration contract consists of declared typed keys,
component-scoped resolution access, resolved-value provenance, and structured
`Consequence` failures. Runtime, subsystem, and component configuration are
resolved by the runtime before component access. Request properties, operation
parameters, and arbitrary property lookup do not override a protected declared
configuration value.

Configuration classification is explicit:

- public values may be returned as typed component configuration values;
- confidential values may be consumed only through a runtime-approved safe
  use; and
- secrets cross the component boundary only as opaque `SecretReference`
  values.

No component-facing API resolves a `SecretReference` to credential bytes or
text. Authorized runtime providers and Process Execution drivers resolve secret
values only at their owned execution boundary.

## Resource Trees

A resource tree is a named, admitted, read-only logical capability. Its public
contract consists of a logical identity, a reference, limits, deterministic
entries, and an immutable snapshot. It never exposes a physical root, host
`Path`, provider handle, credential, or ambient directory traversal API.

The runtime binds logical tree identities to providers and physical roots.
Providers enforce traversal, symlink, depth, file-count, per-file, and
aggregate-byte policy before a snapshot becomes visible to a component.
Deterministic ordering is part of the snapshot contract.

## Process Execution Materialization

An admitted resource-tree snapshot may be represented as a distinct Process
Execution tree input. The runtime materializes that input under a
WorkArea-relative target after Process Execution admission. A component or
request never supplies an arbitrary host path as a Process Execution input.

Tree admission and Process Execution admission are separate checks. Process
capability policy declares which logical tree identities are accepted. Request
limits may narrow runtime and program limits but never widen them. WorkArea and
UnitOfWork cleanup apply equally to fixed input files and materialized tree
inputs.

## Observability And Testability

Diagnostics record only safe structural metadata: declared configuration key
identity and provenance category, secret classification, logical tree identity,
snapshot counts and limits, Process Execution capability, and WorkArea-relative
materialization target. They do not record configuration values, secret
references or values, physical roots, tree content, or host paths.

Every capability has deterministic in-memory or fake runtime support for
executable specifications. Production secret providers, physical-tree
providers, and external Process Execution drivers remain runtime-owned
implementations of the same contracts.
