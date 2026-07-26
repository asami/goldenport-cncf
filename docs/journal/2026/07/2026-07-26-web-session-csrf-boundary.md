# Web Session CSRF Boundary

date = 2026-07-26
status = decision record
target_phase = 54

## Context

An ArtScene progressive-enhancement review found that browser JavaScript can
call CNCF `/form-api` operations directly with `fetch`, while the current
server requires a matching CSRF cookie and form value for operation
submission. Sending the session cookie with `credentials: "same-origin"` is
not sufficient: the request must also carry the framework-issued CSRF token.

The CSRF requirement is a security improvement and must not be removed to make
application JavaScript work. The missing piece is a common CNCF Web contract
for JavaScript and for browser-facing REST ingress.

## Two REST Audiences

The discussion distinguished two REST uses.

### Web-facing REST

Web-facing REST is called by JavaScript running in a browser and normally uses
the CNCF Web session cookie. Because the browser attaches the cookie
automatically, unsafe methods remain vulnerable to cross-site request forgery.

This ingress requires:

- session authentication;
- CSRF verification for unsafe methods;
- same-origin operation by default;
- explicit, narrow CORS policy where cross-origin access is admitted;
- ordinary CNCF subject/capability authorization;
- XSS and Content Security Policy defenses; and
- user/session-attributable audit and diagnostics.

### External REST

External REST is called by another service, CLI, or batch client. It normally
uses explicit Bearer, OAuth, service-account, or mTLS authentication rather
than an automatically attached browser session cookie.

This ingress does not normally require CSRF verification. It instead requires:

- explicit client and, where applicable, delegated-user identity;
- token issuer, audience, expiration, scope, and rotation validation;
- scope-to-capability normalization and ordinary Operation authorization;
- replay and idempotency controls where required;
- client/tenant rate limits and request-size limits; and
- client-attributable audit and diagnostics.

REST syntax does not determine the security profile. Authentication and
ingress classification do.

## Decision

CNCF will generalize the existing Static Form CSRF mechanism into one Web
session CSRF mechanism.

- `/form-api` unsafe requests use the common mechanism.
- Web-facing REST unsafe requests use the same mechanism.
- Application JavaScript must attach the framework-issued token.
- `GET`, `HEAD`, and other admitted safe requests do not require a token.
- External REST authenticated through an explicitly selected non-cookie API
  profile is outside the CSRF requirement.
- Cookie-authenticated requests are not exempt merely because their path is
  under `/rest`.
- Mixed or ambiguous authentication must not silently select the weaker
  profile.

The canonical implementation should provide a CNCF-owned JavaScript helper or
fetch wrapper. Applications must not invent token generation, cookie parsing,
or verification rules.

## Documentation Lifecycle

The implementation proposal is:

- `docs/notes/web-session-csrf-unification-implementation.md`

The development ledger is:

- `docs/phase/phase-54.md`
- `docs/phase/phase-54-checklist.md`

The note and this journal entry are not the final runtime contract. After
implementation and executable verification, the accepted parameter, header,
ingress, failure, and projection behavior must be promoted to the relevant
documents under `docs/spec` and `docs/design`.
