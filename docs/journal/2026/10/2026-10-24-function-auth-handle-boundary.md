# Function AuthHandle Boundary

Date: 2026-10-24

## Decision

Application-tier Cloud Functions remain lightweight orchestration and
availability/security boundaries. They do not reproduce the full CNCF
authentication runtime and do not receive direct User DB authority.

The centralized User Management / identity domain resolves admitted external
OAuth/OIDC credential evidence and returns an opaque, bounded-lifetime
`AuthHandle`. The Application Function may cache the credential-to-AuthHandle
mapping under expiry/revocation policy and reuse one handle while orchestrating
multiple Domain Components.

A Domain Function API receives the AuthHandle, Function/service identity,
Operation request and observability context. Domain ingress resolves the handle,
constructs the authoritative `SecurityContext` / `ExecutionContext`, applies
Capability/Guard authorization, and invokes the ordinary Operation path.

Function-local prechecks and caching are DDoS/load-shedding optimizations, not
authorization authority. Function autoscaling must be decoupled from bounded
User Management and Domain capacity through admission/backpressure.

## Architectural consequences

- authentication/User identity resolution is centralized;
- authorization remains Domain-local;
- AuthHandle is opaque and is not UserId, OAuth token, roles, capabilities, or
  serialized ExecutionContext;
- Function code has no User DB credential solely for context construction;
- the same AuthHandle can support one Application Operation that coordinates
  multiple Domains;
- Domain-side AuthHandle resolution may use a bounded trusted cache;
- business Operations remain unaware of Function-specific authentication
  transport.

## Related contracts

- `docs/design/textus-security-architecture.md`
- `docs/journal/2026/04/execution-context-securitycontext-session-integration-note.md`
- `docs/phase/phase-75.md`
- `docs/phase/phase-92.md`
