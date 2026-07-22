# Configuration Model and Platform Compilation Architecture
## for Cloud Native Component Framework (CNCF)

status = draft
audience = architecture / platform / tooling
scope = system-dsl / config-dsl / model / platform-compilation

---

## 1. Motivation

In CNCF, systems are composed from Components and Subsystems,
and deployed onto cloud platforms such as AWS ECS, Lambda, or Kubernetes.

A naive approach would treat configuration files
as simple key–value property stores, directly accessed by runtime code.

CNCF explicitly rejects this approach.

> **Configuration is not “data to be read”  
> but “a model to be constructed.”**

This document defines how CNCF:
- separates logical structure from execution details,
- normalizes configuration into a semantic model,
- and compiles that model into platform-specific DSLs (e.g. CDK).

---

## 5. Configuration Propagation Model

This section consolidates the **propagation story** for CNCF’s runtime layers.
It defines how configuration is resolved, structured, and accessed within
CNCF’s runtime layers.

### Purpose

This document defines how configuration is **resolved, structured, and propagated**
within the CNCF runtime during Phase 2.x.

The goal is to:

- Keep configuration semantics **explicit and layered**
- Avoid premature abstraction (e.g. `ApplicationConfig`)
- Align configuration ownership with **deployment and execution boundaries**

---

### Core Principles

#### 1. Deployment Unit First

- The **primary deployment and execution unit is `Subsystem`**
- Configuration resolution, lifecycle, observability, and scaling are all scoped to a Subsystem

> Therefore, configuration modeling starts from **Subsystem**, not Application.

---

#### 2. No ApplicationConfig (for now)

`ApplicationConfig` is **intentionally not defined** in Phase 2.x.

Reasons:

- “Application” is an abstract composition concept
- No concrete lifecycle or deployment boundary exists for Application
- Introducing it early blurs ownership and responsibility

Application may later be defined as a **composition of Subsystems**,
but it is **not a configuration root**.

---

#### 3. Runtime Selection Keys

Runtime selection uses `textus.*` keys without the intermediate `runtime`
segment when the key directly selects a model element.

Primary keys:

- `textus.component`
- `textus.subsystem`
- `textus.component.dev.dir`
- `textus.component.car.dir`
- `textus.component.file`
- `textus.subsystem.descriptor`
- `textus.subsystem.file`
- `textus.subsystem.dev.dir`
- `textus.subsystem.sar.dir`
- `textus.id.namespace.major`
- `textus.id.namespace.minor`

Selector-less development startup uses the directory keys directly:

- `textus.component.dev.dir`
  - sbt/cozy component development directory, equivalent to
    `--component-dev-dir`
- `textus.subsystem.dev.dir`
  - application/subsystem development root, equivalent to
    `--subsystem-dev-dir`
- `textus.component.car.dir` and `textus.subsystem.sar.dir`
  - expanded archive directories for CAR/SAR loader debugging
- `textus.id.namespace.major` and `textus.id.namespace.minor`
  - operational runtime namespace for new `EntityId` generation
  - default to `single` and `global`
  - override for customer, tenant, district, region, or site partitioning

The older `textus.runtime.*` forms are compatibility aliases only.
The older `cncf.*` forms are also compatibility aliases while command lines and
samples converge on the Textus namespace.

CozyTextus is the product name and CNCF is the implementation name. This means
`textus.*` is the normal namespace for product-facing configuration, while
`cncf.*` may still be used intentionally for hidden implementation controls,
diagnostics, development hooks, and backward compatibility. A `cncf.*` key
should be migrated to `textus.*` only when it is meant to be ordinary
CozyTextus application configuration.

---

### Configuration Layers

Configuration is structured as **owner-centric nested models**.

```
System.Config
   ↓
Subsystem.Config
   ↓
Component.Config
```

Each layer:

- Lives next to its owning runtime object
- Receives a CNCF-owned semantic projection appropriate to its lifecycle
- Does **not** mutate or re-resolve configuration

---

#### System.Config

**Scope**: Entire runtime / process  
**Owner**: Runtime (e.g. `CncfRuntime`)

Responsibilities:

- Execution environment assumptions
- Observability defaults
- Global runtime behavior

Examples:

- environment name
- observability flags
- logging / tracing modes
- clock / timezone / locale assumptions

Characteristics:

- Created once per runtime
- Independent of Subsystem count
- Passed *implicitly* to lower layers (via execution context, not as raw config)

