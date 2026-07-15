# Execution Determinism Capability Design Notes

Status: proposed, non-normative

Date: 2026-07-15

Source handoff:
`docs/journal/2026/07/execution-determinism-handoff-2026-07-15.md`

## Purpose

These notes explore a CNCF execution capability model that makes
component-observable behavior reproducible without turning `ExecutionContext`
into a configuration container or moving effect ownership into component code.

The intended result is a runtime-selected execution profile that can control:

- wall-clock time;
- operational scheduling, delay, timeout, retry, and deadlines;
- domain random values through seeded named streams;
- ID and entropy generation independently of domain random values;
- asynchronous execution ordering where deterministic behavior is required;
- locale, timezone, charset, line separator, i18n, and math assumptions;
- an explicitly selected immutable environment snapshot.

This is not yet a normative contract. Settled decisions must be promoted to
`docs/design` and `docs/spec` before implementation is treated as complete.

## Current Findings

### Implemented baseline

- `RuntimeConfig.executionClock` resolves the ordinary system UTC clock or an
  advancing offset clock selected by `textus.clock.virtual-start-at`.
- The resolved clock is recovered from `GlobalRuntimeContext` when CNCF creates
  component execution contexts.
- `BehaviorFeaturePart` exposes `execution_clock`, `current_instant`, and
  `current_zoned_datetime` to component ActionCall implementations.
- CNCF has deterministic and nondeterministic `IdGenerationContext`
  implementations.
- `JobEngine` has a `JobTimeSource`, `ManualJobTimeSource`, and `JobTimer`
  boundary.
- Explicit `test.yaml` / `test.json` descriptors can override runtime
  configuration, assembly wiring, SPI selection, datastores, and test-home
  behavior.

### Gaps

- The core `RandomContext` currently returns constant values. It has no seeded
  advancing generator and no named stream derivation.
- The nondeterministic `IdGenerationContext` reads wall-clock time and entropy
  directly instead of using execution capabilities.
- Job time is selected independently from the clock carried by
  `ExecutionContext`.
- An offset clock changes reported time but does not allow tests to advance
  delayed work without real waiting.
- locale, timezone, encoding, line separator, math context, i18n, and
  environment values are built with fixed CNCF defaults rather than resolved
  from one runtime profile.
- observable framework paths still contain direct `Instant.now`, UUID,
  environment, system-property, and sleep calls.
- CNCF has no standard execution-profile diagnostic that explains whether an
  execution was replayable and which assumptions were active.

These gaps mean that the same operation input can still produce different
observable results because of runtime state that is not controlled by the
ActionCall execution boundary.

## Design Principles

### Resolved capabilities, not configuration snapshots

Bootstrap configuration selects implementations and values. An
`ExecutionContext` carries only the resolved assumptions and capability
handles needed by one execution.

Component logic must not inspect raw configuration to decide which clock,
random implementation, scheduler, ID generator, or locale policy to use.

### Preserve core ownership

Goldenport core already owns generic execution assumptions:

- clock;
- random context;
- locale and timezone;
- charset;
- math context;
- virtual-machine and environment assumptions.

CNCF should extend inadequate core abstractions in core first, then adapt them.
It must not create competing CNCF versions of generic clock, random, locale, or
math abstractions.

### Runtime owns effects and lifecycle

An execution context may carry immutable references to resolved runtime
facades, but the runtime or scope owns mutable schedulers, queues, executors,
timers, and provider lifecycles.

The context is immutable even when a referenced manual clock or scheduler is a
controlled runtime facility.

### Internal DSL is the component boundary

Component and provider implementation code reads execution assumptions and
performs controlled operations through protected CNCF internal DSL methods.
Direct access to ambient JVM or OS state is migration debt when it can affect
component-observable behavior.

### Determinism is a profile, not a Boolean

The runtime should describe separately whether time, random values, IDs,
scheduling, and asynchronous ordering are controlled. A partially controlled
execution must not be reported as fully replayable.

