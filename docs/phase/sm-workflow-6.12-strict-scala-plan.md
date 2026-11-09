# CNCF capability handoff for sm-workflow Phase 6.12

Stage Status:
- Current status: OPEN / planned qualification
- Owner: CNCF development (`CNCF開発`), canonical goldenport-cncf main
- Update rule: Record actual existing capability and shared-rule consumption.
- Closure basis: EXEC-01–EXEC-04 below; no new runtime API is assumed.

Planned: 2026-11-09
Consumer: [sm-workflow Phase 6.12](../../../sm-workflow/docs/phase/phase-6.12.md)
Coordination: [delivery dependencies](../../../sm-workflow/docs/phase/sm-workflow-6.12-dependencies.md)
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

## Work and acceptance ledger

- [ ] EXEC-01: Return actual typed FixCheck/Compile/test capability, configuration
      and retained-result contracts with canonical revision and invocation limits.
      Qualify existing behavior before proposing any new public operation.
- [ ] EXEC-02: In a tiny ordinary SBT target, show a selected lint violation and
      subsequent correction return through normal UoW execution as actual check
      outcomes. Reuse qualifying existing evidence and preserve actual failures;
      no AI-side SBT execution or fabricated result/termination records.
- [ ] EXEC-03: Once SLP-RULES is accepted, consume its shared baseline/team package
      in CNCF's own build where applicable. Preserve local differences and scoped
      existing debt; do not duplicate rule implementations or make unrelated
      repository-wide cleanup a prerequisite.
- [ ] EXEC-04: Return SLP-EXEC with source/artifact selection, actual configuration
      surface, results, review disposition and known gaps. If existing APIs suffice,
      state explicitly that no runtime implementation change is needed.

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
