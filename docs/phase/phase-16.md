# Phase 16 — Authentication Baseline With Cwitter

status = closed

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
- Support both minimal component-CAR startup and SAR deployment:
  - `Cwitter` component = `CAR`
  - `Cwitter` component CAR may act as a deemed subsystem for local/simple
    startup
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
- `textus-user-account.loginName` is the provider canonical identity field.
- `Cwitter` may project `handle = loginName`; `handle` is not a provider-core
  field.
- `Cwitter` owns app domain pages and uses CNCF auth/session behavior.
- `textus-user-account` owns account UI for signup, reset, and optional 2FA
  while CNCF continues to own transport/runtime integration.
- CNCF owns message-delivery provider SPI, subsystem wiring, and runtime
  invocation.
- CNCF owns common Web composition surfaces for theme and provider-page light
  customization.
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
- G (DONE): CW-03 — Run Cwitter manually and complete the provider-page reuse/minimal-code sample direction.
- H (DONE): PH-16H — Harden the runtime surfaces discovered by Cwitter manual use.
- I (DONE): PK-16 — Support repository-resolved component CAR startup beside SAR deployment.

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
- CW-03 is complete: `Cwitter` uses provider-owned signup/signin/reset/2FA pages
  directly, keeps application code focused on timeline/post/mention/DM, and
  documents minimal CAR and SAR launch modes.
- PH-16H is complete: structured Web/API errors, production admin guards,
  admin navigation, working-set policy, debug trace-job metadata, locale-aware
  display formatting, shared Web theme, provider-page light customization, and
  lifecycle audit defaults are in place.
- PK-16 is complete: component CARs can carry `web/*` and
  `assembly-descriptor.*` defaults, dependencies are resolved from the component
  repository or local `repository.d`, and SAR assembly overrides merge by field.
- `Cwitter` is scaffolded as `component/ + subsystem/` and remains the concrete
  consumer for this phase.
- local `repository.d` staging is development-only; production remains
  repository-first.

## 5. Development Items

- [x] AU-01: Complete the CNCF auth/session contract on top of the existing `AuthenticationProvider` and subsystem security wiring.
- [x] AU-02: Add Web session login/logout/current-session flow at ingress/runtime.
- [x] AU-03: Add `textus-user-account` adapter as the first provider.
- [x] CW-01: Implement `Cwitter` auth-aware baseline.
- [x] CW-02: Derive and implement the minimum user-management additions needed for mention/DM.
- [x] AU-04: Add message-delivery SPI with stub-backed password reset and optional 2FA.
- [x] CW-03: Run Cwitter manually and complete the provider-page reuse/minimal-code sample direction.
- [x] PH-16H: Harden runtime surfaces discovered by Cwitter manual use.
- [x] PK-16: Support repository-resolved component CAR startup beside SAR deployment.

## 6. Next Candidates

- NP-1601: Provider replacement and multiple-provider precedence beyond the first provider baseline.
- NP-1602: Real email/SMS message-delivery providers after the stub-backed message-delivery path is stable.
- NP-1603: External identity/federation after the built-in Web session baseline is complete.

## 7. Closure Verification

Closed on Apr. 26, 2026 after the following checks:

- CNCF `sbt --batch Test/compile`
- CNCF focused executable specs:
  - `org.goldenport.cncf.subsystem.GenericSubsystemDescriptorSpec`
  - `org.goldenport.cncf.cli.CncfRuntimeConfigFileSpec`
  - `org.goldenport.cncf.component.repository.ComponentRepositoryCarSpec`
  - `org.goldenport.cncf.http.WebDescriptorSpec`
- Cwitter `sbt --batch "component/cozyBuildCAR" "subsystem/cozyBuildSAR"`
- Cwitter smoke server startup on port `19533`
- Cwitter smoke Web entrypoints:
  - `/web/cwitter`
  - `/web/textus-user-account/signup?returnTo=/web/cwitter`
  - `/web/textus-user-account/signin?returnTo=/web/cwitter`
  - `/web/textus-user-account/password-reset?returnTo=/web/cwitter`

The local smoke server required non-sandbox execution to bind/connect to the
localhost port.

## 8. References

- `docs/phase/phase-15.md`
- `docs/design/management-console.md`
- `docs/design/web-layer.md`
- `src/main/scala/org/goldenport/cncf/security/AuthenticationProvider.scala`
- `src/main/scala/org/goldenport/cncf/security/IngressSecurityResolver.scala`
