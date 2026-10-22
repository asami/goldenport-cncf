# Private OAuth connection runtime

Date: 2026-10-21

This contract composes [trusted configuration](oauth-connection-configuration.md),
[OAuth protocol](oauth-connection-protocol.md), and
[private credential custody](oauth-credential-custody.md). Its executable
counterpart is [OAuthConnectionSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthConnectionSpec.scala).
It specifies framework-private synchronous runtime effects. Production still
requires the following typed native operation/interpreter, bootstrap, managed
Drive strategy, HTTP callback adapter and Studio integration.

## R1: Ownership, construction and safe projections

`OAuthConnection` is final, non-Product, `private[cncf]`, with
`private[oauth]` construction and a redacted display. Its configuration metadata
is available only inside CNCF; client and custody references remain OAuth-private.
The trusted `createC(configuration)` factory loads the selected store exactly
once, validates the restored grant and exact client ownership, and accepts genuine
absence or a valid expired grant. A malformed or mismatched record fails closed.
Construction does not exchange, refresh, save, forget, create directories or draw
entropy. A package-private composition overload receives the same configuration,
store and `OAuthEntropy`; it performs the same admission and loading. None of
these capabilities is an application test API.

`OAuthEntropy.tokenBytes()` is a private host capability. The standard factory
selects a runtime implementation returning a fresh 32-byte SecureRandom array
on every call. No clock, secret discovery or workflow registry is implicit.
Null configuration, custody or entropy is refused.

The only status fields are `state`, `canRefresh`, `expiresAt`,
`authorizationPending`, `updating` and `lastOutcome`. States are `Connected`,
`AuthorizationRequired`, `Expired`, `Uncertain`; outcomes are `Authorized`,
`Denied`, `Expired`, `Failed`, `Disconnected`. Refreshability and expiry describe
the actual cached grant even while uncertain. Status contains no account,
owner, email, token, proof, code, state marker, path or client secret, and makes
no Google-account or offline-permission claim.

`OAuthAuthorizationHandoff` is final, private and non-Product with a redacted
display. Its URI deliberately goes to the browser. Its proof is reserved for
the following HTTP adapter's HttpOnly callback-path cookie and must never enter
JSON, logs or diagnostics. No runtime method returns a raw credential.

## R2: Actual execution authority and native effects

The private synchronous contracts are:

```scala
statusC(context: ExecutionContext): Consequence[OAuthConnectionStatus]
beginC(context: ExecutionContext): Consequence[OAuthAuthorizationHandoff]
completeC(returnedState: String, browserProof: String, code: Option[String],
  error: Option[String], uow: UnitOfWork): Consequence[OAuthConnectionStatus]
authorizationC(uow: UnitOfWork, expectedProvider: OAuthConnectionProviderKind):
  Consequence[Map[String, String]]
forgetC(context: ExecutionContext): Consequence[OAuthConnectionStatus]
```

Every operation requires a nonnull actual ExecutionContext whose nearest
Component.Context in its scope ancestry has exactly the configured component
and instance IDs. Status, begin, private header use and forget additionally
require `SecuritySubject.from(context.security)` to be both authenticated and
provider-authenticated with exactly the configured owner ID. Public attributes,
copied provenance, trusted-local authentication and other owners are insufficient.
These checks precede entropy, custody and HTTP. Completion requires the exact
scope and the stored flow's state/proof; it does not require, create or upgrade
ingress authentication or service grants.

Time comes only from the bound `context.clock.instant()`. Token exchange and
refresh evaluate the existing fixed OAuthClient ExecUowM through
`new UnitOfWorkInterpreter(uow).evaluateInActiveUnitOfWorkC` in the very same
supplied UnitOfWork. The runtime never creates, commits or aborts a UnitOfWork,
retries a POST or replays a business operation. Native HTTP admission, driver,
CallTree and structured failure Conclusion remain authoritative. Component
program construction must never call these effectful synchronous methods, and
no generic evaluator is introduced.

## R3: Transient flows, time and mutation

Each connection has at most one private redacted non-Product pending flow,
holding only request, proof, issuance and expiry. Its lifetime is 300 seconds.
It is not persisted or registered globally. Reconstruction loses the pending
flow and loads a valid saved grant; this is not a claim of actual JVM restart
acceptance or permission to replay a code.

A brief per-connection synchronized gate protects the state. A busy flag covers
actual in-flight credential mutations. No monitor is held over HTTP. Status can
read a safe snapshot during a mutation. Begin, completion, header acquisition
and forget while busy fail without effects or pending consumption.

Begin authorizes scope/owner, reads the bound clock, checks idle and computes
checked expiry. It draws three separate 32-byte arrays for state, verifier and
browser proof, each encoded as 43-character unpadded base64url. Invalid entropy
or invalid/overflow time leaves the prior flow unchanged. Only successful
OAuthClient.beginC preparation replaces it: latest explicit begin wins while
retaining the cached grant, uncertainty and domain data.

Completion first matches both state and proof with MessageDigest.isEqual after
bounded base64url 43..128 admission. Foreign, malformed, absent or null markers
do not consume a legitimate flow or alter its outcome. A matched flow is
consumed before provider-result validation or exchange. Its bound time must
satisfy `issuedAt <= now < expiresAt`; expiry or rollback records Expired and
performs no HTTP or custody effect. A matched invalid clock is consumed and
cannot fall back to ambient time.

