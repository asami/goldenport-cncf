# Phase 16 — Authentication Baseline With Cwitter Checklist

This document contains detailed task tracking and execution decisions
for Phase 16.

It complements the summary-level phase document (`phase-16.md`).

---

## Checklist Usage Rules

- This document holds detailed status and task breakdowns.
- The phase document (`phase-16.md`) holds summary only.
- A development item marked DONE here must also be marked `[x]`
  in the phase document.

---

## Status Semantics

- ACTIVE: currently being worked on (only one item may be ACTIVE).
- SUSPENDED: intentionally paused with a clear resume point.
- PLANNED: planned but not started.
- DONE: handling within this phase is complete.

---

## Recommended Work Procedure

Phase 16 work proceeds in this order:

1. AU-01 completes the existing CNCF auth/session contract first.
2. AU-02 makes Web session login/logout/current-session a real runtime path.
3. AU-03 adapts `textus-user-account` to the CNCF auth boundary.
4. CW-01 implements the `Cwitter` auth-aware baseline on that contract.
5. CW-02 derives the minimum extra user-management capability needed for
   mention/DM.
6. AU-04 adds message-delivery SPI plus stub-backed password reset and optional
   2FA.

This order keeps the auth boundary CNCF-owned and avoids hard-wiring `Cwitter`
directly to provider internals.

---

## AU-01: CNCF Auth/Session Contract Completion

Status: DONE

### Objective

Complete the existing CNCF auth/session footholds so they form one canonical
runtime contract.

AU-01 must ensure:

- `AuthenticationProvider` remains the canonical auth boundary
- subsystem `security.authentication.providers` is the authoritative provider
  wiring surface
- authenticated vs anonymous subject behavior is fixed at runtime
- provider selection from subsystem wiring is deterministic
- provider results are mapped into `ExecutionContext.security`
- auth failures stay in `Consequence`, not exception flow

### Detailed Tasks

- [x] Fix the canonical request/response/auth-result contract around the
      existing `AuthenticationProvider` path.
- [x] Fix provider selection behavior from subsystem wiring.
- [x] Clarify authenticated vs anonymous subject semantics.
- [x] Ensure `ExecutionContext.security` is the single runtime carrier for the
      resolved auth/session state.
- [x] Add executable coverage for subsystem auth wiring and auth contract
      failure/success behavior.

### Expected Outcome

- CNCF has one completed auth/session contract centered on the already-existing
  authentication footholds.
- `AuthenticationProvider` now has explicit `Some/None/Failure` semantics, and runtime fallback no longer hides provider failures.

### Guardrails

- No second auth abstraction beside `AuthenticationProvider`.
- No provider-specific contract in application code.

---

## AU-02: Web Session Runtime Path

Status: DONE

### Objective

Make browser-oriented Web session auth the canonical Phase 16 runtime mode.

### Detailed Tasks

- [x] Add login success path that creates an authenticated session.
- [x] Add login failure path as deterministic `Consequence.Failure`.
- [x] Add logout of the current session.
- [x] Add current-session/current-user restoration at ingress.
- [x] Keep anonymous request behavior aligned with current authorization rules.
- [x] Reuse the same provider-owned session through browser cookie and
      REST/client `x-textus-session` header.

### Expected Outcome

- Browser/Web requests can authenticate once and restore principal/session on
  subsequent requests.
- Built-in `auth.login`, `auth.logout`, and `auth.session` operations now back
  the shared runtime flow.
- `secret token` / `refresh token` management remains provider-owned.

### Guardrails

- No OAuth/OIDC.
- No SSO.
- No mandatory/global MFA policy in this phase.
- No token-pair-first design.

---

## AU-03: `textus-user-account` Provider Adapter

Status: DONE

### Objective

Adapt `textus-user-account` as the first provider implementation under the CNCF
boundary.

### Detailed Tasks

- [x] Adapt login/logout/logout-all/current-account behavior into the CNCF auth
      contract.
- [x] Map account identity to principal id and principal attributes.
- [x] Restore provider-backed session state into `SecurityContext`.
- [x] Keep provider internals out of `Cwitter`.
- [x] Add executable coverage for the provider adapter contract.

