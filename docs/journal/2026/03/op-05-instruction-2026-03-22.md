# OP-05 Instruction (Executable Specification Closure for Phase 8)

status=ready
published_at=2026-03-22
owner=cncf-spec

## Goal

Close Phase 8 by completing OP-05 executable specifications for the full
operation path:

- parse -> AST/model -> generation propagation -> runtime/projection behavior

## Execution Policy

- Primary implementation: CNCF repository.
- Conditional follow-up: add minimal Cozy/SimpleModeler spec updates only
  if cross-repo coverage is insufficient to prove Phase 8 contract.
- Do not expand scope beyond OP-05 closure.

## Background

Current phase status:

- OP-01: DONE
- OP-02: DONE
- OP-03: DONE
- OP-04: DONE
- OP-05: PLANNED

Phase 8 can be closed only after OP-05 is DONE and phase documents are aligned.

## In-Scope

1. Grammar parse spec closure
- Add/extend executable specs for valid/invalid `operation` declarations.
- Cover canonical form and convenience parameter form.

2. AST/model mapping spec closure
- Verify parsed operation definitions map deterministically to normalized model.
- Verify `inputType` normalization and stable ordering.

3. Validation spec closure
- Add explicit failure specs for:
  - operation kind (`command/query`) vs input value kind mismatch
  - dual declaration (`input + parameter`) inconsistency

4. Generation propagation spec closure
- Verify operation metadata propagation across Cozy -> SimpleModeler -> CNCF.
- Ensure no semantic loss for:
  - name
  - kind
  - input/output type
  - input value kind
  - parameters

5. Runtime/projection behavior closure
- Verify generated operation metadata is visible and deterministic in:
  - `meta.*`
  - `help`
  - `describe`
  - `schema`
  - openapi-related projection where applicable
- Verify command/query runtime semantics remain consistent with OP-04 baseline.

6. Phase document closure
- Mark OP-05 DONE in checklist and phase summary.
- Close Phase 8 when completion check conditions are satisfied.

## Out of Scope

- New grammar/features not required by Phase 8 contract.
- Refactoring unrelated runtime/projection architecture.
- Policy model redesign beyond existing Job/Event baselines.

## Suggested File Targets

### CNCF

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/component/ComponentLogicOperationDefinitionSemanticsSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/projection/AggregateViewProjectionAlignmentSpec.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/cli/CommandExecuteComponentSpec.scala`
- Additional focused spec files under:
  - `/Users/asami/src/dev2025/cloud-native-component-framework/src/test/scala/org/goldenport/cncf/...`

### Cozy (conditional)

- `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
- `/Users/asami/src/dev2025/cozy/src/test/resources/modeler/operation-grammar*.dox`

### SimpleModeler (conditional)

- `/Users/asami/src/dev2025/simple-modeler/src/test/scala/...` (operation propagation-related specs)

## Required Deliverables

1. Executable spec additions/updates that close all OP-05 checklist items.
2. Green focused test results (with exact commands) for impacted areas.
3. Updated phase documents:
- `docs/phase/phase-8-checklist.md`:
  - OP-05 `Status: DONE`
  - OP-05 detailed tasks `[x]`
- `docs/phase/phase-8.md`:
  - OP-05 checkbox `[x]`
  - work stack final state with no ACTIVE/SUSPENDED
  - `status = close` (or equivalent closed state used in this repo)
4. Final closure report containing:
- changed files
- coverage matrix (which OP-05 task is covered by which spec)
- executed test commands and pass/fail summary
- remaining risks (if any)

## Validation

Run focused suites first; broaden only as needed.

Example command pattern:

```bash
sbt --batch "testOnly org.goldenport.cncf.component.ComponentLogicOperationDefinitionSemanticsSpec org.goldenport.cncf.projection.AggregateViewProjectionAlignmentSpec org.goldenport.cncf.cli.CommandExecuteComponentSpec"
sbt --batch "testOnly org.goldenport.cncf.job.SCENARIO.JobLifecycleScenarioSpec"
```

Conditional cross-repo validation:

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"

cd /Users/asami/src/dev2025/simple-modeler
sbt --batch "testOnly *Operation*"
```

If wildcard suites are noisy, list concrete spec classes explicitly.

## Definition of Done (OP-05)

OP-05 is DONE when all conditions hold:

1. Parse/AST/normalization/validation specs are explicitly covered.
2. Generation propagation is verified across required repo boundaries.
3. Runtime/projection visibility and command/query semantics remain green.
4. Focused impacted tests pass with reported commands/results.
5. Phase 8 docs are updated consistently and indicate closure readiness.
