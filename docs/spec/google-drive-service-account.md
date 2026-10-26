# Google Drive service-account connection

Date: 2026-10-24

## Selection and compatibility

`GoogleDriveConnectionRuntime` extends the existing `OAuthConnectionRuntime`.
Factories declare both existing `oauth.connection.configuration` and new
`google-drive.service-account.configuration` opaque SecretReference keys.
Absent service-account configuration retains the existing OAuth behavior,
including owner-bound consent, refresh, custody and HTTP management. Both
settings together fail configuration; a failed selected service account never
falls back to OAuth, ambient credentials or another component's connection.

The new private configuration is a UTF-8 JSON object with schema
`cncf.google-drive-service-account/1`, `connectionId`, nonempty unique
`allowedSubjects`, `scopes`, and exactly one of `credentialFile` or
`credentialEnvironment`. The latter must be the literal
`GOOGLE_APPLICATION_CREDENTIALS`; only this explicit selection reads that
environment variable. Its value, or credentialFile, is an absolute canonical
path to the downloaded Google service-account JSON. No implicit ADC discovery,
metadata-server fallback, user impersonation or domain-wide delegation exists.
Allowed scopes are Drive `drive`, `drive.readonly`, and `drive.file` URIs.

Both files use the existing RuntimeSecretResolver.privateFile contract:
current OS owner, exact file 0600/direct parent 0700, no symlink ancestors,
regular single-link file, bounded strict UTF-8/duplicate-rejecting JSON.
No permission repair or credential write is performed. Operational files stay
outside Git and synchronized folders. Changing key/configuration requires
component reconstruction; no global registry or cross-component grant exists.

## Execution

Existing GoogleDriveCredentials.managedConnectionC remains the consumer API.
Only its native Drive-authorization intent can select this strategy. OAuth
status/authorize/callback/disconnect continue to operate on OAuth only; they
never pretend that a service account completed browser consent.

Before token use or exchange, the actual UnitOfWork context must belong to the
configured component and instance and have a provider-authenticated subject
explicitly included in allowedSubjects. This outbound allowlist does not grant
Material/Entity access; the owning operation must authorize that first.

The framework signs the standard RS256 JWT with the JDK RSA implementation:
fixed `https://oauth2.googleapis.com/token` audience/endpoint, configured Google
service-account issuer, requested scopes, bound context-clock iat and exp
(3600 seconds), and no `sub`. Credential token_uri cannot redirect the exchange.
The exchange uses the existing native UnitOfWork HTTP operation, driver,
execution admission, CallTree and response-size limit; no SDK-side network or
generic evaluator escapes those boundaries.

A successful bounded JSON token response must contain a valid Bearer token
and a whole positive expiry no greater than 3600 seconds. Any returned scope
must match the requested set. Cache is in-memory per initialized connection,
used only between its issue time and expiry minus 30 seconds. Expiry obtains
a new token, never a refresh-token grant. No token/key is persisted. Clock
rollback, expired results, provider denial, malformed results and HTTP admission
fail closed. One acquisition performs at most one exchange; it never retries
or replays a business write. Concurrent acquisitions may independently mint
equivalent grants; no lock is held over HTTP.

## Diagnostics and acceptance

Keys, JWT assertions, tokens, credential/configuration paths and provider error
bodies must not appear in diagnostics or object displays. Structured native
HTTP failures are retained through existing disclosure policy; local parsing
and signing failures use fixed safe messages.

Executable specifications cover genuine RSA signature/claims, strict private
configuration, native authorization before HTTP, token caching/renewal,
component/instance/subject refusal, no fallback and existing OAuth regression.
Controlled provider evidence is not real Google/Workspace/Drive ACL acceptance.
No list/delete or domain-wide delegation capability is added to FileStore.
