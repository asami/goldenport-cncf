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

## Typed Client Model

The client model is owned by `org.goldenport.cncf.mcp.client` and is separate
from the JSON/Circe models used by the MCP server adapter.

- `McpClientServerSet` contains one logical server-set identity, unique logical
  servers, and positive execution limits.
- `McpClientCatalog` contains only tools belonging to those servers and orders
  complete server/tool identities deterministically.
- `McpInputSchema` represents recursive object, array, scalar, null, and
  unconstrained input shapes without retaining JSON Schema wire objects.
- `McpValue` represents recursive provider-neutral invocation/result values.
- `McpClientCall` carries one admitted tool identity and object arguments.
- `McpClientResult` carries typed text or structured content. A remote MCP
  `isError` result is mapped to `Consequence.Failure`, not retained as a
  successful result flag.
- `McpClientDiagnostic` carries only a typed diagnostic kind, bounded logical
  reason, and optional pre-redacted bounded summary.

Arguments and results remain semantic values rather than encoded byte arrays.
The selected transport measures their encoded size and enforces the server
set's input/output byte limits before those values cross the transport policy
boundary.

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

Stdio, legacy SSE, arbitrary subprocess execution, arbitrary HTTP calls, and
filesystem transport are outside the initial boundary. Adding another
transport requires a separate ExtensionPoint and runtime admission policy; it
does not broaden the consumer service contract.

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

Transport resources are runtime-owned and participate in runtime shutdown.
Cancellation and timeout must not leave an in-flight call or transport resource
untracked.

## Consumer Responsibility

A consumer Component translates domain behavior to admitted tool usage. It may
adapt the admitted catalog to a provider-neutral domain decision, but it must
not expose MCP infrastructure controls as ordinary Operation parameters.

AI-provider function-definition serialization, model continuation loops,
prompt construction, and agent scheduling belong to Textus AI or another
consumer. They are not MCP transport responsibilities.
