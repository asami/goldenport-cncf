# Phase 58.6 - Core Resource Lifecycle, Concurrency, and Observability

status=done
closed_at=2026-08-22
split_from=[Phase 58](phase-58.md)
depends_on=[Phase 58.5](phase-58.5.md)
successor=[Phase 58.6.1](phase-58.6.1.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.6 Checklist](phase-58.6-checklist.md)
consumes_handoff=RSC-06 authorization, integrity, and disclosure constraints

## Goal

Deliver the core resolver and resource lifecycle behavior: bounded,
idempotent, concurrent-safe, and observable without exposing sensitive
 evidence. Phase 58.6.1 exclusively owns the two deferred terminal-ordering
 recoveries: producer cancellation plus provenance mismatch, and waiter
 cancellation observed after a shared flight has already reached a terminal
 release/unload/shutdown outcome.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: bounded-settled
- recommended_parent_profile: gpt-5.6-terra / high
- profile_cost_role: lower-cost execution
- expensive_reasoning_kernel: none
- frozen_profile_transition_handoff: RSC-06 authorization and non-leakage invariants
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 2–4h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved recovery split of Phase 58.6

## Scope

- Define cache reuse, refresh, invalidation, load/release/unload, and shutdown
  ownership.
- Prove safe multi-instance and concurrent resolution, including refresh,
  ordinary waiter-cancellation, interruption, and release/unload/shutdown
  races; Phase 58.6.1 owns the terminal-completion versus waiter-cancellation
  ordering.
- Add bounded, non-sensitive CallTree, metrics, and diagnostics evidence.

## Closure

Shared immutable artifacts, in-flight resolution, cache refresh/invalidation,
unload/shutdown, waiter cancellation/interruption, diagnostics, and metrics
have deterministic ownership and failure-isolation behavior. Phase 58.6.1
owns the excluded producer-cancellation plus provenance-mismatch and
terminal-completion versus waiter-cancellation orderings before Phase 58.7
consumes the complete read-only operational contract.

## Implementation and Release Evidence

The accepted RSC-07A Step commit is
`f79a54b3cafbd023c463f9b7d37cbf09f6770004` (`Implement component resource
lifecycle policy`). Its mandatory Phase review, ordinary closure repair, and
one exceptional closure repair were completed before the approved recovery
split. The focused lifecycle specification recorded 18/18 successes and the
affected accumulator recorded 43/43 successes; the retained terminal-ordering
findings are exclusively owned by Phase 58.6.1.

The frozen-tree final release validation was serialized SBT invocation
`85269-20260821T210709Z`: 449 suites completed with 3,347 succeeded, 0 failed,
13 canceled, 1 ignored, and 46 pending tests. Both SBT and wrapper exits were
zero and the shared lock was released. This Phase's release commit records the
authoritative final tree; Phase 58.6.1 remains unstarted and owns no work
accepted by this Phase.

## Non-Goals

Changing access policy, repository admission semantics, or providing Help/Admin
presentation beyond the stable read-only contract. This Phase does not own the
producer-cancellation plus provenance-mismatch recovery or the
terminal-completion versus waiter-cancellation recovery case.

## Approved Recovery Split

On 2026-08-21 the developer resolved
`D-58.6-POST-EXCEPTION-CONVERGENCE` as `SPLIT_PHASE` and approved
`D-58.6-SPLIT-PROPOSAL: ADOPT_58.6_PLUS_58.6.1_RECOVERY`.

The Phase's full review, ordinary closure repair, exceptional closure repair,
focused validations, and focused rereviews remain historical evidence of this
source Phase. The accepted Step commit remains
`f79a54b3cafbd023c463f9b7d37cbf09f6770004`; focused evidence recorded 18/18
LifecycleSpec and 43/43 affected-accumulator successes before the final
exceptional rereview retained `CB-58.6-02` only for a producer cancellation
combined with a provenance mismatch.

The split reason was the exhausted one-time repair/review budget for that
single lifecycle ordering edge. At the original split, Phase 58.6 retained all
other RSC-07 closure work and new Phase 58.6.1 owned the one recovery outcome:
a cancelled producer is removed and receives its cancelled result, while an
already-admitted waiter receives the resource-provenance failure without
resource or owner leakage.

At that original split, the expected saving was qualitative: both units were
estimated at Terra/high because they were protected lifecycle work, while the
recovery avoided reopening a consumed review loop or re-running a broad
lifecycle investigation. The added overhead was one Phase/checklist handoff,
its focused validation/review, and a separate local release commit. The later
extension below supersedes the child profile and recovery scope. Phase 58.6.1
consumes this Phase's core lifecycle boundary, immutable resource/key matching,
and completed-history evidence; Phase 58.7 consumes Phase 58.6.1's complete
RSC-07 handoff.

## Baseline Decision Record

On 2026-08-21 the developer resolved
`D-58.6-BASELINE-CLOSURE-001` as `SPLIT_PHASE` after baseline review found
`CPB-P58.6-001` (waiter cancellation can overwrite an already-completed
release/unload/shutdown terminal result) and `CPB-P58.6-002` (the RSC07B
registry row-count contradiction). The developer then approved
`D-58.6-NESTED-SPLIT-001: EXTEND_EXISTING_58.6.1`.

The extension preserves the existing ordered chain `58.6 -> 58.6.1 -> 58.7`;
it does not create an unsupported nested decimal identifier. Phase 58.6.1 now
owns both deferred terminal-ordering recoveries and the RSC07B registry
correction. This concentrates the only remaining protected concurrency
reasoning in one independently closable child. The child rises from the
original 2–4 hour Terra/high estimate to 4–6 hours at Terra/xhigh; avoiding a
new child avoids an additional handoff, validation, review, and release commit.
The frozen transition handoff is Phase 58.6's core lifecycle ownership,
immutable resource/key matching, and historical focused evidence; Phase 58.7
continues to consume Phase 58.6.1's complete RSC-07 contract.
