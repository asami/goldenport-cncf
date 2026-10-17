# Textus Security Architecture

status=initial
authority=canonical
updated_at=2026-10-14

## Purpose

This document is the canonical security architecture for Textus. Security is a
cross-cutting architectural concern, not an optional deployment feature.
Subsystem and project documents may refine responsibilities, but must not define
a conflicting Textus-wide trust model.

The working principle is:

> Trust explicit contracts and admitted authority, not network location,
> credentials alone, a running process, or the identity of a developer or AI.

## Threat Model

Textus assumes that public Web/API surfaces are continuously exposed to hostile
input; credentials and administrator endpoints can be abused; endpoints and
supplier environments can be compromised; dependencies and generated code can
contain defects; and a running instance can become untrustworthy.

Network separation, VPN membership, possession of a credential, or successful
authentication is therefore not sufficient authority for a business operation.

## Security Boundaries

The preferred execution path is:

```text
external subject
  -> authentication / normalized SecuritySubject
  -> exposed Operation
  -> Capability / authorization context
  -> Guard / policy evaluation
  -> EntityRealm / service boundary
  -> Repository / DataStore
```

Public surfaces must not gain arbitrary DataStore, filesystem, process, network,
or subsystem authority merely because they run inside a trusted deployment.

### Authentication and authorization

Authentication establishes subject identity and authentication facts.
Authorization establishes what that subject may do. These concerns remain
separate.

CNCF's authorization model is ABAC-centered. RBAC, ACL/DAC and ReBAC are
evaluation patterns and inputs rather than independent trust domains.
Capabilities express admitted authority; Guards and authorization policies
evaluate operation, subject, entity, relation and environmental context.

Deployment profiles should support phishing-resistant authentication such as
FIDO2/WebAuthn/passkeys through external identity infrastructure where
appropriate. CNCF does not duplicate an identity provider.

### Least authority

Authority is scoped to the smallest useful Operation, Entity/resource,
component/subsystem and duration. Administrator or service identity must not
imply unrestricted DataStore or cross-subsystem access.

Cross-component and cross-subsystem calls cross an explicit contract boundary
and carry only the authority required by the target operation.

## Public Surface

Web, API, MCP and other external adapters are ingress surfaces, not owners of
business data. They validate transport-level input and invoke declared
Operations. Business authorization remains at the operation/runtime boundary so
that an alternate adapter cannot bypass it.

WAF, API gateways, rate limiting and bot controls are useful deployment
controls, but are defense in depth and never substitutes for application
authorization or safe data access.

## Safe-by-Construction Application Security

Textus standard application development MUST make the safe path the ordinary
path. Developers should not have to remember a per-handler security ritual for
common Web vulnerabilities. The architecture follows four ordered defenses:

1. **Impossible by default** — omit dangerous primitive APIs from ordinary
   application surfaces where a typed framework operation can replace them.
2. **Safe by construction** — CML, generated code, logical types, adapters and
   runtime mechanisms automatically preserve the relevant safety property.
3. **Explicit escape hatch** — exceptional unsafe/powerful operations are named,
   typed and statically identifiable rather than hidden in ordinary strings or
   generic APIs.
4. **Lint and Review** — CAR lint detects deterministic escape/boundary misuse;
   CBD Review assesses cross-cutting security meaning and justification.

Lint is therefore not the primary defense against SQL injection, XSS or CSRF.
The normal API/runtime path should prevent the vulnerability first.

### SQL injection and persistent-store access

Ordinary application code uses typed Entity/Repository/DataStore query and
mutation APIs. Values are bound as parameters by the DataStore implementation;
application values are not concatenated into generated SQL.

Raw/native SQL, where genuinely required, is an explicit Security Escape Hatch.
It must be statically identifiable and reviewable. Direct JDBC/DataStore access
outside declared framework/provider boundaries is not the normal application
path and is eligible for CAR lint/CBD Review findings.

### XSS and output contexts

Display/UI models distinguish ordinary text from trusted markup and other output
contexts. Ordinary text is escaped by the renderer for its actual context.
HTML, URL, script and style contexts must not be treated as interchangeable
Strings.

Trusted/raw markup is an explicit escape hatch. A plain String must not become
trusted HTML merely by calling a convenient public constructor. Sanitization or
another declared trust-producing boundary is required when untrusted rich text
must become renderable markup.

URL/redirect targets should use logical types/policies that reject unsafe
schemes and unintended external redirects rather than relying on caller string
discipline.

### CSRF and HTTP mutation semantics

CNCF/Cozy Web exposure derives HTTP mutation semantics from declared Operation
semantics. Read/query operations may map to safe retrieval methods; state
mutation must not be exposed as GET merely for convenience.

For browser sessions whose authentication is automatically attached by the
browser, the Web ingress/runtime supplies CSRF protection (for example
token/origin policy) before invoking a mutation Operation. Individual business
handlers do not own ad-hoc CSRF checks. Non-cookie/API authentication profiles
may use a different declared policy where CSRF is not applicable.

### Input binding and mass assignment

External request bodies bind to declared Operation input models, not directly to
persistent Entity instances. Only declared input fields can participate in the
command. Persistence-only, authority-bearing or administrative fields are not
implicitly writable because a caller supplied matching JSON/property names.

### Authorization before handler execution

Authorization is a runtime boundary, not a helper that handlers are expected to
remember to call. An exposed Operation reaches its business handler only after
the applicable subject, Capability/Guard and authorization policy have been
evaluated.

### Sensitive and dangerous logical types

Security-sensitive values such as credentials, secrets, access tokens and API
keys should have explicit logical/runtime types or metadata where practical.
Their default stringification, logging, serialization and Display Model
projection must be redacted or prohibited as appropriate.

