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
Action to remember security boilerplate.

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
cookie-authenticated browser mutations receive CSRF protection before Action
execution.

### P107-04 Declared input binding

Freeze request -> Operation Input -> command binding so external properties
cannot implicitly mutate persistent Entity fields. Preserve explicit codecs and
logical types; do not deserialize arbitrary request objects directly into
Entities.

### P107-05 Operation and Entity authorization enforcement invariant

Consolidate the existing SecuritySubject/entity authorization path with
Capability/Guard semantics so exposed Operations cannot enter business application Actions
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
- representative exposed Operations prove authorization occurs before Action
  execution;
- escape-hatch usage is statically attributable and reviewable;
- no management hash, duplicate integrity machinery or speculative corruption
  checking is introduced.

## Handoff

Cozy Phase 83 consumes the resulting ABI to generate safe Web/application
surfaces. CBD Support Phase 16 consumes escape/boundary metadata for review.
sm-workflow Phase 6 consumes their evidence through the existing review route.


## Existing authorization chokepoints — implementation baseline

Phase 107 does not introduce a new Entity authorization framework. CNCF already
has two distinct enforcement boundaries:

1. Operation authorization before the authorized ActionCall/Action execution
   route is entered.
2. Entity/object authorization at the internal DSL -> UnitOfWork boundary,
   interpreted centrally by UnitOfWorkInterpreter / OperationAccessPolicy.

Application Actions using the canonical internal DSL therefore obtain Entity
authorization through the UoW path without adding ad-hoc authorization calls.

Phase 107 must verify and harden this existing architecture rather than duplicate
it. In particular:

- entry Operation authorization does not imply unrestricted Entity access;
- read/update/search/list/batch/relation paths using the standard DSL retain the
  UoW authorization chokepoint;
- public/generated routes do not bypass the internal DSL/UoW path for ordinary
  Entity access;
- ServiceInternal/System access-mode transitions cannot be selected from
  untrusted external input and do not leak broader internal visibility back to
  an unauthorized caller;
- ExecutionContext/SecuritySubject and required authority provenance remain
  correct across the Action/UoW and relevant async/continuation boundaries.

Representative connected acceptance starts with: a subject can create/read/
update/list/search its authorized Material, changing only the target identity to
another subject's Material is denied/not visible, and internal-service
delegation does not widen the external caller's effective result unless an
explicit bounded proxy operation authorizes it.

## Material consumer acceptance — 2026-11-02

The [UC-01..04 contract](../spec/untrusted-client-material-acceptance.md) is
a concrete consumer profile, not another authorization framework.
P107-05 owns the common UC-02/04 enforcement and bounded internal delegation;
P107-04 covers untrusted authority-bearing input. UC-01 credential distribution
and UC-03 Material admission retain their Core/TKL owners.

- [ ] P107-05 consumer acceptance: public Operation denial prevents protected
  execution; allowed Operation plus forbidden Entity denies disclosure/mutation;
  read/update/list/search/original-access paths preserve caller scope through
  service delegation. Include authorized controls and downstream state checks.
- [ ] Hand actual common capability/evidence and any gaps to Studio Phase 5,
  App 6.10/7 and TKL. Those consumers need not wait for unrelated Phase 107 work;
  no application-local replacement authorization engine is introduced.

Existing implementation and prior evidence remain reusable at their actual
scope. These additions claim no completed tests or new Phase acceptance.

## Small-business baseline allocation — 2026-11-02

Deliver the [standard/configurable/external contract](../spec/small-business-security-baseline-and-options.md)
through P107-01..06 and existing consumer work, not a separate security engine.

- [ ] SB-01: the selected standard application/deployment path enables required
  authentication, Operation/resource and safe input/output boundaries without
  customer-written guards or expert settings; reuse applicable UC evidence.
- [ ] SB-02: supported policy choices consistently strengthen the baseline;
  missing capabilities/prerequisites cannot masquerade as active protection.
- [ ] SB-03: document actual defaults, deployment/operator duties and external
  equipment limits in business terms; standard-use acceptance does not require
  every optional strong-authentication or physical-isolation feature.

Preserve existing Step IDs and acceptance. Shared policy contracts belong in
CNCF; domain policy, identity and physical deployment remain with their owners.
