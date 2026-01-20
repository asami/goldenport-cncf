# Ping Special-Casing Audit and Removal Proposal

Purpose: Record the audit of the remaining ping / HelloWorld special casing and define a concrete removal plan anchored in Phase 2.8 resolver work.

## Background

`admin.system.ping` and the HelloWorld demo started life as a hard-coded “always succeed” shortcut to bootstrap early infrastructure. Phase 2.8 now stabilizes alias handling, canonical selector construction, and the resolver table, so the runtime no longer needs bespoke ping logic to satisfy demos.

## Audit Results

1. **`ComponentLogic._ping_action_` intercepts `admin.system.ping`.**
   - Classification: **(C) structural shortcut requiring removal**
   - Evidence: runtime logic directly matches `service.contains("admin.system") && operation == "ping"` and bypasses protocol resolution to emit `PingAction`, even though `AdminComponent` already exposes the same operation.

2. **Alias + resolver rewriting for `ping`.**
   - Classification: **(A) acceptable operational example**
   - Evidence: `PathPreNormalizer.rewriteSelector` and `AliasResolver` rewrite `/ping` or the keyword `ping` to the canonical `admin.system.ping` before the resolver sees it, and `AliasResolutionSpec` documents the behavior.

3. **Documentation and checklist guidance referencing `admin.system.ping`.**
   - Classification: **(B) comment-only guidance**
   - Evidence: Phase 2.6/2.8 notes and HelloWorld documentation repeatedly single out `admin.system.ping` (e.g., `docs/notes/phase-2.6-demo-done-checklist.md`) as the demo anchor, but these are descriptive rather than executable shortcuts.

## Removal Strategy

- Expectation: the canonical resolver path is already established in Phase 2.8; after executable specs confirm end-to-end routing for `admin.system.ping` (including aliases), `_ping_action_` is deleted within Phase 2.8 with no semantic changes.
- Required executable specs:
  1. **Resolver-driven ping coverage:** a spec exercising `OperationResolver` (via `Subsystem` / `ComponentSpace`) that confirms selectors `admin.system.ping` and its alias `ping` both resolve to the builtin admin operation without helper shortcuts.
  2. **Canonical ping execution spec:** a spec showing `ComponentLogic` receives a resolver-resolved request for `admin.system.ping` and still produces the `PingAction` response, covering HTTP/CLI surfaces that call into `ComponentLogic` after canonical normalization.

## Phase 2.8 Positioning

- Phase 2.8 work includes the audit, the documentation of this removal plan, and the spec-first confirmation that canonical resolution already covers ping.
- Once the specs pass, the Phase 2.8 workflow immediately removes `_ping_action_`; no extra runtime behavior needs to be coded beyond what the specs already assert.

## Expected Outcome

- No special-casing remains for ping or HelloWorld; the resolver + alias pipeline is the single source of truth.
- All ping routing continues to flow through canonical paths, preserving existing functionality while eliminating duplicated shortcuts.

## Outcome

- Executable specs added:
  - AdminSystemPingResolverSpec
  - AdminSystemPingExecutionSpec
- Both specs passed, confirming canonical resolver-based routing for admin.system.ping and its alias.
- Legacy `_ping_action_` special-casing removed.
- Ping / HelloWorld now rely exclusively on canonical resolver paths.
- Work completed within Phase 2.8.
