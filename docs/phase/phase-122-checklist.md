# Phase 122 checklist — Typed SBT qualification and shared Scala lint integration

status=planned

Owner: CNCF development (`CNCF開発`), canonical goldenport-cncf main.
Plan: [Phase 122](phase-122.md). The existing EXEC identifiers are retained
for cross-project coordination. All items are OPEN; no implementation has started.

## Stage: Capability qualification and shared-rule handoff

Stage Status:
- Current status: OPEN
- Owner: CNCF development
- Update rule: Record actual existing capability and shared-rule consumption.
- Closure basis: EXEC-01–EXEC-04 below; no new runtime API is assumed.

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
