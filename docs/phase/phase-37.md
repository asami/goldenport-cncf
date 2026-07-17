# Phase 37 - Downstream Runtime Boundary Adoption

Stage Status:
- Current status: PLANNED
- Start condition: Phase 36 is closed and its released CNCF capability surface
  is available to the downstream project.
- Owner: CBD Support runtime-boundary adoption, with CNCF contract
  coordination.
- Update rule: Update this block and `phase-37-checklist.md` whenever a stable
  work-item state changes. Do not activate this phase before Phase 36 closes.

status = planned

## 1. Purpose

Phase 37 applies the explicit CNCF runtime-boundary capabilities from Phase 36
to CBD Support. The migration removes CBD Support's ambient host clock,
configuration, filesystem, and process access without introducing
CBD-specific, Cozy-specific, CAR Review-specific, or CAR ABI-specific types
into CNCF.

This is a downstream adoption phase. It validates that a reusable component can
use the CNCF capability boundary as designed; it does not reopen Phase 36 or
turn the CNCF core into the implementation home of CBD Support.

## 2. Dependencies

- Phase 31 provides component-observable execution time through
  `ExecutionContext`.
- Phase 33 provides logical single-resource access where it remains sufficient.
- Phase 34 provides admitted `ProcessExec`, bounded WorkAreas, terminal
  results, and deterministic process fixtures.
- Phase 35 provides UnitOfWork-owned terminal resource reclamation.
- Phase 36 provides declared configuration, opaque secret references, admitted
  resource trees, WorkArea materialization, and the provider-neutral adapter
  pattern.

Phase 37 starts only after Phase 36 RB-09 records closure and the downstream
project selects the released or explicitly pinned CNCF capability surface.

## 3. Scope

- Replace direct operational clock access with the bound execution-time
  capability.
- Declare CBD Support runtime configuration, including public values and opaque
  secret references, through assembly/runtime configuration.
- Replace direct registered-development/CAR tree access with admitted,
  read-only resource-tree snapshots and bounded Process Execution
  materialization.
- Replace direct JVM process transport with a registered Process Execution
  capability and a CBD-owned provider adapter.
- Preserve distinct process terminal outcomes until the CBD adapter maps them
  into the CBD result/failure vocabulary.
- Prove behavior with deterministic configuration, resource-tree, and Process
  Execution fixtures before optional live-provider integration evidence.
- Run CBD Support CAR lint and document which ambient-boundary findings are
  resolved by the migration.

## 4. Boundaries

- CBD Support does not obtain `Clock.system*`, `System.getenv`, `sys.env`, host
  `Path` values, `ProcessBuilder`, a process handle, executable paths, or shell
  command text through normal component behavior.
- The CBD adapter receives logical inputs, admitted WorkArea-relative paths,
  declared artifacts, and neutral `ProcessExecutionResult` values only.
- Secret values remain runtime/provider-owned. CBD Support receives opaque
  `SecretReference` values and must not place them in diagnostics.
- A newly discovered CNCF gap is proposed and separately scoped in CNCF. It is
  not filled with a CBD-specific runtime API or descriptor escape hatch.
- CAR publication, ABI release, deployment automation, and production secret
  providers remain outside this phase.

## 5. Planned Work Stack

- A (OPEN): BA-01 - Freeze the downstream migration mapping against the closed
  Phase 36 contracts and selected CNCF version.
- B (OPEN): BA-02 - Migrate clock and declared configuration access.
- C (OPEN): BA-03 - Migrate admitted resource-tree and WorkArea inputs.
- D (OPEN): BA-04 - Migrate the bounded external evidence-provider invocation
  and CBD result adapter.
- E (OPEN): BA-05 - Add deterministic executable evidence and boundary lint.
- F (OPEN): BA-06 - Record migration evidence, deferred production work, and
  close Phase 37.

## 6. Completion Conditions

Phase 37 closes only when:

- CBD Support operational behavior uses the bound execution clock and declared
  configuration rather than ambient JVM/OS state;
- known configuration, unavailable configuration, and secret-reference paths
  produce safe structured outcomes;
- only admitted resource trees become external-tool WorkArea input;
- external evidence-provider invocation passes through Process Execution
  capability admission, UnitOfWork lifecycle, and bounded artifact handling;
- fake-runtime tests prove success and each process terminal outcome without a
  live Cozy/provider installation;
- CBD Support CAR lint shows the targeted ambient-boundary warnings resolved or
  explicitly records any remaining independently owned warning; and
- CNCF core remains provider-neutral, and follow-up framework gaps are tracked
  separately.

## 7. Source Record

The non-normative input is
`docs/journal/2026/07/cbd-support-runtime-boundary-handoff-2026-07-17.md`.
The normative upstream contracts are:

- `docs/design/component-runtime-boundary-capabilities.md`;
- `docs/spec/component-runtime-boundary-capabilities.md`;
- `docs/design/process-execution-runtime.md`; and
- `docs/spec/process-execution-runtime.md`.

The downstream implementation and its executable specifications are owned by
the CBD Support repository. This phase document is the CNCF coordination and
dependency record.
