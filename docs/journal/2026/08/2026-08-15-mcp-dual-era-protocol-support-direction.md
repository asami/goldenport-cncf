# MCP Dual-Era Protocol Support Direction

date=2026-08-15
status=planning-history
phase=[Phase 68](../../../phase/phase-68.md)

## Context

The current CNCF MCP server/client contract was completed in Phase 45 and
Phase 46 around the `2025-11-25` Streamable HTTP lifecycle, with compatibility
for `2025-06-18` and `2025-03-26`.

The stable MCP `2026-07-28` specification changes the protocol boundary rather
than merely adding another initialize revision. It removes protocol-level
session initialization from the modern path and adds request-scoped metadata,
server discovery, new HTTP/header behavior, result disposition, cache hints,
and stronger transport security requirements.

Primary external references at the time of this decision:

- <https://modelcontextprotocol.io/specification/2026-07-28/changelog>
- <https://modelcontextprotocol.io/specification/2026-07-28/basic/versioning>
- <https://modelcontextprotocol.io/specification/2026-07-28/basic/transports/streamable-http>
- <https://modelcontextprotocol.io/specification/2026-07-28/server/tools>
- <https://modelcontextprotocol.io/specification/2026-07-28/server/utilities/caching>

## Decision

Create Phase 68 after the separately planned Phase 67.

Phase 68 will support legacy and modern MCP protocols concurrently. The
existing legacy behavior remains the default during the phase. Modern support
is added as an explicit runtime-owned path and is not treated as an in-place
reinterpretation of the legacy initialize/session protocol.

The exact modern revision must be revalidated and frozen at MCP68-01. The
planning baseline is `2026-07-28`; a future moving `latest` alias is not an
implementation contract.

## Reasons

- Existing CNCF/Textus interoperability is working and remains useful while
  peers continue to support the legacy protocol.
- Immediate replacement would combine lifecycle, HTTP, schema/result, cache,
  security, and downstream migration risk in one cutover.
- The existing provider-neutral Component Port and internal Operation-tool
  boundaries allow protocol differences to remain in the runtime transport.
- Explicit coexistence gives executable regression evidence for legacy peers
  while modern interoperability matures independently.
- A silent fallback after uncertain delivery could duplicate a side-effecting
  tool call, so fallback must be limited to pre-dispatch incompatibility.

## Boundary Consequences

- Protocol era and revision are infrastructure policy, not application input.
- Server detection and client selection must reject ambiguous mixed-era
  evidence rather than guess.
- Legacy session state and modern request-scoped state remain separate typed
  lifecycles.
- Both paths reach the same admitted tool catalog, authorization,
  ActionCall/UnitOfWork, structured failure, and observability authorities.
- Cache identity includes protocol and authorization context.
- Observability records compatibility decisions but not endpoints,
  credentials, headers, arguments, or raw results.
- Changing the default to modern requires an explicit later closure decision
  backed by framework and downstream acceptance; it is not implied by adding
  modern support.

## Deferred

Phase 68 does not promise every optional MCP capability. Roots, Sampling,
Tasks, Subscriptions, unrestricted server-initiated behavior, additional
transports, and application-selected infrastructure remain separately admitted
future work.
