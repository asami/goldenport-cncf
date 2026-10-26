# OAuth connection protocol

Date: 2026-10-21

This contract defines provider-neutral OAuth2 authorization-code and mandatory
PKCE S256 primitives in `org.goldenport.cncf.oauth`. The executable contract is
[OAuthClientSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthClientSpec.scala).
It is one part of the connected PHASE-3 server delivery. This document does not
claim an operational Google account connection or completion of that delivery.

## Responsibility and construction

Trusted runtime -> `OAuthClient` primitives/native HTTP -> future persistent
connection owner -> Google Drive FileStore -> Studio native operations/browser.

Provider, client and request construction, and construction of token/refresh
programs are pure. They acquire no network, filesystem, clock, entropy or browser
process. Callers supply securely generated state/verifier values and explicit
observation times. The runtime remains responsible for native component-scoped
HTTP origin/method admission and response limits. These outbound credentials do
not confer ingress authentication or owning entity/source authorization.

`OAuthProvider`, `OAuthClient`, `OAuthAuthorizationRequest` and `OAuthCredential`
are final classes with private construction, without case-class/Product
semantics. Requests, clients and credentials always have redacted displays.
Data types have instance-independent ownership.

```scala
OAuthProvider.createC(name: String, authorizationEndpoint: String,
  tokenEndpoint: String): Consequence[OAuthProvider]
OAuthProvider.googleDrive: OAuthProvider
OAuthClient.createC(provider: OAuthProvider, clientId: String,
  clientSecret: Option[String], redirectUri: String,
  scopes: Vector[String]): Consequence[OAuthClient]
```

Provider names match `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`. Endpoints are nonnull
ASCII strings of at most 2048 characters, absolute hierarchical HTTPS URIs with
an actual host and absent port or port 443. User information, query, fragment,
backslash, whitespace and controls are prohibited. No discovery, endpoint fetch,
arbitrary request-time provider override or caller-supplied extra parameters
exist. Safe provider properties expose the name and the two configured endpoints.

`googleDrive` fixes name `google-drive`, authorization endpoint
`https://accounts.google.com/o/oauth2/v2/auth`, and token endpoint
`https://oauth2.googleapis.com/token`. Its authorization-only extras are exactly
`access_type=offline` and `prompt=consent`. Generic providers have no extras.

Client IDs are nonempty printable ASCII without whitespace, at most 1024
characters. An optional secret is nonempty printable ASCII without whitespace,
at most 16384 characters. A null Option or `Some(null)` is invalid.

Redirects are exact configured ASCII absolute hierarchical URIs of at most
2048 characters, without user information, query, fragment, backslash, whitespace
or controls. HTTPS requires an actual host and absent port or port 443. HTTP is
permitted only for numeric `127.0.0.1` with explicit port 1 through 65535.
`localhost`, wildcards, other loopback spellings and arbitrary HTTP hosts are
not admitted. The configured spelling is preserved, without normalization or a
request-time redirect override.

Scopes are a nonnull, nonempty Vector of at most 16 unique nonempty tokens, each
at most 1024 characters. Each token uses the RFC6749 scope-token alphabet:
`%x21 / %x23-5B / %x5D-7E`. Space, control, quote, backslash and non-ASCII are
invalid. Entered order is preserved; no scope is implicitly added.

## Browser authorization

```scala
client.beginC(state: String,
  codeVerifier: String): Consequence[OAuthAuthorizationRequest]
request.authorizationUri: String
```

State matches `[A-Za-z0-9_-]{43,128}`. Verifiers match
`[A-Za-z0-9._~-]{43,128}`. These shape checks do not create or establish secure
entropy. PKCE uses SHA256 over the ASCII verifier followed by unpadded base64url,
as in RFC7636 S256. This is protocol cryptography.

The browser handoff contains exactly `response_type=code`, `client_id`, exact
`redirect_uri`, space-joined `scope`, `state`, `code_challenge`,
`code_challenge_method=S256`, and the provider's fixed authorization extras.
Each key/value is encoded once using standard UTF8 form encoding. The secret
and verifier never appear in the URI. Only `authorizationUri` is a public browser
handoff; request state, verifier and client binding are package-private.

## Token exchange and refresh

```scala
client.complete(request: OAuthAuthorizationRequest, returnedState: String,
  code: String, observedAt: java.time.Instant): ExecUowM[OAuthCredential]
client.refresh(credential: OAuthCredential,
  observedAt: java.time.Instant): ExecUowM[OAuthCredential]
```

Completion rejects null or foreign-client requests, malformed/mismatched state,
null observation time, and empty, nonprintable, whitespace/control/non-ASCII or
over-8192-character codes before HTTP admission/acquisition. State is compared
with the JDK constant-time byte comparison. Request ownership is bound to the
exact client instance.

