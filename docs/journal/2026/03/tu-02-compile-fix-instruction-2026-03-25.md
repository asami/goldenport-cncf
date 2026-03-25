# TU-02 Compile-Fix Instruction (textus-user-account)

status=ready
published_at=2026-03-25
owner=textus-user-account

## Goal

Restore green build for `textus-user-account` and complete TU-02 implementation
for package/layout rules without breaking CNCF entity persistence integration.

Primary target:

- `/Users/asami/src/dev2026/textus-user-account`

## Current Failure Snapshot

Observed command:

```bash
cd /Users/asami/src/dev2026/textus-user-account
sbt --batch compile
```

Observed result:

- failed (`62 errors`)
- major error cluster:
  - missing types: `EntityPersistable`, `EntityPersistableCreate`,
    `EntityPersistent`, `EntityPersistentCreate`, `EntityPersistentUpdate`,
    `EntityPersistentQuery`
  - missing givens required by `entity_create`, `entity_update`, `entity_search`
  - failures occur in:
    - generated sources under `target/scala-3.3.7/src_managed/...`
    - handwritten glue such as `ComponentFactory.scala`

## Working Hypothesis

- Recent Cozy/CNCF model/type migration changed expected persistence type names
  and/or import paths.
- Generated code and local handwritten code are no longer aligned on the same
  entity persistence contract.

## Scope

In scope:

1. Align generated entity persistence contract with current CNCF/SimpleModeler expectations.
2. Fix handwritten integration code in `textus-user-account` to use the same contract.
3. Keep TU-02 package requirements intact:
  - configurable generated Component package
  - EntityValue package `${package}/entity/*`
4. Run focused validation and produce handoff report.

Out of scope:

- TI-* identity subsystem work.
- Broad refactors unrelated to compile restoration.

## Execution Policy

- Drive from `textus-user-account`.
- Update `cozy` / `sbt-cozy` / `cloud-native-component-framework` only if required.
- Related project updates and `sbt` executions require no confirmation prompt.

## Suggested Debug Order

1. Contract diff capture
- Compare failing generated imports/types with latest generated output contract
  from Cozy/SimpleModeler.
- Identify canonical persistence interfaces and givens that runtime now expects.

2. Generator alignment
- If generator emits stale type names, patch in Cozy/SimpleModeler first.
- Re-generate in `textus-user-account` and re-check error surface.

3. Local integration alignment
- Patch handwritten files (`ComponentFactory.scala`, CLI/main glue if needed)
  to import/use current canonical persistence types and givens.

4. Package rule integrity check
- Verify generated component package override still works.
- Verify EntityValue output remains under `${package}/entity/*`.

## Suggested File Targets

- textus-user-account:
  - `/Users/asami/src/dev2026/textus-user-account/src/main/scala/org/simplemodeling/textus/useraccount/ComponentFactory.scala`
  - `/Users/asami/src/dev2026/textus-user-account/build.sbt`
  - `/Users/asami/src/dev2026/textus-user-account/src/main/cozy/user-account.cml`
- cozy (conditional):
  - `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`
  - `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
- sbt-cozy (conditional):
  - `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`
- cncf (conditional):
  - only when canonical persistence contract ambiguity must be resolved at runtime boundary

## Required Deliverables

1. Compile restored for `textus-user-account`.
2. TU-02 package/layout requirements preserved and verified.
3. Focused validation results with exact commands.
4. Cross-repo change list (if any) with commit ids.
5. Progress update to Phase 10 docs:
- `phase-10-checklist.md` TU-02 status update (`ACTIVE` or `DONE`)
- `phase-10.md` checkbox sync

## Validation Commands

```bash
cd /Users/asami/src/dev2026/textus-user-account
sbt --batch clean
sbt --batch compile
sbt --batch test
sbt --batch cozyGenerate
sbt --batch cozyBuildCAR
sbt --batch cozyBuildSAR
```

Conditional (only if touched):

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"

cd /Users/asami/src/dev2026/sbt-cozy
sbt --batch test
```

## Definition of Done

This instruction is DONE when:

1. `textus-user-account` compile/test are green.
2. CAR/SAR generation still succeeds.
3. No unresolved persistence-contract type errors remain.
4. Package rules (component package config + EntityValue `${package}/entity/*`) remain effective.
5. Phase 10 progress docs are updated consistently.
