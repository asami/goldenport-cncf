# StateMachine API/SPI Runtime

## Foundation

CNCF provides one StateMachine API/SPI runtime foundation reused by Workflow and other StateMachine-based facilities.

```text
StateMachine Runtime
  Provided API dispatch
  Required SPI provider resolution
  Action execution
  ActionExecution = Completed | Suspended | Failed
  durable Continuation/resume
  provider compatibility/admission
```

## SPI provider resolution

Required operations are resolved through provider bindings independent from the Action implementation.

```text
StateMachine SPI
  -> local typed provider
  -> external continuation provider
  -> deterministic test provider
```

A provider may itself use another Component/StateMachine/Workflow API. Future runtime binding can select local/direct or REST transport without changing the caller Action.

Terminology is layered rather than interchangeable:

- Required SPI is the protocol-independent typed operation contract.
- Provider SPI is the component-programmer implementation boundary constructed
  and injected through ComponentFactory.
- Continuation Protocol is entered only when ActionExecution returns
  `Suspended(Continuation)`.
- Continuation SPI Projection is the durable external IoC port over that
  suspended Required SPI operation.

The runtime persists suspension before publishing a Continuation request. An
injected adapter may project it to Skill, Human, UI, or a remote executor; the
StateMachine runtime does not depend on any of those host-specific APIs.

## Workflow

Workflow Runtime is a consumer/specialization of this foundation. Workflow API/SPI is a projection of its StateMachine API/SPI, with Workflow-specific metadata layered above it.

## Assemble direction

Future assemble/runtime support should bind logical StateMachine SPI operations to provided APIs and separately resolve deployment transport.

```text
bind Required SPI -> Provided API
runtime placement -> Local | REST | future transport
```

## Reuse

Keep the foundation generic enough for Entity lifecycle, Job, UI/client and other StateMachine uses. Skill Workflow Support remains a CNCF projection over suspended external SPI operations, not part of StateMachine semantics.