---

#### Subsystem.Config

**Scope**: Deployment unit  
**Owner**: `Subsystem`

Responsibilities:

- How the subsystem is run
- How it communicates
- What role it plays

Examples:

- HTTP driver
- Run mode
- Datastore / event backend selection
- Subsystem-level capabilities

Characteristics:

- Built from `ResolvedConfiguration`
- Applicative style (error accumulation)
- Lives as `Subsystem.Config`
- Subsystem is the **root of semantic configuration**

---

#### Component.Config

**Scope**: Behavioral unit  
**Owner**: `Component`

Responsibilities:

- Component-specific behavior overrides
- Fine-grained runtime tuning

Examples:

- Component HTTP driver override
- Component run mode
- Feature toggles

Characteristics:

- Optional values
- Defaults inherited from Subsystem behaviorally (not structurally)
- Initialization values are projected from declared typed initialization
  parameters
- Operation-time values remain available only through declared
  `ComponentConfigurationAccess`

---

### Configuration Resolution

#### ResolvedConfiguration

- Produced by `ConfigurationResolver`
- Flat key/value store with trace
- No semantics, no validation

#### Semantic Builders

Runtime and subsystem assembly may define CNCF-owned semantic builders over
the raw resolved configuration:

```scala
Subsystem.Config.from(conf: ResolvedConfiguration): Consequence[Subsystem.Config]
```

Component initialization is not another raw semantic builder. CNCF first
selects the component-instance context and resolves its declared typed
initialization parameters. The component-owned projection receives that typed
snapshot, not `ResolvedConfiguration`.

Properties:

- **Applicative style** (`mapN`)
- Explicit defaults
- Explicit required keys
- No side effects
- No cascading construction

### Component Initialization Parameter Resolution

Component initialization parameter resolution is a semantic projection owned
by CNCF. It starts only after configuration source resolution and assembly have
identified the component type and `ComponentInstanceId`, and it completes
before component-specific construction or initialization may consume the
values.

The lifecycle is:

```text
configuration source resolution
  -> ResolvedConfiguration
  -> component and component-instance context selection
  -> neutral Component/Core allocation and final participant identity
  -> declared initialization parameter resolution
  -> component-owned typed initialization projection
  -> component-specific initialization
  -> component installation
```

The neutral `Component` and `Component.Core` allocation step establishes the
final `ComponentId`, `ComponentInstanceId`, and participant role. It MUST NOT
consume declared initialization values. Factories declare keys through
`initializationParameterDeclarations`; `createPrimaryC` and
`createComponentletC` resolve those declarations only after final identity is
known and deliver the resulting snapshot in `ComponentInit`. Component-domain
initialization uses the consequence-aware `initialize_component_c` hook.
Legacy non-`C` methods remain source-compatibility wrappers; CNCF runtime
admission uses the consequence-aware path.

`ResolvedConfiguration` remains a raw resolved key/value store with source
trace. It does not know component parameter declarations, decode
component-domain values, select a component instance, or validate combinations
of component parameters. The initialization resolver receives only explicitly
admitted named layers from configuration and assembly processing; it does not
discover an ambient source or reinterpret the source-resolution precedence.
The exact initialization-layer precedence is a separate component-parameter
contract and does not change the `HOME -> PROJECT -> CWD -> ENV -> ARGS`
precedence used to construct the runtime configuration layer.

CNCF owns:

- component and component-instance context selection;
- admitted initialization-layer precedence;
- typed decoding and required-or-optional semantics;
- safe, bounded provenance;
- structured failure before component installation; and
- delivery through the component factory/bootstrap boundary.

The component owns, through its factory or equivalent component definition:

- the declaration of required initialization parameters;
- projection of generic typed resolutions into its initialization value; and
- validation of component-domain parameter combinations.

The declaration must be available before component-specific construction. A
component must not receive `ResolvedConfiguration`, an untyped configuration
map, or a source-discovery handle as its initialization contract. Failed
resolution or projection leaves no partially initialized component visible in
the subsystem.

The delivered initialization parameters form an immutable snapshot for one
`ComponentInstanceId`. They are distinct from operation-time
`ComponentConfigurationAccess`, which resolves a declared key through the
bound ActionCall runtime context. Neither mechanism is a fallback for the
other, and request parameters, action properties, ambient environment access,
or arbitrary runtime lookup cannot override the initialization snapshot.