### Operational scheduling remains bounded

Deterministic scheduling does not broaden CNCF into a cron, business calendar,
or workflow timer platform. The boundary in
`docs/design/timer-scheduling-boundary.md` remains authoritative.

## Candidate Runtime Model

The following names are candidates, not frozen API names.

```scala
final case class ResolvedExecutionProfile(
  identity: ExecutionProfileIdentity,
  time: ResolvedTimeAssumptions,
  random: RandomContext,
  entropy: EntropyContext,
  idGeneration: IdGenerationContext,
  scheduling: ExecutionSchedulingContext,
  ordering: ExecutionOrderingContext,
  environment: ResolvedEnvironmentAssumptions,
  replayability: ReplayabilityAssessment
)
```

The object should be split across existing ownership boundaries rather than
stored as one duplicate object:

| Concern | Canonical carrier | Lifecycle owner |
| --- | --- | --- |
| clock, random, locale, timezone, charset, math | core `ExecutionContext.Core` | runtime bootstrap |
| VM and environment assumptions | core VM/environment contexts | runtime bootstrap |
| ID generation | CNCF `ExecutionContext.CncfCore` | execution scope |
| scheduler, timer, deadline facade | CNCF execution-control context | global runtime / JobEngine |
| deterministic async ordering | CNCF execution-control context | global runtime / JobEngine |
| profile identity and replay assessment | CNCF execution-control context | runtime bootstrap |
| HTTP, filesystem, datastore, event bus, AI, knowledge | ScopeContext drivers/providers | owning runtime scope |

`ResolvedExecutionProfile` may therefore be a resolver result and
introspection model rather than a second container stored verbatim in
`ExecutionContext`.

## Profile Modes

The first contract should support three explicit modes.

### `standard`

- system or configured offset clock;
- production random and entropy sources;
- ordinary JobEngine scheduler and bounded worker pool;
- configured locale/environment assumptions;
- no replay guarantee.

### `seeded`

- system or offset clock;
- seeded named random streams;
- deterministic ID/entropy streams;
- ordinary scheduler unless overridden;
- reproducible values, but not necessarily reproducible async ordering or
  elapsed timing.

### `controlled`

- manually advanceable wall clock;
- manually driven operational scheduler;
- seeded named random streams;
- deterministic IDs and entropy;
- deterministic runtime queue ordering;
- explicit environment assumptions;
- suitable for executable specifications and replay-oriented tests.

Startup must reject incompatible strict combinations. For example, a
`controlled` profile must not silently use a real-time Job scheduler.

## Time Model

### Distinct time concepts

The model must distinguish:

- wall time used in domain-visible timestamps;
- monotonic elapsed time used for internal performance measurement;
- controlled logical time used for observable deadlines, delays, retries, and
  timeouts;
- calendar/timezone interpretation.

Changing virtual wall time must not falsify performance duration metrics.
Conversely, an observable timeout must not use an uncontrollable host monotonic
clock when a controlled profile is active.

### Offset and manual clocks

The current offset clock remains useful for date-sensitive demonstrations. It
advances with real time and does not eliminate waiting.

A controlled profile needs a manual clock and scheduler pair:

```text
advanceBy(duration)
  -> advance logical wall time
  -> make due operational timers eligible
  -> enqueue due Job work in deterministic order

runUntilIdle()
  -> execute eligible runtime work without advancing time
```

Advance controls belong to a test harness or CNCF test-control surface.
Component internal DSL may read time and schedule allowed runtime work, but it
must not advance the clock.

### Job and Event integration

- `JobEngine` must adapt to the runtime-selected time capability rather than
  create an independent default source.
- `ManualJobTimeSource` may remain as an adapter or test fixture, but it must
  not be a competing source of truth.
- delayed Job start, retry due time, Job timestamps, and await timeout
  semantics must use the same resolved time policy where they are observable.
- synchronous same-transaction Event reception executes immediately and does
  not pass through the scheduler.