### Expected Outcome

- `textus-user-account` is the first working auth provider without becoming the
  canonical CNCF auth model.
- secret token / refresh token lifecycle, rotation, and revocation remain
  provider-owned and bound to account id inside `textus-user-account`.
- CNCF continues to own only cookie/header transport, provider selection, and
  normalized `ExecutionContext.security` restoration.

### Guardrails

- No direct provider-specific dependency from `Cwitter` business logic.
- Local `repository.d` staging remains development-only.

---

## CW-01: `Cwitter` Auth-Aware Baseline

Status: DONE

### Objective

Implement the first authenticated `Cwitter` baseline using the generated
`component/ + subsystem/` app layout.

### Detailed Tasks

- [x] Add login/logout UX owned by `Cwitter`.
- [x] Add authenticated posting flow.
- [x] Add current-user-aware request handling.
- [x] Keep `Cwitter` deployment in `CAR + SAR` shape.
- [x] Add scenario coverage for authenticated posting through the shared
      auth/session flow.

### Expected Outcome

- `Cwitter` becomes the concrete driver app for the Phase 16 auth baseline.

### Guardrails

- `Cwitter` uses CNCF auth/session behavior, not provider internals.
- No separate profile model in this item.

---

## CW-02: Minimum User-Management Additions for Mention / DM

Status: DONE

### Objective

Extract and implement only the minimum user-management capability needed for
mention and direct-message flows.

### Detailed Tasks

- [x] Fix stable user handle / lookup behavior.
- [x] Add mention target lookup.
- [x] Add DM recipient resolution.
- [x] Enforce authenticated sender semantics.
- [x] Enforce participant/owner visibility boundaries.
- [x] Ensure session invalidation does not leave stale user context.

### Expected Outcome

- The minimum user/account behavior required by `Cwitter` mention/DM is present
  without widening the phase into a full social/profile platform.

### Guardrails

- `textus-user-account` account remains the user identity in Phase 16.
- No separate `Cwitter` profile entity unless proven unavoidable.

---

## AU-04: Notification SPI, Password Reset, and Optional 2FA

Status: DONE

### Objective

Introduce a CNCF-managed message-delivery provider boundary, then use it from
`textus-user-account` to add password reset and optional two-factor
authentication without adding a second auth abstraction.

### Detailed Tasks

- [x] Add `MessageDeliveryProvider`, `UnifiedMessage`,
      `MessageDeliveryResult`, and `MessageDeliveryProviderRuntime` in CNCF.
- [x] Add subsystem message-delivery provider wiring parallel to auth wiring.
- [x] Add a built-in stub message-delivery component for local/test use.
- [x] Implement password reset request/confirm flows in
      `textus-user-account`.
- [x] Implement optional email-backed 2FA enrollment and login challenge in
      `textus-user-account`.
- [x] Update account Web UI wording to use `Handle`, `Handle or Email`, and
      explicit field help.
- [x] Add executable coverage for message-delivery runtime wiring, password reset,
      and 2FA challenge behavior.

### Expected Outcome

- CNCF owns message-delivery provider SPI, provider resolution, and runtime
  invocation.
- `textus-user-account` owns password reset token state, 2FA enrollment, and
  login challenge state.
- Local/test flows run against the built-in message-delivery stub without any real
  SMTP/SMS provider.
- Existing non-2FA Cwitter login/post/DM baseline remains green.

### Guardrails

- No second auth abstraction beside `AuthenticationProvider`.
- No direct SMTP/SMS SDK calls from `textus-user-account` business logic.
- No mandatory/global MFA policy in this phase.
- Real email/SMS provider integration is deferred until after the stub-backed
  flow is stable.

---

## Deferred / Out-of-Scope Notes

- External identity federation.
- SSO.
- Mandatory/global MFA policy.
- Real email/SMS message-delivery providers.
- Multiple-provider precedence beyond the first provider baseline.
- Separate profile/domain expansion for `Cwitter`.
