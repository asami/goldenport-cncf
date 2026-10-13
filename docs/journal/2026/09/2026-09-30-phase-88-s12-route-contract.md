# Phase 88 S12 route-first correction

Date: 2026-09-30
Status: route contract defined; executable acceptance not yet implemented

The S12 implementation order is Workflow → `DataStoreSpace` → one physical
provider. The prior same-owner memory staging remains an internal candidate,
not a public capability. A direct SQLite candidate was started but withdrawn
before any SBT test: it would have bypassed the still-undefined Workflow and
`DataStoreSpace` authority boundary. The exploratory SQL additions were
removed; the focused-287 memory source and test bytes were preserved.

The [route contract](../../../design/workflow-multi-guard-transaction.md)
defines default-deny host-installed exact owner/collection permissions,
same-provider-object resolution for root, successor, all extra guards and
side Creates, opt-in provider capability, one physical commit/rollback, and
post-acknowledgement readback before Workflow success projection. The host
binding must come from trusted runtime/component assembly, not from the pure
plan, an owner label, skill, or AI action.

Next executable specifications must call the Workflow adapter and
`DataStoreSpace` operation, first proving rejection before provider
invocation with a counting fake and then proving memory/SQLite success,
rollback, independent-writer exclusivity, and missing/divergent readback.
No such S12 route spec has passed yet. No new SBT attempt, P2-U15 acceptance,
artifact refresh, commit or publication is claimed by this correction.
