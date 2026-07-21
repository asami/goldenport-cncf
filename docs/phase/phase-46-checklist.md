# Phase 46 Checklist - Internal Operation Tool Source and MCP Server Interoperability

This checklist is the authoritative planned Phase 46 state ledger.

## MT-01: Boundary and Protocol Contract

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after normative design and specification fix the
  internal/remote ownership split, identity domains, no-loopback rule, and
  shared Streamable HTTP lifecycle.

- [x] Define the provider-neutral internal Operation-tool source separately
  from MCP wire models and provider-function models.
- [x] Fix internal identity as `component.service.operation` and remote MCP
  identity as `server/tool` with no implicit conversion in either direction.
- [x] Fix Textus AI as the owner of admitted internal/remote catalog
  composition and provider-function mapping.
- [x] Specify exact runtime admission, invocation limits, structured failures,
  and payload-safe observability for internal Operation tools.
- [x] Specify shared initialize negotiation, initialized notification, protocol
  header, stateless server, and HTTP status semantics.
- [x] Record the normative status or retirement path of the existing WebSocket
  `/mcp` route without making it a second execution model.

## MT-02: Internal Operation Tool Source

Stage Status:
- Current status: DONE
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after one typed, policy-admitted internal catalog
  and invocation path execute through normal Subsystem operation semantics.

- [x] Define typed internal tool identity, definition, input schema,
  invocation, result, and logical tool-set/admission values.
- [x] Extract generic Operation definition/schema construction from
  `McpToolCatalog`; keep MCP JSON projection in the MCP adapter.
- [x] Construct catalogs deterministically from only explicitly admitted
  assembled Operations; default discovery remains empty/denied.
- [x] Invoke admitted tools through `Subsystem.executeOperationResponse` with
  the caller's ExecutionContext and no local HTTP callback.
- [x] Preserve normal request binding, ActionCall/UnitOfWork authorization,
  Consequence/Conclusion, CallTree, metrics, timeout, and cancellation
  behavior.
- [x] Reject unknown, unadmitted, malformed, and pre-dispatch over-limit calls
  before business operation execution; reject oversized results before they
  cross the tool boundary.

## MT-03: Shared Protocol Negotiation

Stage Status:
- Current status: OPEN
- Owner: CNCF MCP maintainers
- Update rule: Mark DONE only after server and client share one protocol model
  and initialize negotiation has exact positive and negative specifications.

- [ ] Introduce a shared MCP protocol-revision type and canonical supported
  revision set used by both server and Streamable HTTP client.
- [ ] Replace the server's fixed `2026-03-19` response with validation and
  negotiation of `initialize.params.protocolVersion`.
- [ ] Use `2025-11-25` as the initial canonical revision unless the normative
  specification records and tests a newer common revision.
- [ ] Reject missing, malformed, and unsupported revisions with bounded
  structured protocol diagnostics.
- [ ] Verify the exact negotiated revision instead of merely checking that the
  response field is parseable.

## MT-04: Notification-aware HTTP Server

Stage Status:
- Current status: OPEN
- Owner: CNCF MCP and HTTP maintainers
- Update rule: Mark DONE only after JSON-RPC requests and notifications produce
  their protocol-correct HTTP outcomes and lifecycle headers are validated.

- [ ] Replace the adapter's string-only result with typed JSON response,
  accepted-notification, and protocol-failure outcomes.
- [ ] Accept `notifications/initialized` only as a notification without an
  `id` and return HTTP `202` with no response body.
- [ ] Keep normal JSON-RPC request responses at HTTP `200` with
  `application/json` and matching request ids.
- [ ] Validate the negotiated `MCP-Protocol-Version` on post-initialize
  requests without inventing a session when the server is stateless.
- [ ] Preserve bounded invalid-request, method-not-found, invalid-params, and
  internal-error mappings without emitting responses to notifications.
- [ ] Add HTTP route specifications for content type, empty notification body,
  protocol header, and unsupported lifecycle requests.

## MT-05: Executable Interoperability Evidence

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime maintainers
- Update rule: Mark DONE only after deterministic internal no-loopback and real
  Streamable HTTP server/client paths both pass their complete evidence sets.

- [ ] Verify internal catalog discovery and deterministic builtin invocation
  without any HTTP exchange.
- [ ] Start a real loopback CNCF HTTP server and consume it through
  `McpClientRuntimeRegistry` using the production Streamable HTTP exchange.
- [ ] Verify initialize, `notifications/initialized`, `tools/list`, and one
  deterministic `tools/call` end to end.
- [ ] Verify unsupported protocol and malformed notification failures.
- [ ] Verify private-network `tool.web.fetch` rejection occurs before
  ResourceAccess and preserve host, redirect, content-size, and timeout tests.
- [ ] Verify CallTree, metrics, and diagnostics expose only safe identities and
  bounded outcomes, not endpoint, header, credential, URL, arguments, or raw
  result payloads.

## MT-06: Downstream Acceptance and Closure

Stage Status:
- Current status: OPEN
- Owner: CNCF maintainers, with Textus AI and Sanpomap maintainers as
  downstream acceptance owners
- Update rule: Mark DONE only after both downstream consumers use the intended
  boundaries, all validation passes, and the phase dashboard records closure.

- [ ] Verify Textus AI consumes the internal Operation-tool source separately
  from its remote `McpClientSocket` source.
- [ ] Verify Textus AI alone composes both admitted catalogs into
  provider-neutral function definitions without identity collisions.
- [ ] Remove the Sanpomap Phase 2 dependency on local MCP loopback for builtin
  tools while retaining external CNCF server/client interoperability evidence.
- [ ] Record Sanpomap assembled policy evidence for URL, host, private network,
  redirect, content size, and timeout.
- [ ] Run focused MCP/tool suites, full CNCF tests, affected Textus AI tests,
  and the Sanpomap Phase 2 assembly check.
- [ ] Run CNCF review, resolve actionable findings and naming/specification
  debt, create the validated release commit, and close Phase 46.
