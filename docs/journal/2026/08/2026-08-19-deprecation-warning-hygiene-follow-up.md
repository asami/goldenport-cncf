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
| HYG-H57-DEPRECATION-002 | READY | 14 mechanically equivalent production deprecated API callers | Invocation `29032-20260819T034346Z` | Frozen ready Batch 2; `URL` and `HttpRequest.url` moved to DEV candidates |
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

## HYG-H57-DEPRECATION-002 — Production deprecated API caller partition

The detailed compiler invocation `29032-20260819T034346Z` identifies 16
production deprecations. Fourteen have mechanically equivalent replacements:
six `failValueInvalid` calls use the existing identical-body
`Consequence.valueInvalid`; four `MapOps.+` calls have disjoint literal keys;
two compiler-inserted copying Array conversions become explicit
`.toIndexedSeq`; and two `Char.+` calls become interpolation with the same
character and suffix. These 14 locations are now admitted by the READY Batch 2
handoff.

The two remaining diagnostics are not admitted as Hygiene: `new URL(...)` in
Docker readiness can alter parsing/exception behavior, and `HttpRequest.url`
is a deprecated public compatibility field whose absence may alter query
fallback for externally constructed requests. They are separately scheduled as
Development Candidates below.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## Development Candidates — HYG-H57-DEPRECATION-002 exclusions

### DEV-H57-DEPRECATION-URL-001 — Docker readiness URL construction contract

Source evidence: `DockerServiceContainerGateway.scala:415` constructs a URL
for the readiness GET request. A `URI`-based replacement can differ in parsing
and exception behavior before the existing `Try(...).getOrElse(false)` policy
is applied. The selected policy is strict URI construction:
`URI.create(s"http://127.0.0.1:$hostport$path").toURL`. Invalid URI text must
therefore follow the existing false-on-error readiness result rather than being
accepted by implementation-dependent `URL` parsing. Implement this as an
ordinary behavior task with focused valid-URI and invalid-URI readiness
specifications; it remains outside the non-behavioral Hygiene batch.

Candidate Triage: COMPLETED
Canonical ID: DEV-H57-DEPRECATION-URL-001
Disposition: STRATEGY_ITEM
Strategy Record: docs/strategy/cncf-development-strategy.md#960-http-url-compatibility-migration
Target Phase: -
Triaged On: 2026-08-19

### DEV-H57-DEPRECATION-HTTPREQUEST-001 — HttpRequest URL-field compatibility

Source evidence: `Subsystem.scala:2360` falls back to the deprecated public
`HttpRequest.url` field when `context.originalUri` is absent. Removing that
fallback changes the observable query resolution of directly constructed
requests and needs a goldenport-core ownership/ABI decision plus an executable
request-construction compatibility specification.

Candidate Triage: COMPLETED
Canonical ID: DEV-H57-DEPRECATION-HTTPREQUEST-001
Disposition: STRATEGY_ITEM
Strategy Record: docs/strategy/cncf-development-strategy.md#960-http-url-compatibility-migration
Target Phase: -
Triaged On: 2026-08-19

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
