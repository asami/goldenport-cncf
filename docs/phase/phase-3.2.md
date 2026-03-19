# Phase 3.2 — CML -> Entity Component Generation

status = done

## 1. Purpose of This Document

This document summarizes the completed work results of Phase 3.2.
It records execution-ready outcomes for CML-based component generation
and CNCF integration.

This is a progress summary, not a design specification.

## 2. Scope

Phase 3.2 targeted:

- CML-driven generation of executable Entity Component code.
- Integration path from generated code to CNCF runtime execution.
- Baseline compatibility for generated query/update shapes.

## 3. Work Results

### 3.1 CML Generation Path (cozy)

- `modeler-scala` generation flow produces Domain/Entity component code
  from CML (`.dox`) models.
- Generated output includes `DomainComponent` and entity-related sources
  under managed Scala sources.

### 3.2 CNCF Compatibility for Generated Shapes

- `Condition[T]` codec support was added for generated query models.
- Generated query condition shapes are executable through CNCF entity search paths.
- Cozy-style update directive shapes are accepted in CNCF update routing.

### 3.3 Execution Integration

- Generated projects can execute against CNCF through embedding bootstrap flow
  (`CncfBootstrap`) without relying on CLI process orchestration.
- End-to-end scripted validation confirms generated component behavior
  can be executed and verified in-process.

## 4. Verification Evidence

Primary verification sources:

- `cloud-native-component-framework`:
  - `src/test/scala/org/goldenport/cncf/directive/ConditionCodecSpec.scala`
  - `src/test/scala/org/goldenport/cncf/entity/runtime/EntityCollectionSearchConditionSpec.scala`
- `cozy`:
  - `src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
  - `src/sbt-test/cozy/entity-simpleentity-action`

Associated handoff notes:

- `docs/journal/2026/03/condition-codec-handoff-2026-03-17.md`
- `docs/journal/2026/03/subsystem-bootstrap-api-handoff-2026-03-18.md`

## 5. Out of Scope / Deferred from Phase 3.2

- MCP server transport/protocol implementation (`/mcp`, JSON-RPC server behavior).
- AI tool invocation transport hardening and policy filtering.
- Expanded schema-level capability projection beyond current metadata baseline.

These are tracked under Phase 3.3.

## 6. References

- `docs/phase/phase-3.md`
- `docs/phase/phase-3.3.md`

closed_at = 2026-03-19
