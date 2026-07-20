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

## Typed Model Contract

Logical server-set and server identities MUST be lowercase, bounded, and
independent of endpoint or host syntax. Tool identity MUST contain one admitted
logical server identity and one bounded MCP tool name. Catalog construction
MUST reject duplicate tool identities and tools belonging to a server outside
the bound server set. Catalog ordering MUST be deterministic.

Input schemas MUST use the recursive provider-neutral `McpInputSchema` algebra.
Invocation and result values MUST use the recursive provider-neutral
`McpValue` algebra. Neither algebra may retain Circe values, JSON-RPC records,
HTTP entities, or provider-native objects.

Each configured server MUST carry an exact, normalized tool-name allowlist.
Catalog discovery MUST omit every unlisted tool before decoding its schema or
display metadata. An unlisted tool therefore MUST NOT become visible and its
malformed non-identity metadata MUST NOT deny admitted catalog entries.

An admitted invocation MUST validate required fields, additional-field policy,
recursive object/array shape, and scalar kinds against the admitted typed input
schema before `callTool`. Validation diagnostics MUST identify nested values
with escaped JSON Pointer field paths rooted at `/arguments`.

Every server set MUST carry positive timeout, call-count, input-byte,
output-byte, and concurrency limits. Input/output byte limits apply to the
transport encoding measured at the admission/transport boundary; semantic
argument and result values MUST NOT carry encoded transport bytes solely for
limit accounting.

A successful `McpClientResult` MUST contain only typed content. A remote tool
error MUST become a structured `Consequence.Failure(Conclusion)` and MUST NOT
be represented as a successful result with an error boolean.

The typed result MUST represent standard MCP text, image, audio, resource-link,
embedded text-resource, and embedded blob-resource content blocks without
retaining their wire JSON. Content annotations MUST preserve admitted audience,
priority, and last-modified values as typed metadata.

JSON object and JSON Schema property names MUST remain bounded exact strings;
the client MUST NOT impose Scala/Java identifier syntax on them or normalize
their spelling. MCP titles and descriptions MAY contain normal multiline text
whitespace, but MUST reject other control characters at the typed boundary.

Diagnostic metadata MUST use a typed diagnostic kind and bounded logical
reason. An optional display summary MUST be explicitly pre-redacted, bounded,
and free of control characters. It MUST NOT be created directly from raw
transport or remote error text.

## Port And Transport Contract

The MCP client requirement MUST resolve through canonical `PortApi` and
`Component.Port` wiring. Transport implementations MUST be selected through an
`ExtensionPoint` after runtime configuration and admission have been resolved.

The initial admitted transport MUST be Streamable HTTP. It MUST implement the
protocol interactions required for initialization, `tools/list`, and
`tools/call`. These protocol method names and wire models MUST remain behind the
transport boundary.

Every accepted JSON-RPC response MUST declare `jsonrpc: "2.0"` and match the
request identity before its result is interpreted. A successful `tools/call`
result MUST contain the required content array. Optional `structuredContent`
MUST be object-shaped. Missing or malformed required fields MUST become a
structured protocol failure rather than an empty successful result.

The transport MUST send `notifications/initialized` after successful
initialization, retain and resend an optional `Mcp-Session-Id`, send the
negotiated `MCP-Protocol-Version` on subsequent requests, accept both JSON and
SSE request responses, and follow `tools/list` cursors. Runtime shutdown MUST
attempt session DELETE when the server established a session. The initial
supported protocol revisions are `2025-11-25`, `2025-06-18`, and `2025-03-26`.

A received `Mcp-Session-Id` MUST contain only visible ASCII characters before
the client stores or resends it. When a stateful request receives HTTP `404`,
the transport MUST discard that expired session, initialize one fresh session,
and replay the interrupted logical request at most once. A repeated `404` MUST
remain a structured transport failure and MUST NOT enter an unbounded retry.

The initial implementation MUST NOT admit stdio, legacy SSE, arbitrary process
execution, arbitrary HTTP, or filesystem transports.

`McpClientPortApi` MUST bind one logical `McpClientRequirement` to one
runtime-owned `McpClientService`. `McpClientRuntimeRegistry` MUST resolve that
contract through a canonical ExtensionPoint and MUST reject an unregistered
server set. Caller-supplied `VariationSelection` infrastructure fields MUST NOT
select or replace an MCP provider or transport.

Transport selection MUST use the separate `McpClientTransportPortApi` and
canonical Port/ExtensionPoint wiring before the runtime registry is created.
The consumer-facing service MUST NOT expose that transport binding.

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

`McpClientModelSpec` fixes logical identity admission, deterministic catalog
normalization, duplicate/foreign-tool rejection, recursive schema/value
representation, positive execution limits, and bounded diagnostic metadata.

`McpClientPortSpec` fixes runtime-owned Port installation, typed discovery and
invocation through deterministic fake transport, caller selector rejection,
unbound server-set rejection, exact allowlist filtering, recursive input
admission, and stale-tool rejection before `callTool`.

`McpStreamableHttpTransportSpec` fixes Streamable HTTP lifecycle order,
session/protocol headers, JSON/SSE response handling, pagination, all standard
typed content blocks, redacted tool errors, endpoint form validation, and
session cleanup without a live remote service. It also fixes visible-ASCII
session admission, one-time session-expiry recovery, JSON-RPC envelope
validation, required tool-result shape validation, and pre-decode omission of
unlisted tool metadata.
