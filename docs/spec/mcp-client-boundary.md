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

A consumer Component MUST declare its logical server-set requirements through
an `McpClientSocket` installed as a `Component.Port` input. Runtime assembly
MUST resolve every declared requirement through the runtime-owned registry
before installing any service in that socket. If any requirement is unavailable,
socket installation MUST fail atomically and MUST NOT leave a partially
installed service set.

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

`McpClientSocket` MUST expose only its declared logical server-set identities
and the services installed for those identities. It MUST NOT expose or accept
endpoint, credential, header, transport, provider-wire, or caller variation
configuration. A service lookup before successful runtime installation MUST
fail structurally.

Transport selection MUST use the separate `McpClientTransportPortApi` and
canonical Port/ExtensionPoint wiring before the runtime registry is created.
The consumer-facing service MUST NOT expose that transport binding.

Tool calls MUST execute through an explicit `McpClientInvocation` created by
`McpClientService.withInvocation`. Call-count and concurrency limits apply to
that invocation scope, not to the lifetime of the installed service. Catalog
discovery MAY be cached by the service and MUST NOT consume the tool-call
budget. A failed transport call consumes one admitted call; a denied tool or
invalid input rejected before admission does not. Closing one invocation MUST
not exhaust the budget of a later invocation.

## Server Publication Separation

The existing CNCF MCP server projection MUST continue to publish MCP-ready CNCF
Operations through `McpToolCatalog`, `McpProjection`, and
`McpJsonRpcAdapter`.

Remote MCP client catalogs MUST NOT be added to `meta.mcp`, converted into CNCF
Operation identities, or executed through `McpJsonRpcAdapter`. Client and
server types MAY share protocol vocabulary only when that reuse does not merge
their ownership, admission, or execution boundaries.

The shared revision set and server-side initialize/notification lifecycle MUST
follow `docs/spec/mcp-server-boundary.md`. Internal Operation tools MUST follow
`docs/spec/internal-operation-tool-boundary.md` and MUST NOT use the MCP client
or server transport as an in-process execution path.

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

The initial Streamable HTTP credential policy supports runtime-owned Bearer
authentication only. A server transport configuration MAY retain an opaque
`SecretReference`; it MUST NOT retain credential material. The transport
provider MUST receive the runtime-internal secret resolver separately from the
consumer Port and MUST resolve the reference immediately before constructing
the HTTP request. A configured reference without a resolver, an unresolved
reference, malformed UTF-8 material, or material outside the admitted Bearer
token grammar MUST fail before HTTP exchange. Neither the reference locator nor
the resolved material may enter diagnostics, typed client values, or consumer
service methods.

The Streamable HTTP transport MUST apply the configured timeout to initialize,
catalog, and tool-call requests. It MUST reject an oversized serialized
`tools/call` request before HTTP exchange and MUST bound every response before
protocol decoding. Initialization and catalog protocol traffic do not consume
the tool input-byte budget. Limit failures MUST use `Cause.Kind.Limit` and
structured `Policy`, `Reason`, `Limit`, and `Actual` facets.
Configured input/output byte ceilings MUST also fit the runtime's bounded byte
buffer representation and MUST fail configuration deterministically otherwise.

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

The consumer-side `McpClientService` / `McpClientInvocation` boundary MUST emit
one `mcp-client:catalog` or `mcp-client:invoke` CallTree span and one
`mcp-client.invocation` runtime metric for each attempted operation. Catalog
loading used internally by invocation MUST NOT create a duplicate catalog span.
Because one invocation permits bounded concurrent calls, MCP spans MUST be
added as concurrency-safe completed spans and MUST NOT leave a shared mutable
CallTree stack frame open across transport execution.
The invoke span and metric MAY contain only the bounded server-set, server, and
tool identities plus outcome, elapsed time, status, and a classification
projected by `ConclusionDiagnostics`.

They MUST NOT contain endpoint URLs, credential values or references, request
headers, raw arguments, raw results, provider payloads, or transport bodies.
Transport error text MUST be redacted or mapped to structured diagnostics
before it crosses the provider-neutral boundary.

## External Definition Import

Codex MCP configuration MAY be used as an operator-selected definition source.
The importer MUST consume a generic decoded `Record` through an isolated
adapter; the canonical MCP client model MUST NOT depend on TOML or Codex model
types.

An import MUST select definitions by exact source name and MUST read only an
absolute HTTP(S) `url` from each selected definition. Unselected definitions
MUST NOT affect the result. A selected definition containing command,
arguments, environment, raw-header, embedded credential, or unsupported
transport configuration MUST fail before a transport provider is created.

Every import MUST receive a separate CNCF policy overlay containing the target
server-set identity, exact non-empty tool allowlist for each selected server,
positive client limits, and any opaque CNCF credential reference. Source-side
tool policy, timeout, credential, and transport authority MUST NOT broaden that
overlay. Imported source names MUST normalize deterministically to logical MCP
server identities, and normalization collisions MUST fail.

The runtime MUST activate a successful import through a runtime-owned assembly
that creates the transport provider and client registry, installs only
normalized services into consumer sockets, and closes the registry with the
runtime lifecycle. Import or policy failure MUST occur before transport
allocation. The import adapter, source record, and import result MUST NOT be
available to consumer components.

## Builtin Operation Tool Contract

Builtin local tools MUST execute as normal CNCF Operations through
ActionCall/UoW. They MUST NOT call the local MCP HTTP endpoint, bypass normal
operation authorization, or define a parallel error or observability path.

