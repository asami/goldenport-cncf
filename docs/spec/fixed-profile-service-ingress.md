# Fixed-Profile Service Ingress Specification

Status: normative

## Rules

### R1 Provider-authenticated Service admission

When a fixed execution profile receives authentication ingress material, CNCF
admits the request only when an admitted authentication provider resolves a
subject whose kind is `Service`. The resolved provider identity and canonical
runtime bindings remain authoritative.

### R2 User rejection

A provider-authenticated subject whose kind is not `Service` is rejected for a
fixed execution profile.

### R3 Unmatched rejection

Authentication ingress material that no admitted provider resolves is rejected
for a fixed execution profile. Privilege fallback and request-provided user
identity do not convert it into fixed-profile admission.

### R4 Declared business session ownership

The resolved Operation's request declaration owns a bare business `sessionId`
parameter. Declaration and supplied property/argument names use the existing
authentication token normalization: case, underscores and hyphens normalize
`sessionId` and `session_id` to the same bare name. When any declared parameter
name has that normalized bare name, Subsystem excludes only supplied properties
and arguments with that same normalized name from its derived security attributes.
The derived security projection preserves the Request and business values,
including long business values that are not valid authentication SessionIds.
Existing framework-field filtering during domain Request creation for validation,
Operation request creation and actual execution remains unchanged. Other
properties/arguments retain their existing conversion, empty value filtering and
last-write precedence.

This projection applies at both normal Request execution and public Action
execution. Normal HTTP ingress uses the same projection after its existing
protocol and framework-header enrichment. A declared business ID alone preserves
the configured local subject in Fixed mode. It does not supply authentication
evidence to an Authenticated profile.

### R5 Reserved authentication evidence remains authoritative

Business ownership suppresses only the colliding declared bare name. Explicit
authorization, access/refresh tokens, federation and privilege fields retain
their existing security contracts. Explicit `x-textus-session`, `x-cncf-session`,
`textus.session`, `cncf.session` and cookies retain their existing authentication
meaning, including alongside a distinct business session ID. Header enrichment,
provider/resolver APIs, the global authentication key catalog, downstream
authorization and ordinary error handling are unchanged.

If the Operation does not declare a normalized bare session parameter,
`sessionId` and `session_id` remain authentication aliases. Built-in authentication
operations with an empty request declaration retain this contract. No Component
identity, operation-name exception, local privilege bypass or provider workaround
is part of this projection.

Fixed-profile credentials still require an admitted provider and a resolved
Service subject under R1–R3. Cookie handling retains its existing profile-specific
behavior. Authenticated ingress still requires actual authentication evidence
and preserves the provider principal and authentication session separately from
business context.

## Examples

### E1 Fixed service ingress

A service token is admitted while an ordinary-user token and an unmatched token
are rejected. Executable coverage: `IngressSecurityResolverSpec` E1.

### E2 Declared property through public Request execution

Fixed local-subject execution accepts declared property `sessionId`, including a
long value outside authentication SessionId limits. The operation sees the original
Request/value and configured local principal. Bounded generated values exercise
the same ownership invariant.

### E3 Declared argument and normalized spellings

An Operation declares argument `session_id`. Public Request calls using canonical,
camel-case, uppercase/underscore and hyphen spellings retain their original
business value and Fixed local principal.

### E4 Public Action execution

The same declared business property crosses `Subsystem.executeAction`. The
operation actually executes with its unchanged business value and local principal.

### E5 Normal HTTP GET query

`Subsystem.executeHttp` receives a GET query `sessionId` through its normal
protocol handler. HTTP 200 exposes the original business ID and local principal.

### E6 Normal HTTP POST named JSON

The normal HTTP server decodes named JSON `sessionId` into `HttpRequest.form`
before it enters `Subsystem.executeHttp` through its normal protocol handler.
HTTP 200 exposes the original business ID and local principal. This producer
scenario sets up the server-decoded Subsystem boundary; raw JSON POST through the
actual launcher/server remains separately required by downstream
`NativeWorkflowCliSpec`.

### E7 Explicit credential rejection

Declared business context alongside authorization/access/refresh/federation,
explicit session headers or namespaced session properties fails in Fixed mode
without an accepting service provider. Canonical and normalized header spelling
variants remain evidence; actual HTTP header enrichment preserves that rejection.
The business operation never executes.

### E8 Undeclared bare aliases

An empty request declaration does not reserve `sessionId` or `session_id`.
Public Fixed ingress without a provider rejects both, and direct
AuthenticationRequest alias interpretation remains unchanged.

### E9 Business context without Authenticated evidence

An Authenticated profile with a configured provider receives only declared
business `sessionId`. It rejects missing authentication evidence without calling
the provider or executing the protected business operation.

### E10 Distinct business and authentication sessions

An Authenticated provider accepts the actual `x-textus-session`, `x-cncf-session`,
namespaced session or Cookie ID while the operation receives a distinct business
ID unchanged. Public Request and real HTTP header/cookie ingress retain the
provider principal and authentication session. The provider receives the auth ID
and no projected bare business session name. Exact full domain Request
expectations retain selectors, arguments and business values while following the
existing authentication metadata filtering: `x-textus-session`, `textus.session`
and `cncf.session` properties are removed; `x-cncf-session` remains.

### E11 Fixed Service authentication alongside business context

Public Fixed ingress accepts a provider Service token with a distinct unchanged
business ID and retains the provider Service principal. A provider User token and
an unmatched token retain ordinary rejection. Business ownership does not hide
the token from the provider.

`SubsystemBusinessSessionIngressSpec` binds E2–E11 through visible afterWord
metadata and adjacent Given/When/Then. These are ordinary FULL scenarios.

| Example | Governing rules | Public ingress |
| --- | --- | --- |
| E2 | R4 | executeWithMetadata, declared property and generated business values |
| E3 | R4 | executeWithMetadata, declared argument and normalized names |
| E4 | R4 | executeAction |
| E5 | R4 | executeHttp GET |
| E6 | R4 | executeHttp POST server-decoded JSON form |
| E7 | R1,R2,R3,R5 | executeWithMetadata and executeHttp, credential negatives |
| E8 | R5 | executeWithMetadata, undeclared alias negatives |
| E9 | R4,R5 | executeWithMetadata, Authenticated evidence negative |
| E10 | R4,R5 | executeWithMetadata and executeHttp headers/cookie |
| E11 | R1,R2,R3,R4,R5 | executeWithMetadata, provider Service/User/unmatched |
