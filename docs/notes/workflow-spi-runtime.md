# Workflow SPI Runtime

## Execution loop

CNCF Workflow Runtime executes the next Action selected by Workflow/StateMachine semantics and handles a typed execution outcome:

```text
Completed(Result)
Suspended(Continuation)
Failed(Error)
```

There is no Workflow-wide orchestration/continuation mode.

`advance` continues through `Completed` actions until it reaches a suspension, terminal state, wait/failure policy boundary, or configured safety bound.

## Workflow SPI

Required typed operations that may be supplied by external participants form the Workflow SPI.

A Workflow SPI specification includes:

- operation identity
- typed input/result
- Context contract/reference requirements
- Completion contract
- Evidence contract
- required capabilities/constraints where generic

Provider bindings:

```text
RequiredOperation
  -> LocalProvider
  -> ExternalContinuationProvider
  -> TestProvider
```

A local provider can complete synchronously/asynchronously under runtime control. An external provider causes suspension and returns a durable Continuation. A test provider enables deterministic executable Workflow specifications.

## Generic Skill Workflow

CNCF Generic Skill Workflow Support projects suspended Workflow SPI operations into compact Skill commands/WorkOrders. The Skill projection is not the source of truth; the Workflow SPI and Continuation are.

This keeps software-development-specific semantics in `sm-workflow` while allowing other Skill workflows to use the same required-interface runtime.
