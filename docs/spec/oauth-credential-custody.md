# OAuth credential custody

Date: 2026-10-21

This contract defines the framework-private synchronous credential custody SPI
and its initial POSIX private-file adapter. Its executable counterpart is
[OAuthCredentialStoreSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthCredentialStoreSpec.scala).
It belongs to the same connected server PHASE-3 delivery as the
[OAuth connection protocol](oauth-connection-protocol.md). It does not establish
an operational Google account connection or completion of that delivery.

The responsibility sequence is protocol credential -> trusted custody -> native
runtime transient-flow owner -> managed Drive -> Studio. Non-normative runtime
context is described in the [architecture overview](../design/cncf-architecture-overview.md).

## R1: Explicit private host selection and filesystem admission

`OAuthCredentialStore` and `PrivateFileOAuthCredentialStore` have `private[cncf]`
visibility. The final adapter has `private[oauth]` construction and no Product
semantics. Its companion accepts only explicitly selected configuration:

```scala
PrivateFileOAuthCredentialStore.createC(
  credentialFile: java.nio.file.Path,
  connectionId: String,
  ownerId: String,
  client: OAuthClient
): Consequence[PrivateFileOAuthCredentialStore]
```

The file and client must be nonnull. The path must already be absolute and
normalized, with a filename and parent. The factory does not normalize `..`,
create directories, select a home directory, read environment configuration or
discover a provider. Connection IDs match `[A-Za-z0-9][A-Za-z0-9._-]{0,127}`.
The ingress owner binding is nonempty printable ASCII without whitespace, at
most 256 characters. It is independent of OS-account ownership.

The operator must select a private directory outside Git and Dropbox. The
initial adapter stores owner-only **plaintext**. It does not provide encryption
or Keychain integration. The protocol document's reference to future encrypted
storage is a future capability, not a description of this adapter. Synthetic
filesystem specifications use disposable files under `target/` and must never
contain operational credentials.

The existing parent must be a no-follow directory with exactly POSIX 0700,
including no extra special permission bits, owned by the current OS process
user. The user principal is resolved from `ProcessHandle.current().info().user()`
through the selected filesystem's principal lookup service. No `user.name`
fallback or ingress/browser owner is used. Every path ancestor and the selected
target must be free of symbolic links, including dangling links. A caller must
select a canonical spelling instead of macOS `/tmp` or `/var` aliases when those
aliases are symlinks.

An existing target must be a no-follow regular file, owned by the same OS user,
with exactly POSIX 0600, no extra special permission bits and Unix link count
one. POSIX or Unix attribute unavailability fails closed. Every load, save and
forget re-admits the configured path, parent and existing target. Invalid
permissions, ownership, type, links or configuration are never repaired.

This is a single trusted host and selected-file ownership model. Mode and
ownership checks do not protect secrets from the same OS user, privileged
processes or hostile concurrent filesystem replacement. Inode/fileKey checks,
content hashes, seals, workflow locks and competing-writer protocols are not
part of this contract.

## R2: Framework-private synchronous custody

```scala
private[cncf] trait OAuthCredentialStore {
  def loadC(): Consequence[Option[OAuthCredential]]
  def saveC(credential: OAuthCredential): Consequence[Unit]
  def forgetC(): Consequence[Unit]
}
```

These methods explicitly perform bounded synchronous private host filesystem
IO. A later typed native operation interpreter or trusted host runtime invokes
them. Component action/program construction must not invoke custody. The SPI
does not expose raw bytes, JSON, paths, token getters, a browser API, callback,
configuration resolver, generic evaluator or application secret access.

Custody performs no network, clock, browser, subprocess, credential-service,
Keychain or JVM-global registry operation. Only genuine absence of the exact
target below the admitted existing parent yields `loadC() = success(None)` or
idempotent forget success. Access and IO errors do not become absence. A null
credential or a credential belonging to another client instance is rejected
before filesystem writes, even if the foreign client's configuration is equal.

## R3: Closed bounded record and validated restoration

Read opens the exact file with `NOFOLLOW_LINKS`, reads at most 131073 bytes,
requires 1 through 131072 bytes inclusive, and closes the input once. UTF8
decoding uses REPORT for malformed or unmappable input. JSON parsing uses
`io.circe.jawn.JawnParser(allowDuplicateKeys = false)`.

The root is a JSON object with exactly the following keys; only `refreshToken`
may be absent:

| Key | Required value |
| --- | --- |
| `schema` | String `cncf.oauth-credential/1` |
| `connectionId` | Exact configured connection ID |
| `ownerId` | Exact configured ingress owner binding |
| `client` | Exact structured current client binding defined in R4 |
| `accessToken` | Valid RFC6750 Bearer token, at most 16384 characters |
| `refreshToken` | Optional nonempty printable ASCII token without whitespace, at most 16384 characters |
| `grantedScopes` | Nonempty unique Vector of valid scope tokens, a subset of the current requested scopes, retaining entered order |
| `expiresAt` | String parsed by `Instant.parse` whose exact spelling equals the resulting `Instant.toString` |

Null, missing required fields, duplicate keys at any object level, unknown keys,
coerced shapes, malformed tokens and unrequested scopes are invalid. Refresh
absence means `None`; JSON null is invalid. No client secret is persisted.
Trailing JSON whitespace may contribute to the byte bound. The store must not
truncate an oversized record into an apparently valid one.

Only two OAuth-package methods are added to the existing client:

```scala
private[oauth] def _connection_binding: io.circe.Json
private[oauth] def _restore_credential_c(
  access: String, refresh: Option[String], scopes: Vector[String],
  expiry: java.time.Instant
): Consequence[OAuthCredential]
```