- asynchronous Event reception enters JobEngine and uses the selected queue,
  clock, and timer policy.
- transaction semantics defined by Command/Event execution policy are not
  changed by deterministic time control.

## Random and Entropy Model

### Named random streams

Core `RandomContext` should grow a real seeded implementation and stable named
stream derivation. Candidate behavior:

```scala
trait RandomContext {
  def stream(purpose: String): RandomContext
  def nextInt(): Int
  def nextInt(bound: Int): Int
  def nextLong(): Long
  def nextDouble(): Double
  def nextBoolean(): Boolean
}
```

A stream seed is derived from the root seed plus a normalized purpose path.
Adding a call in `domain.recommendation` must not alter values from
`domain.pricing`.

Recommended reserved purpose families are:

- `domain.*` for business decisions;
- `retry.*` for operational jitter;
- `id.*` for identifier entropy;
- `security.*` for security-owned randomness, when deterministic security
  behavior is explicitly appropriate for a test.

Security randomness must default to a production-safe provider and must never
become deterministic merely because domain randomness is seeded.

### Execution identity

Replaying one ActionCall requires a stable execution key in addition to a root
seed. A candidate stream derivation is:

```text
hash(profile-seed, execution-key, purpose)
```

The test harness should provide the execution key. A random correlation ID or
arrival-order-dependent value is not a valid replay key.

Concurrent calls to one stream require defined ordering. A strict controlled
profile should either serialize access through deterministic task ordering or
reject an execution shape whose stream ordering is not reproducible.

### Separate entropy from domain random values

ID generation must not consume a domain random stream. Adding an Entity ID must
not change a later price, sampling, or recommendation result.

`EntropyContext` is a candidate generic abstraction for opaque bytes/tokens.
Whether that type belongs in goldenport core must be decided before CNCF API
implementation.

## ID Generation

`IdGenerationContext` should be constructed from resolved execution
capabilities:

```text
namespace + execution clock + dedicated ID entropy/sequence
```

The production implementation uses the selected clock and production entropy.
The controlled implementation uses the selected manual clock, stable execution
key, collection-specific sequence, and dedicated `id.*` stream.

Framework and builtin component paths that create UUIDs or timestamps directly
should migrate to the same ID/entropy boundary when the values are observable.
Migration must preserve the opaque `CanonicalId` and `EntityId` contracts; CNCF
logic must not parse generated IDs to recover execution policy.

## Environment Assumptions

Bootstrap may read ambient JVM/OS state once, according to explicit policy, and
then build immutable core contexts.

The resolved environment contract should cover:

- locale;
- timezone;
- charset;
- line separator;
- math context;
- i18n policies;
- an allowlisted environment-variable snapshot.

The default environment snapshot should be empty or narrowly allowlisted.
Secrets and credentials must not appear in profile introspection, calltrees, or
replay manifests.

Component behavior should not call `System.getenv`, `System.getProperty`, or
host locale/timezone APIs directly. Bootstrap, launcher, repository discovery,
and low-level transport code may still need host state; only
component-observable semantics must flow through execution assumptions.

## Scheduling and Execution Ordering

A deterministic executor is not a license for components to spawn arbitrary
threads. CNCF asynchronous work remains Job-managed.

The initial controlled-ordering scope should be:

- JobEngine queue insertion order;
- due timer ordering;
- retry enqueue ordering;
- asynchronous Event continuation ordering;
- same-Job task ordering where CNCF already owns the queue.

Provider-internal concurrency is outside this first contract unless the
provider exposes a deterministic fake/test implementation. HTTP, filesystem,
datastore, process, AI, and knowledge effects remain injectable providers or
drivers, not methods on the execution profile.

## Internal DSL Surface

Existing time helpers remain canonical:

- `execution_clock`;
- `current_instant`;
- `current_zoned_datetime`.

Candidate additions are:

