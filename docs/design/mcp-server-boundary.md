# MCP Server Boundary

## Purpose

This document defines the CNCF MCP server as an external Streamable HTTP
projection over runtime-admitted CNCF Operations. It complements the opposite
remote-client boundary in `docs/design/mcp-client-boundary.md` and the
in-process source in `docs/design/internal-operation-tool-boundary.md`.

The corresponding normative requirements are defined in
`docs/spec/mcp-server-boundary.md`.

## Shared Protocol Revision

Server and client use one shared protocol-revision model. The initial supported
set is:

- `2025-11-25` (canonical and preferred);
- `2025-06-18`;
- `2025-03-26`.

Initialize validates `initialize.params.protocolVersion` and echoes the exact
supported revision. Missing, malformed, or unsupported revisions produce a
bounded JSON-RPC protocol failure. The server does not invent a newer revision.

## Streamable HTTP Lifecycle

The normative lifecycle is:

1. `initialize` is a JSON-RPC request and receives HTTP `200` with one JSON-RPC
   response.
2. `notifications/initialized` is a JSON-RPC notification without `id` and
   receives HTTP `202 Accepted` with no JSON-RPC response body.
3. Later requests carry `MCP-Protocol-Version`.
4. A request receives HTTP `200` with a matching JSON-RPC response; an accepted
   notification does not receive a JSON-RPC response.

The initial CNCF server may remain stateless and omit `Mcp-Session-Id`. In that
mode it validates each post-initialize protocol header as a member of the
shared supported set; it does not claim continuity with a retained session.
Stateful session correlation requires a later explicit design.

## Adapter Outcomes

The JSON-RPC adapter distinguishes:

- a response body for a request;
- an accepted notification with no response body; and
- a bounded protocol failure.

The HTTP route maps those typed outcomes to HTTP status and content type. A
string-only adapter result cannot represent this lifecycle correctly.

Request-shaped protocol failures retain a bounded JSON-RPC error body and HTTP
`200`. A rejected notification receives HTTP `400` with no JSON-RPC body; an
accepted `notifications/initialized` notification alone receives HTTP `202`.
The presence of the `id` member determines request shape, including `id: null`.

## Operation Publication And Invocation

The server publishes only MCP-ready Operations admitted by assembled runtime
policy. Generic Operation identity, display metadata, and input schema may be
provided by the internal provider-neutral definition model. `McpToolCatalog`
owns only the MCP JSON projection and MCP publication filtering.

`tools/call` invokes the selected Operation through normal Subsystem execution.
It does not create a separate authorization, transaction, diagnostics, or
error model. Remote MCP client catalogs are never republished by this server.

An Operation failure remains a JSON-RPC result rather than a protocol error:
`isError` and its one legacy `Conclusion.show` text block retain their existing
shape. The adapter additionally projects only an explicitly present
`Conclusion.status.appStatus` to
`structuredContent.error.appStatus`; an explicitly empty value is preserved and
an absent value produces no `structuredContent`. Reason facets, message
interpretation, application/detail codes, raw conclusions, and diagnostics are
not transport inputs for this projection.

## WebSocket Status

The existing WebSocket `/mcp` route is a compatibility transport over the same
adapter. It is not normative for new development, does not define a second
execution model, and must not receive lifecycle features that diverge from
Streamable HTTP. Streamable HTTP POST is the canonical server transport.

Because WebSocket has no Streamable HTTP protocol-version header, its explicit
compatibility context does not claim initialize-session continuity. It reuses
the same request execution adapter and suppresses notification response frames.

WebSocket compatibility and removal may be handled in a later migration slice.
Until then, documentation and introspection must not advertise it as an
equivalent lifecycle-capable transport.

## Diagnostic Safety

Default server diagnostics may retain bounded protocol method, admitted
Operation identity, status, duration, outcome, and structured failure
classification. They do not retain arguments, results, endpoint/host data,
headers, credentials, URLs passed to tools, or raw JSON-RPC bodies.
