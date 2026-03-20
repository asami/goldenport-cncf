# EV-06 Instruction (CML Event -> Reception -> ActionCall Route)

status=ready
published_at=2026-03-20
owner=cncf-runtime

## Goal

Implement the execution route:

- CML Event definition
  -> Reception input
  -> selective routing
  -> ActionCall execution

Scope is EV-06 only.

## Background

Phase 5 is reopened for EV-06.

Required behavior:

- Reception must route only relevant events.
- Reception must not route irrelevant events (drop deterministically).
- This requirement is mandatory for inter-component and external-system integration.

## In-Scope

1. CML Event mapping
- Define canonical mapping from CML Event definition to CNCF reception/runtime event model.

2. Reception input surface
- Implement event-triggered Reception entry point.
- Normalize incoming event shape needed for routing decision.

3. Selective routing
- Implement deterministic filtering at Reception:
  - route target events
  - drop non-target events
- Define deterministic matching rules (event name/kind and other required selectors).

4. ActionCall integration
- Resolve routed event to target Action deterministically.
- Execute via ActionCall path only (no direct component invocation).

5. Runtime interaction
- Align this route with EventBus/EventStore behavior (persistent/non-persistent policy).
- Preserve consequence/observation semantics on route/drop/deny/failure.

6. Documentation alignment
- Reflect EV-06 completion/progress in Phase 5 tracking docs.

## Out of Scope

- Redesign of EventStore/EventBus baselines already completed in EV-02/EV-03.
- New policy model redesign beyond EV-04 baseline.
- Distributed transport guarantees.

## Implementation Constraints

- Keep core/CNCF boundary discipline.
- Do not parse or branch on CanonicalId.
- Preserve deterministic behavior for routing and drops.
- Do not introduce competing public APIs when existing boundaries can host EV-06.

## Suggested File Targets

- Event/reception routing integration:
  - `src/main/scala/org/goldenport/cncf/event/...`
  - existing runtime/action execution integration path under CNCF packages
- CML/cozy generation touchpoint if needed for wiring metadata:
  - `/Users/asami/src/dev2025/cozy/src/main/scala/cozy/modeler/Modeler.scala`
- Specs:
  - `src/test/scala/org/goldenport/cncf/...` for end-to-end route and drop behavior

Use existing package boundaries; do not create parallel architecture tracks.

## Required Deliverables

1. Code changes implementing CML Event -> Reception -> ActionCall route.
2. Deterministic selective routing in Reception:
- relevant events are routed
- irrelevant events are dropped (not routed)
3. Executable specs validating:
- end-to-end route from CML Event definition to ActionCall execution
- deterministic routing/filtering behavior
- non-target-event drop behavior
- failure paths (unknown event, subscription mismatch, policy denial)
4. Progress updates:
- `docs/phase/phase-5-checklist.md`:
  - EV-06 `Status: DONE`
  - EV-06 detailed tasks `[x]`
- `docs/phase/phase-5.md`:
  - EV-06 checkbox `[x]`
  - Current Work Stack: F as DONE
  - close Phase 5 only if completion conditions are fully satisfied

## Validation

Run focused tests first, then impacted suite.

Example command pattern:

```bash
sbt "testOnly org.goldenport.cncf.*Event* org.goldenport.cncf.*Reception* org.goldenport.cncf.*ActionCall*"
```

If wildcard selection is noisy, list concrete spec classes explicitly.

## Definition of Done (EV-06)

EV-06 is DONE when all conditions hold:

1. CML Event -> Reception -> ActionCall route is implemented.
2. Reception selectively routes only relevant events.
3. Irrelevant events are deterministically dropped and never routed.
4. Route/drop/failure behavior is covered by executable specs.
5. Related EV-06 tests pass.
6. `phase-5.md` and `phase-5-checklist.md` are updated consistently.
