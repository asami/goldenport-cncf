# StateMachine CML Parser Checklist
===================================

status=working-draft
published_at=2026-03-19
depends_on=docs/journal/2026/03/statemachine-cml-grammar.md

---

# Scope

Implement parser and validation pipeline for StateMachine CML grammar,
and connect parsed AST to existing runtime planning model.

---

# Phase 1: Lexer / Block Reader

- [ ] Implement heading parser for:
  - [ ] `# StateMachine`
  - [ ] `## <MachineName>`
  - [ ] `### State`
  - [ ] `### Event`
  - [ ] `#### <Name>`
  - [ ] `##### Entry | Exit | Transition | Payload`
- [ ] Normalize line endings and indentation.
- [ ] Preserve declaration order index for transitions.
- [ ] Emit source-position info (line/column) for diagnostics.

---

# Phase 2: AST Construction

- [ ] Map CML blocks to AST:
  - [ ] `StateMachineDef`
  - [ ] `StateDef`
  - [ ] `TransitionDef`
  - [ ] `EventDef`
- [ ] Parse transition fields:
  - [ ] `to`
  - [ ] `on`
  - [ ] `guard` (optional)
  - [ ] `priority` (optional, default 0)
  - [ ] action lines (0..n)
- [ ] Parse event payload fields:
  - [ ] `name :: type`
- [ ] Parse guard text:
  - [ ] single identifier -> `GuardExpr.Ref`
  - [ ] otherwise -> `GuardExpr.Expression`

---

# Phase 3: Validation

- [ ] Machine-level validation:
  - [ ] machine name required
  - [ ] at least one state required
- [ ] Transition-level validation:
  - [ ] `to` required
  - [ ] `on` required
  - [ ] `to` target state exists
- [ ] Event-level validation:
  - [ ] event referenced by `on` exists (or policy-based auto-declare)
- [ ] Priority semantics validation:
  - [ ] integer parse check
  - [ ] preserve declaration order for same priority
- [ ] Guard parse validation:
  - [ ] empty guard string rejected

---

# Phase 4: Runtime Mapping Bridge

- [ ] Implement AST -> transition rule bridge:
  - [ ] `CollectionTransitionRule`
  - [ ] `TransitionTrigger`
  - [ ] `ExecutionPlan`
- [ ] Hook generated rules via:
  - [ ] `CollectionTransitionRuleProvider`
  - [ ] `ComponentFactory` bootstrap registration
- [ ] Ensure runtime selection behavior:
  - [ ] priority ascending
  - [ ] tie-break by declaration order
  - [ ] guard false -> non-match
  - [ ] guard failure -> `Failure`

---

# Phase 5: Executable Specs

- [ ] Parser happy-path specs:
  - [ ] minimal machine
  - [ ] machine with payloads
  - [ ] machine with multiple transitions
- [ ] Parser failure specs:
  - [ ] missing machine header
  - [ ] missing `to` / `on`
  - [ ] unknown target state
  - [ ] invalid priority
- [ ] Guard classification specs:
  - [ ] `Ref`
  - [ ] `Expression`
- [ ] Determinism specs:
  - [ ] priority ordering
  - [ ] same-priority declaration order
- [ ] Integration specs:
  - [ ] generated rules run through `beforeUpdate`
  - [ ] execution order `exit -> transition -> entry`

---

# Phase 6: Cozy Integration Tasks

- [ ] Add generator output for `CollectionTransitionRuleProvider`.
- [ ] Generate `stateMachineTransitionRules` from CML AST.
- [ ] Emit `declarationOrder` explicitly from source order.
- [ ] Emit guard as:
  - [ ] `guardRef(...)` for named guards
  - [ ] `guardExpression(...)` for inline expressions
- [ ] Emit `ExecutionPlan` using `StateMachineRuleBuilder`.

---

# Delivery Criteria

- [ ] CML input parses to deterministic AST.
- [ ] Validation errors include source positions.
- [ ] Generated transition rules are auto-bootstrapped in CNCF.
- [ ] Update/save path triggers planner and executes ordered actions.
- [ ] Regression suite covers determinism and guard failure semantics.

