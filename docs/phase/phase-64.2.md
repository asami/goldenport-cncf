# Phase 64.2 - UnitOfWork Program Planning, Interpreter, and Testability

status=planned
planned_at=2026-09-05
revised_at=2026-09-08
depends_on=[Phase 64.1](phase-64.1.md)
producer=asami/cozy Phase 47.2 sequence (47.2, 47.2.1, 47.2.2)

## Purpose

Extend CNCF's existing Free × UnitOfWork execution model so CML
StateMachine, Composite StateMachine, and Workflow logical actions compile into
and execute through the canonical `UnitOfWorkOp` algebra, while making the
resulting behavior first-class testable without production infrastructure.

This phase does **not** define a second StateMachine/Workflow Action algebra.

The canonical path is:

```text
CML StateMachine / Composite StateMachine / Workflow
  -> Cozy/SimpleModeler logical action resolution
  -> ExecProgram[A]
       = Program[UnitOfWorkOp, A]
  -> UnitOfWork program analysis / planning
  -> Test / Simulation Interpreter
  -> Production UnitOfWork Interpreter / Drivers
```

`UnitOfWorkOp[A]` remains the single source of truth for executable intents.

## Cozy Phase 47.2 split coordination — 2026-09-08

Cozy's former combined Phase 47.2 plan was approved as the ordered
`47.2 -> 47.2.1 -> 47.2.2` sequence. This Phase remains the CNCF-owned
consumer-runtime ledger; the split only gives each unfinished UTP item one
cross-repository delivery boundary:

| Cozy delivery unit | CNCF Phase 64.2 items | Frozen handoff |
| --- | --- | --- |
| Phase 47.2 | UTP-01, UTP-03, UTP-04, UTP-05 | Consumer inventory, classification, planner, and deterministic-test foundation |
| Phase 47.2.1 | UTP-02 | Versioned Cozy logical-action compiler/generated ABI admitted by CNCF |
| Phase 47.2.2 | UTP-06, UTP-07, UTP-08, UTP-09 | Frozen compiler/ABI exercised through composed fixture acceptance |

The UTP identifiers and their execution authority remain in this CNCF Phase.
No row authorizes Cozy to prove CNCF runtime behavior, and no row introduces a
parallel StateMachine/Workflow action algebra.

## Existing Runtime Authority

The existing CNCF contract is the foundation:

```text
sealed trait UnitOfWorkOp[A]

type ExecProgram[A] = Program[UnitOfWorkOp, A]
type ExecUowM[A]    = UowM[UnitOfWorkOp, A]
```

Both declarative Free/UoW paths and direct execution DSLs ultimately construct
this operation algebra. Phase 64.2 must preserve that convergence.

Any proposed new primitive must first answer:

1. Can the CML logical action compile to an existing `UnitOfWorkOp` sequence?
2. Can missing semantics be represented as metadata/planning policy rather than
   a new operation?
3. If neither is possible, is a new generic `UnitOfWorkOp` primitive justified
   for CNCF beyond StateMachine/Workflow?

Only the third case may extend the algebra.

## CML Logical Action Boundary

CML logical action names are not runtime operation classes.

For example:

```text
recordAuthorization
reserveShipment
releaseShipment
```

may compile to one or more existing UnitOfWork operations.

```text
CML Logical Action
      -> resolver/compiler
      -> ExecProgram[UnitOfWorkOp]
```

CNCF consumes the resulting program and metadata; it does not parse CML syntax
or reconstruct model meaning through strings.

## Testability Principle

A StateMachine/Workflow runtime contract is incomplete if verification requires
real databases or external services.

CNCF must provide deterministic test boundaries for:

- transition selection and rejection;
- composite-state derivation;
- compiled `ExecProgram` ordering/provenance;
- UnitOfWork program analysis and local atomic segmentation;
- failure injection;
- abort/rollback behavior;
- local atomic abort/rollback behavior;
- stable occurrence/correlation identity;
- external Operation/Job/HTTP/process intent; and
- correlation/observability metadata.

## Runtime Layers