Restoration uses the existing token/scope validators, rejects null Option,
`Some(null)` and null expiry, checks the requested subset, and constructs a
credential bound to this exact client instance through the trusted factory.
Invalid saved/input grant data uses the fixed custody-record failure in R7.
Access/refresh material remains restricted to existing trusted CNCF code.

Expired grants may load. Custody does not consult a clock or decide whether to
refresh. A grant lacking refresh material remains unable to refresh. The
existing authorization, completion and refresh response behavior is unchanged.

## R4: OAuth identity and owner binding

The nested `client` object has exactly six keys, with values equal to the
currently configured client:

| Key | Binding |
| --- | --- |
| `providerId` | Provider's configured name |
| `authorizationEndpoint` | Exact configured authorization endpoint |
| `tokenEndpoint` | Exact configured token endpoint |
| `clientId` | Exact configured client ID |
| `redirectUri` | Exact configured redirect URI |
| `requestedScopes` | Exact requested scope Vector, including order |

This binding expresses actual OAuth identity, redirect and grant constraints.
It is not a file hash, source-path seal or metadata-only proof. A separately
constructed equivalent client can load the grant, and client-secret rotation
alone does not invalidate it. Changing provider, endpoints, client ID, redirect,
requested scopes or order, ingress owner or connection requires explicit
reauthorization. A saved record must never substitute for changed current
configuration. A mismatched record is retained, including on save and forget.

## R5: Atomic replacement and uncertain durability

Save validates the input credential and admits the current target. If the target
exists, its complete record and bindings must validate before replacement.
The complete exact-schema record is encoded as bounded UTF8. One sibling
staging file is created exclusively with CREATE_NEW semantics and mode 0600 in
the admitted directory. The adapter writes all bytes through FileChannel,
forces the file with `force(true)` and closes it, then installs it at the exact
target with `ATOMIC_MOVE` and `REPLACE_EXISTING`. There is no non-atomic fallback,
old-target deletion, backup rename, seal or hash.

After rename the parent directory is opened with a read FileChannel, forced and
closed. An error before rename reports the fixed unavailable failure, preserves
the old credential and attempts to remove only this attempt's staging name.
Cleanup is best effort and never deletes the old or renamed target or exposes a
temporary name. A residual staging file after cleanup failure remains private
host operational data; it is not an alternate source of credential authority.

If rename succeeded but directory durability cannot be confirmed, the result
is the fixed uncertain-outcome `stateConflict`. It must not claim the write did
not happen. The runtime must read the selected file back and reconcile the
credential outcome. It must not blindly repeat a token exchange or replay a
business operation. Saving an actual refresh result persists its latest rotated
refresh material.

The same source file defines the narrow private host capability
`private[oauth] trait OAuthCredentialDurability`, with
`forceDirectory(directory: Path): Unit`. Its companion's `posix` implementation
performs actual directory FileChannel force/close. The standard factory always
selects `posix`. Private construction additionally receives this capability so
the executable specification can observe actual rename/delete followed by a
directory-sync failure. This is neither an application hook nor a filesystem
or binding-admission bypass.

## R6: Exact local forget

Forget admits and fully validates an existing record and its bindings, deletes
only the exact selected file, then confirms parent-directory durability.
Admitted absence succeeds without deletion or synchronization. A malformed or
foreign record remains intact. A failure after actual deletion but before
durability confirmation uses the same explicit uncertain-outcome failure.

Forget does not revoke remote Google grants, delete Material/files/domain
entities, traverse directories, delete the parent or clean up other records.
Changed configuration never authorizes deleting the old record.

## R7: Fixed failures and safe diagnostics

| Condition | Structured utility and fixed message |
| --- | --- |
| Unsafe configured path, ownership, permissions, type or unsupported filesystem attributes | `configurationInvalid("OAuth credential storage configuration is invalid")` |
| Malformed, foreign or mismatched saved/input grant | `resourceInvalid("OAuth credential storage record is invalid")` |
| Ordinary filesystem IO before mutation completion | `serviceUnavailable("OAuth credential storage is unavailable")` |
| Rename/deletion completed but durability confirmation failed | `stateConflict("OAuth credential storage update outcome is uncertain")` |

Failures contain no submitted record, token, path, temporary filename, native
exception, cause message or secret echo. Actual HTTP standard/full and log
Conclusion projections preserve structured status/taxonomy without disclosing
these values. Displays are `OAuthCredentialStore(<redacted>)` and
`PrivateFileOAuthCredentialStore(<redacted>)`; existing client and credential
displays remain redacted.

## Executable examples and connected work

| Executable group | Contract evidence |
| --- | --- |
| E1 | Explicit configuration, actual process principal, exact parent mode, no discovery or symlink aliases |
| E2 | Re-admission on operations, target mode/type/link refusal, invalid input and actual IO failure |
| E3 | Actual native exchange/save/load across independent clients, secret rotation, native refresh using latest saved token, expired/absent-refresh honesty, at least 100 generated round trips with zero discards |
| E4 | Exact UTF8 bound, malformed/duplicate/unknown/missing/null fields, strict token/scope/expiry restoration |
| E5 | Every configured identity/owner/connection change refused without replacement or forget |
| E6 | Actual private staging/atomic target, no successful-stage residue, exact local forget, real rename/deletion followed by injected directory-sync failure and filesystem readback |
| E7 | Actual fixed Conclusion projections and redacted displays with synthetic markers |

The native pending-flow owner, one-time state consumption, callback/ingress
binding, runtime assembly, managed Drive connection wiring, Studio interaction,
real Google/App use and connected delivery gates remain subsequent work.
The [custody journal](../journal/2026/10/2026-10-21-oauth-credential-custody.md)
records this slice's operational handoff without a validation or acceptance
claim. Existing public OAuth, Drive and FileStore APIs remain unchanged.
