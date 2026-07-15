# Execution Determinism Handoff (2026-07-15)

## Position of This Record

This journal entry records the investigation state and implementation handoff as
of 2026-07-15. It is not a normative design or specification. Decisions that
become CNCF contracts must be promoted to `docs/design` and `docs/spec` in
accordance with `docs/rules/document-lifecycle.md`.

## Context

The SIE Phase 4.5 smoke work required execution from a virtual start date while
time continued to advance. CNCF now resolves `textus.clock.virtual-start-at`
and carries the resulting offset clock in `ExecutionContext`. Component logic
can access it through the internal DSL methods `execution_clock`,
`current_instant`, and `current_zoned_datetime`.

The follow-up investigation considered which other process-global or
nondeterministic inputs should become CNCF-controlled execution capabilities so
that CAR behavior can be tested reproducibly.

The resulting non-normative feature specification exploration is recorded in
`docs/notes/execution-determinism-capability-design.md`.

## Confirmed Current State

- Goldenport core `ExecutionContext.Core` already models environment, virtual
  machine assumptions, i18n, locale, timezone, encoding, clock, math context,
  random context, and logger.
- CNCF currently resolves and rebinds the execution clock from `RuntimeConfig`.
- Locale, timezone, encoding, line separator, math context, environment
  variables, and random context are still constructed with fixed framework
  defaults in CNCF `ExecutionContext`.
- `RandomContext.from` currently returns a constant-value implementation; it
  does not yet provide a seeded advancing random sequence or independent named
  streams.
- `IdGenerationContext` has deterministic and nondeterministic implementations,
  but the default implementation reads wall-clock time and entropy directly.
- Job runtime has its own `JobTimeSource`, `ManualJobTimeSource`, and timer
  boundary. These are not yet unified with the clock carried by the CNCF
  `ExecutionContext`.
- HTTP and several persistence/provider boundaries are already injectable
  through runtime drivers or SPI implementations.
- Direct uses of wall-clock time, UUID generation, sleep/polling, environment
  lookup, temporary files, and executors remain in CNCF runtime code. These are
  migration evidence, not a statement that every occurrence has domain-visible
  behavior.

## Handoff Boundary

The next design slice should treat deterministic execution as resolved runtime
capabilities, not as a copy of bootstrap configuration stored in
`ExecutionContext`.

The core execution-capability candidates are:

1. wall clock and controllable test clock;
2. scheduler, delay, timeout, retry, and deadline handling;
3. seeded random generation with independent named streams;
4. ID and entropy generation separated from domain random streams;
5. deterministic executor and observable asynchronous ordering;
6. locale, timezone, charset, line separator, i18n, and math policy;
7. an immutable environment snapshot when component behavior depends on
   environment values.

Effectful resources should remain runtime/scope-owned drivers or providers:

- HTTP and external network access;
- filesystem, work area, temporary storage, and blob storage;
- process execution;
- datastore, entity store, event bus, message delivery, Job provider, and AI or
  knowledge providers;
- fault and latency injection for provider contract tests.

Component implementations should access both categories only through CNCF
internal DSL or CNCF-provided service boundaries. Direct access to
`Instant.now`, `UUID.randomUUID`, JVM random state, `Thread.sleep`, environment
variables, system properties, or host filesystem state should be treated as
migration debt when it can affect component-observable behavior.

## Important Distinctions

- An offset clock controls reported wall time but does not eliminate real waits.
  Tests for delayed jobs, retries, and timeouts also need a controllable clock
  and scheduler that can be advanced without sleeping.
- A random seed alone is insufficient when parallel operations share one mutable
  sequence. Random streams should be derived by stable purpose keys such as
  execution, domain, retry, and ID generation.
- ID generation must not consume the same stream as domain decisions. Adding an
  identifier must not change a later business-random result.
- Monotonic elapsed-time measurement is distinct from wall-clock time. Internal
  performance measurement may continue to use a monotonic source, but any
  observable timeout or duration contract needs a testable boundary.
- `ExecutionContext` must carry resolved execution assumptions only. It must not
  regain ownership of application configuration snapshots or persistent system
  state.

## Suggested Promotion and Implementation Order

Before implementation, promote the selected contract into design/spec records.
The investigated order was:

1. define seeded and named `RandomContext` behavior;
2. route ID/entropy generation through execution capabilities;
3. unify scheduler, timeout, retry, and Job time with execution time;
4. migrate observable event, workflow, Job, tag, and observability timestamps to
   CNCF time accessors;
5. resolve locale, timezone, charset, math, and environment assumptions at
   bootstrap;
6. add deterministic executor and filesystem/test-driver boundaries where
   observable behavior requires them.

## Test Handoff

A future CNCF test kit should be able to assemble one explicit execution
profile containing a virtual or controllable clock, named seeded random streams,
deterministic IDs, a manual scheduler, deterministic execution ordering, and
fake effect drivers. Executable specifications should verify that component
logic obtains these capabilities through the internal DSL and does not depend
on ambient JVM or OS state.
