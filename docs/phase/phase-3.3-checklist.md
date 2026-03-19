# Phase 3.3 — AI Agent Hub (PoC) Checklist

This document contains detailed task tracking and execution decisions
for Phase 3.3.

It complements the summary-level phase document (`phase-3.3.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-3.3.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## AI-33-01: MCP Projection Publication

Status: DONE

### Objective

Publish MCP projection surfaces for AI-agent discovery without changing
core execution semantics.

### Detailed Tasks

- [x] Add `meta.mcp` as default meta operation.
- [x] Add `spec.export.mcp` projection output.
- [x] Add baseline MCP projection tests.
- [x] Keep existing OpenAPI projection behavior compatible.

---

## AI-33-02: Help Navigation for MCP Discovery

Status: DONE

### Objective

Ensure MCP discovery starts from standard CLI help flow.

### Detailed Tasks

- [x] Add MCP examples to `cncf command help`.
- [x] Add explicit AI/MCP navigation hints.
- [x] Add/adjust CLI help tests for MCP hints.

---

## AI-33-03: MCP Server Core (`/mcp`, JSON-RPC 2.0)

Status: DONE

### Objective

Implement strict JSON-RPC 2.0 MCP server endpoint in CNCF server mode.

### Detailed Tasks

- [x] Add WebSocket `/mcp` route in server runtime.
- [x] Implement MCP request decode (strict JSON-RPC 2.0).
- [x] Implement MCP response encode (result/error envelope).
- [x] Implement method dispatch:
  - [x] `initialize`
  - [x] `tools/list`
  - [x] `tools/call`
- [x] Implement JSON-RPC error code mapping:
  - [x] `-32600` invalid request
  - [x] `-32601` method not found
  - [x] `-32602` invalid params
  - [x] `-32603` internal error

### Reference Base

- `../semantic-integration-engine/src/main/scala/org/simplemodeling/sie/interaction/ProtocolHandler.scala`
- `../semantic-integration-engine/docs/mcp-architecture.md`

---

## AI-33-04: `tools/call` Execution Bridge

Status: DONE

### Objective

Execute existing CNCF operations through MCP `tools/call` without bypassing
current runtime execution paths.

### Detailed Tasks

- [x] Map tool names to stable operation selectors (`component.service.operation`).
- [x] Map MCP arguments to CNCF request arguments.
- [x] Execute via existing subsystem/runtime path.
- [x] Verify at least one end-to-end operation call.
- [x] Add regression tests for invalid tool name/params.

---

## Deferred / Next Phase Candidates

- resources/prompts lifecycle support.
- richer argument schema typing/validation.
- policy-based tool visibility filtering.
- session/auth hardening for MCP access.

---

## Completion Check

Phase 3.3 slice is complete when:

- AI-33-03 and AI-33-04 are marked DONE.
- Phase summary (`phase-3.3.md`) checkboxes are aligned.
- No item remains ACTIVE or SUSPENDED.
