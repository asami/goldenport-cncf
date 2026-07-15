# Execution Determinism

Status: normative

## Purpose

CNCF resolves execution assumptions and runtime controls into one coherent
execution profile. The profile defines how component-observable time, random
values, identifiers, operational scheduling, CNCF-owned asynchronous ordering,
and environment assumptions are selected and bound to an ActionCall.

Execution determinism does not replace ActionCall, UnitOfWork, JobEngine,
Event reception, authorization, or provider boundaries.

## Terminology

The canonical model names are:

- `ExecutionProfileMode`: `Standard`, `Seeded`, or `Controlled`;
- `ExecutionProfileConfig`: bootstrap selection values;
- `ExecutionProfileIdentity`: resolved profile name, mode, run key, and
  sanitized fingerprint;
- `ExecutionInvocationIdentity`: one ActionCall invocation key;
- `ExecutionControlContext`: CNCF-only scheduling, ordering, profile, and
  replayability handles carried by CNCF `ExecutionContext`;
- `ReplayabilityAssessment`: replayability state plus structured reasons;
- `ReplayabilityState`: `Replayable`, `PartiallyControlled`, `Uncontrolled`,
  or `Invalid`.

`ResolvedExecutionProfile` names the bootstrap resolver result. It is not a
second duplicate value stored verbatim in `ExecutionContext`.

## Ownership

Goldenport core owns generic execution assumptions:

- `Clock`;
- `RandomContext`;
- `EntropyContext`;
- locale and timezone;
- charset and line separator;
- math and i18n assumptions;
- virtual-machine and environment assumptions.

CNCF owns:

- execution-profile resolution and validation;
- ActionCall invocation identity;
- `ExecutionControlContext`;
- `IdGenerationContext`;
- JobEngine and Event integration;
- operational scheduler and ordering controls;
- internal DSL access;
- replayability assessment and diagnostics.

`GlobalRuntimeContext` owns the lifecycle of mutable clocks, schedulers,
timers, queues, and executors. An ActionCall `ExecutionContext` carries resolved
values or immutable handles to those facilities. Component code MUST NOT own or
shut down those facilities.

HTTP, filesystem, datastore, process, Event bus, message, AI, and knowledge
effects remain ScopeContext drivers or providers. They MUST NOT be moved into
the execution profile.

## Profile Modes

### Standard

`Standard` is the production default.

- time uses the system UTC clock or the configured advancing offset clock;
- domain random values use the production random provider;
- IDs use production-safe time and entropy;
- JobEngine uses its ordinary bounded scheduler and workers;
- replayability is `Uncontrolled` unless all observable dimensions are
  independently controlled.

### Seeded

`Seeded` selects seeded named domain-random streams.

- a seed is required;
- IDs remain production-safe and MUST NOT become deterministic by default;
- security randomness remains production-safe;
- scheduler and asynchronous ordering remain ordinary unless separately
  controlled;
- replayability is normally `PartiallyControlled`.

`Seeded` may be selected during ordinary runtime startup.

### Controlled

`Controlled` is test-only.

- a profile run key, manual start instant, random seed, manual operational
  scheduler, deterministic CNCF queue ordering, and deterministic ID mode are
  required;
- time advances only through a test-control handle;
- component code can read time but cannot advance it;
- security randomness remains production-safe unless a separate explicit test
  security provider owns deterministic behavior;
- ordinary production startup MUST reject this mode.

`Controlled` may be activated only by an explicit `test.yaml`/`test.json`
descriptor, `cncf test`, or an in-process executable-spec builder. CNCF MUST NOT
auto-discover it.

### Compatibility Matrix

Each profile is a coherent bundle. Explicit dimension keys may restate the
bundle or select an allowed time variant; they MUST NOT silently create a
different bundle.

| Profile | Time | Random | IDs | Scheduler | Ordering | Startup |
| --- | --- | --- | --- | --- | --- | --- |
| `Standard` | `system` or `offset` | `system` | `production` | `realtime` | `concurrent` | ordinary or test |
| `Seeded` | `system` or `offset` | `seeded` | `production` | `realtime` | `concurrent` | ordinary or test |
| `Controlled` | `manual` | `seeded` | `deterministic` | `manual` | `deterministic` | explicit test only |

