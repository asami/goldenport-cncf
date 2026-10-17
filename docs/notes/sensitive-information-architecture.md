# Sensitive Information Architecture — Initial Proposal

Date: 2026-10-16
Status: initial proposal; classification/type design not yet frozen
Related:
- ../design/textus-security-architecture.md
- apk-analysis-untrusted-client-security.md
- ../phase/phase-107.md

## Purpose

Textus should not treat every value as an ordinary application value with the
same storage, display, serialization and client-handling semantics.

The initial goal is to classify security-sensitive information and make the safe
handling path derivable from CML/Cozy/CNCF/Flutter rather than relying on each
application developer to remember local rules.

This note records the current design direction. Exact CML syntax, logical types,
provider interfaces and enforcement levels are intentionally left for the
planned phases.

## Initial classification proposal

### Ordinary Domain Value

Normal domain information handled through ordinary Entity/Operation/View
contracts.

### Sensitive Personal Data

Information that the application may legitimately need as domain data but whose
storage, projection, logging, retention and access require policy.

Examples may include contact/profile information depending on the domain.

### Sensitive Identity

External/provider identity used for authentication or account linkage, such as
a Google provider subject or external account identifier.

The preferred domain model uses a Textus-internal Subject/User ID. Provider
identity is mapped at the identity boundary and is not copied through ordinary
Material, Order, Job, Workflow or audit domain records merely for convenience.

Example:

```text
Google subject
  -> Identity Mapping
  -> Textus SubjectId U123
  -> SecuritySubject / domain owner reference
```

Email address and provider subject are distinct concepts even when a provider
uses email-like identifiers.

### Credential

A bearer or reusable value that grants authority, such as OAuth access/refresh
tokens or session credentials.

Credentials are isolated from ordinary domain models, logs and Display Models.
They require protected storage, least authority, bounded lifetime and
revocation semantics.

### Secret

Long-lived/high-authority secret material such as private keys, server API
secrets or provider management credentials. Prefer KMS/HSM/provider boundaries
rather than ordinary application persistence.

### Excluded Highly Sensitive Value

Values whose presence in normal Textus application memory/model should be
avoided when a trusted provider can own the interaction.

Examples: card verification code, provider password, raw private signing key,
platform biometric template.

The preferred model is UI/interaction delegation to the owning provider. Textus
receives a reference/assertion/result rather than the secret value.

### External Sensitive Reference / Result

A domain-safe reference/result returned by a trusted external interaction.

Examples:
- PaymentMethodReference;
- PaymentTransactionReference;
- IdentityReference / identity assertion;
- SignatureReference/result.

A reference is not assumed to permit arbitrary dereference of the provider's
underlying secret/resource.

## Sensitive Interaction Delegation

For excluded highly sensitive information, delegate the complete sensitive
interaction including UI when practical:

```text
Textus Application
  -> Sensitive Interaction
  -> provider/OS-owned UI
  -> trusted provider
  -> bounded Reference / Assertion / Result
  -> Textus domain
```

This prevents ordinary Textus Widget state, DTOs, logs, analytics, crash data
and application memory from handling values that the domain does not need.

Payment is the primary example, but the pattern also applies to IdP login,
biometric authentication, signing and provider consent.

## Domain modeling principle

Model the business fact Textus owns, not the provider secret.

Prefer:

```text
Payment
  method: PaymentMethodReference
  transaction: PaymentTransactionReference
  status: PaymentStatus
  amount: Money
```

over modeling PAN/CVV as ordinary Sales-domain Value Objects.

Similarly, Material.owner should normally reference a Textus SubjectId rather
than a Google account identifier. ExternalIdentity mapping belongs to the
identity/security boundary.

## CML / Cozy direction

CML should eventually express enough classification/semantic type information
for Cozy to preserve the security intent. Candidate behavior includes:

- warn/reject obviously inappropriate secret-like ordinary properties;
- avoid implicit toString/log/Display/API projection for sensitive values;
- generate provider/reference-based contracts for delegated interactions;
- compile external identity declarations to CNCF identity mapping rather than
  creating application-local identity semantics.

Exact syntax and which cases are hard errors versus warnings remain open.

## CNCF direction

CNCF owns runtime semantics:

- internal SubjectId/SecuritySubject as the normal authority identity;
- identity-provider mapping boundary;
- protected Credential/Secret handling contracts where CNCF must carry them;
- serialization/logging/OTLP/Display restrictions derived from security type or
  metadata;
- External Sensitive Reference and provider-interaction result semantics;
- integration with existing Operation and UoW Entity authorization.

Sensitive values must not be stuffed into ExecutionContext merely because they
are available. ExecutionContext carries only the authority/security facts needed
for execution.

## Flutter direction

Flutter Application Framework exposes application-level Sensitive Interaction
abstractions. Flutter Core owns platform/provider adapters and protected local
credential handling.

Android and iOS share the architectural rule: highly sensitive provider values
should not enter ordinary app state when provider/OS UI can own the interaction.
Platform mechanisms differ but the domain contract remains common.

The framework should make the safe path easy and should not encourage generic
CreditCardNumberField/CVVField/password capture widgets for interactions that
can be delegated.

## Current security principles

1. Client-visible information is not authority.
2. Identifiers are not credentials.
3. Protect actual credentials rather than hiding ordinary IDs.
4. Keep external Sensitive Identity indirect through Textus SubjectId where
   practical.
5. Exclude highly sensitive values from the Textus domain when the business only
   needs a provider reference/assertion/result.
6. Delegate sensitive UI to the trusted provider where practical.
7. Preserve classification through generation/runtime boundaries.
8. Do not invent encryption/hash/anti-tamper machinery merely because a value is
   labeled sensitive.

## Open design questions

- minimal stable classification vocabulary;
- CML syntax and inheritance/composition rules;
- logical type versus metadata/annotation representation;
- persistence/retention semantics for Sensitive Personal Data;
- identity mapping ownership and APIs;
- exact Display/serialization/logging defaults;
- provider adapter ABI for Sensitive Interaction;
- server versus mobile provider flows;
- policy for unavoidable direct sensitive-data capture;
- CAR lint/CBD Review responsibilities and escape hatches.
