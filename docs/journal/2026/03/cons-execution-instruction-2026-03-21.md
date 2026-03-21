# Docs Consistency Execution Instruction (Architecture Review Follow-up)

status=ready
published_at=2026-03-21
owner=cncf-runtime

## Goal

Execute architecture-review follow-up items and resolve docs/implementation inconsistencies:

- CONS-P1-01
- CONS-P1-02
- CONS-P2-01
- CONS-P2-02
- CONS-P2-03
- CONS-P3-01

## Execution Policy

- Spec compliance is prioritized over backward compatibility.
- Breaking changes are allowed for this work item.
- Do not add new production implementation into deprecated `org.goldenport.cncf.config.*`.
- Keep core/CNCF boundary discipline.

## Scope Inputs

- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/docs-consistency-handoff-2026-03-21-r3.md`
- `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/cons-p1-01-handoff.md`

## Work Order (Strict)

1. CONS-P1-01
2. CONS-P1-02
3. CONS-P2-01
4. CONS-P2-02
5. CONS-P2-03
6. CONS-P3-01
7. Integrated validation + handoff update

## Detailed Instructions

### 1) CONS-P1-01: admin.system.status

- Implement `admin.system.status` as first-class operation.
- Keep semantics separation:
  - `ping` = reachability
  - `health` = operability
  - `status` = structured runtime state
- Implement minimum status schema:
  - `status`
  - `timestamp`
  - `uptime`
  - job metrics (when available)
- Align docs/spec and executable specs.

Done when:
- Spec and implementation expose consistent operation set and contract.

### 2) CONS-P1-02: path-resolution staged adoption

Adopt spec direction, but keep staged rollout:

1. Limit `path-resolution` adoption to `CLI command` first.
2. Resolve current spec contradictions (E3/E4).
3. Introduce `PathResolution` before `CncfRuntime` command execution with feature flag.
4. Shrink `OperationResolver` responsibility to post-resolution lookup.
5. Prepare HTTP/script rollout notes only after CLI regression is stable.

Done when:
- CLI command path is aligned with updated path-resolution contract.
- Responsibility boundary between `PathResolution` and `OperationResolver` is explicit in code/spec.

### 3) CONS-P2-01: config-resolution root detection

- Implement upward project-root detection according to spec.
- Preferred implementation location: production path under `org.goldenport.configuration.*`.
- If core-side location is unavailable in this repository, add minimal bridge and record follow-up.
- Do not implement the new canonical behavior in deprecated `org.goldenport.cncf.config.*`.

Done when:
- Project-root detection behavior is spec-consistent and test-covered.

### 4) CONS-P2-02: output-format suffix contract

- Implement spec-defined suffix-based output-format selection.
- Define and apply deterministic precedence with `--format` / request property.
- Apply consistently across command/runtime surfaces in scope.

Done when:
- Suffix/property contract is deterministic and docs/spec match implementation.

### 5) CONS-P2-03: meta API consistency

- Update meta design/spec docs to include implemented `meta.statemachine`.
- Ensure listed API set matches actual runtime exposure.

Done when:
- `meta.*` docs and implementation are fully aligned.

### 6) CONS-P3-01: docs language consistency

- Convert docs under `docs/` to English, except where Japanese is required by I18N purpose.
- Add explicit exception rule/list for I18N-required documents.
- Remove language-policy self-contradiction in rules docs.

Done when:
- No contradiction remains between language rule and document content policy.

### 7) Integrated validation and closure

- Run focused specs for each item, then impacted integration suite.
- Run compile and docs consistency grep checks.
- Update closure notes in journal handoff.

## Suggested Validation Commands

```bash
rg -n "admin\\.system\\.status|meta\\.statemachine|path-resolution|output-format|config-resolution" docs src/main/scala src/test/scala
sbt "testOnly org.goldenport.cncf.component.* org.goldenport.cncf.cli.* org.goldenport.cncf.subsystem.resolver.* org.goldenport.cncf.config.*"
sbt compile
```

## Deliverables

1. Code changes for CONS-P1/P2 implementation items.
2. Spec/doc updates for consistency.
3. Executable specs proving new behavior and boundaries.
4. Updated closure journal:
   - `/Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/03/docs-consistency-handoff-2026-03-21-r3.md` (or successor close note)

## Final Acceptance Criteria

- [ ] CONS-P1-01 complete and verified
- [ ] CONS-P1-02 complete and verified (CLI command scope)
- [ ] CONS-P2-01 complete and verified
- [ ] CONS-P2-02 complete and verified
- [ ] CONS-P2-03 complete and verified
- [ ] CONS-P3-01 complete and verified
- [ ] No unresolved docs/implementation inconsistency remains for listed items
