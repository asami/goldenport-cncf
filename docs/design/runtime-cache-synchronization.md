# Runtime Cache Synchronization

status=design-direction
authority=canonical-design
updated_at=2026-11-12
runtime=[Subsystem Runtime Architecture](subsystem-runtime-architecture.md)
availability=[Availability Architecture](availability-architecture.md)

## Purpose and scope

Provide common synchronization and purge semantics for reconstructable runtime
data caches and read projections. The design supports one current Primary for
new mutation admission and any configured number of Read Replicas. It is
not specialized to a fixed pair or to the temporary A/B/C replacement example.
HA uses this mechanism; it does not own a separate synchronization protocol.

Role names follow
[Availability Architecture](availability-architecture.md#roles-and-established-control-mechanisms):
Primary, Read Replica, Standby and Draining Former Primary. A serving-ready Read
Replica is not automatically a promotion-ready Standby; takeover qualification
is an independent requirement.

This is intended architecture, not an implemented transport, public ABI, accepted
HA capability or a claim that all existing caches satisfy these requirements.
It does not replace authoritative persistence, transaction conflict handling,
Job recovery, authorization or the separate CAR/SAR artifact cache lifecycle.

## Ownership and topology

- The owning persistence/transaction integration establishes committed changes,
  source versions and recoverable change positions for its consistency scope.
- CNCF runtime cache infrastructure distributes and applies changes, manages
  cache membership/readiness, and rebuilds or catches up reconstructable state.
- Availability orchestration consumes those facts for promotion, retention,
  switch-back and retirement. Control Center presents their actual state.
- Applications select supported projection and consistency policy through the
  owning contracts. They do not implement per-machine replication loops.

Members may include the current Primary, multiple serving Read Replicas, warming
replicas and retained former Primaries. Cache participation and mutation admission
are separate roles. A Read Replica gains no business mutation authority
by joining, reading data or applying replicated cache changes.

Use one logical committed-change mechanism per declared consistency scope.
Do not build pair-specific A-to-B, B-to-C and C-to-A authoritative synchronization
rules. Transport may distribute changes in different ways, but receivers must
apply the same source-owned version and ordering semantics.

## Committed changes and delivery

Only committed authoritative changes become synchronization facts. A speculative
memory update, request receipt, HTTP response, process-local counter or clock
timestamp is not a committed source version. Aborted changes do not propagate
as successful updates.

The integration must connect commit outcome to recoverable change discovery.
A crash after data commit and before notification cannot permanently lose the
cache update. Use the owning persistence/commit mechanism to recover that gap;
a volatile post-commit callback alone is insufficient. Transport/backend
selection and any required producer capability remain implementation work.
Do not add an independent HA transaction database, management-hash protocol or
speculative rollback engine to conceal a missing owning capability.

Each logical change identifies its data/projection scope, affected keys or
invalidation scope, operation, source-owned version/change position and any
required transaction-batch correspondence. Exact types are not fixed here.
Keep source data revisions, stream positions, runtime lifecycle identities and
cache rebuild generations distinct. A transport acknowledgement is not evidence
that a replica has applied the change.

Ordering is that of the authoritative source, not message arrival, publisher
identity or wall-clock time. Changes committed by a Draining Former Primary and
the new Primary belong to this same ordering model. Independent stores may need
separate positions; do not manufacture a global scalar commit order or claim a
cross-store atomic transaction where the owning stores provide neither.

Delivery may repeat or reorder notifications. Application of already covered
changes must not roll state back or execute a business side effect again. A
reported applied-through position covers every required preceding change, not
merely the largest sequence number received. Transaction/projection groups must
not become ready while only part of a required group has been applied.

## Updates, deletes, invalidation and purge

The common semantics include:

| Change | Cache effect |
| --- | --- |
| Versioned update | Apply an admitted newer representation, or invalidate the affected cached value for authorized reload |
| Authoritative deletion | Remove the cached representation and retain enough version/deletion knowledge to reject stale resurrection |
| Key or projection invalidation | Make affected values unavailable until refreshed to the required source state |
| Scoped purge | Discard the selected reconstructable cache scope, then rebuild/catch up before treating that scope as ready |

Purge deletes cache state, not authoritative business data. A broad purge must
not erase the ordering/generation boundary needed to reject older notifications
or stale loads that complete after the purge. A concurrent cache miss/load may
publish only if its source version and load generation remain admissible; an
older load must not overwrite newer applied state or undo a deletion.

Replicated application updates only derived cache/projection state. It must not
repeat the original Operation, update authoritative data, trigger a business
Workflow again, or republish the same logical change as a new source mutation.
Authorization, tenant/projection scope and permitted fields remain enforced;
cache sharing is not a route around the original data visibility boundary.

## Initial build, catch-up and rejoin

A new member obtains an authoritative consistent baseline and its corresponding
source position, then applies changes after that position. Establish their
correspondence through the owning source capability. An unrelated snapshot plus
an arbitrarily sampled timestamp/cursor is not a gap-free baseline.

Capture/retain the required delta while the initial image is built. Moving from
initial load to live application must neither skip changes nor let the initial
image overwrite a later update. The member becomes serving-ready only after
its required baseline, projection and catch-up criteria are satisfied.

A disconnected, restarted or retained member resumes from its valid position
when history is still available. If the required history has expired or a gap
cannot be reconciled, mark the affected scope unready and rebuild it. Never
advance the applied position merely to match the current Primary.

Membership is not fixed to two or three machines. Track readiness and applied
progress separately for each member. A slow or missing replica is visible and
handled by the selected consistency/serving policy; it is not silently treated
as synchronized. This design does not require every read cache to acknowledge
every write synchronously.

## Read consistency and observability

The serving policy explicitly distinguishes reads that tolerate replication lag
from those requiring a particular confirmed source state. For the latter, an
eligible replica must meet the required position/version, or the runtime must
wait within policy, use an authorized authoritative read, or report unavailable.
Routing to the current Primary alone does not prove freshness while former-Primary
commits or cache notifications are still pending.

Report at least member role, covered scopes, baseline/rebuild generation,
applied-through position, known target position, lag/gaps, last application
failure and serving/promotion readiness. Distinguish received, applied and
reconstructed state. Positions are source-owned facts, not content hashes,
integrity attestations or a promise of permanent history retention.

## HA use: admission switch before drain completion

During planned replacement:

1. A is the current Primary; B is a caught-up Read Replica qualified as Standby.
2. At the routing/admission boundary, make B responsible for new reads and
   mutations and close A to new independent work. Stale routes cannot admit
   new mutations on A after that boundary.
3. A retains only continuation authority for the exact work already admitted,
   including its necessary child work. B accepts new work while A completes
   those updates. Their confirmed changes feed the same synchronization model.
4. Start replacement Read Replica C and establish its consistent baseline plus
   delta catch-up. Existing additional Read Replicas remain ordinary members.
5. Keep A participating during its drain and fallback retention. Once its
   admitted updates are settled it becomes read-only/fallback-retained; it does
   not regain new mutation admission merely by receiving changes.
6. Retire A only after its admitted updates are settled, the required replacement
   Read Replica capacity is ready, the retention/observation policy is satisfied,
   and no switch-back or unresolved mutation outcome requires it to remain.

Here `settled` means an actual confirmed commit, abort or failure disposition.
An uncertain external effect or unacknowledged commit is not proven settled.
Replacement alone cannot guarantee success for every admitted business update;
it must not force-interrupt that update merely to meet the rotation schedule.
Report prolonged drain or unresolved outcomes instead of declaring retirement.

The new-admission Primary is unique within its authority scope. Temporary old-work
completion authority is not unrestricted active-active mutation. Native
transactions/version checks still handle conflicts between old admitted work
and new work. Cache synchronization itself does not serialize or repair those
business updates. A required causal read/write dependency must be enforced by
its owning transaction/work contract, not guessed from routing order.

Switch-back repeats admission transfer and drain. Before reactivation, the
retained runtime must be compatible, caught up and ready; its old memory is not
restored as authoritative state. Unexpected failure additionally requires that
the former runtime cannot later admit unauthorized new mutations or complete
work under invalid authority. Failure isolation/takeover is an owning runtime
contract, not proof supplied by an unreachable process or a cache cursor.

The [availability control protocols](availability-architecture.md#roles-and-established-control-mechanisms)
reuse established Leader Election/Lease and enforce fencing at the affected
mutation boundaries. Their [planned Switchover](availability-architecture.md#planned-switchover)
retains explicitly scoped old-work completion permission. Their
[unplanned Failover](availability-architecture.md#unplanned-failover) invalidates
former authority and reconciles interrupted outcomes without waiting for a drain
acknowledgement. Cache synchronization proves neither control-service ownership
nor fencing. After fencing, no old completion permit allows a late mutation;
already committed changes are recovered from the authoritative source and still
propagate through this mechanism. A returning runtime rejoins as a replica and
qualifies catch-up before any separately admitted failback.

## Behavioral cases for implementation qualification

- Fan out committed changes to multiple replicas; aborted changes apply nowhere.
- Recover a commit/notification gap after restart without rerunning business work.
- Deliver duplicates and reversed update/delete messages without rollback or resurrection.
- Complete an old cache load after purge without reintroducing stale data.
- Build C while A drains and B commits; expose C only after consistent catch-up.
- Observe a history gap, refuse stale serving and rebuild the affected scope.
- Switch new admissions to B while A completes only its admitted work; include
  A's late commits in every eligible replica's synchronization.
- Block A's retirement independently for pending work, unready replacement
  capacity, retention, unresolved outcomes or an active switch-back.
- Catch up retained A before switch-back, with no authoritative memory rollback.

These are required future behavioral scenarios, not authored or passing tests.
