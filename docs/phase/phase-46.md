# Phase 46 - Internal Operation Tool Source and MCP Server Interoperability

status=active
started_at=2026-07-21
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 46 Checklist](phase-46-checklist.md)

## Purpose

Complete the two tool boundaries left separate after Phase 45:

- expose policy-admitted CNCF Operations as a provider-neutral internal tool
  source without routing them through the local MCP HTTP endpoint; and
- make the CNCF MCP server projection interoperable with the CNCF Streamable
  HTTP client through one shared protocol and notification lifecycle.

The internal Operation source and remote MCP source remain independent. Textus
AI may compose their admitted definitions into one provider-facing catalog,
but CNCF does not perform provider-function composition.

## Selected Direction

- Keep internal tool identity as `component.service.operation` and remote MCP
  identity as `server/tool`.
- Derive internal tool definitions only from runtime-admitted Operations and
  invoke them through `Subsystem.executeOperationResponse`, preserving normal
  ActionCall/UnitOfWork authorization, execution context, diagnostics, and
  observability.
- Extract provider-neutral Operation-tool definition and schema handling from
  the server-specific `McpToolCatalog` surface. MCP publication becomes an
  adapter over the internal Operation definition rather than the owner of the
  generic tool contract.
- Keep internal builtin invocation in-process. A local `/mcp` callback is not
  an accepted implementation or fallback.
- Define one shared MCP protocol-revision model used by both server and client.
  The initial canonical revision is `2025-11-25`.
- Make JSON-RPC request responses and notification acknowledgements distinct
  typed adapter outcomes so the HTTP layer can return the correct status and
  body.
- Prove the external server/client direction through a real loopback HTTP
  executable specification. This verifies interoperability; it does not
  redefine the internal builtin execution path.

## Scope

- Provider-neutral internal Operation-tool identity, definition, typed input
  schema, invocation, result, exact admission, and bounded execution contract.
- Deterministic internal catalog construction from the assembled Subsystem
  without exposing every Operation by default.
- Invocation through the existing operation execution path with no parallel
  authorization or error model.
- Shared MCP protocol revision constants and initialize negotiation.
- JSON-RPC notification recognition and Streamable HTTP `202 Accepted`
  handling for `notifications/initialized`.
- `MCP-Protocol-Version` validation on post-initialize requests.
- Actual HTTP initialize, notification, `tools/list`, and `tools/call`
  interoperability evidence between the CNCF server and client.
- Negative Web-tool evidence showing private-network rejection before
  resource access.
- Downstream Textus AI evidence that internal Operation tools and remote MCP
  tools are composed without identity collapse or caller-selected
  infrastructure.

## Boundaries

- CNCF owns internal Operation admission/execution, MCP transport, server
  protocol behavior, endpoint policy, limits, and payload-safe diagnostics.
- Textus AI owns internal/remote catalog composition, provider-function name
  mapping, model request construction, and continuation loops.
- Sanpomap and other application components select only application purpose
  and approved AI profile. They do not select an Operation, MCP server,
  endpoint, transport, credential, header, or provider function.
- Internal Operation definitions are not converted to remote `server/tool`
  identities. Remote MCP tools are not converted to CNCF Operation identities.
- `McpToolCatalog` remains the external MCP projection adapter; it is not the
  generic internal tool API.
- No local MCP loopback, provider-native MCP pass-through, arbitrary HTTP,
  arbitrary filesystem/process tool, unrestricted header, or application-owned
  credential surface is introduced.
- The server may remain stateless and omit `Mcp-Session-Id`. Session creation,
  resumable streams, and server-initiated notifications are outside this
  phase unless required by the selected protocol revision.
- Existing WebSocket `/mcp` behavior is not expanded into a second tool
  execution model. Its compatibility or retirement must be recorded before
  closure, but Streamable HTTP POST is the normative path for this phase.

## Stages

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| MT-01 | Boundary and protocol contract | Internal Operation tools, remote MCP tools, ownership, identity, and protocol lifecycle are fixed normatively. | done |
| MT-02 | Internal Operation tool source | A policy-admitted typed catalog and invocation path execute through normal Subsystem operation semantics. | done |
| MT-03 | Shared protocol negotiation | CNCF server and client use one shared revision model and reject unsupported initialization deterministically. | open |
| MT-04 | Notification-aware HTTP server | The adapter and HTTP route distinguish request responses from accepted notifications and validate lifecycle headers. | open |
| MT-05 | Executable interoperability evidence | Real HTTP server/client and internal no-loopback specifications cover success, policy rejection, and payload safety. | open |
| MT-06 | Downstream acceptance and closure | Textus AI and Sanpomap consume the corrected boundaries and all phase closure evidence passes. | open |

## Acceptance

- One admitted internal builtin tool is discovered and invoked without any
  HTTP request, using `Subsystem.executeOperationResponse` and the normal
  ActionCall/UnitOfWork path.
- An internal tool not admitted by runtime policy is absent from discovery and
  rejected before operation execution.
- Internal `component.service.operation` and remote `server/tool` identities
  remain distinct through catalog composition and invocation.
- CNCF server initialization returns a protocol revision accepted by the CNCF
  client; unsupported revisions fail structurally.
- `notifications/initialized` receives HTTP `202` with no JSON-RPC response
  body, while normal JSON-RPC requests receive HTTP `200` and a valid response
  envelope.
- A real CNCF Streamable HTTP client can initialize a real CNCF server, list an
  admitted builtin projection, and call a deterministic tool.
- A private-network `tool.web.fetch` target is rejected before ResourceAccess
  and without leaking the URL, endpoint, headers, credential references, or
  raw tool result into default diagnostics.
- Textus AI composes internal and remote admitted definitions into its own
  provider-neutral catalog; CNCF does not construct provider function calls.
- Sanpomap Phase 2 records assembled URL, host, private-network, redirect,
  content-size, and timeout evidence without an application-layer protocol
  workaround.
- Focused MCP/tool specifications, the full CNCF suite, affected Textus AI
  specifications, and the Sanpomap Phase 2 assembly check pass.

## Non-goals

- Provider function-call serialization, prompt management, model selection,
  AI fallback, or agent scheduling in CNCF.
- Merging internal and remote identities into one CNCF tool namespace.
- Requiring local HTTP to invoke CNCF builtin Operations.
- Adding stdio, legacy SSE, arbitrary process execution, browser automation,
  mutation tools, or unrestricted resource access.
- Full stateful MCP session management, server-initiated sampling, resources,
  prompts, or elicitation support.
- Moving Sanpomap geographic acceptance rules or Textus AI purpose/profile
  policy into CNCF.

## Source

- `docs/phase/phase-45.md`
- `docs/design/mcp-client-boundary.md`
- `docs/spec/mcp-client-boundary.md`
- downstream `textus-sanpomap` journal
  `docs/journal/2026/07/2026-07-21-cncf-builtin-mcp-interoperability-handoff.md`

## Resume Point

Begin MT-03 by introducing one shared MCP protocol revision model for the
server and Streamable HTTP client. Negotiate `initialize` against the common
supported set and reject missing, malformed, and unsupported revisions
structurally.