If a profile dimension is omitted, the table supplies its default. A supplied
value outside the row is a configuration error. Phase 31 does not support
custom mixed profiles. A later design may add an explicit custom profile
without changing the meaning of these three names.

## Profile and Invocation Identity

An `ExecutionProfileIdentity` identifies one configured runtime run. Its run
key is required for `Controlled` and optional for other modes.

Every ActionCall receives an `ExecutionInvocationIdentity` derived from:

```text
profile run key + operation selector + deterministic invocation ordinal
```

An in-process executable specification may supply an explicit invocation key.
An explicit key replaces the derived key for that ActionCall.

The invocation ordinal is assigned at the CNCF admission boundary before
ActionCall creation. It is monotonic within one runtime profile. A server whose
request arrival order is not controlled MUST report that dimension as a
replayability limitation.

Random streams, deterministic ID sequences, and CNCF-owned ordering metadata
derive from the invocation identity. Correlation IDs and random protocol IDs
MUST NOT be used as replay keys.

## Random and Entropy

Core `RandomContext` MUST support production and seeded implementations plus
stable named stream derivation:

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

A seeded stream is derived from the profile seed, invocation identity, and
normalized purpose. Calls in one purpose stream MUST NOT change another stream.

Core `EntropyContext` provides opaque bytes or tokens by purpose. CNCF MUST use
separate instances or purpose roots for:

- domain random values;
- ID entropy;
- retry jitter;
- security entropy.

Generating an ID MUST NOT consume a domain-random stream. Selecting seeded
domain randomness MUST NOT select deterministic security entropy.

## ID Generation

CNCF constructs `IdGenerationContext` from:

- the configured ID namespace;
- the resolved execution clock;
- dedicated ID entropy or a controlled sequence;
- the execution invocation identity.

`Standard` and `Seeded` use production-safe ID entropy. `Controlled` uses a
deterministic per-invocation and per-collection sequence.

Generated `CanonicalId` and `EntityId` values remain opaque. CNCF logic MUST
NOT parse an ID to recover profile, seed, sequence, or execution policy.

## Time and Scheduling

CNCF distinguishes:

- wall time used by domain-visible timestamps;
- monotonic time used by internal performance measurement;
- operational time used by observable delay, retry, timeout, and deadline
  behavior;
- timezone/calendar interpretation.

An advancing offset clock follows real elapsed time. It does not provide manual
test scheduling.

A controlled profile provides one runtime-owned manual clock and operational
scheduler. Its test-control handle supports:

```text
advanceBy(duration)
runUntilIdle()
```

`advanceBy` advances logical wall/operational time and makes due Job work
eligible. `runUntilIdle` executes currently eligible CNCF-owned work without
advancing time.

These controls are in-process test surfaces in Phase 31. CNCF does not expose a
production or general server endpoint for clock advancement.

The lifecycle owner exposes the handle as
`ExecutionProfileRuntime.testControl`; ordinary profiles return no handle.
`advanceBy` advances manual time and runs due timer callbacks so their Job work
becomes eligible. `runUntilIdle` drains currently eligible runtime work in the
selected deterministic order and returns the number of drained work items. The
handle is not copied into component `ExecutionContext` and is not an internal
DSL capability.

Performance duration MUST use a monotonic source and MUST NOT be calculated by
subtracting manually advanced wall timestamps.

## Job and Event Integration

JobEngine MUST use the resolved runtime execution-time and scheduling controls.
It MUST NOT create a competing semantic clock for Job timestamps, due times,
retry, delayed start, or observable await timeout.

`JobTimeSource` may remain as an adapter to the selected execution-time source.
It is not an independent policy source.

Asynchronous Event reception enters JobEngine and uses the same scheduler,
clock, timer, and queue-ordering policy. Synchronous same-transaction Event
reception executes immediately and MUST NOT become a scheduled task.

Canonical Event reception derives `ReceptionDomainEvent.occurredAt` and its
standard occurred-at attribute from the bound execution clock. Event-generated
saga boundaries and transition lifecycle Event IDs derive from the bound ID
generation capability. Direct `ReceptionDomainEvent` construction therefore
requires an explicit timestamp; the model MUST NOT supply an ambient default.
Transition lifecycle creation captures one execution-clock instant and uses it
for both the lifecycle timestamp and Event ID timestamp.

