# Web Session CSRF Unification Implementation Proposal

status = proposed, non-normative
date = 2026-07-26
target_phase = 62

## Goal

Apply one CNCF-owned CSRF mechanism to every unsafe request authenticated by a
Web session, including `/form-api` and Web-facing REST, and make CSRF token
attachment mandatory for browser JavaScript.

This proposal extends the existing stateless CNCF Web CSRF implementation. It
does not weaken the existing `/form-api` verification.

## Security Classification

CSRF policy is selected by the ingress authentication profile rather than by
the route name.

| Ingress profile | Typical authentication | CSRF |
| --- | --- | --- |
| Static Form / Form API | Web session cookie | required for unsafe methods |
| Web REST | Web session cookie | required for unsafe methods |
| External REST | Bearer, OAuth client, service account, or mTLS | not applicable |
| Internal service | explicit workload identity | not applicable unless a Web session is admitted |

The first implementation should expose explicit profile metadata such as
`web-session`, `external-api`, and `internal-service`. Automatic fallback from
failed or ambiguous external authentication to a Web-session profile is not
admitted.

## Common Server Mechanism

Introduce or extract one common Web-session CSRF service around the current
`WebCsrf` facilities.

Responsibilities:

- issue one runtime-scoped stateless token bound to the authenticated Web
  session;
- project the token only to pages that need unsafe Web requests;
- read a token from the canonical request header or admitted form field;
- verify cookie, session, and submitted token using one implementation;
- classify safe and unsafe HTTP methods centrally;
- return a structured `403` failure without dispatching the Operation;
- emit bounded diagnostics without recording the token; and
- remain valid across CNCF nodes that share the configured runtime secret.

Initial method policy:

- safe: `GET`, `HEAD`, `OPTIONS`;
- unsafe: `POST`, `PUT`, `PATCH`, `DELETE`;
- unknown extension methods: unsafe unless explicitly admitted.

Initial transport policy:

- canonical JavaScript header: `X-CSRF-Token`;
- admitted HTML/form field: `csrf`;
- cookie: the existing CNCF CSRF cookie;
- if both header and field are present, conflicting values fail
  deterministically.

The exact names remain proposal-level until executable implementation fixes
them.

## JavaScript Contract

CNCF should provide a standard Web helper instead of requiring each component
to parse cookies or construct security headers.

Conceptual API (exact names remain provisional):

```javascript
await TextusWeb.form.definition(selector);
await TextusWeb.form.validate(selector, values);
await TextusWeb.operation.execute(selector, values, { signal });
```

The facade may share a low-level `TextusWeb.fetch` implementation, but its
public methods preserve the architecture boundary: Form API supplies dynamic
Web input definition/validation and REST v1 executes queries and commands.

For an unsafe same-origin request the helper:

- obtains the token from a framework-owned page projection;
- adds the canonical CSRF header;
- preserves same-origin credentials;
- refuses to send when the required token is unavailable;
- does not expose the token in logs, diagnostics, URLs, or exception text; and
- leaves ordinary response/error handling to the caller.

Candidate page projection:

```html
<meta name="textus-csrf-token" content="...">
```

The final projection may instead use the existing structured page context if
that is safer and avoids duplication. There must be one canonical source, and
application code must not read the CSRF cookie directly.

Direct browser `fetch` to an unsafe Web-session CNCF endpoint without the
standard helper or equivalent explicit token attachment is unsupported and
must fail.

## `/form-api` Integration

Replace route-local verification with the common Web-session CSRF guard while
preserving the current form field contract.

- Form submissions continue to send `csrf`.
- JavaScript `/form-api` calls use `X-CSRF-Token`.
- Validation and retained compatibility execution POST routes are both
  protected.
- New browser code does not use direct Form API POST as its canonical Operation
  execution route.
- Authorization runs as part of the normal operation ingress and is not
  replaced by CSRF verification.
- CSRF rejection occurs before operation execution and side effects.

## Web REST Integration

Web-facing REST endpoints using the Web session apply the same guard.

- A browser session cookie makes unsafe REST requests CSRF-protected.
- A route is not exempt because it is called JSON or REST.
- CORS does not replace CSRF protection.
- The server must not infer external-API status solely from an
  `Authorization` header when session credentials are also present.
- The selected ingress profile, authenticated subject, and credential source
  must be deterministic and observable.
- REST v1 is the canonical JSON Operation execution surface for new browser
  query and command flows.

External REST keeps its separate authentication, scope, replay, quota, and
gateway policies. Phase 62 does not implement a complete external API gateway.

## Diagnostics

Use normal `Consequence` / `Conclusion` structured failures. Diagnostics
should distinguish:

- missing token;
- malformed token;
- cookie missing;
- session mismatch;
- token mismatch;
- expired or invalid runtime signature;
- conflicting header and form tokens; and
- ambiguous ingress authentication.

Logs, CallTree, metrics, and audit may record route family, method, ingress
profile, and coarse outcome. They must not record token values or session
secrets.

## Implementation Slices

1. Inventory current CSRF issue, projection, extraction, verification, and
   route-specific code.
2. Fix ingress authentication/profile classification.
3. Extract the common Web-session CSRF guard and token transport parser.
4. Migrate `/form-api` validation and retained compatibility execution routes.
5. Apply the guard to Web-facing REST unsafe methods.
6. Add the CNCF JavaScript helper and page token projection.
7. Provide a CNCF-owned representative component JavaScript flow that does not
   depend on a downstream checkout or application phase.
8. Add diagnostics, audit, and security regression evidence.
9. Promote verified parameter and behavior contracts to `docs/spec` and
   `docs/design`.

## Executable Specification Direction

- Safe Web-session requests do not require CSRF.
- Every unsafe `/form-api` method requires a valid token.
- Every unsafe Web REST request authenticated by session cookie requires the
  same token.
- Valid form-field and valid JavaScript-header tokens are accepted.
- Missing, conflicting, malformed, and mismatched tokens fail with `403`
  before operation dispatch.
- External REST with explicit admitted non-cookie authentication does not
  require CSRF.
- Ambiguous cookie plus external credentials fails according to the selected
  deterministic policy.
- The standard JavaScript helper attaches the token and never leaks it.
- The standard facade keeps Form API definition/validation distinct from REST
  Operation execution.
- A CNCF-owned representative filter/update flow succeeds through the real HTTP
  boundary. ArtScene becomes the first full application driver after Phase 62
  closes and is tracked by Phase 62.1.

## Deferred Scope

- Complete OAuth/OIDC or mTLS implementation.
- General API gateway and developer portal.
- Broad cross-origin public API policy.
- Token storage for third-party SPA runtimes.
- Replacing XSS/CSP, authorization, idempotency, or rate-limit controls with
  CSRF.
- ArtScene-specific page lifecycle and post-baseline reusable client
  extensions, which belong to ArtScene Phase 13 and CNCF Phase 62.1.

## Related Documents

- `docs/journal/2026/07/2026-07-26-web-session-csrf-boundary.md`
- `docs/spec/static-web-application.md`
- `docs/design/web-layer.md`
- `docs/design/web-form-api-schema.md`
- `docs/notes/cncf-hosted-spa-boundary-note.md`
- `docs/phase/phase-62.md`
- `docs/phase/phase-62.1.md`
- `docs/notes/artscene-driven-progressive-static-web-client-integration-provisional-specification.md`
- `docs/journal/2026/08/2026-08-12-form-api-rest-web-boundary.md`
