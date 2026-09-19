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

A protocol-independent Required SPI specification includes:

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

## Continuation SPI and IoC

Continuation Protocol does not replace Required SPI. After a suspension is
persisted, Continuation SPI projects the Required SPI contract as a generic
`ContinuationRequest` and accepts a typed `ContinuationResult` through an
adapter injected by ComponentFactory/provider construction.

```text
Required SPI -> Provider -> Suspended(Continuation)
  -> Continuation SPI -> injected external adapter
  -> ContinuationResult -> resume
```

The adapter may target Skill, Human, UI, or remote execution. Delivery, lease,
model selection, and host scheduling remain outside Workflow semantics.

## Generic Skill Workflow

CNCF Generic Skill Workflow Support projects Continuation SPI requests into compact Skill commands/WorkOrders. The Skill projection is not the source of truth; Required SPI, the durable Continuation, and WorkflowInstance state are.

This keeps software-development-specific semantics in `sm-workflow` while allowing other Skill workflows to use the same required-interface runtime.