EventStore persistence uses an explicit `EventRecordFactory` capability carrying
the execution clock and ID-generation context. Transactional events are
materialized into fixed `EventRecord` values when UnitOfWork stages the commit,
not later inside `EventEngine.commit`. Authorized non-transactional publication
uses the caller execution profile; contextless compatibility publication uses
the EventEngine-bound factory. Commit therefore persists the already-fixed
record identity and semantic timestamp without consulting ambient time or a
constant fallback Event ID.

CNCF-owned user-notification forwarding diagnostics are Event runtime
semantics, even though notification delivery itself is a provider effect. A
forwarding diagnostic MUST derive its `EventRecord` timestamp and identity from
the dispatch execution profile. Authorized dispatch preserves the caller
profile; contextless compatibility dispatch may bind the subsystem component
profile. Provider-owned delivery receipt IDs and timestamps remain outside this
runtime Event contract.

Job input and Job definition lifecycle timestamps are runtime semantics. Their
model constructors and update functions require an explicit instant and MUST
NOT consult an ambient clock or provide an ambient default. Component command
execution and Job Control operations pass the current bound execution clock
instant into those model boundaries. An explicitly supplied Job input
`createdAt` remains authoritative; when it is absent, the fallback is the bound
execution clock, not host wall time. Job input cleanup similarly receives its
operational instant explicitly from its caller.

Job, Task, and Action identities created by CNCF execution boundaries are also
runtime semantics. Job submission, Task execution, component Action,
Event-reception Action, Workflow Action, and JCL Action boundaries derive their
namespace, timestamp, and entropy from the caller's bound execution
capabilities. Equivalent invocation-local capabilities therefore replay the
same identity sequence, while purpose-local sequences keep multiple Jobs,
Tasks, and Actions collision-free. Model-level no-argument generators are
compatibility/test conveniences and MUST NOT be used by CNCF production
execution paths.

InformationSpace lifecycle and Knowledge materialization timestamps are
component-visible runtime semantics. Information registration, mutation,
validation, confirmation, publication, conflict handling, and materialization
MUST receive the caller `ExecutionContext` and use its bound clock. The mutable
InformationSpace does not own or infer a clock. One mutation captures one
instant for all fields changed by that mutation; confirmation therefore uses
the same instant for `confirmedAt` and `updatedAt`, publication uses the same
instant for `publishedAt` and `updatedAt`, and projection uses the caller instant
for `KnowledgeFrame.materializedAt`. `Information.updatedAt` and
`InformationFieldEvent.occurredAt` require explicit constructor values and MUST
NOT supply ambient defaults.

Knowledge working-set load status is component-visible runtime semantics.
`KnowledgeWorkingSet.load` and `KnowledgeSpace.replace` MUST receive the caller
`ExecutionContext` and MUST NOT infer a clock. A synchronous load or reload
attempt captures one execution-clock instant and uses it for both `startedAt`
and `completedAt`. A failed replacement retains the previous snapshot and
indexes while recording the failed attempt status with that same caller-bound
instant.

Entity working-set load status is runtime semantic state. Startup initialization
MUST receive the clock selected by the active execution profile and use that
clock for `Loading`, `Ready`, and `Failed` transition timestamps. The mutable
status holder MUST NOT obtain ambient wall-clock time. Context-free fixture
transitions may omit timestamps, but MUST NOT fabricate semantic timestamps.
Working-set admission and residency evaluation likewise require an explicit
evaluation instant. `WorkingSetPolicy` and custom evaluators MUST NOT define an
ambient default. Context-aware Entity admission, scoped resolution, and search
capture one instant from the bound execution clock and pass it through policy
evaluation. Context-free compatibility admission is valid only for static
`Disabled`, `ResidentAll`, or policy-free behavior; a time-dependent policy is
rejected before storage mutation. Context-free resolution may return stored
data, but MUST NOT mutate a time-dependent resident cache without an evaluation
instant.

Temporal authorization is also component-visible semantic behavior. The ABAC
`now` operand MUST resolve from one execution-clock instant captured by the
authorization boundary. Search/list filtering MUST reuse that instant for every
candidate and condition in the decision. Context-free compatibility evaluation
without an explicit instant MUST leave `now` unresolved and fail the temporal
condition closed; it MUST NOT consult ambient wall-clock time.

