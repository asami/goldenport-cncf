# Deprecation Warning Follow-up

Status: RESOLVED
Created: 2026-08-19
Repository: /Users/asami/src/dev2025/cloud-native-component-framework

All tracked Hygiene items in this follow-up are resolved. The retained URL and
`HttpRequest.url` records below are completed non-Hygiene implementation
records, not open warning-cleanup work.

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
| HYG-H57-DEPRECATION-002 | RESOLVED | 14 mechanically equivalent production deprecated API callers | Compile `66002-20260819T052726Z`; full test `67311-20260819T053029Z` | Batch 2 complete; `URL` and `HttpRequest.url` remain excluded |
| HYG-H57-DEPRECATION-003 | RESOLVED | Test-source deprecated named callers and string syntax | Test compile `82331-20260819T061040Z`; full test `97482-20260819T065407Z` | Named callers and value-equivalent string syntax migrated; Batch 3 complete |
| HYG-H57-COMPILER-WARNING-004 | RESOLVED | Non-deprecation compiler warnings | Focused `12069-20260819T073108Z`; full `12541-20260819T073205Z` | Compiler-warning batch complete; availability diagnostic contract retained as DEV-009 |

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
character and suffix. These 14 locations were completed by the resolved Batch 2
handoff.

The two remaining diagnostics are not admitted as Hygiene: `new URL(...)` in
Docker readiness can alter parsing/exception behavior, and `HttpRequest.url`
is a deprecated public compatibility field whose absence may alter query
fallback for externally constructed requests. They remain outside Hygiene and
are recorded below as implemented non-Hygiene work.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Status: RESOLVED
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: detailed compile `66002-20260819T052726Z`; focused review `CLEAN`; full test `67311-20260819T053029Z` (3,262 succeeded, 0 failed)
Acceptance Commit: reported externally after commit execution

## Non-Hygiene Implementation Records — HYG-H57-DEPRECATION-002 exclusions

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

Record ID: DEV-H57-DEPRECATION-URL-001
Hygiene Disposition: EXCLUDED
Implementation State: PRESENT in checkpoint `7e3439116edd1d1f26b38f10d710a9da42b1b691`
Further Development: none recorded by HYG-002
Strategy Record: -
Recorded On: 2026-08-19

### DEV-H57-DEPRECATION-HTTPREQUEST-001 — HttpRequest URL-field compatibility

Source evidence: `Subsystem.scala:2360` falls back to the deprecated public
`HttpRequest.url` field when `context.originalUri` is absent. Removing that
fallback changes the observable query resolution of directly constructed
requests and needs a goldenport-core ownership/ABI decision plus an executable
request-construction compatibility specification.

Record ID: DEV-H57-DEPRECATION-HTTPREQUEST-001
Hygiene Disposition: EXCLUDED
Implementation State: PRESENT in checkpoint `7e3439116edd1d1f26b38f10d710a9da42b1b691`
Further Development: none recorded by HYG-002
Strategy Record: -
Recorded On: 2026-08-19

## HYG-H57-DEPRECATION-003 — Test-source deprecated APIs

Detail-enabled test compilation `45753-20260819T042645Z` now records the
test-source diagnostic locations and compiler-announced replacements. The
admitted Batch 3 boundary is limited to mechanical named-argument and
value-equivalent string-syntax call-site migrations. The handoff freezes its
exact target inventory.

The deprecated `useApplicationDataStore` test caller is already resolved by
accepted ordinary-task commit `2a90e025405d42dda1f35a1950ee312172642259` and
is excluded. The fixture failures at `EventBusSpec.scala:229` and
`InMemoryJobEngineSpec.scala:476`, and retirement of compatibility scenarios
for `DescriptorPath`, `aggregate_name`, and `install_binding`, are
user-directed ordinary test work. They remain outside HYG-H57-DEPRECATION-003.

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-003
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

Hygiene Status: RESOLVED
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-test-source-hygiene-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: test-source deprecation compile `82331-20260819T061040Z`; focused regression `92129-20260819T064308Z` (57 succeeded, 0 failed); full test `97482-20260819T065407Z` (3,261 succeeded, 0 failed)
Acceptance Commit: reported externally after commit execution

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

Hygiene Status: RESOLVED
Resolution Batch: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-compiler-warning-hygiene-batch-handoff.md
Validated On: 2026-08-19
Validation Evidence: focused test `12069-20260819T073108Z` (56 succeeded, 0 failed); full test `12541-20260819T073205Z` (3,261 succeeded, 0 failed)
Acceptance Commit: reported externally after commit execution
