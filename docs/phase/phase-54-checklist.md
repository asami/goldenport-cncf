# Phase 54 Checklist - Web Session CSRF Unification

status=planned
phase=[Phase 54 - Web Session CSRF Unification](phase-54.md)

This checklist is the authoritative Phase 54 state ledger after Phase 54
starts. Only one stage may be `IN_PROGRESS` at a time. No stage starts before
Phase 53 closes.

## CS-01: Inventory and Contract Freeze

Stage Status:
- Current status: PLANNED
- Owner: CNCF HTTP, Web, Form, REST, security, and component maintainers
- Entry rule: Phase 53 is closed.
- Completion rule: Current security behavior and exact failing-first
  acceptance identities are recorded before implementation.

- [ ] Inventory CSRF token issue, signing, cookie, projection, extraction,
  verification, expiry, and failure behavior.
- [ ] Inventory `/form`, `/form-api`, REST, Static Web, SPA, admin, and custom
  component JavaScript ingress paths.
- [ ] Inventory cookie-session, Bearer, service-account, mTLS, and mixed
  credential selection behavior.
- [ ] Classify HTTP methods as safe or unsafe.
- [ ] Fix Web-session, external-API, and internal-service ingress profiles.
- [ ] Fix the canonical JavaScript token transport candidate.
- [ ] Fix the admitted HTML form-field compatibility contract.
- [ ] Inventory token leakage risks in logs, errors, CallTree, metrics, audit,
  URLs, and page manifests.
- [ ] Register failing-first Executable Specifications for every Phase 54
  acceptance group.

Evidence:
- Pending.

## CS-02: Ingress Security Profile

Stage Status:
- Current status: PLANNED
- Owner: CNCF HTTP and security maintainers
- Entry rule: CS-01 is DONE.
- Completion rule: Credential and profile selection deterministically chooses
  the correct CSRF policy without downgrade.

- [ ] Define explicit Web-session, external-API, and internal-service profile
  metadata.
- [ ] Bind cookie/session authentication to the Web-session CSRF requirement.
- [ ] Bind admitted non-cookie authentication to external/internal policy.
- [ ] Define deterministic mixed-credential behavior.
- [ ] Reject ambiguous or downgrade-prone authentication.
- [ ] Preserve CNCF subject/capability and Operation authorization after
  ingress authentication.
- [ ] Project the selected profile into bounded diagnostics and audit.

Evidence:
- Pending.

## CS-03: Common CSRF Mechanism

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web security maintainers
- Entry rule: CS-02 is DONE.
- Completion rule: One implementation owns CSRF behavior for all Web-session
  ingress.

- [ ] Extract or extend one framework-owned Web-session CSRF guard.
- [ ] Preserve stateless token verification and multi-node compatibility.
- [ ] Centralize safe/unsafe HTTP method classification.
- [ ] Accept the canonical JavaScript header.
- [ ] Preserve the admitted HTML `csrf` field.
- [ ] Reject conflicting header and form values.
- [ ] Return structured `403` failures before Operation dispatch.
- [ ] Add bounded diagnostics without token or session-secret values.
- [ ] Add property-based malformed, mismatch, and non-leakage specifications.

Evidence:
- Pending.

## CS-04: Form API Adoption

Stage Status:
- Current status: PLANNED
- Owner: CNCF Form and Form API maintainers
- Entry rule: CS-03 is DONE.
- Completion rule: Every unsafe Form API path uses the common guard without
  regressing server-rendered forms.

- [ ] Migrate `/form-api` validation POST.
- [ ] Migrate `/form-api` execution POST.
- [ ] Preserve ordinary `/form` hidden-field submission and PRG behavior.
- [ ] Verify authorization and validation still run in the correct order.
- [ ] Verify rejected requests produce no Operation side effects.
- [ ] Add real HTTP tests for valid, missing, malformed, mismatched, and
  conflicting token inputs.

Evidence:
- Pending.

## CS-05: Web REST Adoption

Stage Status:
- Current status: PLANNED
- Owner: CNCF REST, HTTP, and security maintainers
- Entry rule: CS-04 is DONE.
- Completion rule: Unsafe session-authenticated REST uses the common guard
  while explicit external REST remains separately secured.

- [ ] Apply common CSRF verification to unsafe Web-session REST.
- [ ] Verify safe Web-session REST remains token-free.
- [ ] Verify an explicitly admitted external API identity does not require
  CSRF.
- [ ] Verify route naming alone cannot create a CSRF exemption.
- [ ] Verify CORS and `SameSite` do not bypass the guard.
- [ ] Verify mixed credentials follow the deterministic CS-02 policy.
- [ ] Add REST/OpenAPI security metadata needed to distinguish audiences.

Evidence:
- Pending.

## CS-06: JavaScript Contract

Stage Status:
- Current status: PLANNED
- Owner: CNCF Web asset and Static Web maintainers
- Entry rule: CS-05 is DONE.
- Completion rule: Browser JavaScript has one supported non-leaking way to
  perform unsafe Web-session requests.

- [ ] Select one canonical page token projection.
- [ ] Add a CNCF-owned fetch/token helper.
- [ ] Attach the token automatically for unsafe same-origin requests.
- [ ] Refuse unsafe requests when the required token is unavailable.
- [ ] Do not attach the token to untrusted cross-origin requests.
- [ ] Keep token values out of URL, history, logs, errors, and demo manifests.
- [ ] Add browser-level helper and page projection specifications.
- [ ] Document direct `fetch` requirements for component developers.

Evidence:
- Pending.

## CS-07: Component and Security Acceptance

Stage Status:
- Current status: PLANNED
- Owner: CNCF and representative Component maintainers
- Entry rule: CS-06 is DONE.
- Completion rule: Real component browser flows prove the common mechanism,
  authorization continuity, and non-leakage.

- [ ] Migrate the selected ArtScene or equivalent JavaScript flow.
- [ ] Verify filter/validation and mutation requests through the real HTTP
  server.
- [ ] Verify authenticated subject and capability authorization remain
  authoritative.
- [ ] Verify CSRF failure cannot be hidden as a domain or operation error.
- [ ] Verify audit and diagnostics identify the ingress and outcome without
  token values.
- [ ] Update the CBD Support Security View review criteria for browser CSRF.
- [ ] Record downstream evidence without mixing unrelated dirty work.

Evidence:
- Pending.

## CS-08: Verification and Contract Promotion

Stage Status:
- Current status: PLANNED
- Owner: CNCF documentation, security, and release maintainers
- Entry rule: CS-07 is DONE.
- Completion rule: All acceptance evidence passes and verified behavior is
  normative.

- [ ] Run focused HTTP, Form API, REST, Static Web, and security specifications.
- [ ] Run `Test/compile`.
- [ ] Run the CNCF full test with the repository-standard heap configuration.
- [ ] Validate the representative component browser flow.
- [ ] Run naming, executable-specification, and `git diff --check` gates.
- [ ] Promote verified method, header, field, cookie, profile, projection,
  failure, diagnostics, and audit behavior to `docs/spec`.
- [ ] Promote verified ownership, ingress, and security boundaries to
  `docs/design`.
- [ ] Update component developer guidance for mandatory JavaScript CSRF
  attachment.
- [ ] Update strategy and phase evidence only after all completion criteria
  pass.

Evidence:
- Pending.
