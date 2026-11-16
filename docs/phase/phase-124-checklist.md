# Phase 124 checklist — Read-only Operation for Failure and Maintenance

status=planned
execution_started=false

Owner: CNCF. Plan: [Phase 124](phase-124.md). All work is OPEN.

## P124-01 — Contract and configuration

- [ ] Update owning design/specification documents with scope, failure/maintenance reason, transition/established/recovery semantics, ordinary configuration and structured refusal/observation behavior.
- [ ] Keep service mode separate from role, lifecycle and database configuration; define explicit operator entry and any narrowly eligible automatic entry.

## P124-02 — Common mutation admission

- [ ] API, Job/Workflow, scheduled/event and hidden read-triggered business mutations share enforceable admission, including work already queued but not admitted.
- [ ] Audit/observability, cache maintenance and authorized maintenance administration have explicit bounded policies; they cannot become ordinary business-write bypasses.

## P124-03 — Entry and admitted work

- [ ] New business admission closes; valid admitted work settles under existing authority and durable outcome rules while the mode remains visibly transitional.
- [ ] Revoked authority is fenced. Established Read-only is not reported while an old/unreachable scoped path can still mutate.
- [ ] Refused mutations remain unadmitted; they are not silently queued or replayed on restoration.

## P124-04 — Resident reads and retention

- [ ] With persistence slow/unavailable, qualified resident master/Working Set/cache reads succeed without an authoritative reload; missing data, unbuilt indexes and required fresh reads return bounded unavailability.
- [ ] Routine TTL discard and scheduled purge retain eligible business cache entries within declared coverage and memory bounds. Freshness timestamps/deadlines remain meaningful; stale service requires explicit eligibility.
- [ ] Explicit invalidation/deletion and qualified committed synchronization continue. Authentication/session expiry, authorization revocation and required audit dependencies retain their enforcement.
- [ ] Resident data provides no access bypass; two-user private-data isolation and unsafe maintenance read scopes are exercised.

## P124-05 — Maintenance and restoration

- [ ] Operator-selected maintenance and failure degradation expose different reasons/recovery conditions while using the same service-mode semantics.
- [ ] Maintenance administration is authorized separately; read scopes made unsafe by maintenance become unavailable.
- [ ] Restoration requires cause resolution/maintenance completion, valid owner/fencing where applicable, necessary synchronization/readiness and reconciliation of unsettled or uncertain outcomes; time or heartbeat alone is insufficient.

## P124-06 — Qualification and handoff

- [ ] A connected single-runtime fixture proves actual storage impairment, resident continuity, bounded misses, cross-entry mutation refusal and recovery after a fresh authoritative update.
- [ ] Deterministic-clock cases cover routine expiry retention, stale eligibility, explicit invalidation/deletion and continuing security expiry/revocation.
- [ ] State actual resident coverage and operational limits. Activity-scoped and multi-runtime extensions are accepted only against qualified Phase 123/108 capabilities and recorded evidence.

## Phase closure

- [ ] All owning Steps are accepted or outstanding scope explicitly dispositioned; repository-full validation, independent integrated review and implementation handoff are recorded before closure.
