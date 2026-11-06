# Selected authentication provider origin

## Contract

`SecurityContext.authenticatedProvider` returns an optional immutable
`security.AuthenticationProviderIdentity(bindingName, providerName, componentName)`.
The three strings identify the exact resolved deployment binding, the selected
provider's actual name and that binding's component. They contain no credential,
header, session ID, account attribute or provider object. Consumers may pin this
identity as one part of their own declared trust policy; CNCF supplies no
application realm policy or account-ID interpretation.

AuthenticationProviderRuntime retains binding/provider pairs in existing resolved
traversal order. Only an actual successful authenticate, login or currentSession
selection first copies the immutable AuthenticationResult data to a fresh call-local
result and attaches this identity through the existing private identity-keyed weak
provenance storage. It never inherits a cached result's private proof or retags the
provider's original object. Distinct selections returning the same provider-cached
data therefore keep independent binding attribution before context conversion. No-match continues and failure
stops as before. The public providers traversal and logout behavior are unchanged.
Selection metadata alone does not mark the result/context provider-authenticated.

AuthenticationResult.toSecurityContext explicitly inherits private provenance.
Existing ingress provider admission then establishes provider origin. The getter
returns Some only when that origin and actual selected-binding metadata coexist.
Raw/public products, equality/copy, attributes or a constructed/copied identity
supply neither proof nor an authentication grant. Trusted-local origin and legacy
provider contexts without selected binding attribution return None. No constructor,
case-class product, equality, extractor or public serialization field is added.

Existing framework inheritance preserves provider identity together with admitted
origin and credential transport facts through context restoration/rebinding.
Transport-only inheritance copies no provider metadata or authentication origin.
Marking/changing provenance preserves other admitted metadata; trusted-local
origin still cannot expose authenticatedProvider. Existing principal, capabilities,
session, request matching, failure and UnitOfWork correspondence remain unchanged.

## Executable specification and delivery

SecurityAuthenticationProvenanceSpec and SecurityContextSpec specify the proof
boundary, public-copy refusal and compatibility. IngressSecurityResolverSpec
specifies actual second-provider selection, resolved binding attribution,
no-match/failure, runtime login/currentSession and ingress/context restoration.
IngressContextCorrespondenceSpec retains the full context correspondence gate.
These are authored specifications; successful focused/full validation, live
PostgreSQL qualification, genuine independent review, source acceptance and local
publication require separate parent-owned evidence. The development source uses
0.5.5-SNAPSHOT; consumed 0.5.4 and earlier artifacts remain immutable.
