# Activity-Scoped Working Sets and Read-only Operation — Planning Input

Date: 2026-11-13
Status: proposed implementation scope; not a delivered runtime contract.

## Ownership and baseline

[Phase 123](../phase/phase-123.md) owns activity-scoped Entity residency. [Phase 124](../phase/phase-124.md) owns Read-only operation for failure and maintenance. Their checklists track progress. Contract Steps update owning design/specification documents before implementation; this note supplies rationale and scope.

Reuse existing EntityKind/WorkingSetPolicy, EntityStore/UoW persistence, commit-driven cache coherence and authorization. Master data may be configured resident in full. Historical transaction/media content remains store-backed. Existing workflow EntityKind identifies a candidate and does not itself supply active-state or active-user residency. A business workflow Entity and WorkflowEngine instance remain distinct.

## Activity-scoped residency

Active-user carts and needed user/address data belong to a Working Set because of their relationship to current business activity; each related record need not have its own ongoing state machine. Define activation/completion and membership under actual domain/tenant/principal ownership. Reuse resident master data rather than duplicating it for each user. If several activities need one Entity, ending one activity must not evict a member still needed by another.

Re-evaluate membership after activity/state changes and authoritative outcomes. Bound memory and expose warm-up/fallback. Activity completion changes residency, not authoritative existence. Cache state is reconstructible; uncommitted updates are not durable truth. Authorization applies to every access, including already resident values.

## Read-only operation

Use one service mode with failure or maintenance reasons. Close business mutation admission across all entry points and visibly transition while legitimately admitted work settles. Maintain explicit bounded operational/audit/maintenance policies. Revoke invalid writer authority; never report established Read-only while scoped stale writers can still mutate.

Qualified resident reads can preserve useful service when persistence is impaired. Cache misses, unbuilt indexes, required fresh reads and unavailable authorization/audit dependencies need bounded refusal rather than indefinite storage waits or fail-open behavior. Resident continuity does not imply restart recovery without an authoritative source.

Retain eligible business-read cache values through routine TTL discard and scheduled purge, within a declared memory budget. Retention does not make data fresh: retain freshness metadata and serve stale values only where explicitly permitted. Continue explicit deletion/invalidation, committed synchronization and security expiry/revocation. Do not globally freeze caches or security clocks.

Restoration requires resolved failure or completed maintenance, qualified readiness/synchronization, valid authority and reconciliation of uncertain outcomes. Refused business writes were not admitted and are not automatically replayed.

## Incremental qualification

Start Phase 123 with an ordinary multi-user cart/address route. Start Phase 124 with already qualified resident data in one runtime. Neither first route waits for whole Phase 108 closure. Add activity-scoped and cluster coverage only when their actual owning capabilities are qualified. Phase 108's immutable-replacement acceptance and existing priorities remain unchanged.
