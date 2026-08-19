# Hygiene Resolution Task Handoff

Status: RESOLVED
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-task /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-hygiene-resolution-task-handoff-06.md

## Purpose

Make the identified public Scala API labels camelCase. The user explicitly
authorized a non-compatible source migration, so no deprecated aliases are
retained.

## Source Evidence

- Hygiene ID: `HYG-P57.3-002` — RESOLVED
- Source journal:
  `docs/journal/2026/08/2026-08-13-phase-57.3-hygiene-follow-up.md`
- Discovery: 2026-08-13 AES-06R corrective re-review.
- Prior evidence: corrective focused validation
  `36237-20260813T034150Z` (107/107) and clean focused re-review; Phase 57.3
  final validation `69427-20260813T043639Z` (440 suites, 3,224/3,224).

## Frozen Boundary

### Production targets

- `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`
- `src/main/scala/org/goldenport/cncf/mcp/McpJsonRpcAdapter.scala`
- `src/main/scala/org/goldenport/cncf/operationtool/OperationToolModel.scala`
- `src/main/scala/org/goldenport/cncf/path/Alias.scala`

### Required migration

- Make these public parameter labels canonical camelCase names:
  `extraComponents`, `protocolVersionHeader`, `maximumCalls`,
  `maximumInputBytes`, `maximumResultBytes`, `maximumConcurrency`,
  `toolSetId`, and `componentName`.
- Make the public Alias compatibility constant names canonical camelCase:
  `configKey`, `compatibilityConfigKey`, and `defaultForbiddenShortcuts`.
- Update in-repository named-argument callers to canonical labels.

### Compatibility constraints

- Do not change runtime behavior, configuration keys, wire/protocol headers,
  CML/JSON shape, or default values.
- The user authorized removal of the retired source labels and constants.
- Do not add `@deprecatedName`, deprecated aliases, or compatibility overloads.

### Executable evidence targets

- `src/test/scala/org/goldenport/cncf/mcp/McpJsonRpcAdapterSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationtool/OperationToolSourceSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationtool/OperationToolRuntimeConfigurationSpec.scala`
- `src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala`

## Required Validation

1. Run focused tests covering MCP adapter invocation, OperationTool runtime
   configuration, and Alias resolution.
2. Review every changed public declaration and in-repository named-argument
   caller for canonical label use and absence of deprecated compatibility.
3. Run the full validation selected by `cncf-goal-task` before accepting the
   resolved task.

## Completion Contract

Complete only when the four production targets and all affected callers use
the canonical labels, no retired source-compatible aliases are retained, the
executable evidence passes, and the task journal records validation and
acceptance evidence. Do not broaden into unrelated naming cleanup.

## Resolution status

Status: RESOLVED.

The user explicitly authorized this non-compatible source-API migration. The
deprecated compatibility aliases and `@deprecatedName` annotations described
above were intentionally omitted.

Validation Evidence: focused CNCF suites 51/51, post-review-fix suites 22/22,
and final CNCF suite 3,262/3,262 (443 suites), all with zero failures.
Review Evidence: one full review and a clean focused re-review closed the
private-parameter naming blocker.
Acceptance Commit: `50a34cdbfc95384788eb3140a07734b99a6b9e21`
(`refactor: canonicalize public naming APIs`).
