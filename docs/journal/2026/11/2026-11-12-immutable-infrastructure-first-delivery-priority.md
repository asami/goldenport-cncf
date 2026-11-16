# Immutable Infrastructure First Delivery Priority

Date: 2026-11-12
Status: implementation priority and open delivery plan recorded; implementation not started here

The user prioritized the implementation needed to put immutable infrastructure
into practical operation. The [HA design](../../../design/availability-architecture.md#implementation-priority-operational-immutable-replacement)
now identifies first operational planned replacement as the highest priority
within this HA work. [Phase 108](../../../phase/phase-108.md) records five ordered
OPEN work units and a separately identified first operational acceptance gate.

That gate includes qualified runtime authority, a Primary and Standby-capable
Read Replica, an admitted update completing across the admission switch, common
A/B/C cache synchronization, fresh replacement capacity, synchronized fallback,
admitted switch-back, conditional retirement and repeatable scheduled/selected-
CAR replacement. It does not wait for broad automatic Failover, release-discovery
automation, numerous replicas, extra drivers or host/site qualification. Safe
refusal on authority loss and truthful interrupted-work outcomes remain required.

Existing architecture, later work and historical journals are preserved. The
plan is reprioritized, not marked implemented or closed. Initial source inventory
found the ordinary runtime lifecycle and existing durable-work contracts; it did
not establish an implemented HA replacement or synchronization engine. This
documentation change adds no such implementation.

Document links/anchors, formatting and working-tree scope were checked. No native
Session or Goal was started; no source/dependency change, SBT/application test,
commit, publication or live deployment was performed.
