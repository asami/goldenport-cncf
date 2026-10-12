# Phase 105 Checklist: Workflow Validation Hygiene

Status: CLOSED
Work class: HYGIENE — no feature additions.
Phase: [Phase 105](phase-105.md)
All declared implementation and acceptance items are complete. Historical cutoffs below are superseded by the final completion record.

## WCS-01: Existing completion entry points

- [x] Confine changes to existing functionality: no new capabilities, Operations, public APIs, storage formats or recovery guarantees. Record missing features separately without expanding this Phase's acceptance.
- [x] Preserve public Operation names, arguments, responses and public Scala signatures; keep both managed `completeC` methods non-public.
- [x] Accept a valid public input carrier whose codec produces a differently named semantic result type, through both WorkOrder and Decision Operations.
- [x] Consolidate repeated issued-input reads and result admission with clear input-access, business and storage responsibilities.
- [x] Reject actual malformed/undecodable results, wrong targets and incompatible consumed results at their owning boundaries.
- [x] Keep completion sequencing, storage and event/response lifecycle in CNCF; require no sm-workflow replacement completion engine or metadata spoofing.

## WCS-02: Instance and history consumption

- [x] Remove redundant full-history validation in the connected load/select/append/encode route.
- [x] Preserve detection of malformed stored data and invalid new transitions without a new verification flag, copied-history seal or registry.
- [x] Exercise a normal multi-step history through result completion without repeated certification of unchanged history; source review confirms where validation now belongs.

## WCS-03: Committed outcomes

- [x] Deliver actual committed event data without comparing it to a retained canonical-string copy.
- [x] Remove planned-versus-persisted recertification in the non-managed completion route.
- [x] Resolve the listed supporting event/index/cursor comparison paths by updating supported consumers or explicitly retiring unused internals without breaking public interfaces.
- [x] Demonstrate that representational changes allowed by the codec do not turn a valid stored outcome into a failure solely because a copy differs.
- [x] Preserve correct result/state/continuation publication, local effects and event ordering; test the durable-event route explicitly.
- [x] Preserve real commit failure and unreadable/missing committed-data handling, including no fabricated success, unsafe retry or post-commit rollback claim.

## WCS-04: Related Workflow admission

- [x] Remove generator-name and source-location equality gates from the listed Provided/ActionProgram entry points.
- [x] Preserve actual operation/action references, executable ordering and effect requirements.
- [x] Remove additional-unused-field rejection in the listed mechanical/related readers where it has no consumed semantic requirement; document concrete reasons for any retained restriction.
- [x] Update affected specs and tests that encode superseded proof obligations; do not retain them as hidden compatibility gates.
- [x] Preserve public interfaces and storage representation; keep any newly discovered API/schema redesign separate from this Phase.

## WCS-05: Connected validation and handoff

- [x] Run affected CNCF Executable Specifications and meaningful regressions for the connected change; record actual commands and outcomes.
- [x] Identify the corrected CNCF development artifact/checkout explicitly for integration without disturbing parallel consumers.
- [x] Demonstrate sm-workflow result submission through the existing public Operation to committed next state, saved result/continuation completion and acknowledgement; cover WorkOrder and Decision.
- [x] Verify consumer source compatibility without `completeC` exposure or added caller certification fields.
- [x] Use integration checks for existing behavior; do not add unfinished sm-workflow features as a prerequisite to hygiene. Record any consumer-readiness limitation explicitly.
- [x] Complete independent review of the integrated implementation and its remaining necessary checks.
- [x] Provide integration evidence and remaining obligations to the existing sm-workflow Phase 5.2 task without declaring that entire consumer Phase complete.
- [x] Complete applicable release validation and reconcile all checklist items before Phase closure; preserve unrelated work and historical evidence.

## Current evidence

2026-10-12: source investigation and planning only. The investigation identified
concrete checks and distinguished current completion paths from supporting paths
whose sm-workflow use was not established. No implementation, tests, independent
implementation review or consumer acceptance is claimed. Creating this plan
does not activate a Goal, authorize a release or start a new task.

2026-10-12 implementation source cutoff: the dedicated producer source and
Executable Specifications are being authored. This entry reports no compilation,
test, independent review, selected artifact or consumer result. Every acceptance
item remains OPEN pending actual evidence owned by the parent workflow.

## Final completion record — 2026-10-12

WCS-01..05 are accepted as one connected Step WCS-CORE / Slice WCS-CORE-A,
committed at7a640d281d64fa9314b9265e66fdfa4a8665cc9c. Public entry points/signatures,
managed completion visibility, native CAS/provider boundaries and persisted
writer layouts are unchanged. The current implementation and exact remaining
conditions are described in the [applicability contract](../spec/workflow-completion-applicability.md).

| Acceptance | Actual evidence |
| --- | --- |
| WCS-01: carrier/semantic result and native completion | Both public binders and completion persistence executable specs; protected Step and full Phase source review; existing consumer E12 WorkOrder/Decision routes |
| WCS-02: history consumption | Instance persistence and DataStore suites cover public/native decode, incremental adjacent append and multistep stored history; whole-file source review confirms checks at consuming boundaries |
| WCS-03: committed outcomes and real failures | Selected event/V1/V2/index/cursor/native persistence specs, physical SQLite reopen, formatting normalization and refusal/rollback/stale CAS cases; complete host suite25/25 after bounded repair |
| WCS-04: generated/mechanical admission | Generated ABI, mechanical transition and ActionProgram suites; four diagnostic lanes/six variants24 observations preserve executable semantics; real schema/identity/type/order failures remain |
| WCS-05: connected consumer and handoff | Package5 actual selected SNAPSHOT JAR; consumer7 existing E12 eight cases8/8 and both source compilations; selected artifact/tested consumer cutoff and separate obligations in Phase handoff |
| Review | Protected Step review plus focused closure; one original-epoch comprehensive Phase review PASS; Phase repair1 independent focused PASS, current typed ledger5, blockers0 |
| Final release validation | Validation11 exact sbt --batch test:4348/4348,587 complete suites,0 failed/aborted;13 canceled,1 ignored,46 pending; SBT0/wrapper0/lockreleased; actual current-request reader accepted |
| Follow-up persistence | Four canonical OPEN Hygiene records persisted in declared journal; no accepted Development Candidate or unpersisted finding |

The original failed full validation9 remains historical, not a successful
receipt. Its two obsolete host expectations were corrected without restoring
proposal-copy restrictions, then passed focused validation10 and independent
closure before final validation11. Consumer evidence covers its tested cutoff,
not later parallel consumer commits, full sm-workflow Phase acceptance or actual
provider/AI execution. The final Phase release payload includes this closed
checklist, Phase/index/applicability updates and canonical Hygiene journal.