- `random_int(purpose, bound)`;
- `random_long(purpose)`;
- `random_double(purpose)`;
- `random_boolean(purpose)`;
- ID creation helpers that delegate to `IdGenerationContext`;
- controlled deadline/delay operations represented through the existing
  internal program/Job boundaries, not `Thread.sleep`.

The public or protected API should prefer a purpose argument over exposing a
shared mutable random instance. Purpose paths must be stable component
contract names rather than source line numbers.

Every helper should preserve existing ActionCall/UoW chokepoints and CallTree
observability. Reading a random value or ID may record structural metadata such
as purpose and sequence, but never the root seed, secret entropy, or sensitive
generated token.

## Configuration and Test Descriptor

The ordinary runtime configuration is the canonical source. An explicit test
descriptor may provide structured shorthand that normalizes into the same
configuration model.

Candidate `test.yaml` shape:

```yaml
kind: test-descriptor

execution:
  profile: controlled
  key: sie-phase-4-5
  time:
    mode: manual
    start-at: 2026-07-28T09:00:00Z
  random:
    mode: seeded
    seed: phase-4-5
  ids:
    mode: deterministic
  scheduler:
    mode: manual
  ordering:
    mode: deterministic
  assumptions:
    locale: en-US
    timezone: UTC
    charset: UTF-8
    line-separator: lf
    math-context: decimal64
    environment:
      values: {}
```

Candidate canonical configuration keys:

```text
textus.execution.profile
textus.execution.key
textus.execution.time.mode
textus.execution.time.start-at
textus.execution.random.mode
textus.execution.random.seed
textus.execution.ids.mode
textus.execution.scheduler.mode
textus.execution.ordering.mode
textus.execution.locale
textus.execution.timezone
textus.execution.charset
textus.execution.line-separator
textus.execution.math-context
```

The existing `textus.clock.virtual-start-at` remains the canonical offset-clock
surface until a promoted design explicitly supersedes it. A later unified
resolver may treat it as shorthand for `time.mode=offset` plus `time.start-at`.

The structured test descriptor must remain explicit. CNCF must not
auto-discover deterministic seeds or test profiles in production startup.

## Resolution and Propagation

The candidate resolution sequence is:

```text
configuration and explicit test overlay
  -> ExecutionProfileConfig
  -> validate compatible capability combination
  -> build runtime-owned time/scheduler/executor facilities
  -> build core ExecutionContext assumptions
  -> build CNCF ID and execution-control contexts
  -> create GlobalRuntimeContext
  -> derive one ActionCall ExecutionContext
```

Rebinding an execution context beneath another runtime must replace the whole
resolved profile coherently. It must not replace only the clock while retaining
an ID generator, scheduler, or random context from a different profile.

An ActionCall-specific execution key and named streams should be derived at
ActionCall creation, which is the last common boundary before execution.

## Diagnostics and Replayability

Runtime introspection should expose a sanitized execution-profile summary:

- profile name and mode;
- profile fingerprint;
- clock mode and virtual/manual status;
- random mode and seed fingerprint, never the seed;
- ID mode;
- scheduler and ordering modes;
- locale, timezone, charset, line-separator, and math policy;
- replayability assessment and reasons.

Candidate replayability states:

- `replayable`;
- `partially-controlled`;
- `uncontrolled`;
- `invalid`.

The assessment should list dimensions that prevent replay, such as real-time
scheduler, uncontrolled provider, missing execution key, or concurrent shared
stream access. It is diagnostic metadata, not an authorization capability.

CallTree and observability records should identify capability purpose and mode
without recording secret values. Performance duration remains based on a
monotonic source and must not be derived by subtracting manually advanced wall
timestamps.

## Failure Semantics

Invalid profile configuration must fail deterministically at bootstrap with a
normal structured `Conclusion`. Framework code must not invent a parallel
failure taxonomy or use application-owned `Status.detailCodes`.

Examples include:

