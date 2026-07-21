# MCP Client Boundary

## Purpose

The CNCF MCP client boundary lets a Component consume remote MCP tools through
a provider-neutral, runtime-owned service. It is distinct from the CNCF MCP
server projection, which publishes CNCF Operations as MCP tools.

## Boundary Roles

The boundary has four roles:

- the **application caller** supplies domain input to a CNCF Operation;
- the **consumer Component** implements domain behavior using an injected MCP
  client service;
- the **CNCF runtime** owns server-set binding, admission, limits, credentials,
  lifecycle, and diagnostics; and
- the **transport ExtensionPoint** implements one admitted MCP transport.

The application caller is outside the trusted MCP configuration boundary. It
cannot select or supply an MCP endpoint, transport, server, server set,
credential, header, or raw tool payload. A Component may select only a tool
from the admitted catalog exposed by its injected service. It cannot construct
a transport request or bypass runtime admission.

## Port And ExtensionPoint

The MCP client is wired through the canonical CNCF Port model described in
`component-port-wiring.md`.

- A `PortApi` resolves a Component requirement to an MCP client service bound
  to one runtime-owned server-set policy.
- `Component.Port` stores that resolved service for the consumer Component.
- A transport `ExtensionPoint` implements protocol execution after admission.
- Port resolution and transport selection happen at assembly/runtime wiring
  time, not from application request parameters.

The provider-neutral client service exposes typed catalog discovery and typed
tool invocation. The catalog contains only admitted tools. Invocation accepts
only an admitted tool identity obtained from that catalog and a typed argument
value. It does not expose endpoint, header, credential, transport, JSON-RPC,
HTTP, or provider-native payload controls.

`McpClientPortApi` resolves a logical `McpClientRequirement` to a contract
bound to one server set. `McpClientRuntimeRegistry` is the runtime-owned
ExtensionPoint behind that contract. It installs `McpClientService` in the
consumer `Component.Port`; neither the application request nor a caller-side
variation can select an infrastructure provider or transport.

A consumer declares the logical server sets it needs with an
`McpClientSocket` in its input Port. Runtime assembly asks the registry to
install that socket after server-set and transport admission are complete. The
registry resolves the complete requirement set before mutating the socket, so
an unavailable server set cannot leave a consumer with a partial catalog
surface. The socket is the consumer-facing holder; it contains no endpoint,
credential, transport, or protocol configuration.

Transport selection is a separate canonical Port binding using
`McpClientTransportPortApi`. Runtime assembly resolves that binding before it
creates the client registry. The resulting `McpClientTransport` exposes only
typed initialize, catalog discovery, and invocation operations to the runtime
service. Protocol method names and wire records remain implementation details
of the concrete transport.

## Typed Client Model

The client model is owned by `org.goldenport.cncf.mcp.client` and is separate
from the JSON/Circe models used by the MCP server adapter.

- `McpClientServerSet` contains one logical server-set identity, unique logical
  servers, and positive execution limits.
- Each logical server retains a deterministic exact tool-name allowlist. The
  Streamable HTTP transport parses only the remote tool name before admission;
  schema and display metadata are decoded only for admitted names.
- `McpClientCatalog` contains only tools belonging to those servers and orders
  complete server/tool identities deterministically.
- `McpInputSchema` represents recursive object, array, scalar, null, and
  unconstrained input shapes without retaining JSON Schema wire objects.
- `McpValue` represents recursive provider-neutral invocation/result values.
- JSON object and schema property names remain exact bounded strings rather
  than language identifiers, while titles and descriptions admit ordinary
  multiline text whitespace.
- `McpClientCall` carries one admitted tool identity and object arguments.
- Recursive typed input validation runs before `callTool`. Nested diagnostics
  use escaped JSON Pointer paths rooted at `/arguments`, so arbitrary JSON keys
  remain unambiguous.
- `McpClientResult` carries typed text or structured content. A remote MCP
  `isError` result is mapped to `Consequence.Failure`, not retained as a
  successful result flag.
- Standard tool result blocks are represented as typed text, image, audio,
  resource-link, embedded-text-resource, and embedded-blob-resource values.
  Optional audience, priority, and last-modified annotations remain typed.
  Circe/JSON values never cross this content boundary.
- `McpClientDiagnostic` carries only a typed diagnostic kind, bounded logical
  reason, and optional pre-redacted bounded summary.

Arguments and results remain semantic values rather than encoded byte arrays.
The selected transport measures their encoded size and enforces the server
set's input/output byte limits before those values cross the transport policy
boundary.

