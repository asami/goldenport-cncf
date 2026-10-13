# Phase 107: Safe-by-Construction Application Security

status=planned
planned_at=2026-10-14
depends_on=[Phase 100]
related=[Phase 95, Phase 96, Phase 103, Phase 106]
consumers=[Cozy Phase 83, textus-cbd-support Phase 16, sm-workflow Phase 6]
authority=[Textus Security Architecture](../design/textus-security-architecture.md)

## Goal

Implement the CNCF primitives required for the Textus safe-by-construction
application path. Ordinary application code must avoid SQL injection, XSS,
CSRF, mass assignment and authorization-call omission without relying on each
handler to remember security boilerplate.

This Phase does not build a WAF, identity provider or generic security scanner.

## Work

### P107-01 Typed DataStore query boundary

Confirm all ordinary Entity/Repository/DataStore query and mutation paths bind
application values as parameters. Define the public typed expression boundary
and identify any raw/native SQL route as an explicit escape hatch. Do not add
SQL-string validation machinery as a substitute for parameter binding.

### P107-02 Display/output security types

Define CNCF logical/runtime carriers needed by Display Model for plain text,
trusted markup and URL/redirect targets. Ordinary text remains untrusted text;
conversion to trusted markup requires an explicit trust-producing operation.
Keep rendering-context escaping in the Web/UI owner rather than teaching domain
objects HTML syntax.

### P107-03 Operation Web security metadata

Expose sufficient Operation metadata to distinguish safe retrieval from
mutation and to declare browser security policy. Mutation Operations cannot be
normally projected as GET. Define the runtime/adapter contract by which
cookie-authenticated browser mutations receive CSRF protection before handler
execution.

### P107-04 Declared input binding

Freeze request -> Operation Input -> command binding so external properties
cannot implicitly mutate persistent Entity fields. Preserve explicit codecs and
logical types; do not deserialize arbitrary request objects directly into
Entities.

### P107-05 Authorization-before-handler invariant

Consolidate the existing SecuritySubject/entity authorization path with
Capability/Guard semantics so exposed Operations cannot enter business handlers
before required authorization evaluation. Record remaining general-ABAC gaps
rather than duplicating authorization in Web adapters.

### P107-06 Sensitive types and escape-hatch metadata

Define minimal common metadata/classification for security-sensitive values and
Security Escape Hatches. Initial escape classes cover raw SQL, trusted markup,
external redirects and direct filesystem/network/process/native/deserialization
authority. The metadata must be consumable by CAR lint/CBD Review.

## Acceptance

- representative malicious SQL values remain bound data, not executable SQL;
- ordinary Display text containing markup/script syntax is rendered as text;
- trusted markup cannot be obtained from arbitrary untrusted String through the
  ordinary public path;
- browser mutation Operations require the declared CSRF policy and GET mutation
  projection is rejected;
- undeclared request fields cannot mutate Entity state;
- representative exposed Operations prove authorization occurs before handler
  execution;
- escape-hatch usage is statically attributable and reviewable;
- no management hash, duplicate integrity machinery or speculative corruption
  checking is introduced.

## Handoff

Cozy Phase 83 consumes the resulting ABI to generate safe Web/application
surfaces. CBD Support Phase 16 consumes escape/boundary metadata for review.
sm-workflow Phase 6 consumes their evidence through the existing review route.
