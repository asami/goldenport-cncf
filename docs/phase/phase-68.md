# Phase 68 - Legacy and Modern MCP Protocol Coexistence

status=planned
planned_at=2026-08-15
depends_on=[Phase 67](phase-67.md)
technical_foundations=[Phase 45](phase-45.md) and [Phase 46](phase-46.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 68 Checklist](phase-68-checklist.md)
direction=[MCP Dual-Era Protocol Support Direction](../journal/2026/08/2026-08-15-mcp-dual-era-protocol-support-direction.md)
foundations=[MCP Server Boundary](../design/mcp-server-boundary.md), [MCP Client Boundary](../design/mcp-client-boundary.md), and [MCP Client Boundary Specification](../spec/mcp-client-boundary.md)

## Purpose

Add modern MCP protocol support to the CNCF server and client while preserving
the existing legacy MCP contract as the default operational path during the
migration period.

Phase 68 introduces one runtime-owned dual-era protocol boundary. Components
continue to consume provider-neutral catalogs and typed tool calls; they do not
select protocol revisions, sessions, endpoints, headers, or fallback behavior.

## Dependency and Scheduling

Phase 68 begins after Phase 67 closes. Phase 45 and Phase 46 remain the
technical foundations for the MCP client/server ownership split, typed tool
model, protocol negotiation, HTTP transport, and interoperability evidence.

Phase 68 does not reopen those closed phases. It extends their accepted public
boundaries with an explicit compatibility layer.

## Selected Direction

- Keep the currently supported legacy revisions, with `2025-11-25` as the
  preferred legacy revision, operational and covered by regression evidence.
- Add the modern protocol beginning with the stable `2026-07-28` contract,
  after MCP68-01 revalidates the exact official baseline at phase start.
- Keep legacy behavior as the default until modern server, client, security,
  schema, cache, observability, and downstream acceptance all close.
- Select protocol era and revision only at the runtime transport boundary.
  Application requests and Components cannot select or override them.
- Reject mixed, ambiguous, or contradictory legacy/modern headers, body
  metadata, methods, and lifecycle messages deterministically.
- Permit compatibility fallback only under an explicitly admitted policy and
  only before a possibly side-effecting tool invocation is dispatched.
- Preserve one provider-neutral Component Port and typed catalog/invocation
  model. Protocol-era wire records do not enter Component APIs.
- Project selected era, revision, lifecycle, method class, cache decision,
  result disposition, and compatibility outcome through bounded redacted
  observability without retaining endpoints, credentials, headers, arguments,
  or raw results.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| MCP68-01 | Inventory and target freeze | Official legacy/modern contracts, current implementation, consumers, security differences, and executable acceptance identities are fixed. | planned |
| MCP68-02 | Dual-era model and selection contract | One closed protocol-era/revision vocabulary defines server detection, client policy, compatibility, fallback, and structured failures. | planned |
| MCP68-03 | Dual-era server boundary | The server preserves legacy behavior and admits modern discovery, request metadata, HTTP, result, and lifecycle semantics through the same Operation execution path. | planned |
| MCP68-04 | Dual-era client boundary | The client preserves legacy sessions and adds the modern request-scoped path, discovery, result handling, and safe compatibility behavior behind the existing Port. | planned |
| MCP68-05 | Schema, cache, security, and observability | Modern schema/result, cache, Origin/header/auth-context, limits, and redacted evidence are deterministic for both eras. | planned |
| MCP68-06 | Interoperability, downstream acceptance, and closure | Legacy and modern matrices, probes, Textus consumers, documentation, validation, review, and release evidence agree. | planned |

## Acceptance

- Existing legacy CNCF client/server interoperability continues to pass with
  the same preferred revision and lifecycle behavior.
- A modern CNCF client and server interoperate using the selected modern
  protocol without legacy initialization or session assumptions.
- Legacy and modern peers can be admitted independently in one runtime without
  collapsing their lifecycle, cache, result, or failure semantics.
- Mixed or contradictory protocol-era evidence fails before Operation or tool
  execution with a stable structured failure.
- Compatibility fallback never replays a tool call that may already have
  reached business execution or an external provider.
- Components, Textus AI, Sanpomap, Bok, CBD Support, and SIE do not receive raw
  protocol selectors, headers, credentials, endpoints, or wire payloads.
- Modern discovery, request metadata, result disposition, structured content,
  cache, and HTTP/security behavior have executable positive and hostile-input
  evidence.
- CallTree, diagnostics, metrics, and audit evidence distinguish era/revision
  and compatibility outcomes without payload or secret leakage.
- Framework and representative downstream legacy/modern interoperability pass
  focused and full validation before the default can be reconsidered.

## Non-Goals

- Removing the legacy MCP protocol or changing the default to modern during
  initial Phase 68 implementation.
- Allowing application callers or Components to choose protocol revisions,
  endpoints, transports, credentials, arbitrary headers, or fallback policy.
- Supporting every optional MCP capability such as Roots, Sampling, Tasks,
  Subscriptions, or unrestricted server-initiated behavior.
- Adding stdio, arbitrary subprocess, filesystem, legacy SSE, or unrestricted
  HTTP transports.
- Changing internal CNCF Operation-tool identity, authorization, UnitOfWork,
  provider-function mapping, or application workflow semantics.
- Treating a compatibility failure as permission to replay a possibly
  side-effecting invocation.

## Planning References

- [Phase 68 Checklist](phase-68-checklist.md)
- [CNCF Development Strategy](../strategy/cncf-development-strategy.md)
- [MCP Dual-Era Protocol Support Direction](../journal/2026/08/2026-08-15-mcp-dual-era-protocol-support-direction.md)
- [Phase 45](phase-45.md)
- [Phase 46](phase-46.md)
- [MCP Server Boundary](../design/mcp-server-boundary.md)
- [MCP Client Boundary](../design/mcp-client-boundary.md)
- [MCP Client Boundary Specification](../spec/mcp-client-boundary.md)
