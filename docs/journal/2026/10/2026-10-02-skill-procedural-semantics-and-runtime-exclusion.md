# Skill Logic and Runtime Execution Boundary

Date: 2026-10-02
Status: corrected design direction

## Correction

Generic Skill Workflow Support must not treat Skill Logic as the owner of a human-readable top-level procedure. That procedure belongs to Skill Specification/documentation. Executable control procedure belongs to Workflow/StateMachine.

Generic Skill Workflow Support projects a bounded WorkOrder/Continuation to Skill Logic and accepts its Result/Evidence.

```text
Skill Specification
  human-readable work description
        |
        v
Workflow / StateMachine
  executable sequencing / branching / iteration
  continuation / admission / coordination
        |
        | bounded WorkOrder
        v
Skill Logic
  semantic AI work
  bounded request -> Result / Evidence
        |
        v
CNCF Runtime
  execution ownership / exclusion / lease
  persistence / scheduling / recovery
```

## Local execution assumption

One Skill Logic invocation processes one assigned WorkOrder as a local sequential unit. It does not coordinate peer workers or own the overall procedure.

Provider implementation may internally parallelize computation, but concurrent-worker coordination is not part of Skill semantics.

## Runtime implication

Before semantic work is projected as a Skill WorkOrder, the surrounding execution mechanism is responsible for required execution ownership/exclusion.

Skill Logic therefore does not inspect competing Skill/agent/process executions and does not acquire/release locks, manage leases, resolve deadlocks, or invent retry/recovery protocols.

Existing Aggregate/resource locking facilities may be used by execution policy, but they remain Workflow/runtime concerns.

## Stable separation

- Skill Specification: human-readable purpose and work description.
- Skill Logic: thin semantic worker / Workflow adapter.
- Workflow/StateMachine: executable control procedure and deterministic coordination.
- Runtime: concurrency, exclusion, persistence, scheduling and recovery guarantees.

If Skill Logic accumulates sequencing, loops, closure rules, concurrency, or recovery logic, move that responsibility to Workflow/runtime rather than extending the Skill execution model.