Initialization may carry an opaque secret reference but never secret material.
Parameter values, secret references, physical source locations, credentials,
and unrelated configuration keys are absent from default diagnostics and
provenance.

Bootstrap diagnostics use the normal `Conclusion` model rather than a
component-specific error envelope. CNCF applies the
`component-initialization-parameter` policy facet and one bounded reason:
`missing`, `malformed`, `ambiguous`, or `rejected`. The common
`ConclusionDiagnostics` projection derives its diagnostic key from these
facets. Decoder failures are replaced at the initialization boundary so an
arbitrary component decoder cannot place the selected value or a physical
source in the resulting diagnostic.

`ComponentParameterBootstrap` is the lifecycle owner of the
`component-initialization.parameter-resolution` runtime metric. Successful and
absent declarations publish only logical identity, declaration metadata, and
bounded provenance. Failure records add only the common structured diagnostic
projection. Bootstrap has no Action-owned `ExecutionContext`, so this lifecycle
does not create a synthetic CallTree.

Every initialization declaration has a `Public`, `Confidential`, or `Secret`
classification. Ordinary typed constructors are always public. Secret locators
use `ComponentParameterKey.requiredSecretReference` or
`optionalSecretReference` and produce only the non-inspectable
`SecretReference`. A confidential declaration is denied before source lookup
or decoding, so embedded credential material cannot cross the initialization
snapshot boundary. Secret material remains available only to an authorized
runtime-owned provider or driver.

The typed initialization vocabulary is lifecycle-specific:

- `ComponentParameterKey[A]` is a stable declaration identity with a typed
  decoder and required-or-optional policy;
- `ComponentParameterResolver` is a CNCF-owned, context-bound resolver whose
  public operation accepts only a declared key;
- `ComponentParameterResolution[A]` carries the decoded optional value and one
  bounded `ComponentParameterProvenance` category; and
- `ComponentInitializationParameters` is the immutable validated snapshot.

The snapshot resolves only the exact key identities used in its declaration.
It exposes no raw entry collection, map conversion, arbitrary name lookup,
`ConfigurationValue`, or `ResolvedConfiguration`. This identity rule keeps
heterogeneous decoded values type-safe without retaining or re-decoding raw
configuration in component code.

The resolver's source/context lookup is CNCF-protected. A runtime resolver
implementation captures the applicable context and admitted sources; those
inputs are not arguments to component code. Required absence, malformed
decoding, duplicate declaration names, and keys outside the validated snapshot
are structured `Consequence` failures.

The admitted source model uses five named slots rather than a caller-ordered
collection. Their low-to-high order is packaged defaults, component assembly
defaults, subsystem/SAR component-instance settings, resolved runtime
configuration, and explicit test descriptor overlay. Lookup runs in reverse
order and stops at the first present value. Decoding happens after selection,
so an invalid higher-layer value is an error rather than permission to reuse a
lower default.

The runtime slot is a value-only projection from `ResolvedConfiguration`; its
trace and physical source identity do not enter resolver state. The test slot
is projected separately from an explicitly loaded `RuntimeTestDescriptor` and
is empty otherwise. It is not merged into the runtime slot for initialization
parameter provenance. The fixed-slot type has no position for request/action
properties, operation-time configuration, system properties, environment
lookups, arbitrary source readers, or implicit test discovery.

Each assembly-side slot has a distinct CNCF-private source type; three raw
`Configuration` arguments cannot be exchanged while retaining trusted
provenance. Runtime and test values are projected together. Because current
startup configuration resolution includes explicitly selected test descriptor
configuration, the projection removes descriptor-owned keys from the runtime
slot before constructing the separate test-overlay slot. A selected test value
therefore has exactly one initialization provenance even before bootstrap
integration is completed.

`ComponentParameterContext` binds one resolution to one `ComponentId` and one
`ComponentInstanceId`. CNCF selects exactly one packaged
`ComponentDescriptor` that owns the runtime component identity and exactly one
admitted `ComponentInstanceMetadata` record for that instance. Descriptor
`componentName` is the authoritative runtime identity; descriptor `name` is a
fallback only when `componentName` is absent and is otherwise only the CAR
artifact identity. Descriptor ownership includes declared componentlets,
whose runtime component identity is
distinct while their assembly-instance name and settings remain owned by the
same component binding. Missing, ambiguous, or inconsistent descriptor and
instance metadata fail before a parameter layer can resolve.