Manual scheduling is limited by `timer-scheduling-boundary.md`. It MUST NOT add
cron, recurrence, business-calendar, workflow-wait, or general scheduler
semantics.

## Environment Assumptions

Bootstrap resolves these generic assumptions once:

- locale;
- timezone;
- charset;
- line separator;
- math context;
- i18n policies;
- explicitly allowlisted environment values.

The values are immutable for one ActionCall and are carried through core
`ExecutionContext.Core` and its VM/environment contexts.

The default environment snapshot is empty. Environment keys not selected by
policy are absent. Seeds, credentials, tokens, and secret environment values
MUST NOT appear in introspection, CallTree, metrics, or replay diagnostics.

The compatibility defaults are `Locale.ROOT`, `UTC`, `UTF-8`, `LF`, and
`MathContext.DECIMAL64`, with each i18n policy named `default`. Configuration
may replace each assumption, but bootstrap MUST validate the complete set
before constructing `GlobalRuntimeContext`.

For every name in `environment.allow`, an explicit entry in
`environment.values` takes precedence over the ambient process environment.
When no explicit value exists, bootstrap may snapshot the ambient value with
that exact name. Names outside the allowlist are never copied. A configured
value outside the allowlist is a configuration error. Runtime diagnostics may
show selected environment names, but never their values.

## Configuration

