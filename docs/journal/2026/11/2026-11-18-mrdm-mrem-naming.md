# MRDM and MREM Naming Decision (2026-11-18)

## Context and decision

On 2026-11-18, the CNCF in-memory Entity management approach was named at two levels:

- **Memory-Resident Domain Model (MRDM)**: the domain execution architecture in which the active domain working set is held and operated on in memory.
- **Memory-Resident Entity Management (MREM)**: the Entity management mechanism supporting MRDM, including identity, lifecycle, loading, updates, and persistence coordination.

The **system of record is persistent storage**, not memory. Memory-resident Entities are reconstructible operational state. These names are CNCF architectural terms, not claims of established industry standards.

This decision names and clarifies the existing working-set-centric design documented in [Entity Management Model](../../03/entity-management-model.md); it does not replace the established access hierarchy:

`Repository → EntitySpace → EntityRealm → EntityStore → DataStore`.

## Architectural characteristics recorded

| Characteristic | Interpretation |
| --- | --- |
| System of record | Persistent storage through EntityStore/DataStore |
| Primary execution path | In-memory working set of domain Entities |
| Secondary path | Store-backed EntityRealm with optional cache |
| Master resource Entities | All valid instances may be resident |
| Task resource Entities | Active instances resident; inactive/completed instances eligible for eviction |
| Identity | Consistent Entity identity within a defined scope |
| Transaction boundary | CNCF Subsystem / UnitOfWork coordination |
| Durability | Commit success must depend on durable persistence, not merely a memory mutation |
| Recovery | Reload/reconstruct resident state from persistent storage |
| HA | Active/standby and immutable rotation can prewarm the replacement instance |
| Scalability | Residency, ownership, memory limits, and multi-node consistency require explicit policy |

## Constituent techniques and patterns

These are building blocks or evaluation candidates, **not assertions that every technique is implemented**.

| Technique | Role in MRDM/MREM | Notes |
| --- | --- | --- |
| Identity Map | One logical Entity identity maps to one managed instance per defined scope | Define scope explicitly (UoW, realm, subsystem) |
| Unit of Work | Coordinates changes, consistency, and persistence at a transaction boundary | Distinguish CNCF UoW DSL from ORM implementations |
| Repository | Domain-facing Entity access abstraction | Existing CNCF entry point |
| Working Set / Memory Residency | Keeps frequently operated or active Entities in memory | Primary access path |
| Entity Lifecycle Management | Load, activate, update, deactivate, evict, reload | Different policies for master/task resources |
| Change Tracking / Dirty Checking | Identifies modifications requiring persistence | Implementation choice; explicit updates also possible |
| Read-Through / Cache-Aside | Loads missing Entities from persistent storage | Select per EntityRealm |
| Write-Through | Couples update and persistent write | Must specify transaction/commit and failure behavior |
| Eager / Lazy / Hybrid Loading | Controls resident population | Master versus active task resource policies |
| Optimistic Concurrency / Versioning | Detects conflicting updates and stale memory copies | Important across nodes and restarts |
| Pessimistic / Entity-Level Locking | Serializes conflicting operations when necessary | Avoid unbounded lock contention |
| Cache Invalidation / Coherence | Keeps resident copies consistent with committed state | Especially for multiple writers |
| Aggregate Boundary (DDD) | Defines consistency and domain operation boundaries | Not identical to a cache entry |
| Snapshot / Checkpoint | Accelerates rebuilding a working set | Optional; durable store remains authoritative |
| Warm Standby / Prewarming | Loads working set before HA switchover | Coordinate with freshness and cutover |
| Eviction / Admission / Capacity Policy | Bounds memory and selects resident Entities | Avoid confusing reservation with consumption |
| Observability | Measures hit/miss, residency, load, write, conflict, recovery | Needed to validate performance claims |
| Journal / Event Sourcing | Potential future persistence/replay mechanism | Existing design currently favors direct EntityStore/DataStore writes; not assumed implemented |

## Design cautions and open questions

- Define the precise Identity Map scope and how concurrent UnitOfWork instances observe Entities.
- Specify commit ordering, rollback, crash recovery, and whether mutation of resident objects is isolated until persistence succeeds.
- Decide who may write the same Entity across active/standby or multiple Subsystems, and how versions/invalidation work.
- Define memory pressure, eviction, loading, recovery time, and prewarming budgets.
- Validate performance empirically rather than assuming that residency always improves throughput.

## Related material

- `docs/journal/2026/03/entity-management-model.md` — existing working-set and EntityRealm architecture.
- `docs/rules/document-lifecycle.md` — journal versus specifications and implementation planning.

This entry records the naming decision and the technique inventory. Detailed normative contracts and implementation plans belong in their respective specification/planning documents.
