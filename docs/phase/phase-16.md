# Phase 16 — Authentication Baseline With Cwitter

status = open

## 1. Purpose of This Document

This work document tracks the active stack of work items for Phase 16.
It is authoritative for current progress, scope, and execution order.

This document is a progress dashboard, not a design journal.

## 2. Phase Scope

- Establish the CNCF authentication and user-management baseline.
- Use `Cwitter` as the development driver for authenticated application flows.
- Complete the existing CNCF auth/session footholds instead of introducing a
  second auth model.
- Treat `textus-user-account` as the first provider implementation behind the
  CNCF auth boundary.
- Keep `Cwitter` app navigation app-owned while auth/session behavior remains
  CNCF-owned.
- Let `textus-user-account` own account UI for signup, password reset, and
  optional 2FA flows.
- Keep deployment as multi-CAR within one subsystem:
  - `Cwitter` component = `CAR`
  - `Cwitter` subsystem = `SAR`
  - `textus-user-account` = separate `CAR`
  - local development uses `repository.d/*.car` as a component search source
  - production resolves by standard repository using `name + version`
    from the default local cache root `~/.cncf/repository`

Current semantic direction:

- `AuthenticationProvider` is the canonical auth foothold.
- `Component.authenticationProviders` remains the component-side auth hook.
- subsystem `security.authentication.providers` remains the authoritative
  provider wiring surface.
- `SecurityContext` / `SessionContext` remain the canonical runtime carrier.
- ingress restoration remains centered on `IngressSecurityResolver`.
- Web session is the canonical Phase 16 auth mode.
- `textus-user-account` account identity is the `Cwitter` user identity in this
  phase.
- `Cwitter` owns app navigation and uses CNCF auth/session behavior.
- `textus-user-account` owns account UI for signup, reset, and optional 2FA
  while CNCF continues to own transport/runtime integration.
- CNCF owns message-delivery provider SPI, subsystem wiring, and runtime
  invocation.
- no separate `Cwitter` profile entity is introduced in this phase.

## 3. Non-Goals

- No OAuth/OIDC federation.
- No SSO.
- No mandatory/global MFA policy; only optional provider-owned 2FA is in
  scope.
- No external IdP protocol implementation.
- No token-pair-first auth model.
- No real SMTP/SMS delivery provider in this phase; message-delivery stays
  stub-backed.
- No second auth abstraction beside the existing `AuthenticationProvider` path.
- No separate `Cwitter` profile model unless Phase 16 proves it unavoidable.

## 4. Current Work Stack

- A (DONE): AU-01 — Complete the CNCF auth/session contract on top of the existing `AuthenticationProvider` and subsystem security wiring.
- B (DONE): AU-02 — Add Web session login/logout/current-session flow at ingress/runtime.
- C (DONE): AU-03 — Add `textus-user-account` adapter as the first provider.
- D (DONE): CW-01 — Implement `Cwitter` auth-aware baseline.
- E (DONE): CW-02 — Derive and implement the minimum user-management additions needed for mention/DM.
- F (DONE): AU-04 — Add message-delivery SPI with stub-backed password reset and optional 2FA.

Current note:
- Phase 15 is closed and remains the scheduler/timer baseline.
- Phase 16 starts auth-first before `Cwitter` feature build-out.
- AU-01 is complete: provider `Some/None/Failure` semantics, runtime provider ordering, fallback behavior, and `ExecutionContext.security` carriage are fixed.
- AU-02 is complete: app-owned login/logout/session routes, built-in `auth`
  operations, subsystem cookie transport, and `x-textus-session` REST/client
  session reuse now run through the provider boundary.
- AU-03 is complete: `textus-user-account` now implements the CNCF auth
  boundary and owns secret/refresh token lifecycle internally while exposing
  only normalized principal/session state to CNCF.
- browser cookie and `x-textus-session` continue to carry only the
  provider-owned session id; provider tokens remain internal to
  `textus-user-account`.
- AU-04 is complete: CNCF now resolves message-delivery providers through subsystem
  wiring, ships a built-in stub provider, and `textus-user-account` uses that
  boundary for password reset and optional email-backed 2FA.
- password reset tokens and 2FA challenge state remain provider-owned inside
  `textus-user-account`; CNCF only invokes message delivery.
- `Cwitter` is already scaffolded as `component/ + subsystem/` and is the
  concrete consumer for this phase.
- local `repository.d` staging is development-only; production remains
  repository-first.

## 5. Development Items

- [x] AU-01: Complete the CNCF auth/session contract on top of the existing `AuthenticationProvider` and subsystem security wiring.
- [x] AU-02: Add Web session login/logout/current-session flow at ingress/runtime.
- [x] AU-03: Add `textus-user-account` adapter as the first provider.
- [x] CW-01: Implement `Cwitter` auth-aware baseline.
- [x] CW-02: Derive and implement the minimum user-management additions needed for mention/DM.
- [x] AU-04: Add message-delivery SPI with stub-backed password reset and optional 2FA.

## 6. Next Candidates

- CW-03: Run Cwitter manually, capture real-use UX/runtime issues, and fix them without adding a separate profile model.
- NP-1601: Provider replacement and multiple-provider precedence beyond the first provider baseline.
- NP-1602: Real email/SMS message-delivery providers after the stub-backed message-delivery path is stable.
- NP-1603: External identity/federation after the built-in Web session baseline is complete.

## 7. References

- `docs/phase/phase-15.md`
- `docs/design/management-console.md`
- `docs/design/web-layer.md`
- `src/main/scala/org/goldenport/cncf/security/AuthenticationProvider.scala`
- `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`