An admitted completion performs exactly one native `UnitOfWorkOp.HttpPost`
inside `ExecUowM` to the fixed token endpoint. The UTF8 form body contains exactly
`grant_type=authorization_code`, `code`, exact `redirect_uri`, `client_id`,
`code_verifier` and optional `client_secret`. Headers are
`Content-Type: application/x-www-form-urlencoded` and `Accept: application/json`.
The normal `http.max-response-bytes` Property requests a 16384-byte bound; native
runtime policy may tighten it. Token fields never enter the URL. There is no
retry, `response_type`, or authorization-only extra in this request.

Refresh rejects null/foreign-client credentials or a null observation time before
HTTP. A grant without refresh material requires authorization without making a
request. An admitted refresh performs one native form POST with exactly
`grant_type=refresh_token`, `refresh_token`, `client_id` and optional
`client_secret`, with the same headers and response limit. It contains no code,
redirect, PKCE, browser or business-operation request and has no retry.

## Response and credential contract

Only HTTP 200 with `application/json` and a JSON object can succeed:

- `access_token` is a nonempty RFC6750 Bearer token of at most 16384 characters,
  using `[A-Za-z0-9._~+/-]+` followed by optional terminal `=` padding.
- `token_type` is a string equal to Bearer, case-insensitively.
- `expires_in` is a strictly positive integral JSON number fitting Long seconds.
  Integral numeric representations are accepted. Strings, floats with fractional
  values, null and other types are not coerced. `observedAt.plusSeconds` must not
  overflow and the resulting Instant must be later than the observation time.
- An optional `refresh_token`, when present, is a nonempty printable ASCII string
  without whitespace, at most 16384 characters. Null or malformed values fail.
  On initial exchange, absence honestly produces `canRefresh=false`. On refresh,
  absence retains the previous refresh token; a valid present token replaces it
  for future trusted persistence.
- An optional `scope`, when present, is a strict nonempty space-separated list of
  unique valid scope tokens, a subset of the requested scopes. Initial absence
  retains requested scopes. Refresh absence retains the previous grant; even a
  previously requested scope cannot be reintroduced after the grant was narrowed.
  Present scope order is preserved.

Unrelated provider extension keys are ignored. Raw response JSON is not exposed.
The credential's public safe surface is `grantedScopes: Vector[String]`,
`expiresAt: Instant` and `canRefresh: Boolean`. Access/refresh token access is
restricted to trusted CNCF code, and client ownership to the OAuth package.
No application-facing copy or credential fields exist. The exact display is
`OAuthCredential(<redacted>)`.

## Fixed structured failures and native diagnostics

| Condition | Consequence utility and exact message |
| --- | --- |
| Invalid provider/client configuration | `configurationInvalid("OAuth client configuration is invalid")` |
| Invalid request/program input | `argumentInvalid("OAuth authorization request is invalid")` |
| Missing refresh material | `securityPermissionDenied("OAuth connection requires authorization")` |
| HTTP 400/401/403 | `securityPermissionDenied("OAuth authorization was denied")` |
| HTTP 429 or 500..599 | `serviceUnavailable("OAuth authorization service is unavailable")` |
| Other status or malformed success | `resourceInvalid("OAuth authorization response is invalid")` |

Submitted values, provider bodies, codes, error descriptions and exception
messages are never copied into these failures. Native admission/transport
Conclusions remain intact, including their structured taxonomy and exception;
they are not collapsed into strings. Existing safe disclosure and redacted
`UnitOfWorkOp.HttpPost` display apply. Specifications exercise actual enabled
CallTrees and HTTP standard/full/log Conclusion projections with synthetic
secret markers.

## Delivery boundaries and references

Pending-flow lifetime, caller/owner binding beyond this client, one-time state
consumption, persistent encrypted storage, runtime assembly, callback ingress,
Google Drive consumer wiring and Studio UI remain subsequent work in the same
connected delivery. A reusable authorization request here is a pure protocol
value, not an application callback replay defense. Existing Drive fixed-token
and refresh-token APIs remain unchanged by this slice. Operational prerequisites
are recorded in the [provisioning journal](../journal/2026/10/2026-10-21-oauth-connection-provisioning.md).

Protocol references:

- [RFC7636: Proof Key for Code Exchange](https://www.rfc-editor.org/rfc/rfc7636)
- [RFC9700: Best Current Practice for OAuth 2.0 Security](https://www.rfc-editor.org/rfc/rfc9700)
- [Google OAuth 2.0 for native applications](https://developers.google.com/identity/protocols/oauth2/native-app)
- [Google OAuth 2.0 for web server applications](https://developers.google.com/identity/protocols/oauth2/web-server)
