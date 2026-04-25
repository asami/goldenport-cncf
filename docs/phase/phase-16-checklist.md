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
7. CW-03 validates the minimal Cwitter sample manually and removes duplicated
   app-owned account pages.
8. PH-16H hardens runtime surfaces discovered through manual use.
9. PK-16 adds repository-resolved component CAR startup beside SAR deployment.

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
- [x] Keep provider-owned account Web UI generic: `Login name` and
      `Login name or Email` are provider wording; `handle` remains a
      Cwitter-side projection.
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

## CW-03: Cwitter Manual Stabilization and Provider Page Reuse

Status: DONE

### Objective

Validate the Cwitter sample through manual browser use while keeping the sample
intentionally small and provider-page based.

### Detailed Tasks

- [x] Remove duplicated Cwitter-owned signup/signin/reset/2FA pages.
- [x] Use provider routes directly for account flows.
- [x] Keep Cwitter focused on timeline, post, mention, and direct-message
      behavior.
- [x] Keep `handle = loginName` as a Cwitter projection, not a provider field.
- [x] Document minimal sample vs advanced sample positioning.
- [x] Add scripts and docs for CAR-mode and SAR-mode manual startup.

### Expected Outcome

- Cwitter demonstrates how little app-specific code is needed when account UI,
  auth/session restoration, message delivery, and runtime plumbing are shared.
- Provider-owned common pages remain reusable as-is, with only light descriptor
  customization where needed.

### Guardrails

- No Cwitter-specific account page copy.
- No separate Cwitter profile model.
- No provider-core `handle` or `nickname` field.

---

## PH-16H: Runtime Hardening From Manual Use

Status: DONE

### Objective

Close runtime correctness and operability gaps discovered while exercising
Cwitter through the shared CNCF runtime.

### Detailed Tasks

- [x] Add structured error display based on `Conclusion` detail codes.
- [x] Improve admin/manual pages so raw records are secondary and human summary
      is primary.
- [x] Complete baseline admin navigation for entity/data/view/aggregate surfaces.
- [x] Add production admin authorization with privilege ceiling and role policy.
- [x] Add working-set policy support with CML/config/code resolution and
      explicit working-set/store search behavior.
- [x] Add async working-set startup fallback to direct store search while
      initialization is in progress.
- [x] Add debug trace-job metadata and job-specific calltree retention policy.
- [x] Add shared Web theme support and provider common page light customization.
- [x] Add locale/time-zone aware display formatting through runtime/session
      context.
- [x] Enforce runtime lifecycle audit defaults for `createdAt`, `updatedAt`,
      `createdBy`, and `updatedBy`.

### Expected Outcome

- Cwitter-driven flows expose production-relevant runtime behavior without
  requiring app-specific quick hacks.
- Debug, admin, error, working-set, and Web composition surfaces are reusable
  CNCF capabilities.

### Guardrails

- Keep app business logic free of provider SDKs and runtime debug plumbing.
- Do not make production admin reachable by role alone; privilege remains the
  final ceiling.
- Keep CallTree/debug-job behavior opt-in or policy-based.

---

## PK-16: Repository-Resolved Component CAR Startup

Status: DONE

### Objective

Allow a single application component CAR to run as a deemed subsystem while
resolving standard provider component CARs from repository search sources, and
keep SAR deployment as the deployment-level override surface.

### Detailed Tasks

- [x] Package component-local `web/*` resources into CAR.
- [x] Package component-local `assembly-descriptor.*` defaults into CAR.
- [x] Resolve application component CARs by `name + version` from the component
      repository.
- [x] Resolve provider component CAR dependencies from the standard repository
      or local `repository.d`.
- [x] Merge component CAR assembly defaults with SAR descriptors and SAR
      assembly overrides by field.
- [x] Keep provider component CARs separate; do not invent a composite CAR
      artifact format.
- [x] Document Cwitter CAR-mode and SAR-mode startup.

### Expected Outcome

- `cncf --textus.component=cwitter server` can run a repository-deployed
  application component CAR as a deemed subsystem.
- `cncf --textus.subsystem=cwitter server` can run the SAR and override only
  deployment-specific assembly fields.
- Local development can use `repository.d` staging until the standard component
  repository is populated.

### Guardrails

- CAR remains a single component archive.
- SAR remains the subsystem/deployment packaging unit.
- Provider component CARs are resolved, not embedded.

---

## Deferred / Out-of-Scope Notes

- External identity federation.
- SSO.
- Mandatory/global MFA policy.
- Real email/SMS message-delivery providers.
- Multiple-provider precedence beyond the first provider baseline.
- Separate profile/domain expansion for `Cwitter`.

---

## Closure Verification

Status: DONE

Closed on Apr. 26, 2026 after:

- [x] CNCF `sbt --batch Test/compile`
- [x] CNCF focused executable specs:
      `GenericSubsystemDescriptorSpec`,
      `CncfRuntimeConfigFileSpec`,
      `ComponentRepositoryCarSpec`, and
      `WebDescriptorSpec`
- [x] Cwitter `sbt --batch "component/cozyBuildCAR" "subsystem/cozyBuildSAR"`
- [x] Cwitter smoke server startup on port `19533`
- [x] Cwitter smoke Web entrypoints:
      `/web/cwitter`,
      `/web/textus-user-account/signup?returnTo=/web/cwitter`,
      `/web/textus-user-account/signin?returnTo=/web/cwitter`, and
      `/web/textus-user-account/password-reset?returnTo=/web/cwitter`

Note: local HTTP bind/connect checks required non-sandbox execution.
