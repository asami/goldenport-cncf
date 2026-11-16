# Phase 123 checklist — Activity-Scoped Entity Working Sets

status=planned
execution_started=false

Owner: CNCF. Plan: [Phase 123](phase-123.md). All work is OPEN.

## P123-01 — Inventory and contract

- [ ] Separate EntityKind defaults, explicit residency policies, business state and user/activity membership; preserve the distinction between business workflow Entities and runtime WorkflowInstance.
- [ ] Define activity identity/scope, activation, completion and related-data selection using existing typed ownership; specify durable data and authorization boundaries.
- [ ] Update owning design/specification documents from the planning note before implementing new behavior.

## P123-02 — Policy integration

- [ ] Existing metadata/configuration/code selects the policy without changing ordinary defaults or treating every workflow/user record as automatically resident.
- [ ] Document real declaration gaps and producer ownership; no speculative CML or ad hoc ID parsing is introduced.

## P123-03 — Activity membership

- [ ] An ordinary cart activity selects its cart and needed user/address data within the correct domain, tenant and principal scope; already resident master/reference data is reused.
- [ ] Multiple independent activities can share an Entity without premature eviction when one ends.
- [ ] Warm-up/reconstruction and bounded memory policy have observable readiness/fallback behavior; incomplete loading never appears as an empty authoritative result.

## P123-04 — Lifecycle and safety

- [ ] State/activity changes and activity completion re-evaluate residency; disconnect/session expiry semantics are explicit rather than an invented universal timeout.
- [ ] Authoritative mutation outcomes drive update/delete/invalidation; a failed mutation cannot publish uncommitted cache state. Eviction does not delete authoritative records or discard unsettled durable outcomes.
- [ ] Residency grants no access rights; another user cannot read private cart/profile data through resident lookup.

## P123-05 — Qualification and handoff

- [ ] Connected fixtures cover two users, carts/addresses, shared references, overlapping activities, activity completion and authoritative reload after restart.
- [ ] Failure, warm-up, memory-bound and authorization cases verify observed behavior through ordinary entry points.
- [ ] Record actual qualification scope; multi-runtime synchronization and Phase 124 integration are claimed only when exercised against their qualified capabilities.

## Phase closure

- [ ] All owning Steps are accepted or outstanding scope explicitly dispositioned; repository-full validation, independent integrated review and the implementation handoff are recorded before closure.
