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
- Keep `Cwitter` login/logout UX app-owned while auth/session behavior remains
  CNCF-owned.
- Keep deployment as multi-CAR within one subsystem:
  - `Cwitter` component = `CAR`
  - `Cwitter` subsystem = `SAR`
  - `textus-user-account` = separate `CAR`
  - local development uses `subsystem/component.d/*.car`
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
- `Cwitter` owns login/logout UX and uses CNCF auth/session behavior.
- no separate `Cwitter` profile entity is introduced in this phase.

## 3. Non-Goals

- No OAuth/OIDC federation.
- No SSO or MFA.
- No external IdP protocol implementation.
- No token-pair-first auth model.
- No second auth abstraction beside the existing `AuthenticationProvider` path.
- No separate `Cwitter` profile model unless Phase 16 proves it unavoidable.

## 4. Current Work Stack

- A (DONE): AU-01 — Complete the CNCF auth/session contract on top of the existing `AuthenticationProvider` and subsystem security wiring.
- B (DONE): AU-02 — Add Web session login/logout/current-session flow at ingress/runtime.
- C (DONE): AU-03 — Add `textus-user-account` adapter as the first provider.
- D (ACTIVE): CW-01 — Implement `Cwitter` auth-aware baseline.
- E (PLANNED): CW-02 — Derive and implement the minimum user-management additions needed for mention/DM.

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
- `Cwitter` is already scaffolded as `component/ + subsystem/` and is the
  concrete consumer for this phase.
- local `component.d` staging is development-only; production remains
  repository-first.

## 5. Development Items

- [x] AU-01: Complete the CNCF auth/session contract on top of the existing `AuthenticationProvider` and subsystem security wiring.
- [x] AU-02: Add Web session login/logout/current-session flow at ingress/runtime.
- [x] AU-03: Add `textus-user-account` adapter as the first provider.
- [ ] CW-01: Implement `Cwitter` auth-aware baseline.
- [ ] CW-02: Derive and implement the minimum user-management additions needed for mention/DM.

## 6. Next Candidates

- CW-03: Clarify whether Cwitter needs a separate profile/domain model after the auth baseline lands.
- NP-1601: Provider replacement and multiple-provider precedence beyond the first provider baseline.
- NP-1602: External identity/federation after the built-in Web session baseline is complete.

## 7. References

- `docs/phase/phase-15.md`
- `docs/design/management-console.md`
- `docs/design/web-layer.md`
- `src/main/scala/org/goldenport/cncf/security/AuthenticationProvider.scala`
- `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`
