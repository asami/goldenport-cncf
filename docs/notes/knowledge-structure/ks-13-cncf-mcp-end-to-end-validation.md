# KS-13 CNCF MCP Boundary Validation

KS-13 validates the CNCF MCP publication/runtime boundary and the `textus-sie`
MCP facade before the final combined driver smoke. CNCF remains the MCP
transport and operation-dispatch boundary; `textus-sie` owns the tool semantics
and provider behavior.

## Runtime Boundary

- `meta.mcp` and `spec.export.mcp` expose CNCF operations as MCP tools using
  `component.service.operation` names.
- `/mcp` handles JSON-RPC `initialize`, `tools/list`, and `tools/call`.
- `tools/call` executes through normal `Subsystem.execute`; it does not call a
  `textus-sie` special path.
- The shared CNCF MCP tool catalog now backs both static MCP projection and the
  JSON-RPC adapter, so input schemas are consistent.

## SIE Tool Validation

`textus-sie` validates the Phase 25 driver path with:

- `sie.query`: semantic search with RDF results and optional CNCF
  KnowledgeFrame projection.
- `sie.explain`: entity-centric explanation with optional CNCF KnowledgeFrame
  projection.
- `sie.status`: RDF DB, Vector DB, embedding, and component `KnowledgeSpace`
  status/counts.

MCP callers can choose between two KnowledgeFrame modes:

- `registerKnowledgeSpace=true`: materialize the frame into the component-owned
  `KnowledgeSpace`.
- `registerKnowledgeSpace=false`: return the KnowledgeFrame as a one-shot JSON
  string without mutating `KnowledgeSpace`.

## Provider Hardening

The validation path also hardens provider reliability:

- Chroma source registration forwards source content to the provider boundary.
- External provider HTTP calls use configured request timeouts.
- Required `query` / `explain` provider failures remain operation failures;
  `status` reports degraded readiness.

## Result

KS-13 confirms the generic CNCF MCP projection and JSON-RPC dispatch behavior
with a shared typed tool catalog, and confirms the `textus-sie` facade can
invoke query/explain/status through the KnowledgeFrame materialization path.
The final live CNCF startup with `textus-sie` and `/mcp` smoke remains part of
KS-14 verification and closure.
