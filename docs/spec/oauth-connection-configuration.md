# OAuth connection configuration

Date: 2026-10-21

This contract defines trusted private host configuration admission for the
connected PHASE-3 OAuth delivery. The executable counterpart is
[OAuthConnectionConfigurationSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthConnectionConfigurationSpec.scala).
It composes the [OAuth connection protocol](oauth-connection-protocol.md) and
[credential custody](oauth-credential-custody.md). Bootstrap/native-host code
owns loading; component operations, initialization snapshots and
`ExecutionContext` do not acquire secret resolution or host file access.

## R1: Explicit private runtime secret resolution

`RuntimeSecretResolver.privateFile` is a `private[cncf]` runtime provider.
Selecting the provider is inert. Only `resolveSecret` performs private host IO.
The opaque `SecretReference` retains its existing equality, constant hash and
redacted display, with no locator or material getter. A `private[config]`
instance operation reads its locator within the owning class, delegated by the
private runtime provider;
`SecretMaterial` remains `private[cncf]` and copies returned bytes.

Only an explicit hierarchical `file:` URI is admitted. Authority, user info,
query and fragment are forbidden. The filesystem path must already be absolute
and normalized, with a filename and parent. There is no home, environment,
default-path, vault, registry or network fallback.

Every ancestor is an actual no-follow directory. The immediate parent is
exactly POSIX 0700 and the selected existing target is a regular no-follow file
of exactly POSIX 0600, including absence of extra special bits. Both are owned
by the actual process user, resolved with `ProcessHandle.current().info().user()`
and the filesystem principal lookup service. Unix link count must be one.
Symlink ancestors, selected symlinks, dangling links, hardlinks, absent targets,
directories and unsupported attributes are rejected. Neither permission repair
nor directory creation occurs.

The resolver opens the file with `NOFOLLOW_LINKS`, reads at most 131073 bytes,
closes the stream once and admits 1 through 131072 bytes inclusive. Oversized
input is never truncated into valid configuration. The returned material is a
copy. This is private plaintext on one trusted host; it does not defend against
the same OS user, privileged processes or concurrent hostile replacement.
There are no inode/hash seals, locks or competing-writer protocols.

## R2: Closed operator configuration

The final non-Product `private[cncf]` configuration has private construction and
redacted display. Its companion admits only:

```scala
OAuthConnectionConfiguration.loadC(
  reference: SecretReference,
  componentId: ComponentId,
  instanceId: ComponentInstanceId
): Consequence[OAuthConnectionConfiguration]
```

All arguments must be nonnull, and the instance's component must equal the
explicit component before resolution. The trusted loader calls only the private
file resolver. UTF8 decoding reports malformed and unmappable input; JSON uses
`JawnParser(allowDuplicateKeys = false)`. The bounded root must be an object with
exactly these keys; only `clientSecret` may be omitted:

| Key | Required value |
| --- | --- |
| `schema` | String `cncf.oauth-connection/1` |
| `connectionId` | `[A-Za-z0-9][A-Za-z0-9._-]{0,127}` |
| `ownerId` | 1 through 256 printable ASCII characters without whitespace |
| `provider` | The preset string or closed provider object in R3 |
| `clientId` | String admitted by `OAuthClient.createC` |
| `clientSecret` | Optional string admitted by `OAuthClient.createC`; present null is invalid |
| `redirectUri` | String admitted by the existing client and exact callback rules in R4 |
| `scopes` | Array containing only strings, admitted by the existing client |
| `credentialFile` | Explicit absolute already-normalized plain filesystem path |
| `returnPath` | Fixed return path admitted by R4 |

Unknown, missing required, duplicate, null, wrongly shaped, empty or oversized
values are invalid. Google's downloaded web-client JSON is not this schema and
is not automatically converted. `OAuthClient.createC` owns client ID, secret,
redirect and scope validation. Scopes preserve their entered order and existing
nonempty, unique, maximum 16-token and per-token length/character restrictions.
No scopes are inferred or added.

## R3: Provider identity is configuration provenance

The literal string `google-drive` selects `OAuthProvider.googleDrive` and
`OAuthConnectionProviderKind.GoogleDrive`. A generic object contains exactly
`name`, `authorizationEndpoint` and `tokenEndpoint`, all strings, and is admitted
by `OAuthProvider.createC`. It always has kind `Generic`, even when its name and
endpoints equal the Google preset. String-name equality cannot grant later
managed Drive admission.

Generic providers gain no Google preset authorization parameters. Endpoint
construction performs no discovery, DNS request, network operation or caller
extras. Kind is a closed `private[cncf]` enum.

## R4: Component, callback, origin and return binding

The callback component segment is derived with
`NamingConventions.toNormalizedSegment(componentId.localId.value())` and must
match `[a-z0-9]+(?:-[a-z0-9]+)*`. The callback is exactly:

```text
/web/<component-segment>/connections/<connectionId>/callback
```

The configured redirect URI must have that exact raw path. Existing client
admission requires HTTPS with the existing port constraints, or numeric
`127.0.0.1` HTTP with an explicit valid port. User info, query, fragment,
`localhost`, dynamic redirects and encoded callback substitutions are rejected.
The origin is the admitted URI's `scheme://rawAuthority`; request or forwarded
headers never define it.

The fixed return path has one leading slash and one or more nonempty ASCII
segments matching `[A-Za-z0-9._~-]+`, excluding `.` and `..`. Double or trailing
slashes, query, fragment, percent escapes, backslashes, whitespace and controls
are invalid. HTTP parameters cannot supply or alter this value.

## R5: Private custody composition

The loader constructs `PrivateFileOAuthCredentialStore` through its existing
`createC`, using the selected path, connection, owner and client. It does not
create a parent, create a credential, load a grant, forget a grant or write a
grant. Existing target safety and record interpretation remain store-owned;
constructing configuration does not silently interpret an existing grant.
The operator's private plaintext directory must be outside Git and Dropbox.
There is no inferred repository detector.

Within the private configuration class, metadata is limited to `componentId`,
`instanceId`, `connectionId`, `ownerId`, `providerKind`, `redirectUri`,
`configuredOrigin`, `callbackPath`, `returnPath` and ordered `scopes`.
`_client` and `_store` are only `private[oauth]`. There is no raw JSON,
client-secret, bytes, host-path, reference or material accessor, and no test-only
product accessor.

## R6: Fixed structured failures and disclosure

Invalid private references, paths, modes, ownership, types, unsupported
attributes, missing or out-of-bound source files return
`configurationInvalid("Private secret file configuration is invalid")`.
Other private-file IO failures return
`serviceUnavailable("Private secret file is unavailable")`.

Decode, identity, provider, client, URI, JSON and lexical path errors return
`configurationInvalid("OAuth connection configuration is invalid")`. The
resolver and store retain their own fixed structured failures when their own
admission fails. No exception, path, input content or parser diagnostic is
attached or logged. Redacted displays and actual standard/full HTTP and log
Conclusion projections must contain no private input markers.

## Executable coverage and remaining boundary

The executable specification groups private resolver admission, closed schema
and provider provenance, component/browser bindings, and custody/disclosure
composition. It uses disposable canonical POSIX fixtures beneath `target/`,
behavioral Given/When/Then and at least 100 generated valid connection-binding
round trips with zero discards. Existing `SecretReferenceSpec`, initialization,
OAuth client and custody specifications retain their existing semantics.

Native connection ownership, browser/callback service, transient flows,
bootstrap installation, managed Drive and Studio integration are following
units. Configuration admission alone does not establish Google consent, an
operational account, Step acceptance or PHASE-3 completion.
