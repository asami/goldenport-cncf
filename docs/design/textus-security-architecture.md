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

Textus favors immutable release artifacts and replaceable runtime instances:

```text
admitted CAR / configuration
        -> runtime instance
        -> discard and recreate when replaced or untrusted

persistent Entity/DataStore state remains outside the disposable instance
```

This is an operational recovery and containment principle, not a requirement
for continuous Moving Target Defense. Runtime recreation does not by itself
protect persistent data, credentials, or compromised upstream authority.

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
