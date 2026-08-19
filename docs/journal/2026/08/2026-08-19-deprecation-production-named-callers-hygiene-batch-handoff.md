# Hygiene Resolution Batch Handoff

Status: COMPLETE
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md

## Purpose

Remove production deprecated named-argument warning noise by migrating each
identified caller to the compiler-announced canonical parameter label, and
complete the already-implemented exhaustive runtime-profile admission repair.
Neither outcome changes callee declarations, argument values, overload
resolution, or the Fixed-profile admission policy.
The batch also includes the review-identified `ActionEngine.scala:191` call to
the already-canonical `ScopeContext.apply(observabilitycontext = ...)` label.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-DEPRECATION-001 | `docs/journal/2026/08/2026-08-19-deprecation-warning-hygiene-follow-up.md` | `15277-20260819T020915Z`, focused review | HP-001 | No legacy named-argument labels remain in the frozen targets. |
| HYG-H57-WARN-001 | `docs/journal/2026/08/2026-08-19-cncf-runtime-match-warning-hygiene-follow-up.md` | `13855-20260819T020629Z` | HP-001 | Runtime profile admission is exhaustive and admits standalone HOME profiles only for Fixed. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-DEPRECATION-001
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-deprecation-production-named-callers-hygiene-batch-handoff.md
Handed Off On: 2026-08-19

## Frozen Boundary

- Allowed repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Allowed behavior change: none.
- Preserve paths: all paths outside HP-001 and the existing uncommitted journal
  cleanup paths.
- Prohibited expansion: callee parameter declaration changes, compatibility
  aliases, `@deprecatedName` additions/removals, API redesign, Scala/JDK API
  migrations, test-source migration, source decomposition, or semantic changes.

## Baseline and Execution Ledger

- Baseline repository HEAD: `3de8f2d2f959fe86e3bdec8e714ae9b78a818fae`.
- Baseline target-diff identity after the first 59 substitutions and before the
  review correction: `05b2e6aa88e1c99de76452382cba1a4c8988f78e3c61116229a6b0a50c4f0859`.
- Frozen final source-diff identity before focused validation and review:
  `b626134f4f65ec430ef245124eb909dcb2ff3d18b4bfdabe867ca1acdec3c1f4`.
- Owned source set: exactly the 17 paths enumerated in HP-001; no test,
  callee-declaration, build, generated, or sibling-repository path is owned.
- Integration edges: every owned call retains its existing callee and argument
  expression; the only allowed label mappings are the compiler-announced
  flatcase labels and `ActionEngine.scala:191` to `ScopeContext.apply`'s
  already-declared `observabilitycontext` parameter.
- Scope expansion authorization: the user explicitly authorized inclusion of
  `CncfRuntimeInstanceLifecyclePart.scala:620` as HYG-H57-WARN-001 after the
  first final gate. The frozen source diff remains
  `b626134f4f65ec430ef245124eb909dcb2ff3d18b4bfdabe867ca1acdec3c1f4`;
  only the accepted HYG set changed.
- Preserve identity: current journal cleanup and the other three batch handoffs
  are not batch-owned changes.
- Stop conditions: stop before any change to a callee declaration, argument
  expression/order, overload, test, or non-named-label warning family; stop if
  a required label is not already declared by the selected callee.
- Final review: one independent focused reviewer checks the exact owned source
  set against this ledger and must return `CLEAN`; any blocker ends the batch
  without an automatic repair/re-review loop.

## HP-001 — Canonicalize production named-argument callers

- Hygiene IDs: HYG-H57-DEPRECATION-001, HYG-H57-WARN-001
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`
- Targets:
  - `src/main/scala/org/goldenport/cncf/action/ActionEngine.scala`
  - `src/main/scala/org/goldenport/cncf/blob/BlobUrnResolver.scala`
  - `src/main/scala/org/goldenport/cncf/blob/MediaUrnResolver.scala`
  - `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeBootstrapPart.scala`
  - `src/main/scala/org/goldenport/cncf/cli/CncfRuntimeInstanceLifecyclePart.scala`
  - `src/main/scala/org/goldenport/cncf/component/Component.scala`
  - `src/main/scala/org/goldenport/cncf/component/ComponentLogic.scala`
  - `src/main/scala/org/goldenport/cncf/context/ExecutionContext.scala`
  - `src/main/scala/org/goldenport/cncf/context/ScopeContext.scala`
  - `src/main/scala/org/goldenport/cncf/http/Http4sHttpServer.scala`
  - `src/main/scala/org/goldenport/cncf/importer/StartupImport.scala`
  - `src/main/scala/org/goldenport/cncf/observability/global/GlobalObservability.scala`
  - `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`
  - `src/main/scala/org/goldenport/cncf/subsystem/GenericSubsystemFactory.scala`
  - `src/main/scala/org/goldenport/cncf/subsystem/Subsystem.scala`
  - `src/main/scala/org/goldenport/cncf/subsystem/SubsystemFactory.scala`
  - `src/main/scala/org/goldenport/cncf/subsystem/TextusIdentitySubsystemFactory.scala`
- Allowed repair: replace only compiler-reported legacy labels with their
  reported canonical flatcase labels at the corresponding call sites, plus the
  reviewed `ActionEngine.scala:191` `observabilityContext` label with the
  already-declared `observabilitycontext` label; retain the already-applied
  lifecycle `case _ => Consequence.success(preflight)` repair at line 620.
- Prohibited expansion: change an argument expression, call ordering, target
  type, public/private declaration, test, or unrelated warning family.
- Focused validation: compile with `-deprecation`; inspect that the named-label
  diagnostics for these targets are absent; run
  `RuntimeStandaloneUserProfileAdmissionSpec`; run `git diff --check`.
- Dependencies: None.

## Final Focused Review

- Exact target programs/files: the HP-001 target list only.
- Required checks: every HYG-H57-DEPRECATION-001 caller uses the compiler's
  canonical label; HYG-H57-WARN-001 is exhaustive without changing Fixed or
  None behavior; values and overload selection are unchanged; unrelated
  journals were not modified; no semantic deprecation family was absorbed.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework`: `sbt --batch test`

Run once on the reviewed tree. Stop on failure.

## Completion Contract

- Commit only after both final gates pass.
- Update HYG-H57-DEPRECATION-001 and HYG-H57-WARN-001 to `RESOLVED` with batch,
  validation, and commit evidence.
- Mark this batch `COMPLETE` only in the accepted committed tree.
- Do not absorb the separately recorded semantic-API or test-source
  deprecation records, or newly observed non-deprecation warnings.

## Resolution Evidence

- HYG-H57-DEPRECATION-001 and HYG-H57-WARN-001: resolved by HP-001 without
  changing callee declarations, values, call ordering, overload selection, or
  public contracts.
- Detailed target compile: `13467-20260819T030205Z` passed with no Batch-1
  named-label diagnostics.
- Focused lifecycle regression: `20611-20260819T032328Z` passed, 3 succeeded,
  0 failed.
- Independent focused review: `CLEAN`; exact 17-file source-diff identity
  `b626134f4f65ec430ef245124eb909dcb2ff3d18b4bfdabe867ca1acdec3c1f4`.
- Final full-validation gate: `23484-20260819T033031Z` passed, 443 suites and
  3,262 succeeded, 0 failed.
- Acceptance commit: reported externally after commit execution.

## Non-goals

- Removing deprecated API calls other than named-argument labels.
- Modifying test source or public API declarations.
