# ADMISSION-only classification and standard ScalaTest annotations

Date: 2026-10-31
Status: user-approved specification/plan update; product migration pending.
Owner: sm-workflow development, with CNCF producer changes first.

The user identified HEAVY and ScalaTest Slow as duplicate classifications and
chose standard annotations. ADMISSION is the sole custom label. Retire active
SMOKE/FOCUSED/FULL/HEAVY/ON_DEMAND purpose values; FULL/focused remain selection
scopes. Use Slow for long-running cases, DoNotDiscover for explicit-only suites,
and keep feature/duration/environment metadata separate. No compatibility aliases
or automatic Slow-to-HEAVY normalization.

Contract: [standard-annotation selection](../../../spec/test-selection-standard-annotations.md).
Plan: [Phase 103 separate follow-up](../../../phase/phase-103.md#follow-up-standard-annotations-and-admission-only--2026-10-31).
Phase 103 stays closed; its original accepted evidence is not rewritten. First
connect one bounded producer/consumer route, then migrate declarations/callers and
qualify the actual new contract. Keep existing framework behavior and prerequisites
until coordinated adoption; no expensive catalogue replay or new runtime gate.

CNCF a840e382 and sm-workflow 6c1c0ef previously delivered ON_DEMAND, with reported
22/13 successful tests and earlier 7/77/16 results. Those outcomes remain evidence
of the earlier version. Remove the obsolete feature by normal subsequent changes,
not reset/revert of mixed work or deletion of history. Active staged knowledge/
datastore work and test-policy edits are preserved. No product source, index,
Session, runtime selection or test execution is changed by this documentation task.

The full English consumer/producer handoff is in sm-workflow at
`docs/journal/2026/10/2026-10-31-admission-standard-annotations-handoff.md`.
