# Cozy Integration Handoff (StateMachine / SM-02 Wiring)

status=working-draft
published_at=2026-03-19
owner=cncf-runtime

## Purpose

Wire Cozy-generated code to CNCF Runtime state-machine pre-transition validation (SM-02).

This handoff summarizes:
- extension points already implemented on the CNCF side
- minimal tasks required on the Cozy side

## Already Implemented on CNCF Side

### 1. Pre-transition validation hook in runtime path

- Hook execution is inserted before `UnitOfWork` mutation operations:
  - save
  - update
  - updateById

Files:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkInterpreter.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/context/RuntimeContext.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/TransitionValidationHook.scala`

### 2. Planner + execution-order model

- `plan(state,event)` output is executed as `ExecutionPlan`
- Execution order is fixed:
  - `exit -> transition -> entry`

Files:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/ExecutionPlan.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/PlannedTransitionValidationHook.scala`

### 3. Guard handling and determinism

- Runtime support for `GuardExpr.Ref` and `GuardExpr.Expression`
- Deterministic transition selection:
  - `priority` ascending
  - tie-break by `declarationOrder`

Files:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/GuardRuntime.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/TransitionSelector.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/MvelEvaluator.scala`

### 4. ComponentFactory bootstrap wiring

- If a `Component` provides transition rules, ComponentFactory auto-registers them into the planner.

Files:
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentFactory.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/CollectionTransitionRuleProvider.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/CollectionStateMachinePlannerProvider.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/statemachine/StateMachineRuleBuilder.scala`

## Minimum Work Required on Cozy Side

### A. Add trait implementation to generated components

For generated `DomainComponent` (or equivalent component implementation), implement:

- `CollectionTransitionRuleProvider`
- `stateMachineTransitionRules: Vector[CollectionTransitionRule[Any]]`

### B. Generate transition rules from StateMachine DSL/metadata

Convert each transition definition into `CollectionTransitionRule`.

Fields to populate:
- `collectionName`
- `trigger`（save/update）
- `eventName`
- `priority`
- `declarationOrder`
- `guard` (Ref or Expression)
- `plan`（exit/transition/entry）

### C. Use RuleBuilder helpers

Generated code should use these helpers:

- `StateMachineRuleBuilder.updateRule(...)`
- `StateMachineRuleBuilder.saveRule(...)`
- `StateMachineRuleBuilder.guardRef(...)`
- `StateMachineRuleBuilder.guardExpression(...)`
- `StateMachineRuleBuilder.plan(...)`
- `StateMachineRuleBuilder.action(...)`

## Notes for Cozy Implementation

1. `collectionName` must match CNCF `EntityCollection` name exactly.
2. Lower `priority` means higher precedence.
3. Always emit `declarationOrder` to preserve deterministic ordering for same-priority transitions.
4. Guard evaluation error must be treated as `Failure`, not `false`.
5. If MVEL evaluation is used, Cozy must explicitly handle dependency setup.

## Acceptance Criteria (Cozy Side)

1. When generated component starts on CNCF, `ComponentFactory` auto-registers transition rules.
2. `plan(state,event)` is invoked on update/save.
3. Execution order is `exit -> transition -> entry`.
4. guard + priority + declarationOrder are enforced.

## CNCF Reference Specs

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/unitofwork/UnitOfWorkStateMachineHookSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentFactoryStateMachineBootstrapSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentLogicStateMachineHookSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/statemachine/CollectionStateMachinePlannerProviderSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/statemachine/StateMachineRuleBuilderSpec.scala`

## Next Implementation Steps (Cozy)

1. Add `CollectionTransitionRuleProvider` implementation to generation templates.
2. Implement `stateMachineTransitionRules` generation from DSL AST.
3. Validate with one minimal E2E model (for example `Person`).
4. Add regression tests for multi-transition and same-priority ordering cases.