Only the selected instance metadata contributes the subsystem/SAR instance
layer. The layer cannot be constructed independently from arbitrary
configuration, and a resolver for one named component instance cannot observe
another instance's settings. The context remains CNCF-private and does not
expose descriptor or assembly maps through the component initialization
snapshot.

`ComponentParameterBootstrap` maps the five logical slots from already
admitted runtime state: the selected `ComponentDescriptor.config`, effective
subsystem descriptor `config`, selected `ComponentInstanceMetadata.config`, a
value-only projection of `Subsystem.configuration`, and an explicitly selected
`RuntimeTestDescriptor`. A factory with no declarations receives the canonical
empty snapshot without requiring descriptor or instance context. The same
snapshot is retained by `Component`, passed to ordinary initialization, and
forwarded to special-component initialization. Consequence-aware factory,
bootstrap, and subsystem admission paths preserve failure until installation.
Descriptor-based startup attaches the effective subsystem descriptor before
repository construction and supplies one binding's selected instance metadata
to each repository factory context. Consequently assembly defaults and
instance settings are available during real repository discovery, while a
factory failure remains a structured failure rather than an absent component.

### Declared Component Runtime Configuration

Raw configuration remains an assembly/runtime concern. Component behavior uses
`ComponentConfigurationKey[A]` and the protected
`component_configuration(key)` helper to request a declared, typed value. A
key fixes its requirement, decoder, and confidentiality before a request is
handled.

The runtime resolves one declared key in this order:

```text
component configuration -> subsystem configuration -> runtime configuration
```

The result contains the typed optional value and safe provenance. Action
properties, request parameters, and arbitrary configuration lookups cannot
override the declaration. Missing required values, invalid formats, and
confidential-value requests are structured `Consequence` failures.

Public values use string, integer, boolean, or a caller-supplied typed decoder.
Secret-valued declarations use `requiredSecretReference` or
`optionalSecretReference`; component behavior receives only an opaque
`SecretReference`. It cannot resolve or display the credential value. A
runtime-owned provider or driver may resolve a reference only at its authorized
boundary.

`test.yaml` and `test.json` are explicit test assembly overlays. They may
supply deterministic values for declared keys, but are not auto-loaded and do
not turn action/request input into configuration authority. See
`docs/spec/component-runtime-boundary-capabilities.md` for the normative
component boundary and `docs/spec/test-policy.md` for test use.

---

### Temporal Values and Context

Some configuration values (e.g. time) require execution assumptions.

Rules:

- Temporal parsing uses `TemporalValueReader[T]`
- Requires `ExecutionContext` injection
- Accessed via:

```scala
ResolvedConfiguration.getTemporal[T](key)
```

This ensures:

- Timezone / clock / locale correctness
- Deterministic behavior in tests
- No hidden global state

---

### Tier Model (Non-Configuration)

The following are **classification concepts**, not configuration scopes:

- PresentationTier
- ApplicationTier
- DomainTier

They:

- Do **not** own configuration
- Do **not** participate in resolution
- Are implemented as **Component compositions**

Configuration flows *through* them, not *from* them.

---

### Explicit Non-Goals (Phase 2.x)

- No schema validation framework
- No DSL for configuration
- No dynamic config mutation
- No ApplicationConfig
- No cross-layer implicit propagation

---

### Summary

- Configuration ownership follows **execution ownership**
- Subsystem is the semantic root
- Configuration remains explicit, layered, and composable
- The model is intentionally conservative and evolvable

This design is frozen for Phase 2.x and forms the basis for further refinement.


## 2. Design Principles

### 2.1 Configuration Is an Input Language

Typesafe Config (HOCON) is treated as:
- an **input DSL**, not
- a runtime property bag.

No production code should:
- query configuration paths directly,
- branch on raw strings from config,
- or embed platform logic in config lookups.

---

### 2.2 One-Way Flow

The architecture enforces a strict one-way flow:

```
System DSL
   ↓
Configuration DSL (HOCON)
   ↓
Configuration Model
   ↓
Platform DSL (CDK / Terraform / etc.)
```

There is no reverse dependency.

---

### 2.3 Core Config vs Configuration

CNCF distinguishes between two fundamentally different concepts
that are often conflated under the term “config”.

#### Core Config

Core Config represents **concrete runtime settings required for the core itself**,
such as locale, timezone, logging, encoding, or randomness.

These settings:
- are tightly coupled to core runtime behavior,
- are limited in scope and schema,
- and exist to ensure correct execution of the framework.

