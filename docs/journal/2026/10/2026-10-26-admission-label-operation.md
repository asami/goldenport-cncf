# ADMISSION label operation

Use canonical Validation/Test Metadata to declare a small local acceptance set.
The initial specifications cover metadata interpretation, discovery selection and
exact operation binding; ADMISSION is additive to FOCUSED/FULL. `testAdmission`
selects actual registered operations and runs standard SBT testOnly without a
manual suite catalog. `admissionTestSelection` exposes that selection for inspection.
Existing FULL/HEAVY behavior and intentionally excluded fixtures remain intact.

The one-minute target applies to aggregate test-body execution only, excluding
preparation and cleanup. It is not a fixed timeout. Preserve successful results
when the target is exceeded and distinguish measured body time, conservative
upper bounds and unavailable timing. No global ABI duration constant is added.

Adoption preserves ongoing development, existing Session/history/repair counts
and validation evidence. Current invocations finish under their captured
selection. Subsequent validation can choose ADMISSION through its existing
execution owner without restarting a Phase or replaying accepted work.

See [test policy](../../../spec/test-policy.md).

Local execution evidence (2026-10-26):
`/Users/asami/backup/journal/2026/10/admission-label-operation/run-6709fa56e14d/`.
The original failed selection attempt and successful corrected invocation are
retained separately, without replacing prior evidence or committing log payloads.
