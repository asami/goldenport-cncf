# Phase 60.1 Checklist - Component Admin Identity and View Model

status=closed
closed_at=2026-08-28
phase=[Phase 60.1 - Component Admin Identity and View Model](phase-60.1.md)
predecessor=[Phase 60](phase-60.md)
successor=[Phase 60.2](phase-60.2.md)

## ADM-02: Identity and Admin View Model

Stage Status:
- Current status: DONE
- Owner: CNCF Component identity and Admin view-model maintainers
- Update rule: Record the accepted versioned view-model contract before Phase 60.2 begins.
- Entry rule: ADM-01 is DONE.
- Completion rule: One versioned view model distinguishes class, release, instance, Subsystem, implicit Subsystem, provenance, and failure states.

- [x] Define a versioned Component Admin view model.
- [x] Distinguish Component class, logical release, loaded instance, Subsystem, and implicit Component Subsystem.
- [x] Preserve exact source/provenance for every projected field.
- [x] Define unavailable, forbidden, stale, incompatible, and corrupt states.
- [x] Add codec, compatibility, ambiguity, and multi-instance specifications.

Closure evidence:

- ADM-02 is accepted in `2c3b8c63d7c7812fd418dcf6f3e0b09cea8cec8c`.
- The mandatory full Phase review and focused repair-cycle-1 closure review
  accepted the identity/provenance, strict-codec, null-failure, and executable
  specification boundaries.
- `phase60.1-clb-adm02-20260828` records this distinct Phase closure and does
  not start Phase 60.2.
