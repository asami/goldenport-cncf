# Hygiene Resolution Batch Handoff

Status: READY
Created: 2026-08-19
Source Repository: /Users/asami/src/dev2025/cloud-native-component-framework
Target Repositories: /Users/asami/src/dev2025/cloud-native-component-framework
Suggested Invocation: $cncf-goal-hygiene /Users/asami/src/dev2025/cloud-native-component-framework/docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff-02.md

## Purpose

Reduce the size and review surface of `CncfRuntime.scala` by extracting only
cohesive private implementation groups, while preserving the `CncfRuntime`
public facade and all runtime behavior.

## Included Hygiene

| ID | Source | Evidence | Work Package | Required outcome |
| --- | --- | --- | --- | --- |
| HYG-H57-SIZE-002 | `docs/journal/2026/08/2026-08-19-cncf-runtime-source-size-follow-up.md` | `CncfRuntime.scala` is 6,083 lines at triage. | HP-001 | Separate cohesive private helper cohorts into reviewable collaborators without changing observable behavior. |

Hygiene Triage: HANDED_OFF
Hygiene ID: HYG-H57-SIZE-002
Handoff Journal: cloud-native-component-framework:docs/journal/2026/08/2026-08-19-hygiene-resolution-batch-handoff-02.md
Handed Off On: 2026-08-19

## Frozen Boundary

- Allowed repositories: `/Users/asami/src/dev2025/cloud-native-component-framework` only.
- Preserve paths: every pre-existing dirty path outside the target source,
  any new directly supporting private collaborator, directly covering specs,
  and the two source/handoff journals.
- Allowed behavior change: none.
- Prohibited expansion: public API relabeling, source-compatibility policy,
  CLI grammar, configuration keys, protocol/wire shapes, subsystem assembly,
  feature work, architecture redesign, and unrelated hygiene.

## HP-001 — CncfRuntime private-helper extraction

- Hygiene IDs: HYG-H57-SIZE-002.
- Repository: `/Users/asami/src/dev2025/cloud-native-component-framework`.
- Targets: `src/main/scala/org/goldenport/cncf/cli/CncfRuntime.scala`; new
  package-private collaborators under `src/main/scala/org/goldenport/cncf/cli/`
  only when a cohesive private-helper cohort is extracted; directly covering
  CLI specifications under `src/test/scala/org/goldenport/cncf/cli/` only when
  needed to preserve or make existing behavior explicit.
- Allowed repair: relocate cohesive private implementation groups behind the
  existing `CncfRuntime` facade; preserve public declarations and observable
  command/runtime behavior.
- Prohibited expansion: changing public labels, adding/removing CLI modes or
  arguments, changing configuration precedence, moving non-private contracts,
  or reworking unrelated runtime subsystems.
- Focused validation: run the directly covering CLI runtime/configuration
  specifications selected from `src/test/scala/org/goldenport/cncf/cli/` for
  every extracted cohort, then review the complete facade and each new
  collaborator for unchanged delegation and private-name compliance.
- Dependencies: None.

## Final Focused Review

- Exact target programs/files: `CncfRuntime.scala`, every new collaborator,
  and every changed directly covering CLI specification.
- Required checks: the HYG-H57-SIZE-002 size objective, preserved public
  facade, unchanged CLI/configuration/runtime behavior, whole-target naming,
  package visibility, consumer compatibility, and scope containment.
- Failure policy: stop without commit; no automatic review-fix/re-review loop.

## Final Full-Validation Gate

1. `/Users/asami/src/dev2025/cloud-native-component-framework: sbt --batch test`
   through `/Users/asami/.codex/skills/cncf-sbt-serial-execution/scripts/run-sbt-serial.sh`.

Run the changed repository exactly once on the reviewed tree. Stop on failure.

## Completion Contract

- Commit only after the final focused review and final full-validation gate pass.
- Update HYG-H57-SIZE-002 to `RESOLVED` with split, validation, and acceptance
  commit evidence.
- Mark this batch `COMPLETE` only in the accepted committed tree.
- Do not absorb Development Candidates or newly found unrelated Hygiene.

## Non-goals

- A functional rewrite of CLI, bootstrap, discovery, or server behavior.
- A public API or compatibility migration.
- Any change to the currently dirty strategy, phase, or unrelated journal work.
