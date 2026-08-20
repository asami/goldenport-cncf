# Phase 58.5 - Resource Authorization, Disclosure, and Integrity

status=done
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.4](phase-58.4.md)
successor=[Phase 58.6](phase-58.6.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.5 Checklist](phase-58.5-checklist.md)
consumes_handoff=RSC-05 runtime composition-policy matrix

## Goal

Enforce authorized, integrity-checked, and non-leaking resource and child
access across every resolution form.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: protected implementation
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-05 runtime-policy matrix and resolved-resource outcomes
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 3–5h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved split from Phase 58

## Scope

- Enforce role/resource authorization, parent/release/integrity validation,
  and safe archive/path handling before content access.
- Keep restricted source visible only as admitted state and prevent disclosure
  through manifests, diagnostics, metrics, or CallTree.
- Cover hostile archives, corrupt cache, unauthorized source, and manifest
  authority-denial regressions.

## Closure

Restricted source is represented without disclosure; integrity, authorization,
and path safety are enforced before content exposure; diagnostics cannot leak
credentials, content, host paths, or repository secrets.

## Implementation Evidence

Phase-base HEAD is `312e9ca8db9a1bdc6c3adfc7899ba1e03db072ae` (`Close Phase 58.4
operation-mode composition`).

RSC-06A failing-first RED was serialized SBT invocation `5044-20260820T220922Z`:
only the absent RSC-06 policy API caused the intended compile failures (22
errors); the serial lock was released. RSC-06B first focused execution
`7469-20260820T221439Z` exposed and then bounded fixes repaired two policy
defects: parent primary-CAR membership and denial-before-content integrity
ordering. This is intermediate evidence, not final success.

Focused GREEN was invocation `9775-20260820T221958Z`:
`ComponentResourceAuthorizationSpec`, 1 suite / 10 succeeded / 0 failed / lock
released. The direct consumer accumulator GREEN was invocation
`10569-20260820T222053Z`: `ComponentResourceAuthorizationSpec`,
`ResolvedComponentResourcesSpec`, and `ComponentResourceOperationModePolicySpec`,
3 suites / 34 succeeded / 0 failed / lock released.

`D-58.5-STEP-REVIEW-STOP: AUTHORIZE_PROTECTED_STEP_REVIEW_EXCEPTION` authorizes
only omission of the otherwise-required lightweight Luna Step review for this
protected security boundary. RSC-06 was accepted in Step commit
`7386279ea77f7c18ed51fd979f2c7820fd4af736` (`Implement RSC-06 authorization
integrity policy`). The mandatory Terra xhigh full Phase review admitted three
bounded blockers: naming, key-specific signature attestation, and executable
specification traceability. The one Closure Fix Batch passed focused validation
`32297-20260820T225156Z` (10 succeeded / 0 failed) and accumulator
`33209-20260820T225255Z` (34 succeeded / 0 failed); the Luna xhigh focused
closure re-review returned `SEALED_PASS`. The frozen-tree full `sbt --batch
test` release gate is required by this release commit's exact manifest.

## Release Evidence

The RSC-06 Step commit, mandatory Terra xhigh full review, one bounded Closure
Fix Batch, and focused closure re-review provide the accepted Phase evidence.
The final frozen-tree `sbt --batch test` is executed by the same exact release
manifest immediately before this commit; a failing gate creates no release
commit. The commit execution record is the authoritative invocation and
aggregate-test evidence for the Phase closure.

## Non-Goals

New operation authority, manifest-granted authorization, cache lifecycle, or
consumer presentation.
