# OP-04 Instruction (Runtime/Projection Integration for CML Operations)

status=ready
published_at=2026-03-22
owner=cncf-runtime

## Goal

Complete Phase 8 OP-04 by integrating generated CML operation metadata
into CNCF runtime execution and projection/meta visibility surfaces.

Primary implementation repository is CNCF.

## Execution Policy

- Default: CNCF-only implementation.
- Conditional extension: if runtime integration reveals metadata gaps,
  request minimal follow-up changes in Cozy/SimpleModeler.
- Do not start Cozy/SimpleModeler changes preemptively.

## Background

Current state:

- OP-01 to OP-03 are treated as done in Phase 8 tracking.
- Operation metadata propagation exists across Cozy -> SimpleModeler -> CNCF.
- Projection side exposure was added in CNCF (`3ac3d96`) and focused spec is green.

Remaining OP-04 closure is runtime semantic alignment and deterministic surface consistency.

## In-Scope (CNCF)

1. Runtime operation resolution integration
- Ensure generated `operationDefinitions` are usable by runtime resolution path.
- Remove/avoid ambiguous duplicated routes when operation metadata is present.

2. Execution semantics mapping
- Enforce operation-kind semantic baseline:
  - `Command`: asynchronous Job default path
  - `Query`: synchronous path, or Ephemeral Job where policy requires
- Keep consistency with existing Job/Task execution model from Phase 6.

3. Projection/meta consistency closure
- Ensure `meta.*`, `help`, `describe`, `schema`, and openapi-related projections
  expose operation details consistently and deterministically.
- Keep stable deterministic ordering of operation outputs.

4. Regression safety
- Preserve existing command help and operation discovery behaviors.
- Do not introduce parallel public execution APIs.

5. Documentation alignment
- Update Phase 8 tracking docs after implementation and verification.

## Out of Scope

- New operation grammar redesign (OP-01 area).
- Broad CLI UX redesign unrelated to operation integration.
- Workflow/orchestration feature expansion beyond existing Job model.

## Suggested File Targets (CNCF)

- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/MetaProjectionSupport.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/HelpProjection.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/DescribeProjection.scala`
- `/Users/asami/src/dev2025/cloud-native-component-framework/src/main/scala/org/goldenport/cncf/projection/SchemaProjection.scala`

Use actual touched files as discovered; list them in the execution report.

## Conditional Follow-Up (Only If Needed)

Trigger condition:
- CNCF runtime integration cannot complete due to missing metadata contract
  from generated model.

Then:
- Request minimal contract extension in:
  - `/Users/asami/src/dev2025/cozy`
  - `/Users/asami/src/dev2025/simple-modeler`
- Limit changes to required metadata fields only.
- Return to CNCF and complete OP-04 in same cycle.

## Required Deliverables

1. CNCF runtime integration changes implementing operation-kind semantics.
2. Deterministic projection/meta alignment for operation metadata.
3. Executable specs (or targeted regression specs) covering runtime/projection behavior.
4. Phase document updates:
- `docs/phase/phase-8-checklist.md`:
  - OP-04 `Status: DONE`
  - OP-04 tasks `[x]` as completed
- `docs/phase/phase-8.md`:
  - OP-04 checkbox `[x]`
  - work stack updated accordingly
5. Execution report including:
- changed files
- behavior delta summary
- exact test commands and results
- note whether Cozy/SimpleModeler follow-up was required

## Validation

Run focused impacted tests first.

Example:

```bash
sbt --batch "testOnly org.goldenport.cncf.projection.AggregateViewProjectionAlignmentSpec"
sbt --batch "testOnly org.goldenport.cncf.cli.CommandExecuteComponentSpec"
sbt --batch "testOnly org.goldenport.cncf.job.SCENARIO.JobLifecycleScenarioSpec"
```

If needed, add concrete operation/runtime spec classes touched by this work.

## Definition of Done (OP-04)

OP-04 is DONE when all conditions hold:

1. Runtime resolves and executes generated operation metadata path consistently.
2. Command/Query semantics align with Job/Task baseline policy.
3. Projection/meta surfaces expose operation metadata deterministically.
4. Impacted focused tests are green.
5. Phase 8 tracking docs are updated consistently.
6. Any required cross-repo metadata follow-up is explicitly reported.
