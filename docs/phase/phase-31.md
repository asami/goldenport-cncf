# Phase 31 - Deterministic Execution Capabilities

status = closed

## 1. Purpose

Phase 31 implements the `9.28 Execution Determinism` development item. It makes
component-observable execution reproducible by resolving generic execution
assumptions and CNCF runtime controls as one coherent execution profile.

Phase 31 starts from the execution-clock baseline and extends determinism to
random values, ID entropy, Job/Event operational time, CNCF-owned asynchronous
ordering, locale/environment assumptions, and replayability diagnostics.

## 2. Scope

- Define `standard`, `seeded`, and test-only `controlled` profiles.
- Extend goldenport core with seeded named random streams and dedicated entropy.
- Resolve and bind one coherent execution profile at ActionCall creation.
- Route component access through protected CNCF internal DSL helpers.
- Generate IDs from the selected clock and dedicated ID entropy.
- Adapt JobEngine time, timer, retry, delay, and queue ordering to the profile.
- Add test-harness-only manual time advance and run-until-idle controls.
- Resolve locale, timezone, charset, line separator, math, i18n, and an
  allowlisted environment snapshot at bootstrap.
- Add sanitized profile/replayability introspection.
- Classify and migrate observable ambient JVM/OS state access.

## 3. Boundaries

- Generic clock, random, locale, timezone, charset, math, VM, and environment
  assumptions remain goldenport core-owned.
- CNCF owns ActionCall binding, ID generation, Job/Event integration, runtime
  scheduling controls, internal DSL access, and diagnostics.
- Mutable schedulers, timers, executors, and controlled clocks remain
  runtime-owned; `ExecutionContext` carries resolved assumptions and handles.
- HTTP, filesystem, datastore, event bus, process, AI, and knowledge remain
  injectable drivers/providers.
- `controlled` is explicit test behavior and must not activate during ordinary
  production startup.
- Built-in timing remains bounded operational Job control. Phase 31 does not
  add cron, calendar, workflow timer, or general scheduling semantics.
- Security randomness must not become deterministic merely because domain
  randomness is seeded.

## 4. Active Work Stack

- A (DONE): ED-01 - Freeze the execution-profile contract.
- B (DONE): ED-02 - Seeded named random streams and dedicated entropy.
- C (DONE): ED-03 - CNCF profile resolution and ActionCall binding.
- D (DONE): ED-04 - Internal DSL and capability-based ID generation.
- E (DONE): ED-05 - Unified time and Job/Event scheduling.
- F (DONE): ED-06 - Resolved environment assumptions.
- G (DONE): ED-07 - Deterministic ordering, migration, and enforcement.
- H (DONE): ED-08 - Introspection, replay verification, and closure.

Closure guidance:

- Phase 31 is closed. Future deterministic execution work must be selected as
  an explicit follow-up rather than extending the controlled replay contract
  implicitly.

## 5. Development Items

- [x] ED-01: Promote the execution-profile ownership, configuration,
      compatibility, and replayability contract.
- [x] ED-02: Implement seeded named random streams and separate entropy.
- [x] ED-03: Resolve and bind coherent execution profiles.
- [x] ED-04: Add internal DSL access and profile-based ID generation.
- [x] ED-05: Integrate manual time with JobEngine and async Event execution.
- [x] ED-06: Resolve environment and internationalization assumptions.
- [x] ED-07: Fix CNCF-owned ordering and migrate ambient-state debt. Ordering,
      audit classification, CAR lint, Tag timestamps, canonical Event reception,
      transition lifecycle events, EventStore record materialization, and
      user-notification forwarding diagnostics,
      Job lifecycle Event persistence and runtime dispatch,
      Workflow lifecycle identity/time, Job input timestamps, and JobDefinition
      lifecycle timestamps, Job/Task/Action runtime identity, and
      pre-ActionCall authorization-denial Event identity/time,
      InformationSpace lifecycle/materialization timestamps and Knowledge
      and Entity working-set lifecycle timestamps plus Entity working-set
      admission/residency evaluation, temporal ABAC authorization, and
      Aggregate edit-context lifecycle timestamps/expiry and built-in
      `system.status` timestamp/uptime are complete. The final audit classifies
      retained direct host access as transport, compatibility polling,
      realtime adapter, monotonic diagnostic, provider, injected-clock, or
      bootstrap/repository behavior outside the controlled replay contract.
- [x] ED-08: Add diagnostics, replay scenarios, developer guidance, and close
      Phase 31. The completed scope includes sanitized diagnostics and
      redaction, two-runtime replay verification, full CNCF validation, and
      final review. Distributed replay and uncontrolled external providers
      remain explicitly outside this phase.

Detailed status and acceptance evidence are recorded in
`phase-31-checklist.md`.

## 6. Completion Conditions

Phase 31 closes only after verifying that:

- the same controlled profile and invocation sequence produce the same
  business data, IDs, domain/Event timestamps, Task/Event ordering, retry
  schedule, and sanitized profile fingerprint;
- adding ID generation does not change domain random results;
- changing one purpose stream does not alter unrelated streams;
- manual time drives due Job/retry work without real sleeping;
- same-transaction synchronous Event handling remains immediate;
- standard production execution remains production-safe and backward
  compatible;
- invalid or unsafe profile combinations fail with structured `Conclusion`;
- seeds, security entropy, credentials, and environment secrets never appear
  in diagnostics;
- relevant core and CNCF executable specifications and full test suites pass;
- authoritative design/spec/developer documents describe the implemented
  contract and deferred distributed/provider replay work remains explicit.

## 7. Working References

- `docs/notes/execution-determinism-capability-design.md`
- `docs/journal/2026/07/execution-determinism-handoff-2026-07-15.md`
- `docs/design/execution-context.md`
- `docs/design/execution-clock.md`
- `docs/design/job-management.md`
- `docs/design/timer-scheduling-boundary.md`
- `docs/spec/test-policy.md`

The note and journal are non-normative inputs. Phase implementation decisions
must be promoted into design/spec documents before closure.

All completion conditions were verified by ED-08. The controlled replay
contract does not claim deterministic provider-internal concurrency,
uncontrolled external responses, or distributed multi-machine execution.
