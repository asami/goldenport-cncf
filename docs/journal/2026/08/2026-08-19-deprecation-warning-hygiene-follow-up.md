# Deprecation Warning Follow-up

Status: OPEN
Created: 2026-08-19
Repository: /Users/asami/src/dev2025/cloud-native-component-framework

## Discovery Evidence

- Detailed production compilation: SBT invocation `15277-20260819T020915Z`;
  571 sources compiled and 93 total warnings, including deprecated named
  parameter labels, Scala/JDK APIs, and structured-failure APIs.
- Full validation: SBT invocation `16358-20260819T021219Z`; 443 suites,
  3,262 succeeded, 0 failed. It reported 76 production and 419 test-source
  deprecation warnings.
- These are nonblocking warning-cleanup records. They do not describe a test
  failure or authorize behavior changes.

## Development Item Status

| ID | Status | Scope | Evidence | Disposition |
| --- | --- | --- | --- | --- |
| HYG-H57-DEPRECATION-001 | RESOLVED | Production deprecated named-argument labels | Invocations `13467-20260819T030205Z`, `23484-20260819T033031Z` | Mechanical canonical-label migration; Batch 1 complete |
| HYG-H57-DEPRECATION-002 | BLOCKED | Production deprecated APIs with semantic replacements | Invocation `15277-20260819T020915Z` | Await replacement-level semantic evidence; Batch 2 |
| HYG-H57-DEPRECATION-003 | BLOCKED | Test-source deprecation warnings | Invocation `16358-20260819T021219Z` | Await exact test-source location inventory; Batch 3 |
| HYG-H57-COMPILER-WARNING-004 | BLOCKED | Non-deprecation compiler warnings | Invocation `15277-20260819T020915Z` | Await safety-case classification; Compiler-warning batch |

## HYG-H57-DEPRECATION-001 — Production named-argument callers

The production compiler reports deprecated named-argument labels such as
`observabilityContext`, `traceId`, `executionId`, `httpDriverOption`,
`includeEntityIdEntropy`, `unitOfWorkSupplier`, `unitOfWorkInterpreterFn`,
`commitAction`, `abortAction`, `disposeAction`, and
`operationEvaluationCrossSinkPolicyOption`. The replacements are explicitly
given by the compiler and are flatcase canonical labels. Focused review also
found `ActionEngine.scala:191` calling the existing `ScopeContext.apply`
parameter with `observabilityContext`; that callee's canonical label is already
`observabilitycontext`. This joins the same non-behavioral caller migration
without altering a declaration or value.

Required result: change only named-argument call sites to the announced
canonical labels; retain expression values, call order, overload selection,
and runtime behavior.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-001
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Status: RESOLVED
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: target compile `13467-20260819T030205Z`; focused review `CLEAN`; full test `23484-20260819T033031Z` (3,262 succeeded, 0 failed)
Acceptance Commit: reported externally after commit execution

## HYG-H57-DEPRECATION-002 — Production semantic deprecated APIs

The production compiler also reports deprecations that are not a simple
parameter-label spelling migration: `Consequence.failValueInvalid`, Java
`URL`, `HttpRequest.url`, Scala `MapOps.+`, implicit Array conversion, and
`Char.+`. Their replacements must preserve failure taxonomy, URL/query parsing,
collection shape, and string construction semantics.

Required prerequisite: identify an existing canonical replacement and its
nearest executable specification for each API family before admitting any code
edit. No fallback API, new compatibility wrapper, or behavior rewrite is
authorized by this record.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## HYG-H57-DEPRECATION-003 — Test-source deprecated APIs

The full suite reports 419 deprecation warnings while compiling test sources.
The existing invocation provides the aggregate count but not the per-file
diagnostics needed to freeze a source-file list.

Required prerequisite: run a detail-enabled test compilation that records every
test-source warning location and replacement, then partition only mechanical
test call-site updates from any expectation or fixture semantic changes.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-003
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## HYG-H57-COMPILER-WARNING-004 — Non-deprecation compiler warnings

Detailed production compilation also reports one redundant `try` syntax
warning, one `ComponentDescriptor` indentation warning, seven `JobEngine`
indentation warnings, two unreachable-pattern warnings in configuration codecs,
one non-exhaustive `ConclusionDiagnostics` match, and six unchecked generic
`Consequence` pattern warnings in aggregate/view spaces.

Required prerequisite: separate formatting-only and behavior-preserving type
refinements from the `ConclusionDiagnostics` exhaustivity case, whose missing
cause-kind rendering must be defined before implementation. Do not suppress a
warning or add a catch/fallback merely to silence the compiler.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-COMPILER-WARNING-004
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-compiler-warning-hygiene-batch-handoff.md
Handed Off On: 2026-08-19