- controlled profile with a real-time scheduler;
- manual scheduler without a controllable clock;
- seeded domain random mode without a seed;
- replay-required execution without an execution key;
- unsupported charset, locale, timezone, or math context;
- environment entries outside the declared policy;
- async work that bypasses JobEngine under a controlled profile.

## Migration Classification

Not every direct host-state call requires migration. Each occurrence should be
classified before editing:

1. component-visible semantic input: migrate to execution context/internal DSL;
2. Job/Event/runtime semantic timestamp or timeout: migrate to runtime execution
   control;
3. provider effect: move behind or retain an injectable provider/driver;
4. monotonic diagnostics/performance only: retain a monotonic runtime source;
5. bootstrap or host-discovery concern: retain at ingress/bootstrap and
   snapshot when needed;
6. test fixture only: replace real waiting/state when it weakens executable
   specification determinism.

This classification avoids blindly replacing every `Instant.now` or UUID call
with an ActionCall clock where no ActionCall semantics exist.

### ED-07 audit snapshot

The Jul. 15, 2026 source audit grouped the remaining ambient calls by ownership
before migration:

| Boundary | Representative locations | Direction |
| --- | --- | --- |
| component-visible domain state | InformationSpace, Tag, JobControl, Workflow, Aggregate edit contexts, Knowledge and Entity working sets, temporal ABAC authorization | use current execution clock/ID/random capabilities; Tag, InformationSpace, Aggregate edit-context lifecycle time, Knowledge working-set lifecycle time, Entity working-set status/policy time, and ABAC `now` evaluation are migrated |
| Event/Job/runtime semantics | EventReception, EventStore fallback, transition lifecycle events, pre-ActionCall authorization-denial Events, Job lifecycle publication, notification forwarding diagnostics, Job model defaults | route construction and mutation through runtime clock/ID controls; Event records, authorization-denial Events, Job lifecycle publication, and CNCF-owned forwarding diagnostics are migrated |
| provider/driver effects | BlobStore, notification delivery, Docker/filesystem adapters | retain behind provider/driver contracts and supply deterministic test doubles where replay is required |
| monotonic diagnostics and transport telemetry | ActionEngine duration, OpenTelemetry export, dashboard/diagnostic capture | retain monotonic or transport-owned time; do not reinterpret as domain time |
| bootstrap/repository/host discovery | CLI, RuntimeConfig bootstrap, component repository, configuration sources, test-home/work-area setup | retain at ingress/bootstrap and snapshot only values that become component-visible |
| compatibility/test waiting | AwaitSupport and legacy EventAwaitSupport | replace asserted semantic waiting with controlled scheduler operations; bounded host polling may remain only as explicit compatibility infrastructure |

The audit also found direct host filesystem access concentrated in repository,
archive, config, transport, datastore, and provider adapters. Those calls are
not migrated mechanically. CAR lint treats equivalent access in component
sources as a warning so application code cannot silently cross these framework
boundaries.

## Proposed Implementation Slices

### ED-01: Freeze the execution-profile contract

- promote terminology, ownership, compatibility rules, and replayability
  semantics to design/spec;
- decide which generic extensions belong in goldenport core;
- define canonical config and test-descriptor projection.

### ED-02: Seeded named random and dedicated entropy

- implement seeded named streams in core `RandomContext`;
- define entropy separation;
- add CNCF internal DSL helpers and executable specifications.

### ED-03: Capability-based ID generation

- construct `IdGenerationContext` from selected clock and dedicated entropy;
- derive per-execution and per-collection deterministic sequences;
- migrate observable builtin ID generation paths.

### ED-04: Unified time and Job scheduling

- adapt JobEngine time/timer selection to the runtime execution profile;
- add manual clock plus scheduler test control;
- remove real waiting from controlled Job retry/delay/await specs.

### ED-05: Resolved environment assumptions

- resolve locale, timezone, charset, line separator, math, i18n, and allowlisted
  environment values at bootstrap;
- carry them through core `ExecutionContext.Core`;
- add introspection with secret redaction.

### ED-06: Deterministic runtime ordering

