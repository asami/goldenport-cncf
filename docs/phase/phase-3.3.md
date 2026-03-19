# Phase 3.3 — AI Agent Hub (PoC)

status = closed

## 1. Purpose of This Document

This document tracks the active work state for Phase 3.3 (AI Agent Hub PoC).
It is a summary-level progress dashboard, not a design specification.

Detailed task status and verification are tracked in `phase-3.3-checklist.md`.

## 2. Phase Scope

- Provide AI-agent-accessible discovery and projection surfaces for CNCF operations.
- Keep execution semantics single-sourced through existing CNCF runtime paths.
- Implement MCP server capability as PoC transport/protocol integration.

## 3. Current Work Stack

- A (DONE): MCP server core (`/mcp`, JSON-RPC 2.0 ingress/egress, initialize/tools/list/tools/call).
- B (DONE): MCP projection publication (`meta.mcp`, `spec.export.mcp`) and baseline tests.
- C (DONE): CLI help navigation hints for MCP discovery path.
- D (DEFERRED): MCP post-PoC expansion (resources/prompts, richer schema typing, policy hardening).

## 4. Development Items

- [x] AI-33-01: Publish MCP projection surfaces for agent discovery (`meta.mcp`, `spec.export.mcp`).
- [x] AI-33-02: Expose MCP discovery path from `cncf command help`.
- [x] AI-33-03: Implement MCP server endpoint (`/mcp`) with strict JSON-RPC 2.0 behavior.
- [x] AI-33-04: Connect `tools/call` to existing CNCF execution path and verify end-to-end behavior.

Each development item is considered complete when it is completed, cancelled, or explicitly deferred.
Detailed task breakdown is tracked in `phase-3.3-checklist.md`.

## 5. Out of Scope (This Phase)

- ChatGPT-specific custom protocol on `/mcp`.
- Full resources/prompts lifecycle.
- Session/auth hardening beyond current runtime baseline.
- Actor/queue redesign for MCP execution path.

## 6. References

- `docs/journal/2026/03/ai-hub-mcp-server-handoff-2026-03-19.md`
- `docs/phase/phase-3.md`
- `docs/phase/phase-3.3-checklist.md`

closed_at = 2026-03-19
