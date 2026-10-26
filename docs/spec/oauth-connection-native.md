# Native OAuth connection and managed Drive authorization

Date: 2026-10-21

This contract connects [private connection runtime](oauth-connection-runtime.md),
[trusted configuration](oauth-connection-configuration.md),
[credential custody](oauth-credential-custody.md) and the existing
[Google Drive FileStore](google-drive-file-store-v1.md). Its executable counterpart
is [OAuthConnectionNativeSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthConnectionNativeSpec.scala).

## R1: Closed intent and effect boundary

`OAuthConnectionRequest[A]` is a sealed `private[cncf]` intent with a connection
ID and a framework-private typed execution method. Its companion owns all five
final non-Product implementations: status, begin, complete, forget and private
Drive authorization. Factory construction only holds data. It does not load a
runtime or store, draw entropy, read a clock or request HTTP. Completion holds
its state, browser proof, optional code and optional error privately; its display
is always redacted. There is no arbitrary function, generic parameter map,
stored ExecutionContext or caller-selected provider kind.

`UnitOfWorkOp.OAuthConnectionExecute[A]` is the canonical private operation. It
retains `A` without an unsafe cast and has a constant redacted display. Passive
planning classifies every occurrence as External, including status and repeated
identical intents. Planning preserves occurrence order and ordinals alongside
existing Local and Control operations. It does not execute, lower or deduplicate
OAuth requests. N2 specifies these semantics, including at least 100 successful
generated mixtures and valid IDs with zero discarded cases.

## R2: Declared optional component capability

The public `OAuthConnectionRuntime` capability extends Component. Its public
companion owns one stable `ComponentParameterKey[SecretReference]`,
`configurationKey`, created with
`optionalSecretReference("oauth.connection.configuration")`. A component Factory
must explicitly include that exact object in
`initializationParameterDeclarations`. A reconstructed key with the same name
does not declare the capability's key identity.

Normal `Component.Factory.createPrimaryC` first prepares actual component,
instance, subsystem and typed initialization metadata. The capability calls
super initialization, resolves only its declared key, and either remains
disabled for None or loads the referenced private configuration and constructs
the private runtime for the prepared component and instance. It assigns its
private connection only after both operations succeed. Failure prevents normal
installation. There is no undeclared or global configuration lookup, credential
fallback, permission repair, user inference or deletion of invalid records.

The snapshot contains only an opaque reference. Bootstrap may perform the
framework-private configuration and custody reads required to load the selected
grant once, including a valid expired grant. It never begins, exchanges or
refreshes. Later native status and cached authorization do not reload custody.
Private runtime metadata is available only to CNCF through `oauthConnection`;
there is no public runtime, configuration, client, token or secret-resolver
getter. N1 specifies disabled, valid, malformed, unsafe and undeclared bootstrap
and the actual opaque snapshot and diagnostic projections.

## R3: Exact native selection and authority

The standard UnitOfWork interpreter uses `uow:oauth:connection` with no raw
request or result payload attributes. A null request, null ID or ID outside
`[A-Za-z0-9][A-Za-z0-9._-]{0,127}` returns
`argumentInvalid("OAuth connection request is invalid")` before runtime effects.

Selection uses only the nearest actual Component in the current UoW scope.
An absent component or a component without this capability returns
`serviceUnavailable("OAuth connection runtime is unavailable")`. A disabled
capability or a different configured connection ID returns
`resourceNotFound("OAuth connection is not configured")`. The interpreter never
searches a subsystem, another component, an outer configured component past the
current component, a registry or a global context.

An exact selected connection receives the same actual UnitOfWork. Status,
begin and forget receive its ExecutionContext; completion and private Drive
authorization receive that same UoW. Existing private runtime checks still
require exact component and instance ownership and provider-authenticated owner
authority for management and Drive consumption. Public provenance attributes,
trusted-local identity, anonymous identity and foreign owners do not suffice.
Drive intent fixes the expected provider kind to GoogleDrive; a generic provider
with Google's name and endpoints remains Generic and cannot impersonate it.

An owner-started flow may complete under anonymous ingress in the same semantic
scope with its exact state and browser proof. This narrow callback capability
does not authenticate ingress or grant management, entity/source or Drive
authority. Wrong state or proof performs no token exchange and preserves the
legitimate pending flow. N3 specifies native selection, owner refusals, narrow
callback behavior and explicit owner disconnect; N4 specifies provider
provenance refusal through public FileStore consumption.

