# Phase 64.2 - Action Planner, Interpreter, and Testability Runtime

status=planned
planned_at=2026-09-05
depends_on=[Phase 64.1](phase-64.1.md)
producer=asami/cozy Phase 33.2

## Purpose

Implement the CNCF-side planner/interpreter contract for generated typed Action
Programs and make StateMachine, Composite StateMachine, and Workflow execution
first-class testable without production infrastructure.

The canonical path is:

```text
CML StateMachine / Workflow
  -> Cozy/SimpleModeler typed model + Action Program
  -> CNCF Action Planner
  -> Test / Simulation Interpreter
  -> Production Interpreter
```

CNCF must execute generated logical action programs without reconstructing CML
syntax or accepting opaque callbacks as the canonical path.

## Testability Principle

A StateMachine/Workflow runtime contract is incomplete if its behavior can only
be verified against real databases or external services.

CNCF must provide deterministic test boundaries for:

- transition selection and rejection;
- composite-state derivation;
- generated action ordering;
- transaction segment planning;
- failure injection;
- abort/rollback behavior;
- compensation planning/execution behavior;
- retry/idempotency identity;
- Operation/Job submission intent; and
- correlation/observability metadata.

## Runtime Layers

```text
Pure Model
  transition/rule evaluation
      |
      v
Action Program
  typed logical effects
      |
      v
Action Planner
  local atomic / 2PC / compensatable / irreversible segments
      |
      +--> Test Interpreter
      +--> Simulation Interpreter
      +--> Production Interpreter
```

The same generated Action Program must be usable by all interpreters.

## Deterministic Runtime Capabilities

Runtime nondeterminism that affects observable behavior must be injected through
controlled capabilities. Candidate capabilities include:

- Clock
- IdGenerator
- RandomSource
- subject/tenant/security context
- external Operation/Job result stubs

Production interpreters bind real implementations; tests bind deterministic
ones.

## Test Interpreter

The canonical test interpreter must be able to:

- record interpreted ActionOps in deterministic order;
- expose planner segmentation;
- return configured typed results;
- fail a selected ActionOp/occurrence deterministically;
- distinguish retryable and non-retryable failures;
- report whether the root transition commits or aborts;
- expose planned compensations in reverse causal order where required;
- simulate compensation success/failure;
- avoid external I/O by default;
- preserve stable logical occurrence/correlation ids; and
- emit the same structured outcome categories used by production runtime.

## StateMachine Test Contract

For a simple/local StateMachine, tests must be able to verify:

```text
initial entity state
 + trigger
 + explicit environment
 -> transition decision
 -> generated Action Program
 -> interpreted outcome
 -> final committed or unchanged state
```

A failure in a required atomic action segment must abort the transition and
leave domain state unchanged.

## Composite StateMachine / Workflow Test Contract

Tests must also support:

```text
constituent states/configuration
 + constituent committed transition
 -> derived composite state
 -> derived composite transition
 -> composite Action Program
 -> interpreted outcome
```

A test can verify both collapsed Workflow behavior and the underlying
constituent StateMachine sequence.

## Property / Model-based Testing Hooks

CNCF should expose runtime-neutral helpers or data surfaces that allow test
frameworks to check properties such as:

- rejected transitions do not mutate state;
- every committed transition ends in a declared state;
- repeated duplicate trigger delivery does not duplicate logical progression;
- the same deterministic model input produces the same action plan;
- compensation plans correspond to successfully completed compensatable
  actions;
- a failed atomic segment cannot publish a committed transition; and
- a committed constituent transition derives the same composite outcome as the
  generated rule contract.

CNCF should not mandate one property-test library.

## Planner Contract

The planner consumes generated ActionOp metadata and produces explicit segments,
for example:

```text
ExecutionPlan
  +-- LocalAtomicSegment
  +-- DistributedAtomicSegment
  +-- AfterCommitCompensatableSegment
  +-- IrreversibleSegment
```

The planner must preserve logical action identity, source provenance, ordering,
idempotency identity, compensation relation, and target/runtime capability
requirements.

Tests must be able to inspect the plan without executing it.

## Representative Acceptance

Use the shared Order/Payment/Shipment CML fixture from Cozy Phase 33.2.

Acceptance must prove at least:

- pure derivation of `ReadyToShip` from constituent configuration;
- lower `recordAuthorization` and upper `reserveShipment` action order;
- deterministic Action Program generation and planner segmentation;
- injected `recordAuthorization` failure aborts the transition;
- compensatable `reserveShipment` produces `releaseShipment` compensation when
  a later non-atomic step fails;
- compensation failure is recorded as recovery work rather than rewritten as a
  rollback;
- no real database/network provider is required for the model/interpreter test;
- the production interpreter consumes the same generated program/plan shape.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| ATP-01 | Generated ABI inventory | Cozy Phase 33.2 ActionOp/program/test metadata is inventoried and versioned. | planned |
| ATP-02 | Planner model | Explicit segment planning, ordering, capability admission, and compensation planning are frozen. | planned |
| ATP-03 | Deterministic capabilities | Clock/id/random/context/external-result interfaces needed by tests are defined minimally. | planned |
| ATP-04 | Test interpreter | Recording, typed result stubbing, failure injection, abort and compensation outcomes are implemented. | planned |
| ATP-05 | Simulation/model hooks | Pure model and optional path-exploration/property-test hooks are exposed. | planned |
| ATP-06 | Production interpreter alignment | Production effects use the same logical plan and structured outcome model. | planned |
| ATP-07 | StateMachine acceptance | Simple/local StateMachine success/rejection/abort behavior is proven without production I/O. | planned |
| ATP-08 | Composite/Workflow acceptance | Derived composite transition, lower/upper actions, compensation, duplicate delivery, and recovery are proven with the shared fixture. | planned |

## Acceptance

- StateMachine and Workflow models are testable without real external
  infrastructure.
- Test and production interpreters consume the same generated Action Program.
- Failure injection can target any logical ActionOp occurrence deterministically.
- Atomic failure aborts the root transition according to the admitted
  transaction plan.
- Compensation is observable as a new action sequence, not hidden rollback.
- Planner output is inspectable before execution.
- Runtime nondeterminism is injectable where it affects observable behavior.
- Test outcomes use the same identities and structured failure categories as
  production execution.

## Non-Goals

- Replacing integration/end-to-end tests.
- Building a universal simulation engine.
- Deterministic replay of arbitrary user code.
- Making external systems transactional when their capabilities do not support
  it.
- Hiding production-only semantics from the generated Action Program.

## References

- `phase-64.md`
- `phase-64.1.md`
- `../notes/action-transaction-compensation-runtime-provisional-specification.md`
- `asami/cozy/docs/phase/phase-33.2.md`
