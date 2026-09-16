# Workflow API Proxy and Transport Binding

## Contract

Caller Actions use a typed Workflow API/proxy. They do not use REST, Continuation, WorkflowRun correlation or service discovery directly.

```text
WorkflowApi[I, O]
  call(input) -> durable logical result
```

The runtime binds the API to a provider/transport.

```text
WorkflowBinding
  LocalWorkflowBinding
  RestWorkflowBinding
  future bindings
```

## Local binding

Uses typed in-runtime invocation where possible. It still preserves Workflow call identity and durable semantics; it is not defined merely as a stack-local Scala call.

## REST binding

Maps the same logical operation to a remote CNCF Workflow endpoint. Endpoint/discovery/authentication/transport retry are deployment/runtime concerns.

## Transparency invariant

Changing placement from local to REST must not require changes to caller Action implementation or Workflow StateMachine semantics.

## Callee suspension

If the callee suspends on its own Workflow SPI (Human/AI/etc.), the caller's logical Workflow call remains correlated and completes only when the callee produces its typed result or declared failure/cancel outcome.

## Scope

This is a follow-up runtime layer over the Workflow SPI/suspension foundation. The immediate Phase 77 target remains reliable Skill-driven Continuation execution.
