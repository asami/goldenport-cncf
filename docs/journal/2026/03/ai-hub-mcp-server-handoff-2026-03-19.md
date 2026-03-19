# CNCF Handoff: Help Navigation + MCP Server Implementation (2026-03-19)

- Date: 2026-03-19
- Priority: High
- Scope: `cloud-native-component-framework` (CNCF)
- Base implementation reference: `../semantic-integration-engine` MCP server

## 1. Current Status in CNCF

As of this handoff:

- `meta.openapi` and `meta.mcp` are available on default meta services.
- `spec.export.openapi` and `spec.export.mcp` are available.
- OpenAPI/MCP outputs are currently projection JSON for discovery.
- A strict MCP JSON-RPC server endpoint (`/mcp`) is **not** implemented yet.

This means AI-agent discovery exists, but transport-level MCP interoperability is still missing.

## 2. Objective

Implement end-to-end AI Hub flow with two tracks:

1. Improve command help navigation so agents can reliably discover MCP entry points.
2. Implement MCP server capability in CNCF, based on `semantic-integration-engine` architecture.

## 3. Required User Flow

Agent/user should be able to do:

1. `cncf command help` (or `cncf command meta.help`)
2. discover MCP-related operations (`meta.mcp`, `spec.export.mcp`)
3. connect MCP client to CNCF server `/mcp`
4. run `initialize`, `tools/list`, and `tools/call`
5. execute CNCF operations through MCP tool calls

## 4. Reference Source (semantic-integration-engine)

Primary code references:

- `../semantic-integration-engine/src/main/scala/org/simplemodeling/sie/interaction/ProtocolHandler.scala`
  - `Mcp.McpJsonRpcIngress`
  - `Mcp.McpJsonRpcAdapter`
  - JSON-RPC error mapping
  - method dispatch (`initialize`, `tools/list`, `tools/call`)
- `../semantic-integration-engine/docs/mcp-architecture.md`
  - strict JSON-RPC 2.0 rules
  - protocol boundary principles

Supporting resource references:

- `../semantic-integration-engine/src/main/resources/initialization.json`
- `../semantic-integration-engine/src/main/resources/mcp.json`

## 5. CNCF Implementation Plan

### 5.1 Help Navigation Track

Update command help to expose MCP path explicitly:

- `src/main/scala/org/goldenport/cncf/cli/help/CommandProtocolHelp.scala`
  - add examples for:
    - `cncf command meta.mcp`
    - `cncf command spec.export.mcp`
  - add short “AI/MCP Navigation” hint.

Validation:

- update/add assertions in:
  - `src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala`

### 5.2 MCP Server Track

Implement strict JSON-RPC 2.0 endpoint over WebSocket `/mcp`:

- Introduce MCP protocol ingress/egress layer in CNCF (parallel to SIE shape):
  - MCP request/response/error DTO
  - JSON-RPC decode/encode
  - protocol error code mapping (`-32600`, `-32601`, `-32602`, `-32603`)

- Implement MCP methods:
  - `initialize`
  - `tools/list`
  - `tools/call`

- Tool model:
  - derive tools from canonical CNCF operation graph
  - stable name based on operation selector (`component.service.operation`)

- Execution bridge:
  - `tools/call` maps tool name + arguments to CNCF execution request
  - executes through existing subsystem/runtime path (no bypass)

- Server wiring:
  - add `/mcp` route in HTTP server runtime (`Http4sHttpServer`)
  - keep REST route behavior unchanged

## 6. Architectural Constraints

- MCP endpoint must be strict JSON-RPC 2.0 only.
- Do not add ChatGPT-specific shorthand protocol to `/mcp`.
- Keep protocol boundary explicit:
  - decode (ingress) -> CNCF execution core -> encode (egress)
- Keep tool names backward-compatible once published.

## 7. Acceptance Criteria

- [ ] `cncf command help` includes explicit MCP discovery hints.
- [ ] MCP WebSocket endpoint `/mcp` is reachable.
- [ ] `initialize` returns valid JSON-RPC result.
- [ ] `tools/list` returns tool definitions derived from CNCF operations.
- [ ] `tools/call` executes at least one existing CNCF operation end-to-end.
- [ ] Invalid request/method/params return proper JSON-RPC error codes.
- [ ] Existing OpenAPI/meta/help behaviors remain backward-compatible.

## 8. Test Plan

Unit:

- MCP ingress decode (valid/invalid JSON-RPC)
- MCP adapter encode (result/error forms)
- tool-name to selector resolution

Scenario/Integration:

- WebSocket session:
  - `initialize`
  - `tools/list`
  - `tools/call` (happy path + invalid params)
- Regression:
  - existing CLI help/meta/openapi specs must pass

## 9. Non-Goals (This Slice)

- full MCP resources/prompts lifecycle
- ChatGPT-specific protocol route
- auth/session management hardening
- schema-rich tool argument typing beyond current operation metadata

## 10. Immediate First Step

Implement Help Navigation Track first (small, low-risk), then implement MCP server core with a minimal `initialize + tools/list + tools/call` vertical slice.
