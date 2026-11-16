# Phase 123 — Activity-Scoped Entity Working Sets

status=planned
planned_at=2026-11-13
execution_started=false
owner=CNCF

Progress: [Phase 123 checklist](phase-123-checklist.md). This registration starts no implementation or Session.

## Goal

Extend the existing Entity Working Set foundation with explicit business-activity and user-activity selection. Keep valid master data resident where configured; select active transactional data and related user/cart/address data without loading all historical transactions. Residency remains a reconstructible optimization, independent of authoritative persistence and authorization.

Planning input: [Working Set and Read-only scope](../notes/activity-scoped-working-set-and-readonly-development-scope.md). The first Step promotes agreed contracts into the owning design/specification documents; this ledger is work management.

## Delivery groups

| Step | Work | Observable outcome |
| --- | --- | --- |
| P123-01 | Inventory and contract | Distinguish EntityKind, business lifecycle, runtime WorkflowInstance and activity membership; define selection and lifecycle responsibilities. |
| P123-02 | Policy integration | Select explicit policies through existing typed metadata/configuration/code paths and preserve default behavior. |
| P123-03 | Activity membership | Activate/reconstruct carts and related user/address data, including data shared by multiple active activities. |
| P123-04 | Lifecycle and safety | Re-evaluate membership after authoritative changes and activity completion; retain shared members until no active owner/policy needs them. |
| P123-05 | Qualification and handoff | Prove the ordinary multi-user route, persistence/coherence, bounded residency and authorization, then complete validation and review. |

## Dependencies and scope

Reuse the delivered Phase 16/19 residency and EntityKind foundation and current EntityStore/UoW commit/coherence behavior. Consume the verified business transition lifecycle where applicable. This does not reopen those Phases or equate business workflow Entities with WorkflowEngine instances.

Coordinate with [Phase 124](phase-124.md) for Read-only retention and with [Phase 108](phase-108.md) for qualified multi-runtime synchronization. Neither whole-Phase 108 closure nor Phase 124 delivery is a prerequisite for the ordinary single-runtime Working Set route. Cross-runtime claims require actual qualified synchronization; do not simulate that dependency as delivered.

Exclude full historical/media residency, a new identity/authorization registry, a global query cache, a new shared transaction domain and new cluster coordination. Record a concrete producer gap if existing declaration paths cannot express the required policy; do not invent consumer-side CML syntax or automatically open a Cozy Phase.

## Completion

The checklist owns acceptance. Qualification includes active carts and related profile/address data, shared references, independent activities, completion, failed mutations, restart/reconstruction and user isolation. Record proportionate Step checks, final repository-full validation and an independent integrated review before closure. No validation or completion is claimed by registration.
