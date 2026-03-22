# TU-01 / TU-02 Instruction (Phase 10 Next Execution)

status=ready
published_at=2026-03-22
owner=textus-user-account

## Goal

Advance Phase 10 by:

1. Closing TU-01 (`textus-user-account` contract freeze)
2. Starting and completing TU-02 implementation slice for package/layout requirements

Execution is driven from:

- `/Users/asami/src/dev2026/textus-user-account`

and uses this project as the leverage point to drive required updates in:

- `/Users/asami/src/dev2025/cozy`
- `/Users/asami/src/dev2026/sbt-cozy`
- `/Users/asami/src/dev2025/cloud-native-component-framework`

## Required Policy Alignment

- `textus-user-account` development is the primary driver.
- Update Cozy/CNCF/sbt-cozy only when required to satisfy the component contract.
- Related project updates and `sbt` executions do not require confirmation prompts.

## In-Scope

1. TU-01 contract freeze
- Define/fix operation contract for user-account lifecycle.
- Fix command/query boundaries.
- Fix error/failure expectations.
- Fix package-related contracts:
  - generated Component package name must be configurable
  - EntityValue package must be `${package}/entity/*`

2. TU-02 implementation (current slice)
- Implement package-name configurability for generated Component.
- Implement EntityValue output/package layout as `${package}/entity/*`.
- Integrate with existing generation/runtime metadata path without parallel APIs.

3. Cross-repo minimal updates
- `cozy`: model/generator updates for package rules if needed.
- `sbt-cozy`: packaging/generation path updates if needed.
- `cncf`: only if runtime/projection compatibility requires adjustment.

4. Verification and reporting
- Run focused tests in touched repos.
- Report changed files, commands, results, and unresolved gaps.

## Out of Scope

- Full identity subsystem implementation (`TI-*` scope).
- Broad security model redesign.
- Non-essential refactoring unrelated to TU-01/TU-02.

## Suggested File Targets

- Textus component repo:
  - `/Users/asami/src/dev2026/textus-user-account/src/main/cozy/user-account.cml`
  - `/Users/asami/src/dev2026/textus-user-account/build.sbt`
  - `/Users/asami/src/dev2026/textus-user-account/src/main/scala/...`
- Cozy repo (if required):
  - `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`
  - `/Users/asami/src/dev2025/cozy/src/test/scala/cozy/modeler/ModelerGenerationSpec.scala`
- sbt-cozy repo (if required):
  - `/Users/asami/src/dev2026/sbt-cozy/src/main/scala/org/goldenport/cozy/CozyPlugin.scala`
  - `/Users/asami/src/dev2026/sbt-cozy/src/test/scala/org/goldenport/cozy/CozyCoreSpec.scala`

## Required Deliverables

1. TU-01 contract decision note (concise) covering package rules.
2. TU-02 code changes implementing:
- configurable generated Component package
- EntityValue package layout `${package}/entity/*`
3. Focused test/build results for all touched repos.
4. Phase updates:
- `docs/phase/phase-10-checklist.md`
  - TU-01 `Status: DONE`
  - TU-02 moved to `ACTIVE` or `DONE` depending on completion
- `docs/phase/phase-10.md`
  - checkboxes synchronized with actual progress

## Validation

Run focused checks from textus repo first, then dependency repos if touched.

Example command pattern:

```bash
cd /Users/asami/src/dev2026/textus-user-account
sbt --batch compile
sbt --batch test
sbt --batch cozyGenerate
sbt --batch cozyBuildCAR
sbt --batch cozyBuildSAR

cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"

cd /Users/asami/src/dev2026/sbt-cozy
sbt --batch test
```

Use only commands necessary for actually changed repositories.

## Definition of Done (This Instruction)

This instruction is DONE when:

1. TU-01 contract is explicitly frozen with package rules included.
2. TU-02 package/layout implementation slice is delivered.
3. Focused verification is green for touched repos.
4. Phase 10 docs are updated consistently with real progress.