`McpClientService` owns reusable catalog discovery, while each consumer action
opens an explicit `McpClientInvocation`. The invocation owns call-count and
active-concurrency state and closes after the consumer body returns. This
prevents a long-lived installed service from being permanently exhausted and
makes the configured budget correspond to one domain invocation. Only an
admitted tool call consumes the count; failed remote execution still consumes
that admitted slot.

## Runtime Ownership

The runtime owns:

- server-set identity and its ordered server definitions;
- endpoint and transport admission;
- credential references and credential resolution;
- tool allowlists and catalog normalization;
- timeout, call-count, input-size, output-size, and concurrency limits;
- transport lifecycle and shutdown; and
- payload-safe CallTree, metrics, and structured diagnostics.

Configuration may name a server set for a Component binding. The binding
projects an admitted client service; it does not expose the underlying server
set configuration to operation callers. Credential values never enter the
provider-neutral catalog or consumer request/response models.

The initial Streamable HTTP provider models authentication as an optional
runtime-owned Bearer credential reference. Its server configuration retains
only the opaque `SecretReference`. `RuntimeSecretResolver` is injected into the
transport provider, remains unavailable through the consumer Port, and resolves
material at the final HTTP request boundary. Resolution is deliberately not
performed by the Component and the resulting `Authorization` header never
crosses into provider-neutral MCP values or diagnostics. Resolver absence,
unresolved references, invalid UTF-8, and invalid Bearer material are rejected
before exchange. Other authentication schemes require explicit later policy
types rather than arbitrary caller-supplied headers.

## MCP Server Separation

The existing MCP server surface projects MCP-ready CNCF Operations and accepts
remote JSON-RPC requests through `McpJsonRpcAdapter`.

The MCP client surface consumes remote tools. It does not reuse the server
projector's Component/Service/Operation tool identity as remote identity, route
remote calls through `McpJsonRpcAdapter`, or publish remote tools through
`meta.mcp`. Shared protocol vocabulary may be factored only when it preserves
the opposite ownership and trust directions of the two boundaries.

## Initial Transport

The first transport ExtensionPoint is Streamable HTTP. It implements
initialization, `tools/list`, and `tools/call` for runtime-admitted endpoints.
The provider-neutral Port does not expose those protocol method names or wire
records to consumers.

The initial implementation requests protocol revision `2025-11-25` and can
negotiate `2025-11-25`, `2025-06-18`, or `2025-03-26`. It sends the required
initialized notification, retains an optional `Mcp-Session-Id`, sends the
negotiated `MCP-Protocol-Version` on subsequent requests, follows paged tool
catalogs, accepts JSON and SSE responses, and attempts HTTP DELETE when a
stateful session closes. Endpoint URIs exist only in runtime transport
configuration.

The transport validates session identifiers before retention and treats a
stateful HTTP `404` as session expiry. It discards that session, performs one
fresh initialization, and replays the interrupted logical request once. It
also validates the JSON-RPC 2.0 envelope and required tool-result shape before
projecting any typed value.

When a server declares Bearer authentication, every initialize, notification,
catalog, tool-call, and session-delete request resolves the same opaque
credential reference at the HTTP driver boundary. The transport configuration
cannot embed user-info in the endpoint and the consumer cannot supply or
override authentication headers.

Every HTTP request carries the server-set timeout and output-byte ceiling. The
JDK exchange reads response streams through a bounded reader instead of an
unbounded string body handler, and the transport repeats the output check for
custom/fake exchanges. The exact UTF-8 `tools/call` request body is checked
before exchange. Initialization and `tools/list` remain control traffic and do
not consume the user tool-input budget.

Stdio, legacy SSE, arbitrary subprocess execution, arbitrary HTTP calls, and
filesystem transport are outside the initial boundary. Adding another
transport requires a separate ExtensionPoint and runtime admission policy; it
does not broaden the consumer service contract.

## Codex Definition Import

Codex configuration is an optional input format, not a provider or policy
authority. A dedicated adapter accepts the generic `Record` produced by the
configuration decoder and extracts only selected URL-based `mcp_servers`
entries. Keeping the adapter at this edge prevents TOML and externally evolving
Codex schema types from entering the typed MCP client model.

The CNCF import policy is constructed independently. It supplies the target
server-set identity, exact tool allowlists, limits, and optional opaque
credential references. The adapter combines only the source endpoint with that
policy and produces the normal `McpClientServerSet` plus
`McpStreamableHttpServerSetConfig`. Command, environment, header, embedded
credential, and unsupported transport declarations are rejected for selected
servers; unrelated source definitions are ignored. Textus AI receives only the
eventual `McpClientSocket` service and never receives this source record or the
import result.

Runtime activation composes that import result with the Streamable HTTP
transport provider and `McpClientRuntimeRegistry` inside a package-restricted
assembly owner. The owner installs normalized services into consumer sockets
and closes the registry and its transports. Definition or policy failure occurs
before transport allocation; components cannot invoke the importer or retain
the source configuration.

