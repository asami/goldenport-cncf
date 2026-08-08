# Phase 62 - Web Session CSRF Unification

status=planned
planned_at=2026-07-26
depends_on=[Phase 61](phase-61.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 62 Checklist](phase-62-checklist.md)

## Purpose

Generalize CNCF's existing Static Form CSRF protection into one Web-session
ingress contract for `/form-api`, Web-facing REST, and browser JavaScript.

Phase 62 preserves the CSRF requirement. It closes the gap where application
JavaScript can call an unsafe CNCF endpoint without a standard way to obtain
and attach the required token.

## Dependency

Phase 62 begins after Phase 61 closes.

The relevant foundations are the Static Form/Web contracts, the existing
stateless `WebCsrf` implementation, Operation authorization, and the planned
API exposure distinction under strategy item 9.22.

## Selected Direction

- CSRF policy follows the effective ingress authentication profile, not the
  `/form-api` or `/rest` path name.
- Unsafe requests authenticated by a CNCF Web session require CSRF.
- `/form-api` and Web-facing REST share one issuing, projection, extraction,
  verification, failure, and diagnostics mechanism.
- Browser JavaScript uses a CNCF-owned helper or the equivalent explicit
  canonical token header.
- External REST using an explicitly admitted non-cookie identity does not
  require CSRF and remains governed by external API authentication, scope,
  replay, quota, and gateway policy.
- CSRF verification never replaces authentication, authorization, validation,
  idempotency, CORS, CSP, or XSS defenses.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| CS-01 | Inventory and contract freeze | Current Form, Form API, REST, Web session, token, and JavaScript behavior plus failing-first acceptance identities are fixed. | planned |
| CS-02 | Ingress security profile | Web-session, external-API, and internal-service credential selection is deterministic and cannot silently choose a weaker CSRF policy. | planned |
| CS-03 | Common CSRF mechanism | One CNCF Web-session guard owns token issue, projection, extraction, method policy, verification, failures, and diagnostics. | planned |
| CS-04 | Form API adoption | `/form-api` validation and execution use the common guard while preserving normal HTML form submission. | planned |
| CS-05 | Web REST adoption | Unsafe session-authenticated REST requests use the common guard; explicit external REST remains separately governed. | planned |
| CS-06 | JavaScript contract | CNCF provides a safe standard fetch/token path and unsafe JavaScript calls without a token fail before dispatch. | planned |
| CS-07 | Component and security acceptance | ArtScene and representative Form/REST paths pass real HTTP, authorization, audit, and non-leakage acceptance. | planned |
| CS-08 | Verification and contract promotion | Full validation passes and verified parameter/behavior contracts are promoted from notes to design/specification. | planned |

## Acceptance

- `/form-api` and Web-facing REST use one CSRF implementation.
- Safe methods do not require a token and unsafe Web-session methods do.
- HTML form-field and JavaScript-header transports follow one verified token
  contract.
- JavaScript has a CNCF-owned supported way to attach the token.
- Missing or invalid tokens fail with structured `403` responses before
  operation execution.
- External REST exemption requires an explicit admitted non-cookie ingress
  profile.
- Token values never appear in logs, CallTree, metrics, audit payloads, URLs,
  or error text.
- ArtScene or another representative component proves the browser path through
  the real CNCF HTTP boundary.
- Final accepted header, field, cookie, method, profile, failure, and
  projection behavior is recorded under `docs/spec` and `docs/design`.

## Non-Goals

- Removing or weakening current CSRF enforcement.
- Treating CORS or `SameSite` as a complete CSRF replacement.
- Implementing a full public API gateway, OAuth server, or developer portal.
- Making external service APIs use browser session cookies.
- Letting application JavaScript generate or verify CNCF tokens.
- Solving XSS, CSP, authorization, idempotency, or rate limiting through CSRF.

## Planning References

- [Web Session CSRF Boundary](../journal/2026/07/2026-07-26-web-session-csrf-boundary.md)
- [Implementation Proposal](../notes/web-session-csrf-unification-implementation.md)
- [Static Web Application Specification](../spec/static-web-application.md)
- [Web Layer Design](../design/web-layer.md)
- [Web Form API Schema](../design/web-form-api-schema.md)
- [Phase 62 Checklist](phase-62-checklist.md)
