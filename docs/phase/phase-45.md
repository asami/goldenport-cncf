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
- Allow operator-selected Codex MCP definitions to be imported as a
  configuration source, then normalize them into CNCF-owned server sets before
  admission. Codex configuration is not an execution boundary and is never
  passed through to a provider.
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
- A Codex MCP definition importer for the CNCF-supported subset, with explicit
  CNCF policy overlays for allowlists, limits, and credential references.
- A conservative builtin tool baseline implemented as ordinary CNCF Operations:
  resource read, static Web fetch/HEAD, runtime time, deterministic decimal
  calculation, and a provider-neutral Web search contract.
- Deterministic fake transport and a first Textus AI consumer contract.

## Boundaries

- No stdio, SSE, arbitrary process execution, arbitrary HTTP, or arbitrary
  filesystem transport.
- No provider-native remote-MCP pass-through, provider-specific function-call
  serialization, prompt management, or agent scheduling.
- No direct execution of Codex stdio commands, arguments, environment values,
  raw headers, embedded credentials, or unrestricted tool publication.
- Textus AI does not read Codex configuration directly. Import and
  normalization are CNCF runtime responsibilities.
- Builtin tools do not call back through the local MCP HTTP endpoint. Their
  source of truth is the normal ActionCall/UoW Operation path; MCP is an
  external projection of that same operation contract.
- Dynamic browser automation, arbitrary filesystem access, shell/process
  execution, arbitrary JavaScript, unrestricted HTTP headers, and external
  mutation tools are not part of the builtin baseline.
- No application caller configuration for endpoint, header, credential,
  transport, server, or tool selection.
- The CNCF MCP server projector and JSON-RPC adapter remain unchanged unless a
  shared type is demonstrably required.

## Stages

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| MC-01 | Client boundary specification | The client direction, ownership model, server/publication separation, and external contract are fixed. | done |
| MC-02 | Typed protocol model | Server sets, tool catalog, calls, results, limits, and diagnostics are represented without provider wire formats. | done |
| MC-03 | Port and transport ExtensionPoint | Port/registry, Streamable HTTP, and deterministic fake transport are complete. | done |
| MC-04 | Admission and safety | Endpoint, credential-reference, tool, resource-limit, and failure policy is enforced before transport execution. | done |
| MC-05 | Observability and lifecycle | CallTree, metrics, and shutdown behavior expose only safe MCP execution facts. | done |
| MC-06 | Codex MCP definition import | Operator-selected Codex MCP definitions become admitted CNCF server sets through a bounded import adapter and CNCF policy overlay. | done |
| MC-07 | Builtin tool baseline | Common resource, Web, time, calculation, and Web-search contracts are available through normal CNCF Operations and MCP projection. | planned |
| MC-08 | Consumer evidence and closure | CNCF fake evidence and Textus AI integration prove the boundary without a required remote MCP service. | planned |

MC-04 has exact per-server tool allowlisting, recursive typed-input admission,
invocation-scoped call/concurrency budgets, transport-enforced
timeout/input/output byte limits, and runtime-owned opaque Bearer credential
resolution immediately before HTTP exchange.

MC-05 records payload-safe consumer-side catalog and tool invocation spans and
`mcp-client.invocation` metrics. Runtime shutdown closes registry admission,
interrupts and drains tracked in-flight operations, then closes each transport
once in deterministic server-set order. MC-06 has a package-restricted
runtime assembly and an operator-owned policy descriptor that combines a
generic TOML definition source with exact CNCF admission policy. Textus AI
now proves the consumer sees only a logical server-set requirement and the
normalized `McpClientSocket`, while application-purpose configuration cannot
select MCP infrastructure. MC-07 adds a safe,
operation-backed builtin tool baseline. MC-08 consumer evidence and closure
remains the final Phase 45 work.

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
- Supported Codex MCP definitions can be imported deterministically, while
  unsupported transports and unsafe embedded configuration are rejected before
  registry creation.
- Builtin tools execute through ActionCall/UoW authorization and observability,
  and are projected through the existing MCP server boundary without local
  MCP loopback.
- Web access rejects unsafe schemes, disallowed hosts, private-network targets,
  unsafe redirects, oversized content, and timeout exhaustion structurally.

## Downstream

Textus AI Phase 6 is the first consumer. It will adapt the admitted catalog to
provider function definitions, combine internal Operation tools with remote MCP
tools while preserving source identity, and execute provider continuation
loops. That provider work is not part of this phase.

## References

- [Phase 45 Checklist](phase-45-checklist.md)
- [MCP Client Boundary Design](../design/mcp-client-boundary.md)
- [MCP Client Boundary Specification](../spec/mcp-client-boundary.md)
- Textus AI: `textus-ai/docs/phase/phase-6.md`
