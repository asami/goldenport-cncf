# Phase 53 - CML ComponentStyle, ExecutionContext, and Capability Resolution

status=planned
planned_at=2026-07-30
depends_on=[Phase 52](phase-52.md)
strategy=[CNCF Development Strategy](../strategy/cncf-development-strategy.md)
checklist=[Phase 53 Checklist](phase-53-checklist.md)

## Purpose

Let CML select a CNCF-provided `ComponentStyle`, project its provided
ComponentCapabilities and required SubsystemCapabilities, and verify that the
assembled Subsystem can satisfy that contract.

Phase 53 introduces a versioned CNCF ComponentStyle catalog containing
CNCF-supplied built-in styles. Its metadata contract reserves provider identity
and an extension boundary so a future Metadata Factory can contribute
additional styles, but Metadata Factory registration/discovery is not
implemented in Phase 53. CML selects a CNCF style by identifier; it does not
redefine the style or copy its policy parameters.

Operating differences are resolved outside Component execution. Web ingress
selects `WebApplicationMode`; the Subsystem selects user-context and datastore
providers; CNCF then constructs the same mode-free `ExecutionContext` contract
for every Component invocation. A Component implementation does not receive,
inspect, or branch on operating mode.

ArtScene is the first real consumer. It selects
`full-fledged-with-standalone`, keeps its full user-scoped domain model in both
standalone and multi-user operation, and receives either a fixed or
authenticated current user through `ExecutionContext`.

## Dependency

Phase 53 begins after Phase 52 closes.

Phase 53 does not change Phase 52 Entity or collection identity. It resolves a
ComponentStyle and its parameters before datastore initialization; it does not
derive Entity ownership or datastore identity from an Entity ID.

## Problem Statement

ArtScene currently has private mode and datastore-policy authorities inside the
Component boundary. That lets launcher, Web, ComponentFactory, and domain
execution disagree and forces Component code to understand deployment shape.

Phase 53 creates one declaration and resolution path:

```text
CNCF built-in ComponentStyle catalog
  -> explicit CML COMPONENT style selection
  -> Cozy typed style reference
  -> development and packaged component descriptor
       -> provided ComponentCapabilities
       -> required SubsystemCapabilities
  -> root implicit or explicit Subsystem assembly
  -> stable Subsystem identity and mode-free ExecutionProfile
  -> WebApplication profile when ingress is Web
  -> capability matching and provider selection
  -> resolved SecurityContext, formatting, datastore, and typed policies
  -> ExecutionContext
  -> mode-free Component execution
```

## Selected Direction

### Style and capability contract

`ComponentStyle` is a versioned CNCF catalog entry describing a standard
bundle of capabilities a Component provides and Subsystem facilities it
requires. It is not an operating-mode selector or an untyped policy bag.

The initial ArtScene style `full-fledged-with-standalone` provides:

- `domain.full@1`;
- `user.multi-user@1`; and
- `user.fixed-context-compatible@1`.

It requires:

- `user-context.current@1`;
- `datastore.persistent@1`;
- `datastore.transactional@1`; and
- `datastore.optimistic-concurrency@1`.

The name expresses a full-fledged, multi-user-capable domain Component that
also runs unchanged when CNCF supplies one fixed current user.

Detailed capabilities remain structured. `domain.full@1` is a versioned bundle
of Entity, Aggregate, Command, Query, domain-event, projection, persistence,
transaction, and optimistic-concurrency capabilities.

### Mode ownership and permanent Component boundary

Phase 53 does not introduce `ApplicationMode`, `ComponentMode`, or
`SubsystemMode` as a permanent Component execution contract.

`OperationMode` continues to describe execution posture such as `production`,
`demo`, `develop`, and `test`. It is resolved at the launcher/runtime boundary
and is not exposed to Component domain logic.

`WebApplication` remains the canonical name for the Web application context,
and `WebApplicationMode` remains the Web-specific `standalone` / `multi-user`
contract. It controls Web ingress and supplies Web-specific identity evidence
to the Subsystem. The Subsystem resolves the user-context provider.
`WebApplicationMode` is not forwarded into Component execution and is not
required by non-Web ingress.

Permanent Component APIs, generated DSLs, ComponentFactory hooks, ActionCall
implementations, and domain policies must not receive or expose an operating
mode, read mode/configuration keys, construct a standalone UserId, select a
datastore from mode, or branch on fixed versus authenticated user origin.

Components consume only resolved facts through `ExecutionContext`, including
current principal/user, authorization, locale/timezone, datastore and
EntityStore bindings, UnitOfWork/transaction semantics, resource access, and
typed mode-free policies.

