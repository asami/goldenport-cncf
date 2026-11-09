# Phase 122 — Typed SBT qualification and shared Scala lint integration

status=planned
planned_at=2026-11-09
execution_started=false
owner=CNCF

Progress: [Phase 122 checklist](phase-122-checklist.md). All EXEC-01–EXEC-04
items remain OPEN. This Phase records CNCF's producer work; sm-workflow Phase
6.12 is its consumer, not the CNCF Phase number.

Consumer: [sm-workflow Phase 6.12](../../../sm-workflow/docs/phase/phase-6.12.md)
Coordination: [delivery dependencies](../../../sm-workflow/docs/phase/phase-6.12-dependencies.md)
Existing route: [typed SBT execution](../spec/typed-sbt-execution.md)

## Existing capability and ownership

SLP-EXEC qualifies the existing typed SBT/UoW route, including FixCheck, ordinary
execution/result handling and resolved configuration. CNCF executes the selected
fixed build task; it does not merge application lint profiles, implement new
Scalafix rules, infer a clean result from unavailable analysis or interpret a
check as successful tests. sm-workflow owns profile resolution and workflow FIX;
the build owns the task's actual Scalafix invocation.

SLP-RULES is a reusable build-tool artifact owned by sm-workflow and independently
consumable by CNCF. It must not depend on the sm-workflow application, Core/CNCF
runtime imports or a consumer Phase's closure. Recognition of Goldenport symbols
in semantic analysis does not require executing the observed Program or importing
the whole runtime into the rule artifact. Existing CAR/resource/effect boundary
guarantees remain with their current owners.

## Work and acceptance

The [checklist](phase-122-checklist.md) retains the existing cross-project
delivery IDs EXEC-01–EXEC-04 and is the completion ledger. EXEC-01/02 qualify
the existing execution route, EXEC-03 consumes the independently delivered
shared rules, and EXEC-04 returns the scoped SLP-EXEC handoff. No new runtime
API is assumed.

## Dependencies and boundaries

EXEC-01 can be handed off independently now; EXEC-02 uses a bounded build fixture.
Only EXEC-03 depends on SLP-RULES, not sm-workflow Phase 6.12 completion. The
consumer uses EXEC-01/02 for A integration without waiting for unrelated CNCF
Phases or a whole-tree naming migration. A genuine missing public capability is
a separately assessed gap, not permission to invent a process fallback here.

No CNCF work is assigned to sm-workflow-dev development. Preserve canonical-main
ownership, all current Phases/Goals/Sessions/results/history/repair counts and
unrelated changes; create no repair worktree. This document starts no build,
workflow operation, publication, implementation dispatch, commit or sync.