Exactly one nonnull Some(code) or Some(error) is required, with the other None
and both Option objects nonnull. Codes use the protocol's printable 1..8192
admission; error values use printable 1..128. Null, empty, malformed, both or
neither records Failed after consuming the matched flow, without exchange,
save or cache replacement. A valid provider error records Denied, returns safe
status and preserves an existing grant and uncertainty. It does not echo the
error or accept description, URI, owner or scope overrides.

## R4: Credential rotation, uncertainty and recovery

An admitted code reserves busy and performs one native exchange. A returned
grant is saved exactly once before readiness; cache replacement follows save
success. Successful reauthorization clears uncertainty and records Authorized.
Native failure or save failure retains the original structured Conclusion,
prior cache and disk outcome, records Uncertain/Failed and forbids subsequent
header use. A rename followed by a durability failure may already have changed
disk: the runtime must not claim otherwise or fabricate a successful readback.
No automatic exchange retry or disk reconciliation takes place. The supported
explicit recovery is successful reauthorization or successful local forget.

Header acquisition requires the exact configured provider kind, including
provenance distinction between the Google preset and an equivalently named
generic provider. An unexpired certain grant returns only the private Bearer
header without HTTP or writes. Absent or expired unrefreshable grants require
authorization. An expired refreshable grant reserves busy, performs one native
refresh, saves once and updates the cache only after save. Refresh success
retains the last authorization outcome; native/save failure records
Uncertain/Failed without retry or business replay. Bound time is rechecked
before header return. If the saved replacement is already expired, it remains
saved but the call requires authorization without refreshing again. Only the
following private native Drive strategy may consume this header.

Forget authorizes scope/owner, reserves busy and clears pending/cache as explicit
local disconnect intent before calling the selected store once. Success clears
uncertainty and records Disconnected. Failure records Uncertain/Failed and does
not restore cached use or claim removal. It neither revokes a Google grant nor
deletes source, Material, Entity, domain files or remote ACLs. All mutation
paths release busy in synchronous finally blocks, including native failures
and unexpected local exceptions.

Status never refreshes, writes or consumes a flow. A certain cached unexpired
grant is Connected; a certain cached expired grant is Expired; certain absence
is AuthorizationRequired; uncertainty always yields Uncertain. Pending is true
only within the bound flow interval. Stale status or a Google browser message
does not establish authentication or authorization success.

## R5: Fixed failures and diagnostic boundaries

| Condition | Structured utility and exact message |
| --- | --- |
| Missing/foreign actual scope | securityPermissionDenied("OAuth connection scope is not allowed") |
| Missing provider-authenticated configured owner | securityPermissionDenied("OAuth connection owner is not allowed") |
| Busy credential mutation | stateConflict("OAuth connection update is in progress") |
| Invalid/overflow bound clock | stateConflict("OAuth connection clock is invalid") |
| Missing/foreign/invalid state or proof | securityPermissionDenied("OAuth authorization flow is invalid") |
| Matched expired/rolled-back flow | stateConflict("OAuth authorization flow has expired") |
| Invalid matched provider result | argumentInvalid("OAuth authorization response is invalid") |
| Wrong/null expected provider kind | securityPermissionDenied("OAuth connection provider is not allowed") |
| Missing usable grant | securityPermissionDenied("OAuth connection requires authorization") |
| Uncertain credential outcome | stateConflict("OAuth connection requires reconciliation") |
| Unexpected local exception | serviceUnavailable("OAuth connection operation is unavailable") |

Factory and storage record failures retain the existing fixed configuration
and custody categories. Already structured native/custody failures retain
their Conclusion. Local exception text, secret bytes, paths and provider
content are not attached to new failures. Actual standard/full HTTP and log
Conclusion projections, redacted object displays and enabled native CallTree
must omit synthetic token, code, state, proof, secret and path markers. Direct
browser URI delivery is the deliberate handoff exception.

The initial custody adapter remains owner-only plaintext on a trusted POSIX
host, outside Git/Dropbox by operator selection. It is not encryption, Google
revocation, account discovery or independent ingress authentication.

## Executable contract map

| Group | Required evidence |
| --- | --- |
| E1 | Factory single load, absent/expired grants, malformed/foreign records and null capability refusal |
| E2 | Actual owner/provenance and component/instance checks for every operation; narrow callback authority |
| E3 | Three draws, PKCE, 300 seconds, replacement, entropy and clock admission |
| E4 | Foreign marker preservation, generated inputs with at least 100 successes/zero discards, matched consumption, denial and replay |
| E5 | One native POST, original admission/failure, no UoW commit, persistence and independently reconstructed runtime |
| E6 | Honest refresh/no-refresh, saved token rotation, current-clock recheck, uncertainty and explicit recovery |
| E7 | Exact local forget, sentinel preservation, post-rename/delete durability failure and real readback |
| E8 | Bounded concurrent native driver, busy refusal without consumption, safe status, finally release and joined threads |
| E9 | Actual diagnostic projections, displays and enabled native CallTree privacy |

The [journal](../journal/2026/10/2026-10-21-oauth-connection-runtime.md) records the
operational handoff. This private unit alone is not connected delivery
acceptance, real Google/App evidence, actual restart evidence or Phase closure.
