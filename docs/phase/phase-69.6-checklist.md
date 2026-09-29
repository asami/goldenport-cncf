# Phase 69.6 Checklist - User and Operator Job Experience

status=active
phase=[Phase 69.6](phase-69.6.md)

Phase 69.5 must be CLOSED before this checklist starts. Only one stage may be
`IN_PROGRESS`.

## JM69-08: User and Operator Job Experience

Stage Status:
- Current status: IN_PROGRESS
- Owner: CNCF Job application/admin, Web, Help, notification, accessibility, and operator maintainers
- Update rule: Update the status with its checklist; it reaches `DONE` only when every listed criterion is checked, and then records the frozen successor handoff.
- Entry rule: Phase 69.5 is CLOSED.
- Completion rule: Users and operators can discover, understand, control, and recover admitted Jobs through one canonical management model.

- [ ] Define My Jobs, application Job, system-administration projections, ownership/capability boundaries, status/progress/result/recovery vocabulary, and actionable next steps.
- [ ] Define notification creation/update, unread/read state, deduplication, links, expiry, provider-unavailable behavior, and canonical operator search/diagnostic/recovery/retention/definition/queue/scheduler/health operations.
- [ ] Implement descriptor-backed Web/Help/API surfaces without parallel models or application hardcoding; preserve accessibility, progressive enhancement, safe polling, bounded pages, CSRF, authorization, and redaction.
- [ ] Add user/operator isolation, notification, read-state, accessibility, control, recovery, pagination, expired-result, and provider-failure Executable Specifications.

Evidence:
- JM69-08A implementation is complete against the frozen
  [specification](../spec/job-user-operator-experience.md).
  Focused validation passed: HTTP 21/21 and 18 affected-consumer suites
  316/316 (337/337 total), with zero failures or pending tests and compilation
  passed. Independent review and Step acceptance remain pending.

## Phase Completion Gate

- [ ] JM69-08 is DONE with authorized user/operator contract and UX evidence.
- [ ] Phase 69.7 receives the frozen UX handoff.
