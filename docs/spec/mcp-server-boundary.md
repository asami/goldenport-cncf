# MCP Server Boundary Specification

## Scope

This specification defines the CNCF MCP server's Streamable HTTP protocol and
its projection of admitted CNCF Operations.

## Revision Contract

The MCP server and Streamable HTTP client MUST use one shared protocol-revision
model. The initial supported revisions MUST be `2025-11-25`, `2025-06-18`, and
`2025-03-26`, with `2025-11-25` as the canonical preferred revision.

`initialize.params.protocolVersion` MUST be present, string-shaped, and in the
supported set. The server MUST echo the exact accepted revision. Missing,
malformed, and unsupported revisions MUST fail with bounded structured
protocol diagnostics and MUST NOT silently select `2026-03-19` or another
unrequested revision.

## HTTP Lifecycle Contract

An `initialize`, `tools/list`, or `tools/call` JSON-RPC request MUST receive
HTTP `200`, `application/json`, and one JSON-RPC response with the matching
request id.

`notifications/initialized` MUST be accepted only when it is a JSON-RPC 2.0
notification with no `id`. An accepted initialized notification MUST receive
HTTP `202 Accepted` with no JSON-RPC response body. A request-shaped initialized
message MUST fail as an invalid lifecycle message.

Post-initialize requests and notifications MUST carry
`MCP-Protocol-Version`. A stateless server MUST validate that value against the
shared supported set and MAY omit `Mcp-Session-Id`; it MUST NOT claim retained
session negotiation. A stateful server requires a separate explicit session
contract.

The adapter MUST expose typed response, accepted-notification, and
protocol-failure outcomes so that the HTTP route can preserve these status and
body semantics. It MUST NOT synthesize JSON-RPC responses for accepted
notifications.

A request-shaped protocol failure MUST receive HTTP `200` with one bounded
JSON-RPC error response. A rejected notification MUST receive HTTP `400` with
no JSON-RPC body. The presence of an `id` member, including `id: null`, MUST
make a message request-shaped. Duplicate, missing, malformed, and unsupported
post-initialize protocol-version headers MUST fail deterministically.

## Publication And Execution Contract

The server MUST publish only uniquely identified, MCP-ready Operations admitted
by assembled runtime policy. Tool identity MUST remain the exact
`component.service.operation` identity. Remote MCP client tools MUST NOT be
published, converted into Operation identity, or invoked through this server.

Generic Operation definition and typed schema construction MAY be shared with
the internal Operation-tool model. MCP JSON Schema and JSON-RPC records MUST
remain server-adapter concerns.

An admitted `tools/call` MUST execute through the normal Subsystem operation
path and preserve ActionCall, UnitOfWork, authorization, transaction,
`Consequence` / `Conclusion`, diagnostics, timeout, cancellation, and resource
cleanup semantics.

When an admitted Operation returns `Consequence.Failure`, `result.isError` MUST
remain `true` and `result.content` MUST remain exactly one legacy text block
whose `text` is the existing `Conclusion.show` value. If and only if
`Conclusion.status.appStatus` is present, including an explicitly empty string,
the response MUST additionally contain exactly
`result.structuredContent = { "error": { "appStatus": value } }`. When it is
absent, `structuredContent` MUST be absent. The projection MUST NOT expose a
Reason facet, message-derived code, detail code, application code, raw
Conclusion, diagnostics, or another failure payload.

## Protocol Failure Contract

Invalid request, method-not-found, invalid-params, and internal failures MUST
remain bounded JSON-RPC errors for request messages. Notifications MUST NOT
receive JSON-RPC error responses. Protocol failures MUST NOT include raw
request bodies, arguments, results, endpoint data, headers, credentials, or
provider payloads.

## Transport Contract

Streamable HTTP POST `/mcp` MUST be the normative transport. The existing
WebSocket route MAY remain as compatibility behavior over the same Operation
execution adapter, but MUST NOT define a second execution model or be
advertised as lifecycle-equivalent. New lifecycle behavior MUST target
Streamable HTTP first.

The WebSocket compatibility context MAY omit Streamable HTTP header validation,
but it MUST NOT claim retained negotiation and MUST NOT emit a response frame
for a notification outcome.