The canonical configuration keys are:

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
textus.execution.i18n.text-normalization-policy
textus.execution.i18n.text-comparison-policy
textus.execution.i18n.date-time-format-policy
textus.execution.environment.allow
textus.execution.environment.values
```

The canonical mode values are:

```text
profile: standard | seeded | controlled
time.mode: system | offset | manual
random.mode: system | seeded
ids.mode: production | deterministic
scheduler.mode: realtime | manual
ordering.mode: concurrent | deterministic
```

`time.start-at` is required for `offset` and `manual`. `random.seed` is required
for `Seeded` and `Controlled`. `execution.key` is required for `Controlled`.
The environment allowlist contains names; the environment values entry is a
name/value map. A provided value whose name is not allowlisted is a
configuration error.

`textus.clock.virtual-start-at` remains the compatibility surface for the
existing offset clock. When unified time keys are absent, it resolves to
`time.mode=offset` and the same start instant.

If `textus.clock.virtual-start-at` and `textus.execution.time.start-at` are both
present, they MUST represent the same instant. A mismatch is a configuration
error. `time.mode=manual` conflicts with `textus.clock.virtual-start-at`.

The explicit test-descriptor `execution` block normalizes to these keys and
retains test-descriptor provenance so `Controlled` can be validated as
test-only.

## Binding and Rebinding

Bootstrap resolves and validates the complete profile before creating
`GlobalRuntimeContext`.

ActionCall creation binds:

- core clock, random, locale, timezone, charset, math, i18n, VM, and
  environment assumptions;
- CNCF execution control, invocation identity, ID generation, scheduling,
  ordering, and replayability metadata.

Runtime rebinding MUST replace every profile-dependent field coherently. It
MUST NOT combine a clock from one runtime with random, ID, scheduler, ordering,
or profile metadata from another runtime.

## Internal DSL

Component and provider ActionCall behavior accesses execution assumptions
through protected CNCF internal DSL.

Canonical time helpers are:

- `execution_clock`;
- `current_instant`;
- `current_zoned_datetime`.

Canonical purpose-based random helpers are:

- `random_int(purpose, bound)`;
- `random_long(purpose)`;
- `random_double(purpose)`;
- `random_boolean(purpose)`.

Canonical ID helpers are:

- `entity_id(collection, purpose)` for the configured runtime ID namespace;
- `collection_entity_id(collection, purpose)` when an `EntityId` must retain
  the collection namespace and round-trip without external collection
  context;
- `opaque_id(purpose)` for non-Entity opaque identifiers.

Every helper records only structural execution-capability CallTree metadata.
Generated values, raw seeds, and entropy tokens MUST NOT be recorded.
Component behavior MUST NOT read the root seed, raw entropy, scheduler,
executor, or test-control handle.

`IdGenerationContext` is constructed from the selected execution clock and a
dedicated ID entropy context. Controlled bindings derive the ID entropy seed
from the profile fingerprint, invocation identity, and invocation ordinal.
Entity and opaque ID sequences are isolated by collection and purpose. ID
generation therefore MUST NOT consume a domain random stream.

Component-visible behavior MUST NOT call ambient clock, UUID, random, sleep,
environment, system-property, or host filesystem APIs directly. Bootstrap,
transport, repository discovery, provider, and monotonic diagnostics may use
host facilities when the result is outside component semantics.

Workflow instance lifecycle is runtime semantics. Instance identity MUST use
the invocation-bound `IdGenerationContext`; `startedAt`, `updatedAt`, and every
history timestamp MUST use the invocation-bound execution clock. Workflow
history values MUST require an explicit timestamp and MUST NOT carry an ambient
construction default. Equivalent controlled capabilities and event sequences
therefore reproduce Workflow identity and lifecycle time, while purpose-local
ID sequencing prevents collisions within one invocation.

## CNCF-Owned Ordering

Under a `controlled` profile, CNCF serializes the runtime work that it owns.
The normative tie-breakers are:

- operational timers: due instant, then timer registration sequence;
- ready Jobs: numeric priority, then Job/work enqueue sequence;
- delayed retry: original Job priority, then the retry enqueue sequence when
  its operational due instant becomes eligible;
- same-Job async continuation Tasks: inherited Job priority, then Task enqueue
  sequence;
- Event subscriptions: numeric priority, then subscription registration
  sequence.

`runUntilIdle` drains eligible timers and registered CNCF work queues in their
runtime registration order. Advancing manual time only makes due work eligible;
it does not bypass the queue tie-breakers above.

A seeded named random stream is deterministic only when its calls have a
deterministic order. The `controlled` profile therefore supports shared-stream
use only from CNCF-managed serialized work, such as one ActionCall or the
ordered Job/Event Tasks above. Components MUST NOT create threads or executors
to call a shared stream concurrently. Such arbitrary-thread shared-stream use
is outside the replay contract and makes the execution partially controlled or
invalid when replay is required; CNCF does not assign reproducible values by
host thread arrival order.

## Ambient-State Boundary

Direct host-state access is classified by semantic ownership rather than by
API name alone:

1. Component-visible values and persistent domain state use `ExecutionContext`
   or protected internal DSL capabilities.
2. Job/Event/runtime semantic timestamps, delays, retries, and ordering use the
   selected operational clock and scheduler.
3. External effects remain behind providers or drivers.
4. Performance duration and similar diagnostics retain a monotonic source.
5. Bootstrap, transport, and repository discovery may inspect host state and
   must snapshot any value that later becomes component-visible.
6. Executable specifications use controlled facilities instead of host sleep
   or arrival timing when those affect the asserted behavior.

CAR lint reports direct clock, UUID/random, sleep, environment/property,
filesystem, thread, and executor access in component sources. A framework or
provider boundary may suppress a warning only with an explicit documented
exception; suppression does not make that value part of the replay contract.

## Replayability

The runtime computes `ReplayabilityAssessment` from every observable dimension.

- `Replayable`: all CNCF-owned dimensions and declared provider inputs are
  controlled for the scenario;
- `PartiallyControlled`: at least one dimension is controlled and at least one
  observable dimension is uncontrolled;
- `Uncontrolled`: no replay contract is claimed;
- `Invalid`: the selected profile combination cannot execute.

Assessment reasons include uncontrolled request arrival, realtime scheduling,
missing invocation identity, uncontrolled provider responses, or concurrent
shared-stream access.

The diagnostic projection may expose profile mode, sanitized fingerprint,
dimension modes, replayability state, and reasons. It MUST NOT expose raw seeds,
entropy, credentials, or secret environment values.

## Failure Semantics

Invalid profile configuration fails at bootstrap as
`Consequence.Failure(Conclusion)`. CNCF MUST use the normal
Consequence/Conclusion observation structure and MUST NOT create a parallel
failure taxonomy or use application-owned `Status.detailCodes`.

## Compatibility

- `Standard` preserves ordinary production execution.
- Existing offset-clock behavior remains supported.
- Existing callers that do not select an execution profile receive
  `Standard`.
- Existing Job timer types may remain adapters but cannot remain independent
  policy sources.
- Deterministic IDs are test-only during Phase 31.
- No provider is implicitly replaced merely because a profile is selected.
