# Skill Procedural Semantics and Runtime Exclusion

Date: 2026-10-02
Status: design direction

## Principle

Generic Skill Workflow Support must present Skill execution as a conceptually single-threaded procedural unit.

A Skill does not participate in concurrency semantics. It must not acquire/release locks, coordinate peers, implement lease ownership, resolve deadlocks, or contain concurrent retry/recovery protocols.

Those responsibilities belong to Workflow/runtime infrastructure.

```text
Skill
  human-readable sequential procedure
        |
        v
Workflow / StateMachine
  execution ownership
  coordination
  continuation
  admission
        |
        v
CNCF Runtime
  exclusion / locking / lease
  persistence
  job / recovery
```

"Single-threaded" describes the Skill contract, not the provider implementation. Internal implementation may use parallel computation, but Generic Skill Workflow Support must not expose concurrent-worker coordination as Skill semantics.

## Runtime implication

Before semantic work is projected as a Skill WorkOrder, the surrounding execution mechanism is responsible for establishing any required execution ownership/exclusion for the affected resource scope.

The Skill therefore executes as if the work assigned to it is exclusively and safely available. It does not inspect competing Skill executions.

Existing Aggregate/resource locking facilities may be used by concrete execution policies, but locking must remain a runtime concern rather than a Skill command vocabulary.

## Human-readable procedure

Skill also has value as the human-readable top-level procedure of work. It may orchestrate calls to deterministic Operations, Workflow/StateMachine execution, AI-native semantic work, human approval, and sub-Skills while keeping the overall work understandable to a person.

This yields a stable separation:

- Skill: procedural knowledge, AI-native work, ambiguous/non-routine work, human-readable top-level work description.
- Workflow/StateMachine: deterministic execution semantics and coordination.
- Runtime: concurrency, exclusion, persistence, recovery and infrastructure guarantees.

When a Skill starts accumulating concurrency or recovery logic, move that behavior into Workflow/runtime rather than extending the Skill language.
