# Phase 2.5 Observability Overview

status = draft
since = 2026-01-08

## Short Description

Phase 2.5 fixes the structure and responsibility boundaries of observability in CNCF.
It does not define backends, exporters, or operational policies.

## Reading Order

1) [scope-context-design.md](scope-context-design.md)
   - Defines the ScopeContext boundary and structural ownership of context.

2) [conclusion-observation-design.md](conclusion-observation-design.md)
   - Defines the boundary between logic-bearing Conclusion and descriptive Observation.

3) [observability-engine-build-emit-design.md](observability-engine-build-emit-design.md)
   - Defines the separation between build (semantic) and emit (side effects).

## Closing Note

Phase 3 is expected to build on top of these decisions.
Next: Phase 2.6 checklist at `docs/notes/phase-2.6-demo-done-checklist.md`.
