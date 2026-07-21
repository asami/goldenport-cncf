# Internal Operation Tool Boundary

## Purpose

This document defines the provider-neutral CNCF boundary that exposes selected
runtime Operations as internal tools. It is an in-process runtime capability,
not an MCP transport, provider function model, or alternate operation engine.

The corresponding normative requirements are defined in
`docs/spec/internal-operation-tool-boundary.md`.

## Ownership

CNCF owns:

- admission of assembled Operations into logical internal tool sets;
- deterministic definition and input-schema projection;
- in-process invocation through the assembled `Subsystem`;
- authorization, execution limits, cancellation, diagnostics, and lifecycle;
- preservation of normal ActionCall and UnitOfWork semantics.

A consumer such as Textus AI owns:

- selection of one runtime-installed internal tool set by application purpose;
- composition with independently admitted remote MCP catalogs;
- provider-function naming and serialization;
- model continuation and tool-result feedback.

An application caller does not select an Operation, tool set, endpoint,
transport, credential, header, or provider function.

## Identity Domains

An internal Operation tool has the exact identity:

```text
component.service.operation
```

The identity is derived from the assembled runtime route and remains distinct
from remote MCP identity:

```text
server/tool
```

No CNCF adapter implicitly converts either identity into the other. A consumer
may place both definitions in one consumer-owned catalog only while retaining
their source domain and complete identity.

## Provider-Neutral Model

The internal tool model consists of:

- `OperationToolIdentity`: exact component, service, and operation segments;
- `OperationToolDefinition`: identity, bounded display metadata, and typed
  input schema;
- `OperationToolInputSchema`: provider-neutral object/array/scalar structure
  derived from canonical operation parameters;
- `OperationToolCall`: one admitted identity and structured arguments;
- `OperationToolResult`: the normal operation result projected without
  provider or MCP wire objects;
- `OperationToolSetId`: one logical runtime binding selected by assembly; and
- `OperationToolAdmission`: the exact identities and invocation limits for one
  tool set.

Generic definition and schema construction belongs to this model. JSON Schema
and MCP `tools/list` records belong to the MCP server adapter.

## Admission

Internal discovery is deny-by-default. An Operation appears only when all of
the following are true:

- its Component is an assembled primary participant;
- its complete identity resolves uniquely in the assembled Subsystem;
- the runtime-owned tool-set policy admits that exact identity; and
- the operation remains available under normal component/runtime policy.

MCP publication readiness may be an input to an explicitly selected policy,
but it is not an implicit grant to every internal consumer. Remote MCP client
allowlists do not admit internal Operations.

Admission is immutable for one invocation and carries positive limits for
call count, input size, result size, and active concurrency. Call-count,
input-size, and concurrency denial occurs before business execution; result
size is measured immediately after execution and before the result crosses the
tool boundary. Operation-defined timeout and cancellation remain authoritative
and are not replaced by a second tool-local scheduler. Unknown, duplicate,
unadmitted, malformed, or pre-execution over-limit calls fail before business
operation execution.

The runtime policy is selected through `textus.operation-tools.policy`; the
runtime and CNCF namespace aliases are
`textus.runtime.operation-tools.policy` and `cncf.operation-tools.policy`.
Its provider-neutral record shape is:

```yaml
toolSets:
  - id: builtin-tools
    operations:
      - admin.system.ping
      - tool.time.now
    limits:
      maximumCalls: 8
      maximumInputBytes: 16384
      maximumResultBytes: 16384
      maximumConcurrency: 1
```

Omitted limits receive the bounded values shown above. Generic subsystem
startup resolves every exact identity, creates one registry, and installs its
services into consumer-owned `OperationToolSocket` values. Components added
after activation receive the retained registry as part of subsystem assembly.
Activation is atomic: invalid policy or any unresolved socket requirement
leaves the registry and all affected sockets unpublished.

## Invocation

Invocation resolves the admitted identity to the normal CNCF `Request` and
calls `Subsystem.executeOperationResponse` with the caller's admitted
`ExecutionContext`. It therefore preserves:

- parameter binding and validation;
- ActionCall and internal DSL execution;
- UnitOfWork authorization and transaction behavior;
- Job/Event semantics selected by the Operation;
- `Consequence` / `Conclusion` failures;
- CallTree, metrics, timeout, cancellation, and resource cleanup.

The internal path must not call `/mcp`, create an HTTP client, invoke
`McpJsonRpcAdapter`, or synthesize remote MCP identity. A local MCP loopback is
not a fallback when internal admission or execution fails.

## Diagnostics

Default diagnostics may contain the logical tool-set id, complete internal
identity, operation kind, duration, outcome, and structured
`ConclusionDiagnostics`. They must not contain argument values, result values,
prompts, provider payloads, endpoint details, credentials, headers, or raw
request/response bodies.

## Relationship To MCP Publication

The MCP server projects an admitted Operation definition into MCP JSON and
accepts remote calls through its own transport boundary. It may reuse generic
Operation definition/schema construction, but it remains an external adapter.
The internal source neither depends on nor routes through that adapter.