- fix ordering guarantees for CNCF-owned Job/Event queues;
- define strict behavior for concurrent named stream access;
- add `runUntilIdle` test-kit support.

### ED-07: Migration and enforcement

- audit observable ambient-state calls by the migration classification;
- migrate component and builtin behavior;
- extend CAR lint/review guidance for direct clock, random, UUID, sleep,
  environment, and filesystem access.

### ED-08: Introspection and closure

- expose sanitized profile/replayability diagnostics;
- verify standard, seeded, and controlled profiles end to end;
- publish the settled component-developer and test-kit guidance.

## Executable Specification Plan

### Core specifications

- the same root seed, execution key, and purpose produce the same sequence;
- different purpose streams do not affect each other;
- adding ID generation does not change domain random results;
- existing `RandomContext` numeric bounds remain valid;
- existing public enum/value contracts remain stable where applicable.

### CNCF context specifications

- bootstrap resolves one coherent profile into core and CNCF context fields;
- ActionCall creation derives stable execution-local streams;
- runtime rebinding replaces profile-dependent facilities coherently;
- internal DSL reads the selected time/random/ID capabilities;
- invalid capability combinations fail with structured `Conclusion`.

### Job and Event specifications

- advancing manual time makes due retries/jobs eligible without sleeping;
- equal due times retain deterministic queue ordering;
- async Event reception uses the same Job time and scheduler;
- same-transaction synchronous Event handling does not create a scheduled task;
- `JobAsync` plus test await/run-until-idle completes without real delay;
- wall-time advancement does not change monotonic performance duration.

### Environment specifications

- locale/timezone/charset/math values are resolved once and propagated;
- environment values are immutable snapshots;
- undeclared environment values are absent;
- profile diagnostics never expose seeds, credentials, or environment secrets.

### Replay scenario

Run one component scenario twice with the same controlled profile and verify the
same:

- business result;
- generated entity IDs;
- domain/Event timestamps;
- Event and Task ordering;
- retry schedule and outcome;
- sanitized execution-profile fingerprint.

Then change only one named random purpose input or profile seed and verify that
unrelated purpose streams remain unchanged.

## Decisions Recommended for Promotion

The following direction is sufficiently supported for a later normative
design:

- execution determinism is resolved at runtime bootstrap and bound at
  ActionCall creation;
- generic assumptions reuse or extend goldenport core abstractions;
- effectful resources remain ScopeContext/runtime-owned providers and drivers;
- domain random, ID entropy, and security randomness are separate;
- Job/Event operational time uses the selected runtime time capability;
- component code accesses observable assumptions through internal DSL;
- controlled time advancement is test-harness-only;
- deterministic execution does not broaden the built-in scheduling boundary;
- replayability is assessed by dimension and never inferred from one seed flag.

## Open Decisions

- final type names and the exact split between core and CNCF;
- whether `EntropyContext` is a goldenport core abstraction;
- the stable execution-key source for non-test replay scenarios;
- whether production supports the `seeded` mode or only tests do;
- precise manual scheduler control API and server-mode access policy;
- whether a strict controlled profile rejects all uncontrolled providers or
  reports partial replayability;
- compatibility policy for existing `textus.clock.virtual-start-at`;
- whether deterministic profile metadata belongs in general introspection,
  Job metadata, or both;
- the next strategy item and phase that own ED-01 through ED-08.

## Promotion Targets

If adopted, split the final contract across existing canonical documents:

- `docs/design/execution-context.md`: resolved assumption ownership and
  propagation;
- `docs/design/execution-clock.md`: offset/manual clock semantics;
- `docs/design/job-management.md` and
  `docs/design/timer-scheduling-boundary.md`: Job/Event scheduler integration;
- `docs/spec/test-policy.md`: explicit controlled profile and test controls;
- `docs/notes/internal-dsl-guideline.md`: developer-facing helper usage after
  the normative API exists;
- strategy and a new phase/checklist: implementation sequencing and closure.
