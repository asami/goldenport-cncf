status=closed
closed_at=2026-01-18

    This document is authoritative for Phase 2.8 Context refactoring.
    Create a design-freeze memo for Context re-architecture (Phase 2.8), capturing invariants and responsibilities, without modifying code.
    
    Purpose:
    - Fix the intended responsibilities and boundaries for RuntimeContext / ScopeContext / ExecutionContext.
    - Declare what MUST NOT change during the refactor.
    - Serve as the authoritative design note for subsequent Codex execution steps.
    
    Deliverable:
    - A single design memo document (plain text / markdown) to be added under docs/notes/ (or equivalent notes area).
    
    Memo contents (MUST include):
    
    1) Scope and intent
       - This memo freezes the design intent for consolidating RuntimeMetadata and SystemContext into RuntimeContext + ScopeContext.
       - This is a structural refactor with zero semantic change.
    
    2) Non-negotiable invariants
       - No change to observable runtime behavior (CLI, server-emulator, HTTP, script).
       - No change to defaults, configuration keys, or resolver/path semantics.
       - No new abstractions that add semantics (pure relocation/renaming allowed).
       - ExecutionContext remains request-scoped and side-effect free.
    
    3) Context taxonomy (authoritative)
       - RuntimeContext:
         * Owns execution environment capabilities (HttpDriver, runtime mode, observability backend).
         * Provides hierarchical lookup (runtime → subsystem → component).
       - ScopeContext:
         * Represents logical scope (runtime / subsystem / component / operation).
         * Carries scope identity and override hooks, not execution capabilities.
       - ExecutionContext:
         * Represents a single execution.
         * References RuntimeContext and ScopeContext.
         * Must NOT carry duplicated capability fields (e.g., HttpDriver).
    
    4) Explicitly forbidden patterns
       - ExecutionContext carrying Option[HttpDriver] or similar capability overrides.
       - Runtime/system metadata stored in ad-hoc config maps without an owning context.
       - Reintroducing SystemContext-like “god objects”.
    
    5) Deletions planned (declarative)
       - SystemContext (type and plumbing).
       - RuntimeMetadata (type and formatting responsibility).
       - componentHttpDriver / subsystemHttpDriver fields on ExecutionContext.
    
    6) Replacement rules
       - All HttpDriver resolution must occur via RuntimeContext lookup.
       - Ping/introspection formatting must be a pure view over RuntimeContext + ScopeContext.
    
    7) Migration notes
       - Temporary adapters are allowed only to bridge existing call sites.
       - Final state must have no references to SystemContext or RuntimeMetadata.
    
Constraints:
       - Do NOT include implementation code.
       - Do NOT speculate beyond the above scope.
       - Language must be declarative and normative (“MUST / MUST NOT / OWNS”).

Output:
       - Emit the full memo content in a single ```text fenced block.
       - All lines inside the block must be indented by exactly 4 spaces.

## Closed Items

- ExecutionContext: runtime-only carrier for action/component execution (closed 2026-01-18)
- RuntimeContext: concrete behavior owner with ScopeContext-driven HttpDriver resolution (closed 2026-01-18)
- ScopeContext: driver chain now delegates up to GlobalRuntimeContext (closed 2026-01-18)
- SystemContext: removed from runtime wiring (closed 2026-01-18)
- ApplicationContext: removed, runtime/environment flows through RuntimeContext/ScopeContext (closed 2026-01-18)
- RuntimeMetadata: removed in favor of GlobalRuntimeContext/ping via the runtime chain (closed 2026-01-18)

## Open Items

- Application / Domain Context の再定義（OPEN）
- Subsystem 初期化フェーズの整理（OPEN）
