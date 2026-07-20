# MCP Client Boundary Specification

## Scope

This specification defines the CNCF contract for consuming remote MCP tools.
It does not define the existing CNCF MCP server projection.

## Ownership Contract

The CNCF runtime MUST own MCP server-set definitions, endpoint admission,
transport selection, credential references, tool admission, resource limits,
and transport lifecycle.

An application caller MUST NOT supply or override an MCP endpoint, transport,
server, server set, credential, header, tool allowlist, or raw protocol payload.
A consumer Component MUST receive its MCP client service through a resolved
`Component.Port`.

The resolved service MUST be bound to one admitted server-set policy. The
service MUST NOT expose endpoint, credential, header, transport, JSON-RPC, or
HTTP configuration through its public consumer contract.

## Consumer Contract

The provider-neutral service MUST support:

- discovery of a typed catalog containing only admitted tools; and
- invocation of one admitted tool using typed input and returning a typed
  result or `Consequence.Failure(Conclusion)`.

A tool invocation MUST use an identity obtained from the admitted catalog. The
runtime MUST reject an unknown, stale, or denied identity before invoking the
transport. The consumer contract MUST NOT expose provider wire records or raw
MCP request/response bodies.

Operation implementations MAY select among admitted tools as part of trusted
Component domain logic. They MUST NOT forward application-supplied MCP
infrastructure selectors or raw tool names directly to the client service.

## Port And Transport Contract

The MCP client requirement MUST resolve through canonical `PortApi` and
`Component.Port` wiring. Transport implementations MUST be selected through an
`ExtensionPoint` after runtime configuration and admission have been resolved.

The initial admitted transport MUST be Streamable HTTP. It MUST implement the
protocol interactions required for initialization, `tools/list`, and
`tools/call`. These protocol method names and wire models MUST remain behind the
transport boundary.

The initial implementation MUST NOT admit stdio, legacy SSE, arbitrary process
execution, arbitrary HTTP, or filesystem transports.

## Server Publication Separation

The existing CNCF MCP server projection MUST continue to publish MCP-ready CNCF
Operations through `McpToolCatalog`, `McpProjection`, and
`McpJsonRpcAdapter`.

Remote MCP client catalogs MUST NOT be added to `meta.mcp`, converted into CNCF
Operation identities, or executed through `McpJsonRpcAdapter`. Client and
server types MAY share protocol vocabulary only when that reuse does not merge
their ownership, admission, or execution boundaries.

## Admission Contract

Admission MUST occur before transport execution. It MUST cover:

- resolved server-set binding and endpoint policy;
- transport and credential-reference policy;
- normalized tool allowlist;
- typed input validity;
- timeout and call-count limits;
- input-byte and output-byte limits; and
- concurrency limits.

A denied or exhausted request MUST NOT invoke the transport.

## Failure Contract

The client MUST preserve distinct structured failures for:

- unknown or unavailable server-set binding;
- unknown or denied tool;
- invalid typed input;
- runtime limit exhaustion;
- transport failure;
- protocol failure; and
- remote tool failure.

Failures MUST remain normal `Consequence.Failure(Conclusion)` values. CNCF MUST
NOT introduce a parallel MCP-specific error envelope or classify failures by
parsing human-readable messages.

## Diagnostic Safety

CallTree and runtime metrics MAY contain bounded server-set identity, normalized
tool identity, operation kind, duration, outcome, and structured diagnostic
classification.

They MUST NOT contain endpoint URLs, credential values or references, request
headers, raw arguments, raw results, provider payloads, or transport bodies.
Transport error text MUST be redacted or mapped to structured diagnostics
before it crosses the provider-neutral boundary.

## Lifecycle Contract

Transport resources MUST be owned by the CNCF runtime and released during
runtime shutdown. Timeout or cancellation MUST leave no untracked in-flight
call or transport resource.

Normal executable specifications MUST use a deterministic fake transport and
MUST NOT require a remote MCP service. Optional live Streamable HTTP evidence
MUST be explicitly enabled as heavy validation.

## Executable Evidence

Later Phase 45 stages MUST provide executable specifications for typed catalog
normalization, admitted invocation, rejection before transport execution,
bounded limits, structured failures, payload-safe observability, lifecycle
cleanup, and unchanged MCP server publication behavior.
