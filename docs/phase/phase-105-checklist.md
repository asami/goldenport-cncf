# Phase 105 Checklist: Workflow Validation Simplification

Status: PLANNED
Phase: [Phase 105](phase-105.md)
All implementation and acceptance items remain OPEN.

## WCS-01: Existing completion entry points

- [ ] Preserve public Operation names, arguments, responses and public Scala signatures; keep both managed `completeC` methods non-public.
- [ ] Accept a valid public input carrier whose codec produces a differently named semantic result type, through both WorkOrder and Decision Operations.
- [ ] Consolidate repeated issued-input reads and result admission with clear input-access, business and storage responsibilities.
- [ ] Reject actual malformed/undecodable results, wrong targets and incompatible consumed results at their owning boundaries.
- [ ] Keep completion sequencing, storage and event/response lifecycle in CNCF; require no sm-workflow replacement completion engine or metadata spoofing.

## WCS-02: Instance and history consumption

- [ ] Remove redundant full-history validation in the connected load/select/append/encode route.
- [ ] Preserve detection of malformed stored data and invalid new transitions without a new verification flag, copied-history seal or registry.
- [ ] Exercise a normal multi-step history through result completion without repeated certification of unchanged history; source review confirms where validation now belongs.

## WCS-03: Committed outcomes

- [ ] Deliver actual committed event data without comparing it to a retained canonical-string copy.
- [ ] Remove planned-versus-persisted recertification in the non-managed completion route.
- [ ] Resolve the listed supporting event/index/cursor comparison paths by updating supported consumers or explicitly retiring unused internals without breaking public interfaces.
- [ ] Demonstrate that representational changes allowed by the codec do not turn a valid stored outcome into a failure solely because a copy differs.
- [ ] Preserve correct result/state/continuation publication, local effects and event ordering; test the durable-event route explicitly.
- [ ] Preserve real commit failure and unreadable/missing committed-data handling, including no fabricated success, unsafe retry or post-commit rollback claim.

## WCS-04: Related Workflow admission

- [ ] Remove generator-name and source-location equality gates from the listed Provided/ActionProgram entry points.
- [ ] Preserve actual operation/action references, executable ordering and effect requirements.
- [ ] Remove additional-unused-field rejection in the listed mechanical/related readers where it has no consumed semantic requirement; document concrete reasons for any retained restriction.
- [ ] Update affected specs and tests that encode superseded proof obligations; do not retain them as hidden compatibility gates.
- [ ] Preserve public interfaces and storage representation; keep any newly discovered API/schema redesign separate from this Phase.

## WCS-05: Connected validation and handoff

- [ ] Run affected CNCF Executable Specifications and meaningful regressions for the connected change; record actual commands and outcomes.
- [ ] Identify the corrected CNCF development artifact/checkout explicitly for integration without disturbing parallel consumers.
- [ ] Demonstrate sm-workflow result submission through the existing public Operation to committed next state, saved result/continuation completion and acknowledgement; cover WorkOrder and Decision.
- [ ] Verify consumer source compatibility without `completeC` exposure or added caller certification fields.
- [ ] Complete independent review of the integrated implementation and its remaining necessary checks.
- [ ] Provide integration evidence and remaining obligations to the existing sm-workflow Phase 5.2 task without declaring that entire consumer Phase complete.
- [ ] Complete applicable release validation and reconcile all checklist items before Phase closure; preserve unrelated work and historical evidence.

## Current evidence

2026-10-12: source investigation and planning only. The investigation identified
concrete checks and distinguished current completion paths from supporting paths
whose sm-workflow use was not established. No implementation, tests, independent
implementation review or consumer acceptance is claimed. Creating this plan
does not activate a Goal, authorize a release or start a new task.
