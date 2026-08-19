# Hygiene Resolution Batch Handoff

Status: READY
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md

## Purpose

Remove the 14 production deprecated API callers whose replacement is already
proven mechanically equivalent. The remaining `java.net.URL` and
`HttpRequest.url` diagnostics are excluded: each requires a separately planned
compatibility decision and is recorded as a Development Candidate.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-DEPRECATION-002 | `docs/journal/2026/08/2026-08-19-deprecation-warning-hygiene-follow-up.md` | `29032-20260819T034346Z`; core API inspection | HP-001 | Remove 14 mechanically equivalent production deprecation callers without changing behavior. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-semantic-api-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Allowed behavior change: none.
- Preserve paths: `DockerServiceContainerGateway.scala`, `Subsystem.scala`,
  all tests, all existing dirty Scala and journal paths, and every source path
  not named by HP-001.
- Prohibited expansion: new failure taxonomy, public API, compatibility layer,
  URL parsing policy, transport behavior, test expectation change, or upstream
  goldenport-core change.

## Evidence Partition

The detailed production compile `29032-20260819T034346Z` reports 16
deprecations. HP-001 admits 14 locations whose replacement is already defined
and equivalent:

- `Consequence.failValueInvalid(value, dt)` to `Consequence.valueInvalid(value, dt)`:
  the existing core implementations both construct
  `Consequence.Failure(Conclusion.valueInvalid(value, dt))`.
- `MapOps.+` varargs to `++ Map(...)`: all four base maps contain only `dsl`,
  `operation`, and `aggregate`, while the appended keys are distinct
  `command` and `entity_id`.
- compiler-inserted Array-to-immutable-indexed-sequence conversion to explicit
  `.toIndexedSeq`: the compiler identifies its existing conversion as copying,
  and the explicit replacement retains that copy semantics.
- `Char.+(String)` to string interpolation: each use concatenates an uppercased
  initial character with an unchanged String suffix.

`DockerServiceContainerGateway.scala:415` (`new URL(...)`) and
`Subsystem.scala:2360` (`HttpRequest.url`) are excluded. The former may change
URL parsing/exception behavior inside readiness polling; the latter can alter
the behavior of externally constructed `HttpRequest` values whose deprecated
`url` field is populated without `context.originalUri`.

## HP-001 — Canonicalize proven mechanical production API callers

- Hygiene IDs: HYG-H57-DEPRECATION-002
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets:
  - `src/main/scala/org/goldenport/cncf/action/ActionCallFeaturePart.scala:1549,1582,1683,1717`
  - `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeBootstrapPart.scala:1172,1255`
  - `src/main/scala/org/goldenport/cncf/http/StaticFormAppRendererCorePart.scala:226`
  - `src/main/scala/org/goldenport/cncf/http/WebTableColumnResolver.scala:78`
  - `src/main/scala/org/goldenport/cncf/information/InformationModel.scala:135`
  - `src/main/scala/org/goldenport/cncf/knowledge/KnowledgeModel.scala:22,39,64,195,450`
- Allowed repair: only `failValueInvalid` to `valueInvalid`; `MapOps.+` varargs
  to `++ Map(...)`; the two explicit copying `.toIndexedSeq` calls; and the two
  string-interpolation expressions. Retain every value, key, ordering,
  overload, and return type.
- Prohibited expansion: change a callee declaration, failure taxonomy, URL or
  query handling, Array ownership/mutation policy, template-label behavior,
  executable specification, or add a compatibility utility.
- Focused validation: detailed production compile with `-deprecation`; focused
  `ActionCallAggregateResolveSpec`, `RuntimeLaunchFailureSpec`,
  `StaticFormAppRendererSpec`, `InformationIdentityBindingSpec`, and
  `KnowledgeSpaceSpec`; `git diff --check`.
- Dependencies: None.

## Final Focused Review

- Exact target programs/files: the six HP-001 targets only.
- Required checks: all 14 compiler diagnostics are absent; the core
  `valueInvalid` failure construction remains identical; Map keys/values are
  unchanged; Array conversion remains copying; generated labels are bytewise
  equivalent; no URL/query source path is touched.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework`: `sbt --batch test`

## Completion Contract

- Do not absorb the two Development Candidates or any additional deprecation
  family into this batch.
- After admission, commit only after the focused review and one full validation
  gate pass on the reviewed tree.

## Non-goals

- Migrating `java.net.URL` or `HttpRequest.url` without their separately
  planned compatibility contracts.