### CNCF style catalog and future Metadata Factory extension

CNCF supplies built-in ComponentStyles and their canonical identifiers. The
Phase 53 metadata schema contains the information a future Metadata Factory
would need to contribute another style:

- unique style identity and provider identity;
- provided capability bundles and their deterministic expansion;
- required Subsystem capabilities;
- typed, mode-free parameter schema where needed; and
- implementation-evidence metadata.

Duplicate built-in identities, provider/schema disagreement, or a style missing
from the generation/runtime catalog fail structurally. Cozy and CNCF consume
the same metadata contract. The generated descriptor snapshots the resolved
style metadata so development and packaged launch do not depend on runtime CML
parsing or an unversioned registry lookup.

Phase 53 does not implement Metadata Factory registration, discovery,
dependency packaging, or conflict resolution. Those are recorded as a future
development item after the built-in contract is verified.

### CML and descriptor authority

The explicit CML `COMPONENT` declaration selects one ComponentStyle. CML does
not define new style metadata, select WebApplicationMode, configure a fixed
user, or choose a datastore. `project.yaml` does not duplicate the selection.

Cozy resolves and validates the style against the common metadata catalog and
generates the typed reference and metadata snapshot. sbt-cozy makes the same
projection available to source-directory launch. CNCF runtime consumes the
generated descriptor and does not parse CML.

Development and packaged descriptors generated from the same CML must be
semantically equivalent and schema-versioned.

The descriptor contains style identity, provider/schema identity, deterministic
provided-capability expansion, and required Subsystem capabilities. It contains
no Component mode, fixed UserId, locale, or datastore policy.

### Subsystem capability matching and ExecutionContext absorption

The explicit or implicit Subsystem resolves the complete Component dependency
closure and must satisfy every required capability before activation. Provider
metadata, not Component-local names or mode strings, supplies matching
evidence.

Standalone and multi-user operation construct the same Component-facing
contract:

```text
standalone Web, CLI, job, or other fixed-user ingress
  -> Subsystem user-context-provider resolution
  -> ~/.textus FixedUserProfile common/subsystem baseline
  -> always-admitted ~/.cncf FixedUserProfile common/subsystem override
  -> SecurityContext + formatting + datastore bindings
  -> ExecutionContext

multi-user Web or another authenticated ingress
  -> authenticated invocation identity
  -> Subsystem user-context-provider resolution
  -> ignore both FixedUserProfile documents
  -> authenticated user preferences
  -> SecurityContext + formatting + datastore bindings
  -> ExecutionContext
```

Standalone therefore retains multi-user domain semantics with one fixed
current user. Multi-user operation never falls back to fixed-user
configuration.

`~/.textus/user-profile.yaml` is the normal-operation `FixedUserProfile`
baseline. `~/.cncf/user-profile.yaml` is an always-admitted
higher-precedence overlay using the same schema. Admission is not gated by
`OperationMode`; both documents participate only in fixed-user resolution and
are ignored by multi-user operation. Each document supports common values and
field-by-field `subsystems` overrides keyed by the stable Subsystem identity.
PROJECT and CWD FixedUserProfile documents are not admitted.

Common-field environment and argument inputs retain their existing precedence.
A controlled runtime/test injection uses the distinct
`ConfigurationOrigin.ExplicitOverride`. Resolved fields retain source path or
input identity, `.textus`/`.cncf` layer, common/subsystem target, stable
Subsystem identity, logical field, override history, and final source through
the existing `ResolvedConfiguration`/`ConfigurationTrace` authority.
Component execution receives only effective values.

The canonical public Web operation parameter is
`textus.web.application-mode`. It is resolved before FixedUserProfile. For a
normal direct-Component launch, CNCF contributes a traceable `standalone`
default only when no explicit value wins, the implicit Subsystem has a valid
standalone ExecutionProfile, and the Component provides
`user.fixed-context-compatible@1`. FixedUserProfile does not contain the Web
operation selection.

The fixed UserId is stable across restart; changing an ID that owns persisted
data is a user-data migration. An overriding UserId requires isolated data
unless migration is intentional.

### ComponentFactory, datastore, and migration boundary

ComponentFactory may provide typed, side-effect-free Component parameters and
implementation evidence for domain capabilities. It may not receive mode,
choose a user-context provider or datastore, read FixedUserProfile
configuration, or
return mode-specific parameters.

Datastore placement, provider, endpoint/path, credentials, pool lifecycle, and
migration lifecycle belong to the Subsystem. Both operation profiles expose
the same mode-free datastore interfaces to Component execution.

