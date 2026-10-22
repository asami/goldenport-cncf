# OAuth connection HTTP

Date: 2026-10-21

This transport binds the existing [native OAuth intent](oauth-connection-native.md)
to the existing http4s listener. Its executable counterpart is
[OAuthConnectionHttpSpec](../../src/test/scala/org/goldenport/cncf/http/OAuthConnectionHttpSpec.scala).
Configuration, custody and flow semantics remain owned by the
[private runtime](oauth-connection-runtime.md) and
[configuration contract](oauth-connection-configuration.md).

## Listener and exact selection

The listener composes its private OAuth routes before its existing routes,
including the generic HEAD-to-GET conversion. The complete decoded
`/web/<component>/connections` prefix is captured, including unsupported methods
and suffixes. Selection uses the host's existing selected Component reference
and requires its actual `OAuthConnectionRuntime` capability. The raw path must
use the canonical normalized local component segment. Aliases, percent-encoded
substitutions, unavailable components/capabilities, unknown connection IDs and
unknown suffixes return fixed 404 without native execution or business fallback.

The configured connection has these routes:

| Method and suffix | Result |
| --- | --- |
| GET `/connections` | `{"connections":[<safe status>]}` |
| GET `/connections/<id>/status` | Safe status |
| POST `/connections/<id>/authorize` | `{"authorizationUri":"<deliberate handoff>"}` and proof cookie |
| GET `/connections/<id>/callback` | Fixed 303 after configured Host admission |
| POST `/connections/<id>/disconnect` | Safe status after explicit native local forget |

All suffixes above follow `/web/<canonical component>`. A disabled optional
configuration exposes an empty list only to actual provider-authenticated
management ingress. Per-ID paths are unavailable. Configured discovery executes
native status and therefore also requires the exact configured owner. Discovery
never begins authorization.

Known routes reject unsupported methods with fixed 405. HEAD responses are
empty and cannot invoke callback completion through the generic GET conversion.
Unknown suffixes retain 404. GET cannot authorize or disconnect. Responses grant
no CORS authority or credential permission.

## Safe wire vocabulary

Status contains exactly `connectionId`, `provider` (`google-drive` or `generic`),
`state` (`connected`, `authorization-required`, `expired`, `uncertain`),
`canRefresh`, `authorizationPending`, `updating`, and optional `expiresAt` (ISO
instant) and `lastOutcome` (`authorized`, `denied`, `expired`, `failed`,
`disconnected`). Absent optionals are omitted. No owner, account, client,
credential path, scope configuration, token, refresh material or browser proof
is projected. A generic provider with Google endpoints remains generic.

The authorization URI deliberately includes the pending state for browser
handoff. Its proof appears only in the narrow HttpOnly cookie. The callback's
only Location is the configured return path plus `#oauth-returned`; it has no
query, result, provider error, code, state or proof. That marker is a return
notification, not an authentication or success grant. Later authenticated
status is authoritative. This unit provides no completion page or Studio UI.

Every adapter response carries `Cache-Control: no-store`, `Pragma: no-cache`,
`Referrer-Policy: no-referrer` and `X-Content-Type-Options: nosniff`.
Noncallback outcomes use JSON with UTF8; HEAD bodies remain empty. No error
renders raw HTML, loads an external resource or uses a redirect-with-query
helper.

## Host, Origin, query and body admission

Only the selected private configuration supplies the expected origin. Host is
singular, nonempty, bounded, printable ASCII and a plain parsed authority.
Decorated, comma-separated, whitespace-bearing, malformed and duplicate values
are refused. All configured requests, including list/status/callback, must have
that Host. An absent-configuration list still requires a singular well-formed
Host. An absolute request target's scheme and authority, when present, must
agree too. Forwarded headers never repair or establish authority.

