# EX-01 Instruction (Phase 9 Executable Spec Closure)

status=ready
published_at=2026-03-22
owner=cncf-spec

## Goal

Close Phase 9 by completing EX-01:

- executable spec closure across Cozy / sbt-cozy / CNCF
- phase document closure updates

## Background

Current Phase 9 status:

- CS-01: DONE
- CS-02: DONE
- PK-01: DONE
- PK-02: DONE
- RT-01: DONE
- EX-01: PLANNED

Phase 9 can be closed only after EX-01 is done and phase docs are aligned.

## In-Scope

1. Parse/model spec closure (Cozy)
- Confirm Component/Subsystem grammar parse and invalid-case specs are green.

2. Packaging spec closure (sbt-cozy)
- Confirm CAR/SAR layout and precedence metadata specs are green.

3. Runtime integration spec closure (CNCF)
- Confirm CAR/SAR intake, manifest validation, precedence, and projection visibility specs are green.

4. Closure documentation
- Update Phase 9 summary/checklist to DONE/close state consistently.
- Record verification commands/results snapshot.

## Out of Scope

- New runtime feature implementation.
- New domain implementation (`textus-user-account`, `textus-identity`).
- Additional packaging feature expansion beyond current contract.

## Target Repositories

- Cozy: `/Users/asami/src/dev2025/cozy`
- sbt plugin: `/Users/asami/src/dev2026/sbt-cozy`
- CNCF: `/Users/asami/src/dev2025/cloud-native-component-framework`

## Required Deliverables

1. Green focused test report for all three repos.
2. `docs/phase/phase-9-checklist.md`:
- EX-01 `Status: DONE`
- EX-01 tasks `[x]`
3. `docs/phase/phase-9.md`:
- EX-01 checkbox `[x]`
- Work Stack F -> DONE
- `status = close`
4. Final closure note with:
- executed commands
- pass/fail summary
- residual risks (if any)

## Validation

Execute focused suites:

```bash
cd /Users/asami/src/dev2025/cozy
sbt --batch "testOnly cozy.modeler.ModelerGenerationSpec"

cd /Users/asami/src/dev2026/sbt-cozy
sbt --batch test

cd /Users/asami/src/dev2025/cloud-native-component-framework
sbt --batch "testOnly org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec org.goldenport.cncf.projection.AggregateViewProjectionAlignmentSpec"
```

## Definition of Done

EX-01 is DONE when:

1. Focused specs are green in Cozy, sbt-cozy, and CNCF.
2. EX-01 tasks are all checked in `phase-9-checklist.md`.
3. `phase-9.md` and `phase-9-checklist.md` are consistent.
4. Phase 9 status is set to closed.