Temporary migration adapters, if unavoidable, remain internal and deprecated,
carry an explicit removal item, and never enter CML, catalog metadata,
generated DSLs, permanent Component APIs, or normative specification.

### Development-directory use

Phase 53 consumes the existing `cozyPrepareRuntime` route. It extends that
route with the ComponentStyle and implicit Subsystem projection while
preserving its ownership of
`target/cncf.d/runtime-classpath.txt`,
`target/cncf.d/car-runtime-manifest.json`, and related extensible evidence. A
source-directory launch does not require `buildCar`.

Missing, inconsistent, or stale development evidence fails with an actionable
diagnostic. CNCF must not silently select an older locally published CAR.

## ArtScene Acceptance

Its Phase 53 adoption:

- selects `full-fledged-with-standalone` in explicit-component CML;
- advertises the full domain and user capabilities in its generated
  descriptor;
- removes private Component mode, `local-default`/`external-required`
  datastore policy, hardcoded standalone user, and mode branches;
- uses the same Component operations for fixed and authenticated current
  users;
- resolves the current user's locale through ExecutionContext;
- uses Subsystem-owned local or shared datastore bindings; and
- verifies both development-directory and packaged-CAR launches.

The acceptance matrix is:

| OperationMode | WebApplicationMode | User context | Component mode input | Datastore expectation |
| --- | --- | --- | --- | --- |
| `develop` | `standalone` | fixed user with common/subsystem overlay | none | isolated persistent local development binding |
| `production` | `standalone` | fixed user with common/subsystem overlay | none | user-owned persistent local binding |
| `develop` | `multi-user` | authenticated user | none | explicit shared development binding |
| `production` | `multi-user` | authenticated user | none | explicit shared production binding |

A development standalone launch must not reuse or overwrite production
standalone data implicitly.

## Work Groups

| Group | Outcome |
| --- | --- |
| CS-01 | Inventory current style, mode, policy, metadata, descriptor, runtime, HTTP, launcher, and ArtScene authorities; register failing-first behavior. |
| CS-02 | Implement the CNCF built-in style catalog, extension-ready metadata contract, CML style selection, typed model, validation, and deterministic descriptor projection. |
| CS-03 | Extend the existing `cozyPrepareRuntime` route with coherent development descriptor/implicit-Subsystem evidence and packaged/development parity. |
| CS-04 | Implement Subsystem capability matching, fixed/authenticated ExecutionContext construction, and the WebApplication boundary. |
| CS-05 | Implement fixed-user overlays, Subsystem datastore selection, trace provenance, diagnostics, and launcher selection. |
| CS-06 | Adopt the CNCF component style in ArtScene and pass the full operating-mode matrix. |
| CS-07 | Run full cross-repository validation and promote verified behavior into normative design/specification. |

## Documentation Lifecycle

The planning contract is
[CML ComponentStyle, ExecutionContext, and Capability Specification Proposal](../notes/cml-component-style-execution-context-capability-specification-proposal.md).
It is intentionally a non-normative note.

The selected planning decisions are consolidated in
[Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation](../journal/2026/07/2026-07-30-phase-53-component-style-execution-context-configuration-consolidation.md).

Phase 53 does **not** create or revise normative `docs/design` or `docs/spec`
before implementation. CS-01 through CS-06 use the note plus failing-first
Executable Specifications. CS-07 writes normative documents only from
implemented, reviewed, and verified behavior.

Phase 53 cannot close while the final contract exists only in this note, the
consideration journal, phase documents, or tests.

## Repository Scope

| Repository | Responsibility |
| --- | --- |
| `cozy` | CML selection of CNCF built-in styles, common style metadata consumption, typed model, validation, and descriptor projection |
| `sbt-cozy` | extension of the existing `cozyPrepareRuntime` descriptor/runtime-evidence and freshness contract |
| `simplemodeling-lib` | existing String-keyed configuration/trace authority, `ConfigurationOrigin.ExplicitOverride`, and minimal source metadata required by Phase 53 |
| `cloud-native-component-framework` | built-in style catalog, capability matching, descriptor consumption, stable Subsystem identity, FixedUserProfile parsing/semantic resolution, fixed/authenticated ExecutionContext construction, Web-operation resolution/default contribution, and diagnostics |
| `cncf-launcher` | runtime artifact selection, exact argument forwarding, and source-directory launch |
| `textus-launcher` | runtime artifact selection and exact argument forwarding without duplicating CNCF configuration semantics |
| `textus-art-scene` | first production-shaped declaration and acceptance consumer |

`simplemodeling` is admitted only if CS-01 establishes that the affected CML
semantic model is owned there. Additional sample repositories require an
explicit Phase 53 acceptance reason.