Core Config is not intended to describe system architecture.

#### Configuration (Architectural Configuration)

Configuration, in contrast, is a **general-purpose input language**
used to describe and constrain system and subsystem architecture.

It:
- accepts structured DSLs (e.g. HOCON),
- resolves multiple sources deterministically,
- and produces a normalized structural representation.

Configuration does **not** encode semantics by itself.
All interpretation, validation, and platform decisions
are performed in later stages (Configuration Model and Compilation).

For clarity and correctness,
CNCF treats these as separate concepts with separate responsibilities.

--

### 2.4 Subsystem as the Compilation Unit

The **Subsystem** is the minimal unit for:
- configuration,
- validation,
- deployment,
- and platform compilation.

This aligns with:
- deployment boundaries,
- runtime ownership,
- and scaling decisions.

---

## 3. Logical DSL vs Configuration DSL

CNCF uses two different DSL layers with distinct responsibilities.

---

### 3.1 System / Subsystem DSL (Logical)

The System DSL describes **what exists**.

Example (conceptual):

```
system EcommerceSystem {
  subsystem OrderDomain {
    tier domain
    components {
      OrderAggregate
      PaymentPolicy
    }
    capabilities {
      datastore
      event_bus
    }
  }
}
```

Characteristics:
- Purely logical
- Platform-agnostic
- No execution details
- Human- and AI-friendly

---

### 3.2 Configuration DSL (HOCON)

The Configuration DSL describes **how requirements are satisfied**.

Example:

```
subsystems.order-domain {
  tier = domain

  capabilities.datastore {
    type = postgres
  }

  deploy {
    platform = ecs
    cpu = 1024
    memory = 4096
  }
}
```

Characteristics:
- Environment- and platform-aware
- Supports inheritance and overrides
- Suitable for dev/prod separation

---

## 4. Why “Property Reading” Is Forbidden

A common anti-pattern is:

```
config.getString("subsystems.order.capabilities.datastore.type")
```

Problems:
- Structural knowledge leaks into code
- Validation is scattered
- Platform generation becomes ad-hoc
- Refactoring is fragile

Instead, CNCF mandates:

> **All configuration must be normalized
> into a Configuration Model before use.**

---

## 5. Configuration Model (Core Concept)

The Configuration Model is the semantic “truth”
derived from DSLs and config inputs.

It represents:
- validated structure,
- resolved defaults,
- explicit intent.

---

### 5.1 Minimal Configuration Model

```
SystemModel
 └─ SubsystemModel
     ├─ tier
     ├─ kind
     ├─ components
     ├─ capabilities
     └─ deploy
```

Key properties:
- Platform-independent
- Explicitly typed
- Free of config paths and strings

---

### 5.2 Subsystem Tier and Kind

Each subsystem is defined by two orthogonal axes:

- **tier**: logical responsibility
  - ui
  - application
  - domain

- **kind**: execution form
  - service
  - job
  - gateway
  - external

These axes:
- impose structural constraints,
- define defaults,
- and guide compilation.

---

## 6. Tier-Aware Defaults and Constraints

### 6.1 UI Tier
- datastore forbidden
- kind = gateway (typical)
- deploy required

### 6.2 Application Tier
- implemented as cloud functions
- deploy.platform defaults to lambda
- datastore forbidden
- event_bus allowed

### 6.3 Domain Tier
- stateful runtime
- deploy.platform defaults to ecs/k8s
- datastore required
- in-memory entities allowed

These rules are enforced at the **model validation stage**,
not in runtime code or platform generators.

---

## 7. Capability Binding

Capabilities represent infrastructure contracts
required by a subsystem.

Examples:
- datastore
- event_bus
- clock
- external_api

In HOCON:

```
capabilities.datastore {
  type = postgres
  url = ${?ORDER_DB_URL}
}
```

In the Configuration Model:
- `datastore` becomes a typed capability
- `postgres` becomes an implementation reference
- all remaining values become structured parameters

Platform code never sees raw config paths.

---

## 8. Configuration Model Validation

Validation occurs immediately after model construction.

### Required checks:
- tier/kind compatibility
- required capabilities present
- forbidden capability usage
- deploy defaults resolved
- unknown fields rejected (by default)

This ensures:
> **Invalid architectures fail early,
> before any platform code is generated.**

---

## 9. Platform Compilation

Platform compilation consumes the Configuration Model
and produces platform-specific artifacts.

