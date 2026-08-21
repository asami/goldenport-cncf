# Phase 58.6.1 - Lifecycle Terminal-Outcome Recovery

status=closed
split_from=[Phase 58.6](phase-58.6.md)
depends_on=[Phase 58.6](phase-58.6.md)
successor=[Phase 58.7](phase-58.7.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 58.6.1 Checklist](phase-58.6.1-checklist.md)
consumes_handoff=Phase 58.6 core lifecycle ownership, immutable resource/key matching, and observability constraints

## Goal

Complete the two deferred RSC-07 terminal-ordering recoveries: a cancelled
loading producer that later returns mismatched provenance, and an admitted
waiter whose cancellation arrives only after the shared flight has reached a
release/unload/shutdown terminal outcome. Each caller must retain its actual,
deterministic terminal result.

Phase Plan Gate: PROCEED
- target: conservative upper bound <= 6h
- planning_demand: protected-decision
- recommended_parent_profile: gpt-5.6-terra / xhigh
- profile_cost_role: expensive reasoning kernel
- expensive_reasoning_kernel: atomic terminal-completion versus per-waiter cancellation ordering across shared lifecycle flights
- frozen_profile_transition_handoff: Phase 58.6 core lifecycle ownership, immutable resource/key matching, and historical terminal-outcome evidence
- parent_reasoning_mode_policy: standard
- estimated_at_recommended_profile: 4–6h
- agent_reasoning_mode_policy: default standard; consider pro only at an eligible agent launch when the active interface supports it and frozen quality-first evidence justifies it
- runtime_suitability: re-evaluate in the Phase execution task
- source: approved extension of the Phase 58.6 recovery split

## Scope

- Establish the producer-cancellation-before-provenance-mismatch ordering for
  an in-flight lifecycle entry with an admitted waiter.
- Prove the cancelled producer is removed and receives a cancelled no-resource
  result while the waiter receives the resource-provenance failure.
- Establish atomic ordering between a completed shared-flight terminal result
  and a later waiter cancellation, preserving the completed release/unload/
  shutdown result when it wins.
- Preserve one-flight loading, no resource publication or owner leakage,
  truthful bounded metrics, and a valid retry after each separated terminal
  outcome.
- Reconcile the RSC07B acceptance-registry row count and ownership prose.

## Closure

The producer cancellation, admitted waiter's provenance failure, and
completed-flight waiter terminal result are deterministic and separate; no
resource or owner leaks, metrics are bounded and truthful, and a later valid
retry succeeds. The acceptance registry has one consistent nine-row ownership
contract. Phase 58.7 consumes the complete RSC-07 lifecycle, diagnostics, and
provenance contract only after this Phase is done.

## Completion Evidence

- 2026-08-22: `RSC-07B1-RED` established the intended pre-fix failure
  boundary; `RSC-07B2-GREEN` delivered the recovery in Step commit
  `bddaa1c771e10640985f4149b51911199e50f00b`.
- 2026-08-22: the focused lifecycle specification passed 22/22 scenarios and
  the affected accumulator passed 47/47 scenarios.
- 2026-08-22: the independent Terra/xhigh Phase full review sealed `PASS`
  with no Current Phase Blocker, Hygiene, or Development Candidate entry.
- 2026-08-22: repository-full release validation passed 3,351/3,351 tests
  across 449 suites (`17965-20260821T224755Z`), with the serialized SBT lock
  released.

## Non-Goals

Changing cache architecture, public APIs, resource authorization, identity,
CallTree/diagnostic design, Help/Admin consumers, or any other RSC-07 race.

## Split Provenance

The developer approved this recovery child on 2026-08-21 through
`D-58.6-POST-EXCEPTION-CONVERGENCE: SPLIT_PHASE` and
`D-58.6-SPLIT-PROPOSAL: ADOPT_58.6_PLUS_58.6.1_RECOVERY`. Its initial scope
was the unresolved `CB-58.6-02` combination found after the source Phase's
ordinary and exceptional closure batches closed.

On 2026-08-21 `D-58.6-BASELINE-CLOSURE-001` selected a further split and
`D-58.6-NESTED-SPLIT-001: EXTEND_EXISTING_58.6.1` approved extending this
existing child rather than creating an unsupported nested decimal Phase. This
child now also owns `CPB-P58.6-001` and `CPB-P58.6-002`. It reuses neither an
acceptance claim nor a closure decision from the source Phase; its own
executable evidence, review, full validation, and local release commit remain
required.
