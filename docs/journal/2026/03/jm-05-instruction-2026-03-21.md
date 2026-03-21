# JM-05 Instruction (Executable Specifications Closure)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Complete JM-05 by closing remaining executable specification gaps for Phase 6 Job CQRS.

Scope is JM-05 only.

## Background

Phase 6 currently marks:
- JM-01: DONE
- JM-02: DONE
- JM-03: DONE
- JM-04: DONE
- JM-05: ACTIVE

Current status:
- Core Job/Event behavior is implemented and green in focused suites.
- JM-05 is still open because several checklist items are only partially covered by existing specs.

## In-Scope

1. Command sync path spec closure
- Add executable specs for synchronous command execution path and result semantics.
- Distinguish command sync path from control-command sync option.

2. Task-first invariant proof specs
- Add specs that prove ActionCall execution happens via Task/Job path only.
- Prevent direct bypass path regressions.

3. Persistence policy boundary specs
- Add explicit specs for:
  - Query execution -> Ephemeral Job path.
  - Event no-match -> Ephemeral Job path.
  - All other execution paths -> Persistent Job path.

4. Continuation mode coverage closure
- Add explicit same-job continuation spec.
- Keep existing new-job continuation coverage and ensure both modes are validated as a pair.

5. Correlation metadata coverage closure
- Add/extend specs for `correlationId`, `causationId`, `parentJobId` propagation in Job/Event integration paths.

6. Integration regression slice
- Add regression specs across ActionCall/Event/Job integration:
  - command submission -> Job lifecycle -> query readback
  - event reception -> continuation dispatch -> Job linkage

7. Documentation alignment
- Reflect JM-05 completion/progress in Phase 6 tracking docs.

## Out of Scope

- New runtime features beyond spec closure.
- Redesign of Job model, EventBus, or EventStore contracts.
- Distributed scheduler/workflow integration.

## Implementation Constraints

- Keep existing architecture boundaries and package ownership.
- Preserve Given/When/Then style in executable specs.
- Prefer deterministic assertions (ordering, terminal state, metadata presence).
- Do not weaken existing policy checks.
- Do not create competing execution APIs.

## Suggested File Targets

- Job specs:
  - `src/test/scala/org/goldenport/cncf/job/JobControlCommandSpec.scala`
  - `src/test/scala/org/goldenport/cncf/job/JobQueryReadModelSpec.scala`
  - `src/test/scala/org/goldenport/cncf/job/SCENARIO/JobLifecycleScenarioSpec.scala`
  - `src/test/scala/org/goldenport/cncf/job/*` (new spec files allowed)
- Event integration specs:
  - `src/test/scala/org/goldenport/cncf/event/EventReceptionSpec.scala`
  - `src/test/scala/org/goldenport/cncf/component/ComponentFactoryEventReceptionBootstrapSpec.scala`

Use existing spec packages and naming style; avoid parallel test architecture.

## Required Deliverables

1. New/updated executable specs covering all open JM-05 checklist items.
2. Green focused test run for impacted Job/Event/Component specs.
3. Progress updates:
- `docs/phase/phase-6-checklist.md`:
  - JM-05 `Status: DONE`
  - JM-05 detailed tasks `[x]`
- `docs/phase/phase-6.md`:
  - JM-05 checkbox `[x]`
  - Work stack E marked DONE

## Validation

Run focused suite first, then broader impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.job.* org.goldenport.cncf.event.EventReceptionSpec org.goldenport.cncf.component.ComponentFactoryEventReceptionBootstrapSpec"
```

If wildcard scope is noisy, list concrete spec classes explicitly.

## Definition of Done (JM-05)

JM-05 is DONE when all conditions hold:

1. Lifecycle/query/control/async-default coverage remains green.
2. Command sync path has explicit executable specs.
3. Task-first invariant has explicit regression-proof specs.
4. Persistence policy boundary (query/no-match/others) is explicitly verified.
5. Continuation modes (same-job/new-job) are both explicitly covered.
6. Correlation metadata propagation (`correlationId/causationId/parentJobId`) is verified in integration flow.
7. ActionCall/Event/Job regression slice is covered and green.
8. `phase-6.md` and `phase-6-checklist.md` are updated consistently.