```text
Pure Model
  transition/rule evaluation
      |
      v
ExecProgram[UnitOfWorkOp]
  structured executable intent
      |
      v
UnitOfWork Analysis / Planner
  local atomic / non-local deferred classification
      |
      +--> Test Interpreter / Fake Drivers
      +--> Simulation Interpreter
      +--> Production Interpreter / Drivers
```

The same `ExecProgram` must be consumable by test and production paths.

## Existing UnitOfWorkOp Classification

Phase 64.2 must classify the existing operation algebra by execution semantics
instead of inventing parallel semantic families.

Examples include:

- datastore/entity mutation and read operations;
- authorization/evaluation metadata operations;
- HTTP operations;
- shell/process execution;
- embedded datastore operations;
- blob/content operations; and
- future generic CNCF operations already admitted by the UnitOfWork contract.

The classification is planner metadata/policy. It need not mirror source-code
package/comment groupings exactly.

## Program Analysis and Planner Contract

`ExecProgram` is not synonymous with one datastore transaction. Existing
`UnitOfWorkOp` already contains both local and externally visible effects.

The planner therefore consumes the Free structure plus CML/generated semantic
metadata and produces an explicit execution plan, conceptually:

```text
ExecutionPlan
  +-- LocalAtomicSegment
  +-- NonLocalDeferredSegment
```

Planning must preserve:

- operation identity/occurrence;
- CML logical action identity;
- constituent/composite provenance;
- causal ordering;
- target/resource identity;
- local atomicity requirement/capability;
- non-local/deferred classification;
- correlation identity;
- authorization context; and
- source/model identity.

Tests must be able to inspect the plan without executing it.

## Algebra Extension Rule

Phase 64.2 explicitly resists adding StateMachine-specific operations to
`UnitOfWorkOp`.

A new `UnitOfWorkOp` case is acceptable only when:

- the behavior is a reusable CNCF executable intent;
- existing operations cannot express it without semantic loss;
- it is not merely CML model metadata;
- it can be interpreted consistently by production and test runtimes; and
- its transaction/effect semantics can be classified explicitly.

StateMachine transition identity, composite-rule identity, and Workflow model
structure should normally remain metadata around/composing executable intents,
not new low-level operations.

## Deterministic Runtime Capabilities

Nondeterminism affecting observable behavior must be controlled through existing
or minimal injectable capabilities, including where required:

- Clock;
- IdGenerator;
- RandomSource;
- subject/tenant/security context; and
- external result stubs/fake drivers.

Reuse existing CNCF context/runtime abstractions before introducing new ones.

## Test Interpreter / Fake Driver Contract

Tests must be able to:

- inspect the `ExecProgram` structure;
- record interpreted `UnitOfWorkOp` occurrences in deterministic order;
- expose planner segmentation;
- return configured typed results;
- fail a selected operation occurrence deterministically;
- report whether the root transition commits or aborts;
- avoid real external I/O by default;
- preserve stable logical occurrence/correlation ids; and
- emit the same structured outcome categories used by production execution.

Testing should prefer interpreter/fake-driver substitution over large mocking
surfaces.

## StateMachine Test Contract

For a local StateMachine:

```text
initial entity state
 + trigger
 + explicit environment
 -> transition decision
 -> compiled ExecProgram
 -> UnitOfWork plan
 -> interpreted outcome
 -> final committed or unchanged state
```

A failure in a required atomic segment aborts the transition and leaves domain
state unchanged.

## Composite StateMachine / Workflow Test Contract

For a composite/workflow model:

```text
constituent states/configuration
 + constituent committed transition
 -> derived composite state
 -> derived composite transition
 -> compiled composite ExecProgram
 -> UnitOfWork plan
 -> interpreted outcome
```

Tests must be able to inspect both the collapsed higher-level behavior and the
underlying constituent transitions/programs.

## Property / Model-based Testing Hooks

Runtime-neutral surfaces should allow test frameworks to check properties such
as:

- rejected transitions do not mutate state;
- every committed transition ends in a declared state;
- repeated duplicate trigger delivery does not duplicate logical progression;
- the same deterministic model input produces the same `ExecProgram` and plan;
- a failed atomic segment cannot publish a committed transition; and
- a committed constituent transition derives the same composite outcome as the
  generated rule contract.

No single property-test library is mandated.

## Representative Acceptance

Use the shared Order/Payment/Shipment CML fixture from Cozy Phase 47.2.2 after
the Phase 47.2.1 compiler/ABI handoff.

Acceptance must prove at least:

- pure derivation of `ReadyToShip` from constituent configuration;
- lower `recordAuthorization` and upper `reserveShipment` logical action order;
- deterministic compilation to existing `UnitOfWorkOp` / `ExecProgram`;
- deterministic UnitOfWork planning;
- injected failure in the program corresponding to `recordAuthorization`
  aborts the atomic transition;
- non-local/external effects are classified explicitly as outside the Phase 64.1 local atomic guarantee;
- no real database/network provider is required for model/interpreter tests;
- production and test execution consume the same canonical program shape; and
- no parallel `ActionOp` execution algebra is required.

## Work Stack

| ID | Stage | Outcome | Status |
| --- | --- | --- | --- |
| UTP-01 | Existing Free/UoW inventory | `UnitOfWorkOp`, `ExecProgram`, `ExecUowM`, direct/declarative DSLs, interpreter/drivers, metadata, and current tests are inventoried. | planned |
| UTP-02 | CML compilation ABI | Cozy Phase 47.2.1 logical-action binding/compilation contract to `ExecProgram` is admitted/versioned. | planned |
| UTP-03 | Operation effect classification | Existing `UnitOfWorkOp` cases are classified for local-atomic versus non-local/deferred execution where relevant. | planned |
| UTP-04 | Planner model | Explicit local-atomic planning, ordering, capability admission, and non-local/deferred classification are frozen. | planned |
| UTP-05 | Deterministic test runtime | Program inspection, fake drivers, typed result stubbing, and failure injection are defined/implemented. | planned |
| UTP-06 | Production interpreter alignment | Production execution preserves the same logical program/plan identities and structured outcomes. | planned |
| UTP-07 | StateMachine acceptance | Simple/local StateMachine success/rejection/abort behavior is proven through compiled `ExecProgram` without production I/O. | planned |
| UTP-08 | Composite/Workflow acceptance | Derived composite transition and lower/upper programs are proven through deterministic local/test execution with the shared fixture; advanced compensation/recovery is deferred. | planned |
| UTP-09 | Algebra gap review | Any required new `UnitOfWorkOp` primitive is justified as generic CNCF functionality or rejected. | planned |

## Acceptance

- `UnitOfWorkOp` remains the canonical execution algebra.
- StateMachine/Workflow actions compile to `ExecProgram` rather than a parallel
  Action algebra.
- Test and production paths consume the same structured executable intent.
- Failure injection can target executable-intent occurrences deterministically.
- Atomic failure aborts the root transition according to the admitted plan.
- Planner output is inspectable before execution.
- Runtime nondeterminism is injectable where it affects observable behavior.

## Non-Goals

- Creating `StateMachineActionOp` / `WorkflowActionOp` as a second canonical
  runtime algebra.
- Replacing `UnitOfWorkOp` or the existing Free × UnitOfWork design.
- Replacing integration/end-to-end tests.
- Building a universal simulation engine.
- Deterministic replay of arbitrary user code.
- Advanced distributed transaction, compensation, and recovery semantics owned by the dedicated follow-up Phase.

## References

- `phase-64.md`
- `phase-64.1.md`
- `../design/free-unitofwork-execution-model.md`
- `../../src/main/scala/org/goldenport/cncf/unitofwork/UnitOfWorkOp.scala`
- `../../src/main/scala/org/goldenport/cncf/unitofwork/types.scala`
- `../design/unitofwork-program-planning.md`
- - `asami/cozy/docs/phase/phase-47.2.md`
- `asami/cozy/docs/phase/phase-47.2.1.md`
- `asami/cozy/docs/phase/phase-47.2.2.md`