## Scope

In scope:

- common typed ComponentStyle and capability identities;
- CNCF built-in style catalog and an extension-ready metadata contract;
- explicit-component CML style selection and generation validation;
- schema-versioned development and packaged descriptor projection;
- Subsystem requirement/provider capability matching;
- fixed and authenticated user-context resolution into one ExecutionContext
  contract;
- `~/.textus` FixedUserProfile baseline and always-admitted field-by-field
  `~/.cncf` higher-precedence override for fixed-user resolution;
- common/subsystem overlay using stable Subsystem identity;
- HOME-only profile-file admission, common-field environment/argument input,
  and controlled explicit runtime/test override;
- generic `.textus` baseline followed by `.cncf` override at every admitted
  HOME/PROJECT/CWD scope, with source admission owned by each typed contract;
- one canonical `textus.*` spelling for each public Phase 53 semantic even
  when `.cncf` supplies the winning value, without a duplicate `cncf.*`
  semantic;
- existing `ResolvedConfiguration`/`ConfigurationTrace` authority with the
  minimal provenance completion required by Phase 53;
- canonical `textus.web.application-mode` resolution and traceable
  conditional direct-Component standalone default;
- OperationMode and WebApplicationMode exclusion from Component execution;
- Subsystem-owned datastore selection and configuration trace;
- common runtime access and Web projection;
- launcher selection and stale development-evidence rejection;
- ArtScene standalone and multi-user adoption; and
- post-verification normative design/specification promotion.

Out of scope:

- Component-visible `ApplicationMode`, `ComponentMode`, `SubsystemMode`,
  `WebApplicationMode`, or `OperationMode`;
- Component-side branching on standalone/multi-user or principal origin;
- making locale or timezone inherent to `standalone`;
- an untyped arbitrary profile property bag;
- arbitrary runtime creation or mutation of ComponentStyle definitions;
- Metadata Factory style registration, discovery, dependency packaging, and
  conflict resolution, which are a future development item;
- ComponentFactory selection of user-context provider or datastore;
- duplicate declaration in `project.yaml`;
- runtime CML parsing;
- permanent compatibility APIs for the removed Component-private modes;
- unrelated datastore pool lifecycle work planned for Phase 54;
- Entity/collection identity changes;
- a general component/subsystem/user data migration framework;
- migration, deletion, or reinterpretation of unrelated CNCF operational state;
- typed generic configuration keys, generic qualifier/candidate resolution,
  namespace catalogs, general alias normalization, and new
  binding/environment codecs planned for Phase 55; and
- normative design/specification written from unverified proposal behavior.

## Completion Rules

Phase 53 closes only when:

- every CS work group is DONE;
- CNCF built-in style discovery and descriptor projection have executable
  evidence;
- capability expansion and requirement/provider matching have executable
  evidence;
- development and packaged descriptors have proven semantic parity;
- invalid composition fails before component/runtime resource startup;
- `~/.textus` baseline, always-admitted `~/.cncf` override,
  common/subsystem target, source-admission rules, and field-level provenance
  through the existing trace authority are verified;
- FixedUserProfile is ignored completely by multi-user resolution;
- `textus.web.application-mode` resolves before FixedUserProfile and the
  conditional direct-Component standalone default is traceable;
- `WebApplicationMode` remains inside the WebApplication boundary and no
  operating mode reaches Component execution;
- fixed-user and authenticated-user construction expose the same
  Component-facing ExecutionContext contract;
- ArtScene passes the full OperationMode/WebApplicationMode acceptance matrix
  with no Component mode input;
- source-directory and packaged-CAR launch both pass;
- full tests pass in every modified required repository;
- clean read-only review passes, with focused re-review only after actionable
  findings were fixed; and
- verified design/specification and user/operation documentation replace this
  proposal as the final authority.

## References

- [Phase 53 Checklist](phase-53-checklist.md)
- [CML ComponentStyle, ExecutionContext, and Capability Specification Proposal](../notes/cml-component-style-execution-context-capability-specification-proposal.md)
- [Phase 53 ComponentStyle, ExecutionContext, and Configuration Consolidation](../journal/2026/07/2026-07-30-phase-53-component-style-execution-context-configuration-consolidation.md)
- [CML Application Mode Capability Consideration](../journal/2026/07/2026-07-30-cml-application-mode-capability-consideration.md)

## Next Step

Phase 53 is planned and must not start before Phase 52 closes.

After Phase 52 closes, start CS-01 with a cross-repository authority and
generation-path inventory. Do not write normative design/specification during
CS-01; first register executable failures against the notes proposal.