### Key rule:

> **Platform backends only depend on the model,
> never on raw config or DSL text.**

---

### 9.1 Subsystem → Platform Mapping

Examples:

- (tier=application) → Lambda stack
- (tier=domain, kind=service) → ECS service
- (tier=ui, kind=gateway) → API Gateway

This mapping is deterministic and explicit.

---

### 9.2 CDK as a Backend Example

For each SubsystemModel:

- generate one CDK Stack
- generate compute resources
- generate capability resources
- generate IAM policies
- wire environment variables

The CDK backend:
- contains no business logic
- contains no architectural decisions
- only reflects the model

---

## 10. Benefits of the Model-Centric Approach

### 10.1 Architectural Integrity
- No accidental cross-tier coupling
- No config-driven spaghetti logic

### 10.2 Tooling and Automation
- Easy diagram generation
- Easy documentation generation
- Easy cost estimation

### 10.3 AI Compatibility
- AI can reason about the model
- AI can generate valid configurations
- AI can review architecture for violations

---

## 11. Relationship to Domain Architecture

This configuration architecture complements
the **Memory-First Domain Architecture**:

- Domain Tier defines runtime truth
- Configuration Model defines deployment truth
- Platform DSL realizes infrastructure truth

Each layer has a single responsibility.

---

## 12. Summary

> **CNCF treats configuration as a language,
> not a bag of properties.**
>
> Logical DSLs define intent,
> configuration DSLs bind reality,
> configuration models define truth,
> and platform DSLs realize execution.
>
> This separation enables correctness,
> scalability, automation,
> and long-term architectural stability.

---

## 13. Configuration Key Naming

The primary configuration namespace is `textus.*`.

The product-facing name is CozyTextus. Runtime options, system properties,
environment-variable names, and local configuration directory names should
therefore use `textus` as the primary spelling.

Older `cncf.*` keys may remain as compatibility aliases, but new documentation and examples should prefer `textus.*`.

Compatibility is one-way:

- New features must document `textus.*`.
- Existing `cncf.*` inputs may continue to work as fallback aliases.
- If both `textus.*` and `cncf.*` values are supplied for the same semantic
  setting, the `textus.*` value wins.

The same naming rule applies to standard runtime configuration directories:

- `.textus/` is the primary directory.
- `.cncf/` is a compatibility directory.
- Within the same HOME / PROJECT / CWD scope, `.textus` values override `.cncf`
  values.

The same naming rule applies to explicit runtime configuration file options:

- `--textus.config.file` and `--textus.config.files` are primary.
- `--cncf.config.file` and `--cncf.config.files` are compatibility aliases.

The same naming rule applies to development-time discovery environment
variables:

- `TEXTUS_DISCOVER_CLASSES` is primary.
- `CNCF_DISCOVER_CLASSES` remains a compatibility fallback.
- `TEXTUS_DISCOVER_PREFIX` is primary.
- `CNCF_DISCOVER_PREFIX` remains a compatibility fallback.

The `runtime` segment should not be used as a generic bucket.
Prefer a concrete semantic owner:

- `textus.assembly.descriptor`
- `textus.server-emulator.baseurl`
- `textus.http.driver`
- `textus.mode`
- `textus.command.execution-mode`
- `textus.id.namespace.major`
- `textus.id.namespace.minor`
- `textus.debug.calltree`
- `textus.debug.trace-job`
- `textus.debug.save-calltree`
- `textus.discover.classes`
- `textus.component.factory-class`
- `textus.workspace`
- `textus.force-exit`
- `textus.no-exit`
- `textus.execution.history.recent-limit`
- `textus.execution.history.filtered-limit`
- `textus.execution.history.filter.operation-contains`
- `textus.logging.backend`
- `textus.logging.level`
- `textus.logging.file.path`
- `textus.service-container.driver`
- `textus.service-container.docker.executable`

`textus.command.execution-mode` accepts the canonical production values
`sync`, `job-sync`, `job-async`, and `job-sync-with-async-cont`. The long alias
`job-sync-with-async-continuation` is also accepted. Legacy values such as
`sync-direct-no-job`, `sync-job`, and `async-job` remain compatibility inputs.
`async-job-and-await` and `sync-job-async-interface` are deprecated
compatibility/test modes and should not be used in new configuration.

Descriptor-level `commandExecutionPolicy` may also specify transaction
semantics. The default is strict:

