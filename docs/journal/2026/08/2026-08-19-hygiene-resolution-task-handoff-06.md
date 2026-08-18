# Hygiene Resolution Task Handoff

Status: READY
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-task /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-hygiene-resolution-task-handoff-06.md

## Purpose

Resolve `HYG-P57.3-002` by making the identified public Scala API labels
camelCase while preserving source compatibility through explicit, deprecated
aliases for the currently accepted labels.

## Source Evidence

- Hygiene ID: `HYG-P57.3-002`
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
- Retain the current public lower-case labels as explicit deprecated source
  aliases where named-argument callers rely on them.
- Make the public Alias compatibility constant names canonical camelCase:
  `configKey`, `compatibilityConfigKey`, and `defaultForbiddenShortcuts`.
  Preserve the current PascalCase names as explicit deprecated aliases.
- Update in-repository named-argument callers to canonical labels.

### Compatibility constraints

- Do not change runtime behavior, configuration keys, wire/protocol headers,
  CML/JSON shape, or default values.
- Do not remove any accepted public compatibility surface in this task.
- Use the repository's established `@deprecatedName` compatibility pattern for
  parameter labels where applicable; retain explicitly named deprecated
  constants for source compatibility.

### Executable evidence targets

- `src/test/scala/org/goldenport/cncf/mcp/McpJsonRpcAdapterSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationtool/OperationToolSourceSpec.scala`
- `src/test/scala/org/goldenport/cncf/operationtool/OperationToolRuntimeConfigurationSpec.scala`
- `src/test/scala/org/goldenport/cncf/path/AliasResolutionSpec.scala`

## Required Validation

1. Run focused tests covering MCP adapter invocation, OperationTool runtime
   configuration, and Alias resolution.
2. Review every changed public declaration and in-repository named-argument
   caller for canonical label use and retained deprecated compatibility.
3. Run the full validation selected by `cncf-goal-task` before accepting the
   resolved task.

## Completion Contract

Complete only when the four production targets and all affected callers use
the canonical labels, the former public labels remain explicitly deprecated
source-compatible aliases, the executable evidence passes, and the task
journal records validation and acceptance evidence. Do not broaden into
unrelated naming cleanup.

## Resolution status

Status: IMPLEMENTED; validation and commit evidence remain pending parent closure.

The user explicitly authorized this non-compatible source-API migration. The
deprecated compatibility aliases and `@deprecatedName` annotations described
above were intentionally omitted.
