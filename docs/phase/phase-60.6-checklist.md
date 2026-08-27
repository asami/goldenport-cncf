# Phase 60.6 Checklist - Component Admin Authorized Management

status=planned
phase=[Phase 60.6 - Component Admin Authorized Management](phase-60.6.md)
predecessor=[Phase 60.5](phase-60.5.md)
successor=[Phase 60.7](phase-60.7.md)

## ADM-07: Authorized Management

Stage Status:
- Current status: OPEN
- Owner: CNCF authorization, management-operation, audit, and Admin maintainers
- Update rule: Record admitted action and deterministic authorization/audit evidence before Phase 60.7 begins.
- Entry rule: ADM-06 is DONE.
- Completion rule: Every admitted management action enforces explicit authorization, validation, lifecycle safety, audit, and deterministic failure.

- [ ] Inventory current Component-owned management Operations.
- [ ] Define the admitted management-action catalog separately from ordinary Component Operations.
- [ ] Enforce explicit authorization, validation, lifecycle preconditions, idempotency where required, and audit.
- [ ] Prevent read-only resource visibility from granting management access.
- [ ] Add forbidden, conflict, unavailable, stale-instance, and retry specifications.