Management POST additionally requires one Origin containing only
`scheme://authority`: no userinfo, path (including a trailing slash), query,
fragment, decoration or whitespace. Scheme and host compare without case;
effective port compares numerically, with implicit and explicit HTTPS 443
equivalent. HTTP permits only configured numeric `127.0.0.1` with an explicit
port. Hostnames, numeric aliases and changed scheme, host or port do not grant
authority. Google's redirect form retains the exact configured raw URI text.

Authorize and disconnect read at most one actual body byte and require an
empty stream, independently of Content-Length and Content-Type. There are no
form, scope, owner, return, redirect or configuration overrides.

Management query accepts only `textus.output.conclusion` and
`cncf.output.conclusion`. Both management and callback raw queries are limited
to 32768 ASCII URI characters and 32 fields. Strict percent and UTF8 decoding
reports malformed input; field names are nonempty and at most 256 decoded
characters, values at most 8192. Management selectors retain duplicate fields
and use existing ConclusionDisclosure selection: only one `full` selects full;
duplicate/conflicting/invalid selections remain standard. Selectors do not
become authentication or business properties. Unknown management fields return
fixed 400 before execution.

Callback requires exactly one bounded base64url state (43..128 characters) and
exactly one printable code (1..8192) or provider error (1..128). Duplicate
critical keys, including identical or percent-encoded duplicates, are invalid.
Code plus error, missing result, representable malformed encoding, invalid markers or any
query bound violation is a confidential fixed redirect without native execution
or pending consumption. Other bounded provider fields are ignored. In
particular, `error_uri` is never followed. Raw callback URI, query and values
never enter Protocol, ActionRequest, ingress authentication or diagnostics.

RFC-invalid request targets, including bare `%` and `%0G` percent syntax, are
refused by normal URI admission before callback routing. Representable raw
callback queries, including malformed UTF8 escapes, reach the strict parser
and receive the fixed 303 on rejection. Callback fixtures use the public,
raw-preserving `Uri.requestTarget` parser for the complete physical target,
without decoding and rerendering the query through a convenience constructor.
H5 separately covers both URI-admission refusals with an unchanged pending
owner flow and a subsequent valid completion, and a legitimate literal percent
in a code whose token form retains `code=synthetic%25code`. URI-admission coverage
does not claim a raw-socket HTTP 400 or wire acceptance; connected wire testing
remains a later gate.

The host-only cookie is named `cncf-oauth-proof`, has exact callback Path,
HttpOnly, SameSite Lax and Max-Age 300; Secure is set for HTTPS. There is no
Domain or ingress/session identity. Callback Cookie headers have a combined
16384-character and 64-pair bound. Exactly one matching valid marker is
required; missing, duplicate or malformed proof is refused without runtime
mutation. Only native Success (authorization or matched denial) clears this
cookie. Foreign/stale flows and failures never clear the latest legitimate
proof. Matched runtime failures consume their flow and cannot replay, even
when the browser's bounded cookie remains.

## Context, native authority and lifecycle

Management retains the host's original profile-aware request-context resolver
for the actual selected Component. Public attributes and trusted-local or
anonymous identities cannot replace provider authentication. Configured native
status/begin/forget still require the exact configured owner.

The private host callback-context supplier first validates the runtime Web
resolution and subsystem execution profile. It obtains that exact Component's
normal `logic.executionContext()` and replaces only security with an explicit
anonymous principal, empty capabilities, anonymous level/kind and no session,
using existing security rebinding. It never invokes an authentication provider,
inherits incoming authentication, sets fake provenance, installs the owner or
creates a fallback ExecutionContext. Normal scope, actual driver/admission,
bound clock, runtime mode and UoW rebinding remain intact.

Every accepted intent is lifted to the canonical native operation and run by
`new UnitOfWorkInterpreter(context.runtime.unitOfWork).run(...)`. The same
resolved context supplies the actual UoW and its final commit/abort. Completion
and nested token exchange share that UoW; there is no inner lifecycle or new
listener. Private runtime/native IO runs through IO.blocking, with no host
monitor held over HTTP. An already executed token request or private storage
effect is not promised to roll back if a final enclosing commit fails.

