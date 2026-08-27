# Phase 60.4 Checklist - Component Admin Runtime and Datastore Visibility

status=planned
phase=[Phase 60.4 - Component Admin Runtime and Datastore Visibility](phase-60.4.md)
predecessor=[Phase 60.3](phase-60.3.md)
successor=[Phase 60.5](phase-60.5.md)

## ADM-05: Runtime and Datastore Visibility

Stage Status:
- Current status: OPEN
- Owner: CNCF runtime, datastore, lifecycle, and Admin maintainers
- Update rule: Record exact runtime/datastore identity and rejection evidence before Phase 60.5 begins.
- Entry rule: ADM-04 is DONE.
- Completion rule: Runtime and datastore state is projected with exact identity, provenance, lifecycle state, and instance isolation.

- [ ] Show lifecycle, health, runtime, dependency, and ClassLoader state.
- [ ] Show datastore, schema, collection, Entity ID, and collection ID evidence through their authoritative runtime contracts.
- [ ] For every admitted Admin Entity-ID input, require its declared backing `EntityCollection` and exact collection equality before resolution.
- [ ] Prove scalar locator, foreign canonical ID, entropy fallback, missing owner, and ambiguous owner are rejected deterministically.
- [ ] Distinguish configured, resolved, active, degraded, and failed state.
- [ ] Keep instance state isolated across versions and Subsystems.
- [ ] Add standalone and multi-user `ExecutionContext` acceptance.
