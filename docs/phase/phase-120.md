# Phase 120 — CNCF ID inventory and ownership contracts

status=planned
planned_at=2026-11-06
owner=CNCF
checklist=[Phase 120 checklist](phase-120-checklist.md)

## Start condition

Keep this Phase unopened through completion of the 2026-11-10 NICT demo.
The date alone does not start it. After the demo, review demo findings and
choose the first bounded migration. Until then, prioritize the existing Studio
and App rehearsal and use only the temporary disposition in the
[work inventory](../journal/2026/11/2026-11-06-id-phase-demo-boundary.md).

## Scope and sequence

| Step | Deliverable and acceptance |
| --- | --- |
| P120-01 Inventory | Reconcile the [ID design](../design/id.md) and [type inventory](../notes/2026-11-06-id-types-and-ownership-inventory.md) with writers, persisted values, readers, equality, public contracts and responsible repositories. Classify each type as retained, dedicated UniversalId, role, scoped name, external reference or obsolete. Preserve the contracts already established in Phases 52 and 56. |
| P120-02 Ownership first | Trace issuance, authentication, create, reload and authorization. Specify UserPrincipalId over the full UserId, explicit OwnerId, independent audit actors and Entity-defined privilege. Qualify two ordinary users, rename, session renewal, proxy creation and ambiguous legacy owners. |
| P120-03 Remaining families | Group storage/seed, media, component and subsystem catalogs, jobs, rules, knowledge and observability by owner and compatibility impact. Preserve public collaborator APIs, trace propagation and external IDs. Keep Identifier for its current grammar; classify datatype.ObjectId as obsolete and migrate its consumers by role. |
| P120-04 Compatibility and handoff | For each proposed conversion name the owner, compatibility reader, legacy fixtures, cutover and rollback conditions. Coordinate event delivery with Phase 76: exact delivery key to issued EventId, legacy replay and competing retries. Record which changes require a separate producer Phase. |
| P120-05 Qualification | Verify the selected migration through normal callers, including saved records and denied access. Retire any demo workaround only after equivalent positive and negative coverage passes. Close the inventory with explicit dispositions for deferred migrations. |

Prefer changes at the responsible consumer. Keep simplemodeling-model and
goldenport-core at the verified baseline by default. A later shared contract
change requires a concrete case that cannot be handled at the consumer, an
identified producer, compatibility evidence and a separate implementation plan.
The inventory does not authorize a bulk rewrite of public APIs or stored data.