## R4: Native HTTP and enclosing lifecycle

Token exchange and refresh remain the existing fixed OAuthClient HttpPost
programs, interpreted in the same actual UoW with normal native HTTP admission,
driver and CallTree. The OAuth operation creates no separate UoW and performs no
inner commit or abort. The enclosing native run retains its single final
commit/abort responsibility. An enclosing commit failure does not undo or claim
rollback of an already completed token POST or private credential write.

Native permission and transport Conclusions retain their structured status,
taxonomy and policy; transport failures retain their original exception.
Refused native token permission acquires no driver and never retries the
business request. Failed refresh or save prevents every following Drive GET,
preserves the actual disk outcome and records uncertainty. A later implicit
read cannot retry through uncertainty; existing explicit recovery semantics
remain private-runtime-owned. N3 covers enclosing lifecycle, N4 covers native
permission, refresh/save failures and rotation ordering, and N5 covers actual
transport disclosures. The exhaustive private runtime failure matrix remains in
[OAuthConnectionSpec](../../src/test/scala/org/goldenport/cncf/oauth/OAuthConnectionSpec.scala).

## R5: Opaque managed strategy and compatibility

Public `GoogleDriveCredentials.managedConnectionC(connectionId)` validates the
same ID grammar and constructs only an opaque private selector. Invalid input
uses the existing fixed credential configuration argument failure. Its private
authorization program lifts exactly one canonical Drive authorization request;
it performs no direct runtime, custody, clock or HTTP effects. There are no raw
token accessors, scope/provider selectors or credential persistence here.

`GoogleDriveFileStore` retains its existing API and authorization chain. Its
public stat/read programs consume managed cached headers and original binary
octets. An expired saved grant refreshes once, saves access/refresh rotation
before Drive GET, and uses the rotated cached header for subsequent GETs in the
same native UoW. Existing entity/source authorization remains upstream of
FileStore authorization; this strategy introduces no business authorization
bypass.

Legacy `accessTokenC` and `refreshTokenC` signatures, validation, forms and
response behavior remain unchanged. Legacy refresh credentials still perform a
fresh exchange for every interpreted authorization without caching or adopting
response refresh material. Compatibility is specified by
[GoogleDriveCredentialsSpec](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveCredentialsSpec.scala)
and [GoogleDriveFileStoreSpec](../../src/test/scala/org/goldenport/cncf/filestore/GoogleDriveFileStoreSpec.scala).
N4 specifies the new managed strategy through those same public operations.

## R6: Disclosure and deliberate browser handoff

Requests, operations, managed strategies and secret-bearing private runtime
objects have redacted displays. Enabled native traces retain the OAuth and HTTP
labels and safe result summaries while omitting authorization code, state,
browser proof, token, client secret and private file markers. Actual standard,
full and log Conclusion projections obey the same boundary.

The begin result's authorization URI is the intentional browser handoff. Its
state is deliberately present there; the browser proof is reserved for the
following host callback adapter and is absent from that URI and public
application projections. N5 specifies these actual displays, summaries, traces
and failure disclosures. No public application runtime getter or alternate
execution service is added to enable testing or hosting.

## Executable scenario map

| Group | Given | When | Then |
| --- | --- | --- | --- |
| N1 | Actual Factory declaration, prepared owner metadata and private files | Normal creation and installation | Disabled absence or one exact runtime; unsafe/malformed/undeclared input refuses installation; opaque projections |
| N2 | Closed typed requests and passive mixed operations | Factory/Free construction and planning, including generated IDs and mixtures | No flow/store/HTTP effects; all five External; repeated occurrence order and ordinals retained |
| N3 | Actual component scope, owner provenance and browser flow | Canonical native management, callback, refusal and enclosing lifecycle | Exact selection and owner admission, narrow anonymous completion, one outer lifecycle, no rollback claim |
| N4 | Persisted cached/expired grants and controlled native peers | Existing public FileStore stat/read | Exact octets, cached header, saved rotation before GET; provider/permission/refresh/save refusal stops GET |
| N5 | Enabled CallTree and synthetic private markers | Native success or original transport failure | Useful labels and structured Conclusions without secret disclosure; URI remains deliberate handoff |