An explicit disconnect forgets only selected local custody and pending flow.
It does not revoke Google credentials or mutate domain data. A Drive request
which already acquired authorization may still complete remotely; disconnect
does not claim remote cancellation.

## Original Conclusions and safe detailed logs

Management failures retain their original Conclusion and `status.webCode`
through StructuredHttpError and its existing envelope vocabulary. Disclosure
receives a safe physical request containing only admitted canonical path,
selector fields and physical credential headers, never a raw callback URL.
ConclusionDisclosure owns standard/full selection and credential discovery for
HTTP and detailed log projections. No ad hoc exception string or redactor is
used. Invalid arguments and origin refusals have fixed messages without
submitted values; unavailable routes/methods have fixed 404/405 messages.

Once a bound context exists, failures are observed by that context's own
`cncfCore.scope.observe_errorC` with fixed message `OAuth connection request
failed` and a detailed ConclusionDisclosure.log record. Before-context failures
have no ambient logger fallback. Callback failures retain confidential fixed
redirects while the actual safe log preserves allowed diagnostic detail.
Failure and uncertain state never trigger an automatic exchange retry or a
false connected result.

## Executable scenario map

Every scenario has adjacent Given setup, When request interpretation and Then
observable expectations. The spec uses normal Factory creation and stable key
declaration, actual installed Subsystem, real private POSIX files, a MultiUser /
Authenticated profile, a real AuthenticationProvider and controlled native
HttpDriver with actual HTTP admission and a bound runtime clock.

| Group | Given | When | Then |
| --- | --- | --- | --- |
| H1 discovery | Configured, absent, generic and fixed-local wiring; owner/foreign/forged identities | Normal discovery, status and invalid canonical/query paths | Safe facts, exact provider owner, empty-list authentication, fixed refusal before effects, no auto begin |
| H2 completion | Authenticated owner, actual bound runtime and browser handoff | No-Bearer callback, ignored callback credentials, same-owner managed Drive read | Cookie/URI, one native token POST and private save, fixed 303, connected status, saved-grant use, no callback provider call or authority upgrade |
| H3 origin/body | Configured HTTP or HTTPS origin | Equivalent 443/case, hostile Host/Origin/absolute targets/forwards, nonempty actual body | Only configured URI origins admitted, exact redirect retained, refusal before mutation, no CORS authority |
| H4 methods | Pending actual flow and listener's existing generic routes | HEAD callback, unsupported methods and GET mutators | Empty HEAD 405 retains flow, later GET completes once, no fallback or CORS grant |
| H5 callback | Latest/old flows, prior grant and bounded browser proof | Stale/wrong/duplicate/malformed/oversized input, replay, expiry, denial and bounded extras | Flow/cookie preservation on refusal, matched one-shot semantics, no URI following or raw-query ingress, old grant retained on denial |
| H6 failures | Original structured provider Conclusion, actual log sink, native transport/permission/storage failures | Normal route failure and replay with disclosure selectors | Original status/history semantics, credential-safe HTTP/logs, fixed callback redirects, uncertainty, no retry |
| H7 disconnect | Connected owner, pending flow, domain sentinel, acquired Drive GET | Wrong owner/Origin then explicit disconnect, late callback or in-flight completion | Only selected custody/pending removed, sentinel retained, no revoke/replay or remote cancellation claim |
| H8 generated | Actual normal route and generated origin/critical-query variants | At least 100 successful ScalaCheck cases with zero discards | Native effect count, pending preservation and response/cookie outcomes agree |

The existing private-runtime suites retain their exhaustive matrices. This
controlled in-process HTTP specification does not claim real Google consent,
Studio/browser integration, actual JVM restart, validation success, review
acceptance or Phase closure.