Upload and other hostile binary/content ingress should likewise use a bounded
framework model for size, media/content policy, filename handling and temporary
storage rather than exposing unrestricted raw host resources to application
logic.

### Security Escape Hatch Registry

Textus maintains a conceptually common registry/classification of operations
that intentionally cross a safe default boundary. Initial candidates include:

- Raw/native SQL;
- trusted/raw HTML or equivalent markup;
- external/unvalidated redirect targets;
- direct filesystem access;
- direct network access;
- process execution;
- native-library execution;
- unsafe/general deserialization.

The registry is metadata for visibility and review, not a mandate to implement
one giant security API. Each owning subsystem keeps the actual typed mechanism.
Escape-hatch use must be attributable to a source/component and available to
CAR lint/CBD Review without requiring speculative whole-program inference.

## Data Protection

Persistent data is separated from replaceable runtime state.

Applications should minimize collection and retention of sensitive data and,
where practical, avoid possessing high-value secrets at all by delegating them
to specialized services or using tokenized references.

Encryption in transit and appropriate encryption at rest are deployment
requirements for sensitive data. Encryption does not replace authorization:
an authorized running application can often see decrypted data.

Public/application code should reach persistent stores through declared
Entity/Repository/DataStore mechanisms rather than arbitrary database access.
A compromise of one operation must not automatically grant bulk database
authority.

## Replaceable Runtime

The canonical runtime and availability semantics are defined by
[Subsystem Runtime Architecture](subsystem-runtime-architecture.md) and
[Availability Architecture](availability-architecture.md). Security consumes
those architectures rather than defining a separate security-only failover
mechanism.

Textus favors immutable release artifacts and replaceable Domain Subsystem
instances. Memory-resident caches are allowed for performance but are
reconstructable; authoritative persistent state remains outside the disposable
runtime instance.

Active mutation authority and Standby read-only/takeover authority are explicit.
A Standby does not gain mutation rights merely because it is reachable or
healthy. Regular planned Active/Standby rotation continuously exercises
readiness, switch, drain and reconstruction paths, reducing the risk that an
emergency Standby fails only when first needed.

The preferred externally exposed Application Tier is Function-like/ephemeral
where practical and reaches Domain Subsystems through bounded Operations rather
than direct DataStore authority. This limits persistence in the public-facing
tier and hides Domain Subsystem replacement from clients.

Runtime replacement is containment and recovery, not proof of cleanliness and
not continuous Moving Target Defense. It does not repair stolen credentials,
malicious persistent data or compromised upstream authority.

Security must not reintroduce management hashes, repeated artifact comparison,
whole-tree copies, bespoke rollback machinery, or speculative corruption checks
without an independently justified requirement. Published/distributed artifact
integrity mechanisms may be used where the distribution threat model requires
them.

## Development and Supply Chain

Developer, vendor, AI and local-LLM output enter through the same engineering
quality boundary. Authorship is not assurance.

sm-workflow owns deterministic development workflow and evidence progression.
CAR lint and CBD Support Review provide bounded static/semantic quality
evidence. Admission/release decisions consume explicit evidence rather than
trusting a tool, vendor, model, or successful compilation alone.

Security review must remain proportional. It must detect unsafe authority,
exposure, secret handling, injection-prone boundaries and dependency/supply
chain risks without encouraging speculative defensive machinery.

## Observability, Detection and Response

Security-relevant operations should produce structured operational evidence
through the normal CNCF/OTLP observability route. Textus Control Center composes
runtime, subsystem and infrastructure signals for diagnosis and response.

Logs and traces must not become a second sensitive-data store. Credentials,
tokens and unnecessary personal data must be redacted or omitted.

Incident response prefers containment, authority revocation where applicable,
replacement of untrusted runtime instances, and recovery from authoritative
persistent state over attempting to prove an arbitrary running instance clean.

## Project Responsibilities

| Project / subsystem | Security responsibility |
| --- | --- |
| CNCF | Canonical trust model; SecuritySubject; authorization; Capability/Guard; Operation/Entity/DataStore boundaries; runtime security primitives |
| Cozy | Generate applications and CAR metadata that preserve CNCF boundaries; reject unsafe generation patterns where deterministically knowable |
| sm-workflow | Development/review/admission workflow, evidence and human decision boundaries |
| textus-cbd-support | CAR Review security/quality policy and canonical review findings |
| Textus Control Center | Security-relevant operational visibility, diagnosis and response presentation |
| textus-orchestrator | Least-authority delegation to AI/agents/tools and explicit human/automation authority boundaries |
| Applications | Data classification, domain-specific policy, exposed operations, retention and deployment-specific controls |

## Continuous Governance

Security architecture is continuously maintained:

```text
new threat / incident / design finding
  -> project journal
  -> architecture assessment
  -> canonical CNCF design update when Textus-wide
  -> affected project design/notes
  -> implementation phase and review rules when needed
  -> operational feedback
```

Project journals preserve why a decision was made. Project design documents
state durable responsibilities. Notes hold implementation proposals and open
questions. This document remains the canonical Textus-wide architecture.

## Initial Follow-up Topics

The initial architecture intentionally leaves implementation detail to later
work. Follow-up should cover at least:

- Capability/Guard model consolidation and general ABAC evaluation;
- authentication ingress and external IdP/passkey integration profiles;
- sensitive-data classification, retention, tokenization and key ownership;
- subsystem/service credential and secret lifecycle;
- immutable CAR deployment and replaceable runtime lifecycle;
- security event taxonomy and Control Center incident views;
- security checks assigned to CAR lint versus CBD Review versus deployment;
- dependency/SBOM and release/distribution assurance where justified;
- backup, recovery and destructive-operation authorization.