An operator selects the external definition file through a separate CNCF MCP
client policy descriptor. That descriptor owns the source kind/path,
server-set identity, selected Codex source names, exact admitted tools, limits,
and optional opaque credential references. The generic core Record loader
decodes both the CNCF descriptor and Codex TOML source; TOML syntax remains
outside the MCP client model and component Port.

The operator descriptor has the following normalized shape. Relative source
paths resolve against the descriptor directory. Present limit values must be
integral; malformed values fail configuration loading rather than falling back
to defaults.

```yaml
source:
  kind: codex
  path: config.toml
serverSet:
  id: research
  limits:
    timeoutMillis: 12000
    maximumCalls: 3
    maximumInputBytes: 4096
    maximumOutputBytes: 8192
    maximumConcurrency: 1
  servers:
    - sourceName: research_service
      admittedTools:
        - paper.search
      credentialReference: env://MCP_RESEARCH_TOKEN
```

## Execution And Failure Semantics

Every discovery or invocation crosses runtime admission before transport
execution. Denied requests do not invoke the transport. The boundary preserves
distinct structured failures for:

- unknown or unavailable server-set binding;
- denied or unknown tool identity;
- invalid typed input;
- exhausted runtime limit;
- transport or protocol failure; and
- remote tool failure.

The source of truth is `Consequence.Failure(Conclusion)`. Metrics and CallTree
project classifications from the structured Conclusion and never derive them
from display-message parsing.

## Observability And Payload Safety

CallTree and metrics may record bounded server-set identity, normalized tool
identity, operation kind, duration, outcome, and structured diagnostic
classification. They must not record endpoint URLs, credentials, headers, raw
arguments, raw results, provider payloads, or transport bodies.

Tracing is owned by the consumer-side MCP client service rather than by the
transport provider. Explicit catalog discovery creates
`mcp-client:catalog`; each attempted typed invocation creates
`mcp-client:invoke`. Internal catalog loading performed as part of invocation
uses the same service state without producing a duplicate catalog span. Both
spans project to `mcp-client.invocation` metrics and carry only bounded logical
identities, outcome, duration, status, and `ConclusionDiagnostics`
classification. Typed arguments and results are never summarized into these
records. Since bounded calls may overlap within one invocation, the service
adds each result as an atomic completed CallTree span after execution rather
than sharing open `enter`/`leave` stack frames across threads.

Transport resources are runtime-owned and participate in runtime shutdown.
Cancellation and timeout must not leave an in-flight call or transport resource
untracked.

The runtime registry owns shutdown and applies this order:

1. close registry admission so no new service can be resolved;
2. close each server-set service in deterministic server-set order;
3. close service admission so no new catalog or tool call can begin;
4. interrupt and drain every operation already admitted by that service; and
5. close the owned transport exactly once after the service has no in-flight
   operation.

An admitted transport operation must honor thread interruption and its bounded
timeout. Service shutdown waits for admitted operations to leave the tracked
set, and registry shutdown continues cleanup of the remaining server sets if
one service close fails. Closing a registry or service is idempotent. Calls and
service resolution attempted after shutdown return structured lifecycle
failures and do not cross the transport boundary.

Registry assembly owns each transport as soon as its ExtensionPoint binding
succeeds. If a later server-set binding fails, assembly closes all transports
already acquired in reverse acquisition order before returning the structured
binding failure. A failed assembly therefore never transfers a partial registry
or leaves its earlier transport resources orphaned.

## Consumer Responsibility

A consumer Component translates domain behavior to admitted tool usage. It may
adapt the admitted catalog to a provider-neutral domain decision, but it must
not expose MCP infrastructure controls as ordinary Operation parameters.

AI-provider function-definition serialization, model continuation loops,
prompt construction, and agent scheduling belong to Textus AI or another
consumer. They are not MCP transport responsibilities.

## Executable Evidence

`McpClientPortSpec` fixes canonical Port installation, server-set-bound catalog
discovery and invocation, rejection of caller infrastructure selection,
rejection of an unbound server set, exact allowlist filtering, recursive input
validation, and stale-tool rejection before the fake transport call boundary.
The fake transport is deterministic and does not require a remote MCP service.

`McpStreamableHttpTransportSpec` fixes initialization and notification order,
session/protocol headers, JSON and SSE responses, catalog pagination, standard
typed content blocks, redacted remote-tool failures, endpoint shape admission,
pre-decode allowlist filtering, session expiry recovery, JSON-RPC/result shape
validation, runtime-owned Bearer credential-reference admission, and session
DELETE through a deterministic HTTP exchange.