`tool.resource.read` MUST accept one required string property `reference`,
MUST limit it to 2,048 characters, and MUST parse it through
`ResourceReference`. It MUST read only through the
`ResourceAccess` bound to the admitted execution context. The initial contract
is text-only: content MUST decode strictly with its declared charset or UTF-8,
and content larger than 1,048,576 bytes MUST fail structurally. The response
MUST contain `text`, `byteSize`, `scheme`, `mediaType`, and `charset`. It MUST
NOT expose provider roots, provider identity, credentials, raw bytes, or an
independent filesystem/network access path.

`tool.web.fetch` and `tool.web.head` MUST accept one required string property
`url`. The URL MUST pass the common resource-reference grammar and MUST use
HTTPS with no user-info, fragment, or non-default port. Every resolved address
MUST be public: any-local, loopback, link-local, site-local/private,
multicast, and IPv6 unique-local targets MUST fail before ResourceAccess. The
configured ResourceAccess host allowlist remains independently authoritative.

Web reads MUST disable redirects, use a 5-second connect timeout and 5-second
read timeout, and enforce a 1,048,576-byte transport and projection ceiling.
Only `text/*`, `application/json`, `application/xml`, and
`application/xhtml+xml` are admitted. Redirect, timeout, resolution,
content-type, and byte-limit outcomes MUST remain structured failures.
`tool.web.fetch` MUST return bounded decoded text and safe content metadata.
`tool.web.head` MUST use the same admitted GET-backed path but MUST omit the
text value. Neither Operation may accept headers, credentials, method, redirect
policy, timeout, or provider-selection parameters from its caller.

`tool.web.search` MUST accept one required string property `query` and one
optional integer property `limit`. The normalized query MUST be non-empty and
no more than 512 characters. `limit` MUST be from 1 through 10 and defaults to
10. The Operation MUST NOT accept provider, mode, engine, credential, header,
or provider-specific request properties. Provider selection and credentials
MUST remain runtime-owned behind the CNCF `web-search` SPI socket.

The provider-neutral response MUST contain at most the requested number of
items. Each item MUST contain a non-empty title of at most 512 characters and
an absolute public-network HTTPS URL of at most 2,048 characters with no
user-info, fragment, or non-default port. An optional non-empty snippet MUST be
at most 4,096 characters. URLs MUST be distinct. CNCF MUST validate provider
output before projecting `items` and `count`, and MUST NOT expose provider
identity, credentials, ranking internals, or raw provider payloads.

`tool.time.now` MUST read `ExecutionContext.clock`. Its optional `timezone`
property MUST be no more than 128 characters and MUST be either `UTC` or a
registered IANA region identifier. If omitted, the Operation MUST use the
execution-context timezone. An invalid or oversized timezone MUST return a
structured argument failure. The response MUST contain `instant`,
`epochMillis`, `timezone`, and `zonedDateTime` values derived from one clock
read.

`tool.decimal.calculate` MUST accept required string properties `operator`,
`left`, and `right`. Each operand MUST use plain base-10 decimal notation, MUST
be no more than 128 characters, and MUST have at most 128 significant digits.
The initial operator set is exactly `add`, `subtract`, and `multiply`. The
response MUST contain canonical plain decimal strings for `left`, `right`, and
`value`. Implementations MUST NOT use binary floating-point conversion or
evaluate expressions, functions, scripts, or host-language code. Division and
rounding are outside the initial contract.

All builtin tool Operations MUST be eligible for the existing MCP server
projection, including the identities `tool.resource.read`, `tool.time.now`,
`tool.decimal.calculate`, `tool.web.fetch`, `tool.web.head`, and
`tool.web.search`. MCP publication
MUST execute the same Operation implementation as CLI, REST, and internal
subsystem dispatch.

## Lifecycle Contract

Transport resources MUST be owned by the CNCF runtime and released during
runtime shutdown. Timeout or cancellation MUST leave no untracked in-flight
call or transport resource.

Registry shutdown MUST stop new service resolution before closing services.
Services MUST stop new catalog and invocation admission before cancellation,
MUST track each admitted operation until its `finally` release, MUST interrupt
and drain those operations, and MUST close their transport exactly once only
after the tracked set is empty. Transport implementations MUST respond to
thread interruption and configured timeout bounds. Registry shutdown MUST
visit services in normalized server-set order and MUST attempt cleanup of every
service even when an earlier close fails. Registry and service close operations
MUST be idempotent.

Service resolution, invocation-scope creation, catalog discovery, and tool
invocation attempted after the owning boundary starts shutdown MUST fail with
a normal structured `Conclusion` using `Policy("mcp-client.lifecycle")` and
MUST NOT invoke the transport.

Registry assembly MUST own every successfully bound transport immediately. If
a later server-set transport binding fails, it MUST close all transports
already acquired in reverse acquisition order before returning failure. A
cleanup failure MUST be combined structurally with the primary binding failure;
it MUST NOT cause a partial registry to be returned.

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
admission, stale-tool rejection before `callTool`, and payload-safe caller-side
CallTree/runtime metric evidence. It also fixes shutdown admission, cooperative
in-flight cancellation/drain, deterministic cleanup order, and idempotent
transport close, plus rollback cleanup when registry assembly fails partway.

`McpStreamableHttpTransportSpec` fixes Streamable HTTP lifecycle order,
session/protocol headers, JSON/SSE response handling, pagination, all standard
typed content blocks, redacted tool errors, endpoint form validation, and
session cleanup without a live remote service. It also fixes visible-ASCII
session admission, one-time session-expiry recovery, JSON-RPC envelope
validation, required tool-result shape validation, and pre-decode omission of
unlisted tool metadata. Runtime-owned Bearer credential references, resolver
requirements, and pre-exchange rejection of unresolved or malformed material
are fixed without exposing secret locators or values.