- `callerTransactionPolicy = join-caller`
- `eventTransactionRequirement = required`
- `jobTransactionScope = per-task`
- `continuationEventTransactionRequirement = required`

Relaxing event handling to `best-effort` or `ignore`, or forcing a synchronous
phase to use `new-transaction`, is an explicit operation design decision.

`textus.output.shape=envelope` / `cncf.output.shape=envelope` returns the
canonical CNCF response envelope. The `data` root is the business payload;
metadata is grouped under roots such as `execution`, `job`, `continuation`,
`page`, `diagnostics`, `debug`, and `links`. `textus-execution` is no longer a
canonical envelope root, and `result` is reserved for external protocol
adapters that require it.

`textus.debug.calltree` controls the operation result calltree projection
described in `docs/design/observability/calltree-runtime-result.md`.

`textus.debug.trace-job` requests job-managed debugging for a target request.
Normal Query execution is direct synchronous execution, but trace-job Query
execution is retained as a persistent JobEngine record while preserving the
normal Query response.

`textus.debug.save-calltree` requests persistent job CallTree storage even when
the execution succeeds and is not slow.

`textus.execution.history.*` controls the retained action execution history
used by `admin.execution.history` and `admin.execution.calltree`.

`textus.service-container.driver` selects the opt-in long-lived managed
service driver. Its values are `none` (default) and `docker`.
`textus.service-container.docker.executable` optionally selects the Docker
executable path without adding a shell or arbitrary Docker argument surface.
The runtime is installed lazily only when a component requests a managed
service. See `docs/design/managed-service-container-runtime.md`.

Diagnostic payload externalization policy is described in
`docs/design/observability/diagnostic-payload-externalization-policy.md`.
Current CallTree/history debug keys remain valid.

OB-03 adds opt-in diagnostic payload externalization keys:

- `textus.observability.payload.externalization.enabled`
- `textus.observability.payload.externalization.destination`
- `textus.observability.payload.externalization.local.root`
- `textus.observability.payload.externalization.threshold.bytes`
- `textus.observability.payload.externalization.payloads`
- `textus.observability.payload.externalization.operation`
- `textus.observability.payload.externalization.operation-contains`
- `textus.observability.payload.externalization.allow-request-override`
- `textus.observability.payload.externalization.unsafe-opaque-payloads`
- `textus.observability.payload.externalization.retention.days`

Externalization is disabled by default. Develop/test mode uses
`target/cncf.d/observability/payloads` for local-file externalization when no
destination is specified. Production mode requires an explicit destination and
fails configuration validation when externalization is enabled without one.
`blob-store` uses the configured CNCF `BlobStore`, allowing S3 or another object
store provider to be supplied behind the BlobStore boundary. The BlobStore must
be durable; in-memory BlobStore is rejected for diagnostic payload
externalization because its references cannot be resolved reliably.

OB-06 adds opt-in OpenTelemetry export keys:

- `textus.observability.otel.enabled`
- `textus.observability.otel.endpoint`
- `textus.observability.otel.protocol`
- `textus.observability.otel.traces.enabled`
- `textus.observability.otel.metrics.enabled`
- `textus.observability.otel.logs.enabled`

OpenTelemetry export is disabled by default and is an export/projection
boundary only. V1 supports `otlp-http`. Develop/test mode uses
`http://127.0.0.1:4318` when export is enabled without an endpoint; production
requires an explicit endpoint. The policy is described in
`docs/design/observability/opentelemetry-export-policy.md`.

Use `runtime` only when the value is genuinely about the runtime process itself and no clearer owner exists.

Examples to avoid for new primary keys:

- `textus.runtime.assembly.descriptor`
- `textus.runtime.server-emulator.baseurl`
- `textus.runtime.http.driver`
- `textus.runtime.mode`
- `textus.runtime.command.execution-mode`
- `textus.runtime.calltree`
- `textus.runtime.debug.trace-job`
- `textus.runtime.discover.classes`
- `textus.runtime.component-factory-class`
- `textus.runtime.workspace`
- `textus.runtime.force-exit`
- `textus.runtime.no-exit`
- `textus.runtime.execution.history.recent-limit`
- `textus.runtime.execution.history.filtered-limit`
- `textus.runtime.execution.history.filter.operation-contains`
- `textus.runtime.logging.backend`
- `textus.runtime.logging.level`
- `textus.runtime.logging.file.path`

Compatibility aliases may still accept older keys where they already exist.
