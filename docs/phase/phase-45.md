# Phase 45 - MCP Client and AI Tool Boundary

status=active
started_at=2026-07-21
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)

## Purpose

Provide a provider-neutral, runtime-owned MCP client boundary. Consumers can
discover and invoke only tools admitted by CNCF policy; application callers do
not control an MCP endpoint, transport, credential, header, or raw payload.

## Selected Direction

- Define an MCP client Port for typed server-set resolution, tool discovery,
  and tool invocation.
- Define transport as an ExtensionPoint. The first admitted transport is
  Streamable HTTP.
- Keep server-set identity, endpoint admission, credential references, tool
  allowlists, timeouts, byte limits, and call limits under operator/runtime
  ownership.
- Use deterministic fake transport and redacted diagnostics so normal
  executable specifications do not need a remote MCP service.
- Keep the existing CNCF MCP server projection separate. It publishes CNCF
  operations; this phase consumes remote MCP tools.

## Scope

- Typed server-set, server, tool, input schema, call, result, limit, and
  structured diagnostic models.
- A client Port and transport ExtensionPoint with one runtime-owned registry.
- Streamable HTTP initialization, `tools/list`, and `tools/call` semantics.
- Named server-set admission, endpoint and credential policy, tool allowlists,
  bounded calls, and redacted CallTree/metric facts.
- Deterministic fake transport and a first Textus AI consumer contract.

## Boundaries

- No stdio, SSE, arbitrary process execution, arbitrary HTTP, or arbitrary
  filesystem transport.
- No provider-native remote-MCP pass-through, provider-specific function-call
  serialization, prompt management, or agent scheduling.
- No application caller configuration for endpoint, header, credential,
  transport, server, or tool selection.
- The CNCF MCP server projector and JSON-RPC adapter remain unchanged unless a
  shared type is demonstrably required.

## Stages

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| MC-01 | Client boundary specification | The client direction, ownership model, server/publication separation, and external contract are fixed. | done |
| MC-02 | Typed protocol model | Server sets, tool catalog, calls, results, limits, and diagnostics are represented without provider wire formats. | in progress |
| MC-03 | Port and transport ExtensionPoint | Consumers resolve tools and invoke them through a runtime-owned Port with Streamable HTTP and fake implementations. | planned |
| MC-04 | Admission and safety | Endpoint, credential-reference, tool, resource-limit, and failure policy is enforced before transport execution. | planned |
| MC-05 | Observability and lifecycle | CallTree, metrics, and shutdown behavior expose only safe MCP execution facts. | planned |
| MC-06 | Consumer evidence and closure | CNCF fake evidence and Textus AI integration prove the boundary without a required remote MCP service. | planned |

## Acceptance

- An admitted named server set resolves a typed tool catalog and executes only
  allowlisted tools.
- A caller cannot configure or infer an endpoint, credential/header, transport,
  or raw tool payload from the public consumer contract.
- Unknown server sets/tools, denied tools, invalid input, limit exhaustion,
  transport failure, and remote tool failure remain distinct structured
  outcomes.
- CallTree and metrics record safe identities, outcome kinds, and bounded
  summaries only.
- Existing MCP server publication behavior remains unchanged.

## Downstream

Textus AI Phase 6 is the first consumer. It will adapt the admitted catalog to
provider function definitions and execute provider continuation loops. That
provider work is not part of this phase.

## References

- [Phase 45 Checklist](phase-45-checklist.md)
- [MCP Client Boundary Design](../design/mcp-client-boundary.md)
- [MCP Client Boundary Specification](../spec/mcp-client-boundary.md)
- Textus AI: `textus-ai/docs/phase/phase-6.md`
