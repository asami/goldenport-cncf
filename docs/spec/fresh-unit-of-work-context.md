# Fresh Unit of Work Execution Context

Status: focused validation passed; independent acceptance pending
Updated: 2026-10-17

`ExecutionContext.withFreshUnitOfWorkContext(ctx)` returns
`Consequence[ExecutionContext]`. For an `ExecutionContext.Instance`, it delegates
to the existing runtime-context rebind operation. The returned context owns a
fresh Unit of Work and interpreter bound to that returned context. Provider
steps execute through the rebound interpreter and observe that same context.

The operation preserves the selected core execution profile, identity
generation, execution control, runtime capabilities and parameters, security
and session, framework settings, and existing response lineage. It does not
select a new invocation, grant authority, or change runtime rebind internals.

Other ExecutionContext implementations fail with `operationInvalid` and
`execution-context-uow-rebind-unsupported`. This helper introduces no locks,
scheduling, retries, or cancellation policy.

Executable specification: `ExecutionContextFreshUnitOfWorkSpec` covers fresh
binding, a real `provider_step`, preservation, and the unsupported-context
failure. Normal focused validation remains owned by the consuming workflow.
