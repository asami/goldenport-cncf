# Phase 106: Review Evidence and Admission Integration

status=planned
planned_at=2026-10-13
driver=sm-workflow Phase 6
consumers=sm-workflow, textus-cbd-support Phase 15, Cozy Phase 81
checklist=[Phase 106 checklist](phase-106-checklist.md)

## Goal and boundary

Support the concrete staged-review route driven by sm-workflow Phase 6 using
existing Candidate-Admission Requirement/Evidence/coverage/freshness facilities.
Identify actual missing contracts and implement only those gaps; do not create
another Admission engine or reopen completed Phase 90/105 work.

Source: [GitHub-origin journal](../journal/2026/10/2026-10-13-staged-quality-review-matrix.md).
Phase 90's existing [contract](https://github.com/asami/goldenport-cncf/blob/main/docs/phase/phase-90.md)
is the starting point, not evidence that this new consumer connection is complete.

CNCF owns generic requested purpose/scope, subject and ordinary revision
association, actual coverage, typed findings/disposition, applicability and
requirement gaps. sm-workflow owns lifecycle timing, repair/re-review and physical
commit/closure. `STEP`, `PHASE`, `FINAL`, Phase/checklist and Git-specific decisions
must not enter generic CNCF ABI. Cozy owns CML relationships; cbd-support owns
concrete quality checks. Reuse Phase 99 finding semantics where applicable and
Phase 103 test metadata when needed; their entire roadmaps are not prerequisites.

## Implementation order

1. REA-01: Inventory existing CAM APIs against the shared driver, fix the finite
   request/result/applicability contract and a fixture with the three consumers.
   Distinguish missing functionality from an already-supported binding.
2. REA-02: Connect the normal evaluation path to actual typed lint/review evidence,
   reporting covered requirements and explicit gaps/Unknown. Keep semantic work,
   deterministic execution and authority decisions on their existing routes.
3. REA-03: Support reuse across progression and affected-scope evaluation after
   repair. Preserve unrelated applicable evidence; require additional evaluation
   only for unmet or no-longer-applicable requirements. Complete specs alongside
   implementation, without a new comparison ledger or per-helper review cycles.
4. REA-04: Accept actual Cozy/provider results through the sm-workflow Step and
   Phase/Final driver, record producer-specific evidence, independently review
   and close the bounded producer delivery.

Freshness is applicability to the real subject/requirements, not elapsed-time
expiry or an unchanged-file certificate. A commit or lifecycle transition alone
does not invalidate evidence. Changes to behavior, relevant inputs or acceptance
are evaluated at their actual affected scope; unresolved semantic applicability
uses the existing judgment/decision route. Evidence association is not permission
or authenticity. No content hashes, snapshots for comparison, TTL, whole-tree
copies or blanket reruns are added. Ordinary revisions/references may identify
subjects; they do not prove all input contents or create artificial rejection.

## Cross-project delivery and completion

[sm-workflow Phase 6](https://github.com/asami/sm-workflow/blob/main/docs/phase/phase-6.md)
owns the shared P6-SQ01..05 scenarios. Supply the minimum contract first so
[Cozy Phase 81](https://github.com/asami/cozy/blob/main/docs/phase/phase-81.md)
and [cbd-support Phase 15](https://github.com/asami/textus-cbd-support/blob/main/docs/phase/phase-15.md)
can implement against it; then accept their actual output in the same driver.
The producer closes on its checklist and the bounded consumer handoff, without
waiting for all Phase 6 features. Phase 6 closes after required producer/provider
deliveries and its integrated acceptance, avoiding mutually dependent full-Phase
closure gates. Full UI/KPI, model coverage and unrelated CNCF work are excluded.

Planning does not start execution, change existing Goals/counters/history, or
claim a review result grants authority. Existing interface stability requirements
are assessed at their owning boundary; this is separate feature work from 105.
