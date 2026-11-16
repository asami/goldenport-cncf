# Phase 124 — Read-only Operation for Failure and Maintenance

status=planned
planned_at=2026-11-13
execution_started=false
owner=CNCF

Progress: [Phase 124 checklist](phase-124-checklist.md). Registration starts no implementation or Session.

## Goal

Provide a selected Subsystem with an explicit Read-only service mode for failure degradation and planned maintenance: stop ordinary business mutations and continue only authorized, trusted and freshness-qualified reads. Use committed resident data to sustain useful service during impaired persistence access where dependencies permit.

The service mode is distinct from Primary/Read Replica/Standby roles, runtime lifecycle and a database read-only flag. Entry reason and recovery conditions distinguish failure from maintenance. Planning input: [Working Set and Read-only scope](../notes/activity-scoped-working-set-and-readonly-development-scope.md).

## Delivery groups

| Step | Work | Observable outcome |
| --- | --- | --- |
| P124-01 | Contract and configuration | Define selected scope, entry reasons, transition/established/recovery semantics and operator-visible refusals using ordinary configuration. |
| P124-02 | Common mutation admission | Refuse business mutations across API, Job/Workflow, schedule/event and read-triggered side effects. |
| P124-03 | Entry and admitted work | Close new admission and settle valid admitted work before reporting established Read-only operation. |
| P124-04 | Resident reads and retention | Qualified resident reads continue; business cache retention suppresses routine discard while preserving freshness, invalidation and security boundaries. |
| P124-05 | Maintenance and restoration | Separate authorized maintenance actions; recover through explicit readiness, authority and outcome reconciliation. |
| P124-06 | Qualification and handoff | Prove actual resident service under impaired persistence, mutation refusal and safe restoration, followed by validation/review. |

## Delivery order and dependencies

First qualify one runtime using existing resident master data, Working Set policies and business-read caches. This route does not require [Phase 123](phase-123.md) or whole [Phase 108](phase-108.md) completion. Consume Phase 123 when qualifying activity-scoped cart/user/address coverage. Consume Phase 108's actual admission, fencing and synchronization capabilities when qualifying a multi-runtime mode; unreachable or stale writers must not be ignored.

Phase 108 retains its existing acceptance and IR-01..IR-05 priorities. This is a separate development plan, not a new Phase 108 completion gate. Detailed public ABI and automatic entry policy are resolved by P124-01, rather than declared implemented by this plan.

## Completion

The checklist owns acceptance. Qualify failure and maintenance entry, controlled transition, resident hits versus unavailable misses/freshness-critical reads, cache retention, security revocation, maintenance scope and explicit restoration. Record Step checks, final repository-full validation and an independent integrated review before closure. Registration proves no delivered mode or measured read coverage.
